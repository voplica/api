# voplica-api

The Voplica API for Python: every message, enum and service of the published protobuf definitions, generated with
[grpcio-tools](https://pypi.org/project/grpcio-tools/), with a type stub (`.pyi`) for every module. The import root is
`voplica`, as in the definitions' own paths: `voplica/api/payment/payment.proto` is the module
`voplica.api.payment.payment_pb2`, and its gRPC stubs are `voplica.api.payment.payment_pb2_grpc`.

The documentation: https://docs.voplica.com/clients/

## Install

```sh
pip install voplica-api
```

Python 3.10 or later. The package depends on `protobuf` and `grpcio`.

## A first call

The native gRPC endpoint opens soon; today the JSON API is served, and these modules give you type-safe requests and
responses for it: build the request message, send it as JSON (protojson, the Connect protocol's JSON mode), and parse
the answer into its response message. Every RPC is a `POST` of its request, as JSON, to
`https://api.voplica.com/<package>.<Service>/<Method>`, with your API key in the `Authorization` header:

```python
import os
import urllib.request

from google.protobuf import json_format
from voplica.api.payment import payment_pb2

API = "https://api.voplica.com"


def call(method, request, response):
    http_request = urllib.request.Request(
        f"{API}/{method}",
        data=json_format.MessageToJson(request).encode(),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {os.environ['VOPLICA_API_KEY']}",
        },
    )
    with urllib.request.urlopen(http_request) as answer:
        return json_format.Parse(answer.read(), response, ignore_unknown_fields=True)


info = call(
    "voplica.api.payment.PaymentService/GetBillingInfo",
    payment_pb2.GetBillingInfoRequest(),
    payment_pb2.GetBillingInfoResponse(),
)
print(info.billing_info.permanent_credits_milli)
```

`ignore_unknown_fields=True` keeps a call working when a later version of the API adds a field to its response. A
failed call raises `urllib.error.HTTPError`; its body is the JSON error, with its `code` and `message`. Use your key
on a server only: a key in an app that other people install can be read by anyone.

## gRPC

Each `*_pb2_grpc` module holds its services' client stubs (`PaymentServiceStub`), servicers and registration
functions, generated for the native gRPC endpoint. Until that endpoint opens, call the API as JSON, as above.

## Types

Every module has its `.pyi` stub and the package carries `py.typed`, so type checkers such as mypy and Pyright see
every message's fields. For mypy, also install `types-protobuf`.

## Versions

The package's version is the API's version (`VERSION` at the root of [voplica/api](https://github.com/voplica/api)),
and each version is tagged `v<VERSION>` there. To stay on one version, pin it exactly: `voplica-api==<VERSION>`.

## Generate it yourself

`scripts/generate.py` writes the package from the definitions in the repository's `proto/` with the pinned generator,
and the build makes the source distribution and the wheel:

```sh
git clone https://github.com/voplica/api.git
cd api/clients/python
python -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python scripts/generate.py
.venv/bin/python -m build --no-isolation
```

## Licence

Licensed under the Apache License 2.0.
