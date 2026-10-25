// node --test, against the built package (dist/): the descriptors, a client, the barrel, and the packed tarball as an
// installed dependency — Node ES modules, CommonJS `require()`, TypeScript resolution. The expected services are the
// ones the protos declare (PROTO_DIR, by default ../../proto, the tree the package is generated from).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { create } from '@bufbuild/protobuf';
import { createClient, createRouterTransport } from '@connectrpc/connect';
import { declaredServices } from './services.mjs';

const pkg = fileURLToPath(new URL('..', import.meta.url));
const protoDir = process.env.PROTO_DIR ?? join(pkg, '..', '..', 'proto');
const load = () => import('../dist/index.js');
const barrel = () => import('../scripts/barrel.mjs');

function fakeModule(root, path, protoPackage, body) {
	const file = join(root, path);
	mkdirSync(dirname(file), { recursive: true });
	writeFileSync(file, `// @generated from file ${path.replace(/_pb\.ts$/, '.proto')} (package ${protoPackage}, syntax proto3)\n${body}`);
}

/** The packed tarball unpacked as an installed dependency; its runtime dependencies (read from its own package.json)
 *  and connect-node linked beside it. Returns the temporary directory. */
function installPacked() {
	const tmp = mkdtempSync(join(tmpdir(), 'voplica-connect-'));
	const [{ filename }] = JSON.parse(execFileSync('npm', ['pack', '--json', '--pack-destination', tmp], { cwd: pkg, encoding: 'utf8' }));
	const target = join(tmp, 'node_modules', '@voplica', 'connect');
	mkdirSync(target, { recursive: true });
	execFileSync('tar', ['-xzf', join(tmp, filename), '-C', target, '--strip-components=1']);
	const packed = JSON.parse(readFileSync(join(target, 'package.json'), 'utf8'));
	for (const dependency of [...Object.keys(packed.dependencies ?? {}), '@connectrpc/connect-node']) {
		const link = join(tmp, 'node_modules', dependency);
		mkdirSync(dirname(link), { recursive: true });
		symlinkSync(join(pkg, 'node_modules', dependency), link, 'dir');
	}
	return tmp;
}

test('every published service has a descriptor', async () => {
	const expected = declaredServices(protoDir);
	assert.ok(expected.size > 0, `no service is declared under ${protoDir}`);
	const services = Object.values(await load()).filter((value) => value?.kind === 'service');
	assert.deepEqual(new Set(services.map((s) => s.typeName)), expected);
	assert.equal(services.length, expected.size, 'a service is exported twice');
});

test('a client round-trips over a router transport', async () => {
	const api = await load();
	const transport = createRouterTransport(({ service }) => {
		service(api.ModelVariantService, {
			getRecommendedBaseModelVariant: () =>
				create(api.GetRecommendedBaseModelVariantResponseSchema, { modelVariant: { variantId: 'mv-1', variantName: 'v1' } }),
		});
	});
	const client = createClient(api.ModelVariantService, transport);
	const response = await client.getRecommendedBaseModelVariant({});
	assert.equal(response.$typeName, 'voplica.api.ai.model.GetRecommendedBaseModelVariantResponse');
	assert.equal(response.modelVariant?.$typeName, 'voplica.types.ai.model.ModelVariant');
	assert.equal(response.modelVariant?.variantId, 'mv-1');
	assert.equal(response.modelVariant?.variantName, 'v1');
});

test('the barrel re-exports a colliding name under its package prefix', async () => {
	const api = await load();
	assert.equal(api.DatasetMPAFilterSchema?.typeName, 'voplica.api.ai.dataset.MPAFilter');
	assert.equal(api.CharacterMPAFilterSchema?.typeName, 'voplica.api.character.MPAFilter');
	assert.equal('MPAFilterSchema' in api, false);
	const types = readFileSync(join(pkg, 'dist', 'index.d.ts'), 'utf8');
	assert.match(types, /\bMPAFilter as DatasetMPAFilter\b/);
	assert.match(types, /\bMPAFilter as CharacterMPAFilter\b/);
	assert.equal([...types.matchAll(/\bMPAFilter\b(?! as )/g)].length, 0, 'MPAFilter is exported unaliased');
});

test('the package imports in plain Node ESM from its packed tarball', () => {
	const expected = declaredServices(protoDir);
	const tmp = installPacked();
	try {
		copyFileSync(join(pkg, 'test', 'esm-consumer.mjs'), join(tmp, 'esm-consumer.mjs'));
		const out = execFileSync(process.execPath, [join(tmp, 'esm-consumer.mjs'), [...expected].join(',')], { cwd: tmp, encoding: 'utf8' });
		assert.match(out, new RegExp(`@voplica/connect: ${expected.size} services`));
	} finally {
		rmSync(tmp, { recursive: true, force: true });
	}
});

test('the package loads with require() in CommonJS from its packed tarball', () => {
	const tmp = installPacked();
	try {
		copyFileSync(join(pkg, 'test', 'cjs-consumer.cjs'), join(tmp, 'cjs-consumer.cjs'));
		const run = spawnSync(process.execPath, [join(tmp, 'cjs-consumer.cjs')], { cwd: tmp, encoding: 'utf8' });
		assert.equal(run.status, 0, run.stderr);
		assert.match(run.stdout, /require: voplica\.api\.ai\.model\.ModelVariantService/);
	} finally {
		rmSync(tmp, { recursive: true, force: true });
	}
});

