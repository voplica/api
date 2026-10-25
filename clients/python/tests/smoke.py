"""The registry smoke of .github/workflows/python.yml: run with the python of an empty venv after
`pip install voplica-api==<VERSION>` from PyPI, as `python -I smoke.py <VERSION> <proto dir>` (the checkout's proto/,
the tree that version was generated from). The installed distribution must be that version, carry its Apache-2.0
licence and hold exactly the three modules of every published .proto and py.typed; every module must import; one message
must round-trip through protojson and a stub must build on a channel. Nothing is called. Self-contained: the workflow
copies it alone."""
import importlib
import importlib.metadata
import json
import pathlib
import sys

import grpc
from google.protobuf import json_format


def main(version, proto_dir):
    installed = importlib.metadata.version("voplica-api")
    if installed != version:
        raise SystemExit(f"voplica-api {installed} is installed, not {version}")
    distribution = importlib.metadata.distribution("voplica-api")
    licences = [path.name for path in distribution.files or [] if path.parent.name == "licenses"]
    if distribution.metadata["License-Expression"] != "Apache-2.0" or licences != ["LICENSE"]:
        raise SystemExit(f"licence: {distribution.metadata['License-Expression']}, files {licences}")
    protos = sorted(path.relative_to(proto_dir).as_posix() for path in proto_dir.rglob("*.proto"))
    if not protos:
        raise SystemExit(f"no .proto file under {proto_dir}")
    stems = [proto[: -len(".proto")] for proto in protos]
    suffixes = ("_pb2.py", "_pb2.pyi", "_pb2_grpc.py")
    expected = {"voplica/py.typed"} | {stem + suffix for stem in stems for suffix in suffixes}
    files = {
        path.as_posix()
        for path in importlib.metadata.files("voplica-api") or []
        if path.parts[0] == "voplica" and "__pycache__" not in path.parts
    }
    if files != expected:
        raise SystemExit(f"missing {sorted(expected - files)[:5]}, unexpected {sorted(files - expected)[:5]}")
    services = 0
    for proto, stem in zip(protos, stems):
        messages = importlib.import_module(stem.replace("/", ".") + "_pb2")
        if messages.DESCRIPTOR.name != proto:
            raise SystemExit(f"{stem}_pb2 describes {messages.DESCRIPTOR.name}, not {proto}")
        importlib.import_module(stem.replace("/", ".") + "_pb2_grpc")
        services += len(messages.DESCRIPTOR.services_by_name)

    from voplica.api.payment import payment_pb2, payment_pb2_grpc
    from voplica.types.payment import payment_pb2 as payment_types_pb2

    response = payment_pb2.GetBillingInfoResponse(
        billing_info=payment_types_pb2.BillingInfo(account_id="a-1", permanent_credits_milli=1500000)
    )
    text = json_format.MessageToJson(response)
    if json.loads(text) != {"billingInfo": {"accountId": "a-1", "permanentCreditsMilli": "1500000"}}:
        raise SystemExit(f"GetBillingInfoResponse as JSON: {text}")
    if json_format.Parse(text, payment_pb2.GetBillingInfoResponse()) != response:
        raise SystemExit("GetBillingInfoResponse does not round-trip through JSON")
    with grpc.insecure_channel("localhost:1") as channel:
        if not callable(payment_pb2_grpc.PaymentServiceStub(channel).GetBillingInfo):
            raise SystemExit("PaymentServiceStub has no GetBillingInfo")
    location = pathlib.Path(payment_pb2.__file__).parents[3]
    print(f"voplica-api {installed}: {len(protos)} protos, {services} services, from {location}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: python -I smoke.py <version> <proto dir>")
    main(sys.argv[1], pathlib.Path(sys.argv[2]))
