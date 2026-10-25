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