test('TypeScript resolves the root and a subpath with NodeNext and with Bundler', () => {
	const tmp = installPacked();
	try {
		writeFileSync(join(tmp, 'package.json'), '{ "type": "module" }\n');
		writeFileSync(join(tmp, 'consumer.ts'), [
			"import { ModelVariantService, type GetRecommendedBaseModelVariantResponse } from '@voplica/connect';",
			"import { ModelService, type GetModelRequest } from '@voplica/connect/voplica/api/ai/model/model_pb';",
			'export const names: string[] = [ModelVariantService.typeName, ModelService.typeName];',
			'export type Exchange = [GetModelRequest, GetRecommendedBaseModelVariantResponse];',
			'',
		].join('\n'));
		for (const resolution of ['NodeNext', 'Bundler']) {
			const config = join(tmp, `tsconfig.${resolution}.json`);
			const compilerOptions = { target: 'ES2022', module: resolution === 'Bundler' ? 'ESNext' : 'NodeNext', moduleResolution: resolution, strict: true, noEmit: true, skipLibCheck: true, types: [] };
			writeFileSync(config, JSON.stringify({ compilerOptions, files: ['consumer.ts'] }));
			const run = spawnSync(process.execPath, [join(pkg, 'node_modules', 'typescript', 'bin', 'tsc'), '-p', config], { cwd: tmp, encoding: 'utf8' });
			assert.equal(run.status, 0, `${resolution}: ${run.stdout}${run.stderr}`);
		}
	} finally {
		rmSync(tmp, { recursive: true, force: true });
	}
});

test('the barrel writes .js specifiers, export type for types and aliases only the colliding names', async () => {
	const { generateBarrel } = await barrel();
	const src = mkdtempSync(join(tmpdir(), 'barrel-'));
	try {
		fakeModule(src, 'voplica/api/ai/dataset/dataset_pb.ts', 'voplica.api.ai.dataset', 'export type MPAFilter = {};\nexport const MPAFilterSchema = 1;\nexport const DatasetService = 2;\n');
		fakeModule(src, 'voplica/api/character/character_pb.ts', 'voplica.api.character', 'export type MPAFilter = {};\nexport const MPAFilterSchema = 1;\nexport enum Pose { A = 0 }\n');
		fakeModule(src, 'voplica/types/base/base_pb.ts', 'voplica.types.base', 'export type Thing = {};\nexport const ThingSchema = 1;\n');
		const text = generateBarrel(src);
		assert.match(text, /^export \* from "\.\/voplica\/types\/base\/base_pb\.js";$/m);
		assert.match(text, /^export type \{ MPAFilter as DatasetMPAFilter \} from "\.\/voplica\/api\/ai\/dataset\/dataset_pb\.js";$/m);
		assert.match(text, /^export \{ MPAFilterSchema as DatasetMPAFilterSchema, DatasetService \} from "\.\/voplica\/api\/ai\/dataset\/dataset_pb\.js";$/m);
		assert.match(text, /^export type \{ MPAFilter as CharacterMPAFilter \} from "\.\/voplica\/api\/character\/character_pb\.js";$/m);
		assert.match(text, /^export \{ MPAFilterSchema as CharacterMPAFilterSchema, Pose \} from "\.\/voplica\/api\/character\/character_pb\.js";$/m);
		assert.equal(generateBarrel(src), text, 'two runs differ');
	} finally {
		rmSync(src, { recursive: true, force: true });
	}
});

test('the barrel throws when two colliding modules derive the same prefix', async () => {
	const { generateBarrel } = await barrel();
	const src = mkdtempSync(join(tmpdir(), 'barrel-'));
	try {
		fakeModule(src, 'acme/api/engagement/engagement_pb.ts', 'acme.api.engagement', 'export enum EngagementEventKind { A = 0 }\n');
		fakeModule(src, 'acme/types/engagement/engagement_pb.ts', 'acme.types.engagement', 'export enum EngagementEventKind { A = 0 }\n');
		assert.throws(() => generateBarrel(src), /"EngagementEventKind" is exported by .* which derive the same alias prefix "Engagement"/);
	} finally {
		rmSync(src, { recursive: true, force: true });
	}
});

test('the expected services survive comments, and strings that look like comments', () => {
	const dir = mkdtempSync(join(tmpdir(), 'protos-'));
	try {
		mkdirSync(join(dir, 'acme', 'mime'), { recursive: true });
		writeFileSync(join(dir, 'acme', 'mime', 'mime.proto'), [
			'syntax = "proto3";',
			'package acme.mime;',
			'// service Commented {}',
			'option (accept) = "image/*";',
			'service RealService {}',
			'/* a note, closed here */',
			'/* service AlsoCommented {} */',
			"option (other) = 'a // b';",
			'service SecondService {',
			'}',
			'',
		].join('\n'));
		assert.deepEqual(declaredServices(dir), new Set(['acme.mime.RealService', 'acme.mime.SecondService']));
	} finally {
		rmSync(dir, { recursive: true, force: true });
	}
});

test('the barrel writes src/index.ts when it is run through a symlinked path', () => {
	const tmp = mkdtempSync(join(tmpdir(), 'barrel-link-'));
	try {
		mkdirSync(join(tmp, 'package', 'scripts'), { recursive: true });
		copyFileSync(join(pkg, 'scripts', 'barrel.mjs'), join(tmp, 'package', 'scripts', 'barrel.mjs'));
		fakeModule(join(tmp, 'package', 'src'), 'x_pb.ts', 'acme.x', 'export type X = {};\nexport const XSchema = 1;\n');
		symlinkSync(join(tmp, 'package'), join(tmp, 'link'), 'dir');
		execFileSync(process.execPath, [join(tmp, 'link', 'scripts', 'barrel.mjs')], { cwd: tmp, encoding: 'utf8' });
		assert.match(readFileSync(join(tmp, 'package', 'src', 'index.ts'), 'utf8'), /^export \* from "\.\/x_pb\.js";$/m);
	} finally {
		rmSync(tmp, { recursive: true, force: true });
	}
});
