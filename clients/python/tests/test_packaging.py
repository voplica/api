"""unittest: the runtime range pyproject.toml declares holds the generated code and admits no protobuf with a known
advisory, and requirements-floor.txt (which the workflow's oldest-runtime test installs) pins exactly its floors. A
generator bump that needs a newer protobuf or grpcio fails here until pyproject.toml moves with it."""
import importlib
import re
import unittest

import support

PROTO = "voplica/api/ai/model/model_variant.proto"
# The first protobuf runtime without CVE-2026-0994 (GHSA-7gcm-g887-7qv7: json_format's recursion limit bypassed by
# nested google.protobuf.Any; the published definitions have an Any field), above the gencode's own floor.
PROTOBUF_SECURITY_FLOOR = (6, 33, 5)


def version_tuple(text):
    return tuple(int(part) for part in text.split("."))


def declared_dependencies():
    """{distribution: (floor, cap or None)} of pyproject.toml's `name>=X.Y.Z` / `name>=X.Y.Z,<N` dependencies."""
    text = support.PYPROJECT.read_text(encoding="utf-8")
    block = re.search(r"^dependencies = \[(.*?)^\]", text, re.M | re.S)
    if block is None:
        raise AssertionError("pyproject.toml has no dependencies list")
    entries = re.findall(r'^\s*"([A-Za-z0-9_.-]+)>=([0-9.]+)(?:,<([0-9.]+))?"', block.group(1), re.M)
    return {name: (floor, cap or None) for name, floor, cap in entries}


class RuntimeRange(unittest.TestCase):
    def test_the_declared_range_holds_the_generated_code(self):
        dependencies = declared_dependencies()
        self.assertEqual(sorted(dependencies), ["grpcio", "protobuf"])
        messages = importlib.import_module(support.module_name(PROTO))
        with open(messages.__file__, encoding="utf-8") as source:
            gencode = re.search(r"^# Protobuf Python Version: ([0-9.]+)$", source.read(), re.M)
        self.assertIsNotNone(gencode, f"{messages.__file__} names no protobuf gencode version")
        gencode = version_tuple(gencode.group(1))
        # protobuf's cross-version guarantee: gencode of major V runs on runtimes of majors V and V+1, never on a
        # runtime older than the gencode (which the generated module refuses when it loads): [gencode, V + 2).
        floor, cap = dependencies["protobuf"]
        self.assertGreaterEqual(version_tuple(floor), gencode)
        self.assertGreaterEqual(version_tuple(floor), PROTOBUF_SECURITY_FLOOR, "the floor admits CVE-2026-0994")
        self.assertEqual(cap, str(gencode[0] + 2), "the protobuf cap is not the gencode's major + 2")
        # grpc's generated modules refuse a grpcio older than the generator; they set no cap.
        stubs = importlib.import_module(support.module_name(PROTO, "_pb2_grpc"))
        floor, cap = dependencies["grpcio"]
        self.assertGreaterEqual(version_tuple(floor), version_tuple(stubs.GRPC_GENERATED_VERSION))
        self.assertIsNone(cap)

    def test_the_floor_requirements_pin_the_declared_floors(self):
        pins = {}
        for line in support.FLOOR_REQUIREMENTS.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                pin = re.fullmatch(r"([A-Za-z0-9_.-]+)==([0-9.]+)", line)
                self.assertIsNotNone(pin, f"requirements-floor.txt: {line!r} is not name==version")
                pins[pin.group(1)] = pin.group(2)
        self.assertEqual(pins, {name: floor for name, (floor, _) in declared_dependencies().items()})


if __name__ == "__main__":
    unittest.main()
