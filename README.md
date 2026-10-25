# The Voplica API

The public definitions of the Voplica API and the clients published from them: the protobuf files of every public
RPC, the OpenAPI document of its JSON API and the TypeScript client `@voplica/connect`. The documentation lives at
<https://docs.voplica.com/>, built from this tree by the
documentation's own private repository.

| Path | What it holds |
|---|---|
| `proto/` | The protobuf definitions: every public RPC, and the types they use. |
| `openapi/voplica-api.openapi.yaml` | The OpenAPI 3.1 document of the JSON API, one `POST` per RPC. |
| `buf.yaml` | The [buf](https://buf.build) module of `proto/`, so `buf lint`, `buf breaking` and `buf generate` work here. |
| `clients/typescript/` | `@voplica/connect`, the TypeScript client. |
| `VERSION`, `CHANGELOG.md` | The version this tree publishes, and what each version changed. |
| `LICENSE` | The Apache License 2.0. |

## A first call

Create an API key in the studio, at <https://voplica.com/profile/api-keys>, then ask for your account's balance:

```sh
curl https://api.voplica.com/voplica.api.payment.PaymentService/GetBillingInfo \
  -H "Authorization: Bearer $VOPLICA_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{}'
```

Every RPC works the same way: a `POST` of its request message, as JSON, to `/<package>.<Service>/<Method>`. This is
the Connect protocol's JSON mode, so any Connect client works too. [Getting started](https://docs.voplica.com/getting-started/)
and [Authentication](https://docs.voplica.com/authentication/) explain the rest, and the
[API reference](https://docs.voplica.com/api/reference/) lists every public RPC.

## The TypeScript client

From npmjs.com — no `.npmrc`, no token — install the package, Connect and its Node transport:

```sh
npm install @voplica/connect @connectrpc/connect @connectrpc/connect-node
```

The package is also published to GitHub Packages, which needs a GitHub token with the `read:packages` scope even for a
public package. To install from there, put these two lines in your project's `.npmrc`, with the token in the
`GITHUB_TOKEN` environment variable, and run the same `npm install`:

```ini
@voplica:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}
```

[Clients](https://docs.voplica.com/clients/) shows a first call. It also shows how to generate a client in another
language from `proto/` with `buf`, which needs no token.

## Versions

`VERSION` holds the version this tree publishes. Every change to the public definitions publishes a higher version.
Each version is tagged `v<VERSION>` and ships a client of the same version. `CHANGELOG.md` lists what each version
added or changed, newest first.

## This tree is generated

`proto/`, `openapi/`, `buf.yaml`, `VERSION`, `CHANGELOG.md`, `LICENSE` and this README are generated from the Voplica
platform's definitions for every release, so pull requests to them cannot be merged. To suggest a change, open a
discussion in [voplica/help](https://github.com/voplica/help).

## Security

To report a vulnerability, follow [SECURITY.md](https://github.com/voplica/help/blob/main/SECURITY.md) in voplica/help.
Never report one in a public issue.

## Licences

The definitions, the client and the code samples in the documentation are licensed under the
[Apache License 2.0](LICENSE); the documentation's text is licensed under CC BY 4.0.
