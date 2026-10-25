// A CommonJS module using the installed package: `require()` of an ES module, which Node 22.12 or later supports.
// The test suite runs it from an unpacked `npm pack` tarball (client.test.mjs); it exits non-zero on any mismatch.
const voplica = require('@voplica/connect');
const { createClient } = require('@connectrpc/connect');

const service = voplica.ModelVariantService;
if (service?.typeName !== 'voplica.api.ai.model.ModelVariantService') throw new Error(`require: ${service?.typeName}`);
if (typeof createClient(service, { unary: async () => {}, stream: async () => {} }).getRecommendedBaseModelVariant !== 'function') {
	throw new Error('no getRecommendedBaseModelVariant');
}
console.log(`require: ${service.typeName}`);
