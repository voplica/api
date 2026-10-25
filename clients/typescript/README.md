# @voplica/connect

The Voplica API for TypeScript: every message, enum and service of the published protobuf definitions, generated with
[protobuf-es](https://github.com/bufbuild/protobuf-es), for [Connect](https://connectrpc.com) clients in Node and in
bundlers. ES modules; `require()` works on Node 22.12 or later.

The documentation: https://docs.voplica.com/clients/

## Install

From npmjs.com — no `.npmrc`, no token — install the package, Connect and its Node transport:

```sh
npm install @voplica/connect @connectrpc/connect @connectrpc/connect-node
```

The package is also published to GitHub Packages, which needs a GitHub token with the `read:packages` scope even for a
public package. To install from there, put these two lines in your project's `.npmrc`, with the token in the `GITHUB_TOKEN`
environment variable, and run the same `npm install`:

```ini
@voplica:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

## A client

Every service of the API is exported from the package root. A key goes in the `Authorization` header of every call; an
interceptor adds it. API keys are for server-side use, never in a browser page.

```ts
import { createClient, type Interceptor } from "@connectrpc/connect";
import { createConnectTransport } from "@connectrpc/connect-node";
import { PaymentService } from "@voplica/connect";

const apiKey = process.env.VOPLICA_API_KEY;
if (!apiKey) throw new Error("Set VOPLICA_API_KEY to your Voplica API key");

const authorization: Interceptor = (next) => (request) => {
  request.header.set("Authorization", `Bearer ${apiKey}`);
  return next(request);
};

const transport = createConnectTransport({
  baseUrl: "https://api.voplica.com",
  httpVersion: "1.1",
  useBinaryFormat: false, // JSON, the protocol the documentation describes
  interceptors: [authorization],
});

const payments = createClient(PaymentService, transport);
const { billingInfo } = await payments.getBillingInfo({});
console.log(billingInfo);
```

A failed call throws a `ConnectError`; branch on its `code`:

```ts
import { Code, ConnectError } from "@connectrpc/connect";

try {
  await payments.getBillingInfo({});
} catch (error) {
  if (error instanceof ConnectError && error.code === Code.Unauthenticated) {
    // the key is unknown, revoked or expired
  }
  throw error;
}
```

## Names

The root exports every name of every generated module. When two protobuf packages declare the same name, the root
exports each under its package's last segment in PascalCase: `MPAFilter` of `voplica.api.ai.dataset` is
`DatasetMPAFilter` at the root, and `MPAFilter` of `voplica.api.character` is `CharacterMPAFilter` (with their schemas
`DatasetMPAFilterSchema` and `CharacterMPAFilterSchema`). A later version can share a name that is unique today, and
the root then renames it the same way. Each module imported by its own path keeps its plain names in every version:

```ts
import { MPAFilterSchema } from "@voplica/connect/voplica/api/ai/dataset/dataset_pb";
```

## Versions and licence

The package's version is the API's version (`VERSION` at the root of [voplica/api](https://github.com/voplica/api)).
Licensed under the Apache License 2.0.
