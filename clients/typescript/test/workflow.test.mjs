// node --test: the publish workflow's registry credentials (.github/workflows/client.yml): the publish and smoke jobs use
// the run's own GITHUB_TOKEN with the packages permission (write to publish, read for the smoke install) and mint no App
// token - GitHub Packages' npm registry accepts GITHUB_TOKEN for a package linked to the workflow's repository, while an
// App installation token was refused the creation of the organisation package (403, 2026-10-25). The publish-npmjs job
// holds no token at all: npm Trusted Publishing exchanges the job's OIDC identity for the publish (npm 11.5.1 or later,
// Node 22.14.0 or later, id-token: write) and signs the provenance with it.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const workflow = readFileSync(new URL('../../../.github/workflows/client.yml', import.meta.url), 'utf8').split('\n');

/** The trimmed lines of one job. */
function job(name) {
	const start = workflow.indexOf(`  ${name}:`);
	assert.ok(start >= 0, `no job ${name}`);
	const end = workflow.findIndex((line, i) => i > start && /^ {2}\S/.test(line));
	return workflow.slice(start, end < 0 ? undefined : end).map((line) => line.trim());
}

test('the publish and smoke jobs use the run token with the packages permission and mint no App token', () => {
	for (const [name, access] of [['publish', 'write'], ['smoke', 'read']]) {
		const lines = job(name);
		assert.ok(lines.some((line) => line.startsWith(`packages: ${access}`)), `${name}: the job does not grant packages: ${access}`);
		assert.ok(lines.includes('NODE_AUTH_TOKEN: ${{ secrets.GITHUB_TOKEN }}'), `${name}: the registry step does not use GITHUB_TOKEN`);
		assert.ok(!lines.some((line) => line.includes('create-github-app-token')), `${name}: an App token is minted`);
		assert.ok(!lines.some((line) => line.startsWith('permission-')), `${name}: an App permission is requested`);
	}
});

/** [x, y, z] >= [a, b, c]. */
function atLeast(version, minimum) {
	const differs = version.findIndex((part, i) => part !== minimum[i]);
	return differs < 0 || version[differs] > minimum[differs];
}

test('the npmjs.com job runs on a version tag once its trusted publisher is registered, with the OIDC permission alone', () => {
	const lines = job('publish-npmjs');
	assert.ok(lines.includes("if: startsWith(github.ref, 'refs/tags/v') && vars.NPMJS_TRUSTED_PUBLISHING == 'true'"), 'publish-npmjs: the gate is not the tag and NPMJS_TRUSTED_PUBLISHING');
	assert.ok(lines.includes('needs: build'), 'publish-npmjs: it does not wait for the build job and its tarball');
	assert.ok(lines.some((line) => /^runs-on: ubuntu-\S+$/.test(line)), 'publish-npmjs: not a GitHub-hosted runner (npm trusts no self-hosted one)');
	const from = lines.indexOf('permissions:');
	assert.ok(from >= 0, 'publish-npmjs: the job grants no permissions of its own');
	const granted = [];
	for (const line of lines.slice(from + 1)) {
		const scope = /^([a-z-]+): (read|write|none)\b/.exec(line);
		if (!scope) break;
		granted.push(`${scope[1]}: ${scope[2]}`);
	}
	assert.deepEqual(granted.sort(), ['contents: read', 'id-token: write'], 'publish-npmjs: the permissions are not id-token: write and contents: read');
	assert.ok(!lines.some((line) => line.startsWith('environment:')), 'publish-npmjs: an environment (the trusted publisher is registered without one)');
});

test('the npmjs.com job holds no token: npm publishes by Trusted Publishing', () => {
	const lines = job('publish-npmjs').filter((line) => !line.startsWith('#'));
	assert.ok(!lines.some((line) => line.includes('NODE_AUTH_TOKEN')), 'publish-npmjs: a NODE_AUTH_TOKEN');
	assert.ok(!lines.some((line) => line.includes('secrets.')), 'publish-npmjs: a secret');
	// setup-node with a registry-url writes an .npmrc that sends NODE_AUTH_TOKEN, a placeholder when none is set
	assert.ok(!lines.some((line) => line.startsWith('registry-url:')), 'publish-npmjs: setup-node writes a token line for a registry');
	assert.ok(!lines.some((line) => line.includes('create-github-app-token')), 'publish-npmjs: an App token is minted');
	assert.ok(!lines.some((line) => line.startsWith('permission-')), 'publish-npmjs: an App permission is requested');
});

test('the npmjs.com job publishes the build tarball with an npm that publishes by Trusted Publishing, with provenance, public', () => {
	const lines = job('publish-npmjs');
	const npm = lines.map((line) => /^npm install -g npm@(\d+)\.(\d+)\.(\d+)$/.exec(line)).find(Boolean);
	assert.ok(npm, 'publish-npmjs: npm is not installed at an exact version');
	assert.ok(atLeast(npm.slice(1).map(Number), [11, 5, 1]), `publish-npmjs: npm ${npm.slice(1).join('.')} is older than 11.5.1, the first to publish by Trusted Publishing`);
	assert.ok(lines.includes(`test "$(npm --version)" = ${npm.slice(1).join('.')}`), 'publish-npmjs: the npm that runs is not checked');
	const node = lines.map((line) => /^node-version: "?(\d+)(?:\.(\d+)\.(\d+))?"?$/.exec(line)).find(Boolean);
	assert.ok(node, 'publish-npmjs: no Node version');
	// a bare major is its newest release; Trusted Publishing needs Node 22.14.0 or later
	assert.ok(node[2] === undefined ? Number(node[1]) >= 22 : atLeast(node.slice(1).map(Number), [22, 14, 0]), `publish-npmjs: Node ${node[0]}`);
	const artifact = lines.indexOf('- uses: actions/download-artifact@v8');
	assert.ok(artifact >= 0 && lines[artifact + 2] === 'name: voplica-connect', 'publish-npmjs: it does not take the tarball the build job packed');
	assert.ok(lines.includes('version="${GITHUB_REF_NAME#v}"'), 'publish-npmjs: the version is not the tag (v + VERSION, which the build job checks)');
	const publish = lines.filter((line) => /^npm (stage )?publish\b/.test(line));
	assert.deepEqual(publish, ['npm publish "./package/voplica-connect-$version.tgz" --provenance --access public --registry https://registry.npmjs.org']);
});
