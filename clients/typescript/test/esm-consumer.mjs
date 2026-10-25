// A plain Node ES module using the installed package the way an API user does: the root, a subpath, a client.
// The test suite runs it from an unpacked `npm pack` tarball (client.test.mjs) with the expected services
// (`package.Service`, comma-separated) as its argument; it exits non-zero on any mismatch.
import * as voplica from '@voplica/connect';
import { ModelService } from '@voplica/connect/voplica/api/ai/model/model_pb';
import { createClient } from '@connectrpc/connect';
import { createConnectTransport } from '@connectrpc/connect-node';

const expected = new Set((process.argv[2] ?? '').split(',').filter(Boolean));
// An ES module namespace of the barrel has no default export; a CommonJS build, imported here, would have one.
if ('default' in voplica) throw new Error('@voplica/connect loaded as CommonJS, not as an ES module');
const services = Object.values(voplica).filter((value) => value?.kind === 'service');
const names = new Set(services.map((s) => s.typeName));
if (expected.size === 0 || names.size !== expected.size || [...expected].some((name) => !names.has(name))) {
	throw new Error(`the root exports ${[...names].sort().join(', ')}; expected ${[...expected].sort().join(', ')}`);
}
if (ModelService.typeName !== 'voplica.api.ai.model.ModelService') throw new Error(`subpath: ${ModelService.typeName}`);
if (voplica.ModelService !== ModelService) throw new Error('the root and the subpath load different modules');
const client = createClient(voplica.ModelVariantService, createConnectTransport({ baseUrl: 'https://api.voplica.com', httpVersion: '1.1' }));
if (typeof client.getRecommendedBaseModelVariant !== 'function') throw new Error('no getRecommendedBaseModelVariant');
console.log(`@voplica/connect: ${services.length} services`);
