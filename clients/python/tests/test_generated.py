"""unittest: the generated package. The workflow installs the built wheel into a fresh venv and runs this suite from an
empty directory, with VOPLICA_API_VERSION set to the version it built; run from clients/python after
scripts/generate.py, the suite tests the generated tree."""
import importlib
import importlib.metadata
import importlib.util
import json
import os
import pathlib
import unittest

import grpc
from google.protobuf import json_format

import support


def package_dir():
    """The one directory `voplica` is imported from (two would merge an installed copy with a generated tree)."""
    spec = importlib.util.find_spec("voplica")
    if spec is None:
        raise AssertionError("voplica is not importable: install the wheel, or run scripts/generate.py first")
    locations = list(spec.submodule_search_locations or [])
    if len(locations) != 1:
        raise AssertionError(f"voplica is imported from {len(locations)} directories: {locations}")
    return pathlib.Path(locations[0])


class GeneratedPackage(unittest.TestCase):
    def test_every_published_proto_has_its_messages_types_and_grpc_modules(self):
        root = package_dir()
        expected = {"py.typed"}
        for proto in support.proto_files():
            stem = proto[len("voplica/") : -len(".proto")]
            expected |= {f"{stem}_pb2.py", f"{stem}_pb2.pyi", f"{stem}_pb2_grpc.py"}
        found = {
            path.relative_to(root).as_posix()
            for path in root.rglob("*")
            if path.is_file() and "__pycache__" not in path.relative_to(root).parts
        }
        self.assertEqual(sorted(expected - found), [], "missing from the package")
        self.assertEqual(sorted(found - expected), [], "in the package without a published .proto")

    def test_every_module_imports_and_names_its_proto(self):
        for proto in support.proto_files():
            with self.subTest(proto=proto):
                messages = importlib.import_module(support.module_name(proto))
                self.assertEqual(messages.DESCRIPTOR.name, proto)
                importlib.import_module(support.module_name(proto, "_pb2_grpc"))

    def test_every_service_has_a_stub_a_servicer_and_a_registration(self):
        found = set()
        for proto in support.proto_files():
            messages = importlib.import_module(support.module_name(proto))
            stubs = importlib.import_module(support.module_name(proto, "_pb2_grpc"))
            for name, service in messages.DESCRIPTOR.services_by_name.items():
                found.add(service.full_name)
                for attribute in (f"{name}Stub", f"{name}Servicer", f"add_{name}Servicer_to_server"):
                    self.assertTrue(hasattr(stubs, attribute), f"{proto}: no {attribute}")
        self.assertEqual(found, support.declared_services())
        # A stub builds on a channel, which connects lazily: nothing is called, and the channel is closed unused.
        from voplica.api.ai.model import model_variant_pb2_grpc

        with grpc.insecure_channel("localhost:1") as channel:
            stub = model_variant_pb2_grpc.ModelVariantServiceStub(channel)
            self.assertTrue(callable(stub.GetRecommendedBaseModelVariant))

    def test_a_message_round_trips_through_protojson(self):
        from voplica.api.ai.model import model_variant_pb2
        from voplica.types.ai.common import model_family_type_pb2
        from voplica.types.ai.model import model_pb2
        from voplica.types.base import publish_status_pb2

        response = model_variant_pb2.GetRecommendedBaseModelVariantResponse(
            model_variant=model_pb2.ModelVariant(
                variant_id="mv-1",
                variant_name="v1",
                status=publish_status_pb2.PUBLISHED,
                model_family=model_family_type_pb2.FLUX_2_KLEIN_9B,
                created_at=1761350400000,
            )
        )
        text = json_format.MessageToJson(response)
        # protojson: lowerCamelCase names, enums by name, 64-bit integers as strings.
        self.assertEqual(
            json.loads(text),
            {
                "modelVariant": {
                    "variantId": "mv-1",
                    "variantName": "v1",
                    "status": "PUBLISHED",
                    "modelFamily": "FLUX_2_KLEIN_9B",
                    "createdAt": "1761350400000",
                }
            },
        )
        self.assertEqual(json_format.Parse(text, model_variant_pb2.GetRecommendedBaseModelVariantResponse()), response)
        self.assertEqual(type(response).FromString(response.SerializeToString()), response)
        request = model_variant_pb2.GetRecommendedBaseModelVariantRequest()
        json_format.Parse('{"compatibleLoraModelFamily": "FLUX_2_KLEIN_9B"}', request)
        self.assertTrue(request.HasField("compatible_lora_model_family"))  # a proto3 optional keeps its presence
        self.assertFalse(request.HasField("output_file_type"))

    def test_the_installed_distribution_is_the_built_version(self):
        expected = os.environ.get("VOPLICA_API_VERSION")
        if not expected:
            self.skipTest("VOPLICA_API_VERSION is not set (the workflow sets it where it installs the wheel)")
        self.assertEqual(importlib.metadata.version("voplica-api"), expected)
        installed = pathlib.Path(importlib.metadata.distribution("voplica-api").locate_file("voplica"))
        self.assertEqual(package_dir().resolve(), installed.resolve())

    def test_the_installed_distribution_carries_its_licence(self):
        # setuptools builds a wheel without its licence when LICENSE is missing beside pyproject.toml: never ship one.
        if not os.environ.get("VOPLICA_API_VERSION"):
            self.skipTest("VOPLICA_API_VERSION is not set (the workflow sets it where it installs the wheel)")
        distribution = importlib.metadata.distribution("voplica-api")
        self.assertEqual(distribution.metadata["License-Expression"], "Apache-2.0")
        licences = [path for path in distribution.files or [] if path.parent.name == "licenses"]
        self.assertEqual([path.name for path in licences], ["LICENSE"])
        repository_licence = (support.REPO_ROOT / "LICENSE").read_text(encoding="utf-8")
        self.assertEqual(licences[0].read_text(encoding="utf-8"), repository_licence)


if __name__ == "__main__":
    unittest.main()
