# Go

There is no published Go module for the Voplica API yet: you generate the Go code yourself from the published
definitions, with [buf](https://buf.build/docs/cli/installation/) and the configuration in this directory. You get the
messages, enums and gRPC service stubs of every file under `proto/`, as packages of the module
`github.com/voplica/api/clients/go`.

The native gRPC endpoint opens soon. Today the API is served as JSON, and the generated types give you type-safe
requests and responses for it: build a request, encode it with `protojson`, send it, and decode the answer into the
response type. The gRPC stubs are there for when the native endpoint opens.

## Generate

You need [Go](https://go.dev/dl/) 1.25 or later and the buf CLI, 1.36 or later. Clone the repository and generate at
its root:

```sh
git clone https://github.com/voplica/api.git
cd api
buf generate --template clients/go/buf.gen.yaml
```

Run `buf generate` at the root, as here: the template's paths start there. It writes the packages into
`clients/go/gen/`, which git ignores, and removes what an earlier run left there. The module's `go.mod` and `go.sum`
are already in `clients/go`, so the tree builds at once:

```sh
cd clients/go
go build ./...
```

The two plugins, `buf.build/protocolbuffers/go` and `buf.build/grpc/go` at the versions `buf.gen.yaml` pins, run on
the Buf Schema Registry. Without an account it allows 10 `buf generate` runs an hour from one address;
`buf registry login`, with a free account, raises the limit.

The default branch has the latest definitions. To build a release instead, clone its tag, `v` followed by the version
in [`VERSION`](../../VERSION), and generate the same way:

```sh
git clone --depth 1 --branch v<VERSION> https://github.com/voplica/api.git
```

## Use it in your module

The generated packages import each other by the module path, so your module points at your clone's `clients/go` with
a `replace` directive (here the clone is beside your module). Start a module if you have none, import the packages you
need, and let `go mod tidy` add the requirement:

```sh
go mod init example.com/yourapp
go mod edit -replace=github.com/voplica/api/clients/go=../api/clients/go
go mod tidy
```

Each directory under `proto/` is one Go package: `proto/voplica/api/payment/payment.proto` is in
`github.com/voplica/api/clients/go/gen/voplica/api/payment`, package `payment`.

## A call

[`example/main.go`](example/main.go) asks for the recommended base model variant. It builds the request as a generated
struct, encodes it with `protojson`, posts it to the method's path, with your key in the `Authorization` header when
`VOPLICA_API_KEY` holds one, and decodes the answer into the generated response. This method also answers without a
key, as for a signed-out visitor. Run it in `clients/go`: `go run ./example`.

```go
// Command example calls the Voplica JSON API with the generated Go types: it builds a request, sends it as JSON and
// reads the answer into the response type. Generate the packages first (at the repository root,
// `buf generate --template clients/go/buf.gen.yaml`), then run it in clients/go, with your API key in VOPLICA_API_KEY
// or without one:
//
//	go run ./example
package main

import (
	"bytes"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"

	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"

	"github.com/voplica/api/clients/go/gen/voplica/api/ai/model"
	"github.com/voplica/api/clients/go/gen/voplica/types/ai/common"
)

// A call to the JSON API is one HTTPS POST of a JSON body to api + "/" + the method's full name.
const api = "https://api.voplica.com"

// call sends request to method ("<package>.<Service>/<Method>") as JSON and reads the answer into response. Your API
// key, when VOPLICA_API_KEY holds one, goes in the Authorization header.
func call(method string, request, response proto.Message) error {
	body, err := protojson.Marshal(request)
	if err != nil {
		return err
	}
	httpRequest, err := http.NewRequest(http.MethodPost, api+"/"+method, bytes.NewReader(body))
	if err != nil {
		return err
	}
	httpRequest.Header.Set("Content-Type", "application/json")
	if key := os.Getenv("VOPLICA_API_KEY"); key != "" {
		httpRequest.Header.Set("Authorization", "Bearer "+key)
	}
	answer, err := http.DefaultClient.Do(httpRequest)
	if err != nil {
		return err
	}
	defer answer.Body.Close()
	data, err := io.ReadAll(answer.Body)
	if err != nil {
		return err
	}
	if answer.StatusCode != http.StatusOK {
		return fmt.Errorf("%s: %s", answer.Status, data) // the JSON error, with its code and message
	}
	// A later version of the API can send fields that this code does not know yet: skip them.
	return protojson.UnmarshalOptions{DiscardUnknown: true}.Unmarshal(data, response)
}

func main() {
	// This method also answers without a key, as for a signed-out visitor; with your key it answers as you.
	request := &model.GetRecommendedBaseModelVariantRequest{
		CompatibleLoraModelFamily: common.ModelFamily_FLUX_2_KLEIN_9B.Enum(),
	}
	response := &model.GetRecommendedBaseModelVariantResponse{}
	if err := call("voplica.api.ai.model.ModelVariantService/GetRecommendedBaseModelVariant", request, response); err != nil {
		log.Fatal(err)
	}
	variant := response.GetModelVariant()
	fmt.Println("Recommended:", variant.GetVariantName(), variant.GetModelFamily())
}
```

`protojson` writes and reads the JSON the API speaks: field names in lowerCamelCase, enums by name and 64-bit integers
as strings. A call that fails answers with another HTTP status and a JSON body with a `code` and a `message`;
[the JSON API](https://docs.voplica.com/api/) describes the protocol and its errors. Keep the key on your server: never
in a web page, in an app that other people install or in a public repository.

## Versions and licence

The definitions' version is the API's version (`VERSION` at the root of this repository). `buf.gen.yaml` pins the
plugins, and `go.mod` requires a protobuf runtime at least as new as the generator. Licensed under the Apache License
2.0.
