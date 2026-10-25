// The registry smoke of .github/workflows/client.yml: run in an empty directory after
// `npm install @voplica/connect@<VERSION> @connectrpc/connect-node@2.1.2`, with the services the published protos
// declare (`package.Service`, comma-separated: `node test/services.mjs proto`) as its argument. The published package
// must load in plain Node ESM, export exactly those services, and make a client for the public host. Nothing is called.
import * as voplica from '@voplica/connect';
import { createClient } from '@connectrpc/connect';
import { createConnectTransport } from '@connectrpc/connect-node';

const expected = new Set((process.argv[2] ?? '').split(',').filter(Boolean));
if ('default' in voplica) throw new Error('@voplica/connect loaded as CommonJS, not as an ES module');
const services = Object.values(voplica).filter((value) => value?.kind === 'service');
const names = new Set(services.map((s) => s.typeName));
if (expected.size === 0 || names.size !== expected.size || [...expected].some((name) => !names.has(name))) {
	throw new Error(`the package exports ${[...names].sort().join(', ')}; the protos declare ${[...expected].sort().join(', ')}`);
}
const transport = createConnectTransport({ baseUrl: 'https://api.voplica.com', httpVersion: '1.1' });
for (const service of services) createClient(service, transport);
// A method named as the protos name it (ModelVariantService.GetRecommendedBaseModelVariant), not read from the service.
if (typeof createClient(voplica.ModelVariantService, transport).getRecommendedBaseModelVariant !== 'function') {
	throw new Error('ModelVariantService has no getRecommendedBaseModelVariant');
}
console.log(`@voplica/connect: ${services.length} services, a client for https://api.voplica.com`);
