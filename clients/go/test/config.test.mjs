// node --test: the Go configuration (clients/go) and its workflow (.github/workflows/go.yml). No network: buf's own
// parser reads the template offline (`buf config migrate --diff`, with the buf of clients/typescript or $BUF), and the
// rest are the files' shapes. The generation itself (remote plugins on the Buf Schema Registry) and the Go build run in
// the Go workflow.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { isAbsolute, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const read = (path) => readFileSync(join(root, path), 'utf8');
const template = 'clients/go/buf.gen.yaml';
const generate = `buf generate --template ${template}`;
const plugins = ['buf.build/protocolbuffers/go', 'buf.build/grpc/go'];

/** The values of the `key: value` lines of a YAML file (a leading "- " and a trailing comment dropped), in order. */
function values(path, key) {
	return read(path)
		.split('\n')
		.map((line) => line.trim().replace(/^- /, ''))
		.filter((line) => line.startsWith(`${key}:`))
		.map((line) => line.slice(key.length + 1).replace(/\s+#.*$/, '').trim());
}

/** The module path and the required versions of clients/go/go.mod. */
function goMod() {
	const text = read('clients/go/go.mod');
	const requires = new Map();
	for (const line of text.split('\n')) {
		const match = /^\s*(?:require\s+)?([a-z0-9.-]+\.[a-z]+\/\S+)\s+(v\S+)\s*(?:\/\/.*)?$/.exec(line);
		if (match) requires.set(match[1], match[2]);
	}
	return { module: /^module (\S+)$/m.exec(text)?.[1], requires };
}

/** a >= b, for versions vX.Y.Z (a pre-release suffix is ignored). */
function atLeast(a, b) {
	const [x, y] = [a, b].map((v) => v.replace(/^v/, '').split(/[.-]/).slice(0, 3).map(Number));
	const differs = x.findIndex((part, i) => part !== y[i]);
	return differs < 0 || x[differs] > y[differs];
}

/** The steps of the Go workflow's build job, each as its trimmed lines. */
function steps() {
	const workflow = read('.github/workflows/go.yml').split('\n');
	const start = workflow.indexOf('    steps:');
	assert.ok(start >= 0, 'go.yml: the build job has no steps');
	const list = [];
	for (const line of workflow.slice(start + 1)) {
		if (/^ {0,5}\S/.test(line)) break;
		if (line.startsWith('      - ')) list.push([]);
		if (list.length > 0 && line.trim() !== '') list.at(-1).push(line.trim().replace(/^- /, ''));
	}
	return list;
}

test('buf reads the template: a valid v2 buf.gen.yaml', (t) => {
	const buf = process.env.BUF ? (isAbsolute(process.env.BUF) ? process.env.BUF : join(root, process.env.BUF)) : join(root, 'clients/typescript/node_modules/.bin/buf');
	if (!existsSync(buf)) {
		assert.ok(!process.env.CI, `no buf at ${buf}: run npm ci in clients/typescript first`);
		t.skip(`no buf at ${buf} (npm ci in clients/typescript, or BUF=<path>)`);
		return;
	}
	const run = spawnSync(buf, ['config', 'migrate', '--buf-gen-yaml', template, '--diff'], { cwd: root, encoding: 'utf8' });
	const output = `${run.stdout}${run.stderr}`;
	assert.equal(run.status, 0, `buf refuses ${template}:\n${output}`);
	assert.match(output, /is a v2 file, no migration required/, `buf did not read ${template} as v2:\n${output}`);
});

test('the template names only remote plugins pinned to an exact version', () => {
	assert.deepEqual(values(template, 'local'), [], 'a local plugin: a checkout of the repository has no plugin binaries');
	assert.deepEqual(values(template, 'protoc_builtin'), [], 'a protoc built-in plugin');
	const remotes = values(template, 'remote');
	for (const remote of remotes) {
		assert.match(remote, /^buf\.build\/[a-z0-9-]+\/[a-z0-9-]+:v\d+\.\d+\.\d+$/, `${remote} is not pinned to an exact version`);
	}
	assert.deepEqual(remotes.map((remote) => remote.split(':')[0]), plugins);
});

test('buf runs the template from the repository root, where its paths start', () => {
	assert.deepEqual(values(template, 'directory'), ['proto'], 'the input is not proto/ of the root');
	assert.ok(existsSync(join(root, 'proto')), 'there is no proto/ at the root');
	assert.match(read('buf.yaml'), /^\s*- path: proto$/m, 'the root buf.yaml does not hold the module proto');
	assert.deepEqual(values(template, 'out'), plugins.map(() => 'clients/go/gen'), 'every plugin writes into clients/go/gen (relative to the root)');
	assert.deepEqual(values(template, 'opt'), plugins.map(() => 'paths=source_relative'));
	const generateSteps = steps().filter((step) => step.some((line) => line.includes(generate)));
	assert.equal(generateSteps.length, 1, `go.yml: no single step runs ${generate}`);
	assert.ok(!generateSteps[0].some((line) => line.startsWith('working-directory:')), `go.yml: ${generate} does not run at the root`);
	for (const [, , block] of read('.github/workflows/go.yml').matchAll(/^( *)defaults:\n((?:\1 +\S.*\n|\n)*)/gm)) {
		assert.ok(!block.includes('working-directory:'), 'go.yml: a default working directory');
	}
	const readme = /```sh\n([\s\S]*?)```/.exec(read('clients/go/README.md'))?.[1].split('\n') ?? [];
	const at = readme.indexOf(generate);
	assert.ok(at > 0, `README: the first sh block does not run ${generate}`);
	assert.equal(readme.slice(0, at).filter((line) => line.startsWith('cd ')).join(), 'cd api', `README: ${generate} does not run in the clone's root`);
});

test('the generated packages belong to the module of clients/go/go.mod', () => {
	const { module, requires } = goMod();
	assert.equal(module, 'github.com/voplica/api/clients/go');
	assert.deepEqual(values(template, 'file_option'), ['go_package_prefix']);
	assert.deepEqual(values(template, 'value'), [`${module}/gen`], 'the Go packages are not under the module');
	assert.equal(values(template, 'enabled')[0], 'true', 'managed mode is off');
	assert.match(read('clients/go/.gitignore'), /^\/?gen\/$/m, 'gen/ is not ignored');
	const generator = values(template, 'remote').find((remote) => remote.startsWith('buf.build/protocolbuffers/go:')).split(':')[1];
	assert.ok(atLeast(requires.get('google.golang.org/protobuf') ?? 'v0.0.0', generator), `the protobuf runtime is older than its generator ${generator}`);
	assert.ok(requires.has('google.golang.org/grpc'), 'the gRPC stubs need google.golang.org/grpc');
});

test('the README shows example/main.go as it is, and names no API version', () => {
	const readme = read('clients/go/README.md');
	const blocks = [...readme.matchAll(/```go\n([\s\S]*?)```/g)].map((match) => match[1]);
	assert.deepEqual(blocks, [read('clients/go/example/main.go')], 'README: the Go block is not example/main.go');
	const version = read('VERSION').trim();
	const named = new RegExp(`(?<![\\w.])v?${version.replaceAll('.', '\\.')}(?![\\w.])`);
	for (const [name, text] of [['README.md', readme], ['example/main.go', blocks[0]]]) {
		assert.doesNotMatch(text, named, `${name} names the version ${version}: it is read from VERSION`);
		assert.doesNotMatch(text, /(?:--branch|checkout|tag:?)\s+v\d+\.\d+\.\d+/, `${name} names a release tag`);
	}
});

test('the Go workflow is triggered by the Go client, its inputs and tags, and only reads', () => {
	const workflow = read('.github/workflows/go.yml');
	const on = /^on:\n([\s\S]*?)^\S/m.exec(workflow)?.[1] ?? '';
	assert.match(on, /^ {2}push:\n {4}tags: \["v\*"\]$/m);
	assert.match(on, /^ {2}workflow_dispatch:$/m);
	for (const path of ['clients/go/**', 'proto/**', 'buf.yaml', 'clients/typescript/package-lock.json', '.github/workflows/go.yml']) {
		assert.ok(on.includes(`      - "${path}"`), `go.yml: a pull request that changes ${path} does not run it`);
	}
	assert.match(workflow, /^permissions:\n {2}contents: read\n(?! )/m, 'go.yml: the permissions are not contents: read alone');
	assert.equal(workflow.match(/^ *permissions:/gm).length, 1, 'go.yml: a job asks for permissions of its own');
	assert.ok(!workflow.includes('create-github-app-token'), 'go.yml: an App token is minted');
	assert.ok(!/^\s*permission-/m.test(workflow), 'go.yml: an App permission is requested');
	for (const secret of workflow.match(/secrets\.\w+/g) ?? []) {
		assert.equal(secret, 'secrets.BUF_TOKEN', 'go.yml: a secret other than the optional BUF_TOKEN');
	}
});

test('the Go workflow installs buf, checks this file, generates, then builds, vets and tests the module', () => {
	const list = steps();
	const at = (predicate, what) => {
		const index = list.findIndex((step) => step.some(predicate));
		assert.ok(index >= 0, `go.yml: no step ${what}`);
		return index;
	};
	const checkout = at((line) => line.startsWith('uses: actions/checkout@'), 'checks out the repository');
	assert.ok(list[checkout].includes('persist-credentials: false'), 'go.yml: the checkout keeps the token');
	const npm = at((line) => line === 'run: npm ci', 'installs buf with npm ci');
	assert.ok(list[npm].includes('working-directory: clients/typescript'), 'go.yml: npm ci does not run in clients/typescript');
	const shape = at((line) => line === 'run: node --test "clients/go/test/*.test.mjs"', 'runs these tests');
	const gen = at((line) => line.includes(generate), `runs ${generate}`);
	assert.ok(list[gen].some((line) => line === `clients/typescript/node_modules/.bin/${generate}`), "go.yml: the generation does not use clients/typescript's buf");
	const go = at((line) => line.startsWith('uses: actions/setup-go@'), 'installs Go');
	assert.ok(list[go].includes('go-version-file: clients/go/go.mod'), 'go.yml: Go is not the version go.mod names');
	const build = at((line) => line === 'go build ./...', 'builds the module');
	assert.ok(list[build].includes('working-directory: clients/go'), 'go.yml: the module is not built in clients/go');
	for (const command of ['go mod tidy -diff', 'go vet ./...', 'go test ./...']) {
		assert.ok(list[build].includes(command), `go.yml: the build step does not run ${command}`);
	}
	assert.ok(checkout < npm && npm < shape && shape < gen && gen < go && go < build, 'go.yml: the steps are out of order');
});
