package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"reflect"
	"strings"
	"testing"

	"github.com/voplica/api/clients/go/gen/voplica/api/ai/model"
)

// roundTrip answers the example's HTTP requests in the test: nothing leaves the machine.
type roundTrip func(*http.Request) (*http.Response, error)

func (f roundTrip) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

// answer makes the HTTP client answer every request of the test with status and body; it returns the requests sent.
func answer(t *testing.T, status int, body string) *[]*http.Request {
	t.Helper()
	var sent []*http.Request
	http.DefaultClient.Transport = roundTrip(func(r *http.Request) (*http.Response, error) {
		data, err := io.ReadAll(r.Body)
		if err != nil {
			return nil, err
		}
		r.Body = io.NopCloser(bytes.NewReader(data))
		sent = append(sent, r)
		return &http.Response{
			StatusCode: status,
			Status:     fmt.Sprintf("%d %s", status, http.StatusText(status)),
			Header:     http.Header{"Content-Type": {"application/json"}},
			Body:       io.NopCloser(strings.NewReader(body)),
			Request:    r,
		}, nil
	})
	t.Cleanup(func() { http.DefaultClient.Transport = nil })
	return &sent
}

// stdout runs f and returns what it printed.
func stdout(t *testing.T, f func()) string {
	t.Helper()
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	saved := os.Stdout
	os.Stdout = w
	f()
	os.Stdout = saved
	w.Close()
	out, err := io.ReadAll(r)
	if err != nil {
		t.Fatal(err)
	}
	return string(out)
}

func TestTheExampleSendsTheRequestAsJSONAndReadsTheAnswer(t *testing.T) {
	t.Setenv("VOPLICA_API_KEY", "pat_test")
	sent := answer(t, http.StatusOK, `{"modelVariant": {"variantId": "mv-1", "variantName": "Klein 9B",
		"modelFamily": "FLUX_2_KLEIN_9B", "status": "PUBLISHED", "visibility": "PUBLIC", "aFieldOfALaterVersion": 1}}`)

	out := stdout(t, main)

	if len(*sent) != 1 {
		t.Fatalf("%d requests were sent, not 1", len(*sent))
	}
	r := (*sent)[0]
	if got, want := r.Method+" "+r.URL.String(), "POST https://api.voplica.com/voplica.api.ai.model.ModelVariantService/GetRecommendedBaseModelVariant"; got != want {
		t.Errorf("the request is %q, not %q", got, want)
	}
	if got := r.Header.Get("Content-Type"); got != "application/json" {
		t.Errorf("Content-Type is %q", got)
	}
	if got := r.Header.Get("Authorization"); got != "Bearer pat_test" {
		t.Errorf("Authorization is %q", got)
	}
	var body map[string]any
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		t.Fatalf("the body is not JSON: %v", err)
	}
	if want := map[string]any{"compatibleLoraModelFamily": "FLUX_2_KLEIN_9B"}; !reflect.DeepEqual(body, want) {
		t.Errorf("the body is %v, not %v", body, want)
	}
	if want := "Recommended: Klein 9B FLUX_2_KLEIN_9B\n"; out != want {
		t.Errorf("the example printed %q, not %q", out, want)
	}
}

func TestAFailedCallReturnsTheStatusAndTheJSONError(t *testing.T) {
	t.Setenv("VOPLICA_API_KEY", "pat_test")
	answer(t, http.StatusUnauthorized, `{"code": "unauthenticated", "message": "Invalid API key"}`)

	err := call("voplica.api.ai.model.ModelVariantService/GetRecommendedBaseModelVariant",
		&model.GetRecommendedBaseModelVariantRequest{}, &model.GetRecommendedBaseModelVariantResponse{})

	if err == nil {
		t.Fatal("a 401 answer returned no error")
	}
	for _, want := range []string{"401 Unauthorized", `"code": "unauthenticated"`, "Invalid API key"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("the error %q does not say %q", err, want)
		}
	}
}

func TestWithoutAKeyTheExampleCallsWithNoAuthorizationHeader(t *testing.T) {
	t.Setenv("VOPLICA_API_KEY", "")
	sent := answer(t, http.StatusOK, `{"modelVariant": {"variantId": "mv-1", "variantName": "Klein 9B", "modelFamily": "FLUX_2_KLEIN_9B"}}`)

	out := stdout(t, main)

	if len(*sent) != 1 {
		t.Fatalf("%d requests were sent, not 1", len(*sent))
	}
	if got, ok := (*sent)[0].Header["Authorization"]; ok {
		t.Errorf("without a key the call sends Authorization %q; the method answers without one", got)
	}
	if want := "Recommended: Klein 9B FLUX_2_KLEIN_9B\n"; out != want {
		t.Errorf("the example printed %q, not %q", out, want)
	}
}
