"""Shared by the tests: where this checkout keeps the published definitions, the workflow and the package's metadata,
and what the definitions declare.

The paths are the checkout's (the suite runs from clients/python/tests of a checkout of voplica/api). The package under
test is whatever Python imports as `voplica`: the installed distribution when the workflow runs the suite in a venv from
an empty directory, or the tree scripts/generate.py writes when the suite runs from clients/python."""
import os
import pathlib
import re

PACKAGE_DIR = pathlib.Path(__file__).resolve().parent.parent
REPO_ROOT = PACKAGE_DIR.parent.parent
PROTO_DIR = pathlib.Path(os.environ.get("VOPLICA_API_PROTO_DIR") or REPO_ROOT / "proto")
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "python.yml"
PYPROJECT = PACKAGE_DIR / "pyproject.toml"
FLOOR_REQUIREMENTS = PACKAGE_DIR / "requirements-floor.txt"

# Strings, line comments and block comments in one pass, in source order (as clients/typescript/test/services.mjs):
# a "/*" inside a string ("image/*") must not open a comment that swallows the declarations up to a later "*/".
_LEXEMES = re.compile(r'"(?:[^"\\\n]|\\.)*"|\'(?:[^\'\\\n]|\\.)*\'|//[^\n]*|/\*[\s\S]*?\*/')


def proto_files():
    """The published .proto files, relative to PROTO_DIR (`voplica/api/payment/payment.proto`), sorted."""
    files = sorted(path.relative_to(PROTO_DIR).as_posix() for path in PROTO_DIR.rglob("*.proto"))
    if not files:
        raise AssertionError(f"no .proto file under {PROTO_DIR}")
    return files


def module_name(proto, suffix="_pb2"):
    """A .proto file's generated module: `voplica/api/payment/payment.proto` is `voplica.api.payment.payment_pb2`."""
    return proto[: -len(".proto")].replace("/", ".") + suffix


def declared_services():
    """Every service the published .proto files declare, as `package.Service`."""
    found = set()
    for proto in proto_files():
        source = (PROTO_DIR / proto).read_text(encoding="utf-8")
        text = _LEXEMES.sub(lambda match: "" if match.group(0)[0] == "/" else '""', source)
        package = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
        for service in re.finditer(r"^\s*service\s+(\w+)\s*\{", text, re.M):
            found.add(f"{package.group(1)}.{service.group(1)}" if package else service.group(1))
    return found
