"""Generate the package `voplica` from the published definitions.

For every .proto file under ../../proto (the public tree of this repository; --proto names another), grpcio-tools'
protoc (the version requirements.txt pins) writes its messages (`*_pb2.py`), their type stubs (`*_pb2.pyi`) and its gRPC
stubs (`*_pb2_grpc.py`) into voplica/, beside pyproject.toml. The import root is `voplica`, as in the definitions' own
paths: voplica/api/payment/payment.proto is `voplica.api.payment.payment_pb2`, and the generated modules import each
other by those absolute names.

The run also writes voplica/py.typed (PEP 561: the stubs are the package's types) and copies the repository's VERSION
(the package's version, which pyproject.toml reads) and LICENSE beside pyproject.toml. voplica/ is removed first, so a
module of an earlier run never reaches a package. The last line is the manifest: the sha256 of the sorted
`<sha256>  <path>` lines of every file in voplica/, the same for the same definitions and the same generator.

Usage: python scripts/generate.py [--proto DIR]
"""
import argparse
import hashlib
import importlib.metadata
import importlib.resources
import pathlib
import re
import shutil
import sys

PACKAGE_DIR = pathlib.Path(__file__).resolve().parent.parent
REPO_ROOT = PACKAGE_DIR.parent.parent
OUTPUT = PACKAGE_DIR / "voplica"
SUFFIXES = ("_pb2.py", "_pb2.pyi", "_pb2_grpc.py")


def fail(message):
    print(f"generate.py: {message}", file=sys.stderr)
    raise SystemExit(1)


def published_protos(proto_root):
    """The .proto files under proto_root, relative to it, sorted; every one under voplica/ (the import root)."""
    protos = sorted(path.relative_to(proto_root).as_posix() for path in proto_root.rglob("*.proto"))
    if not protos:
        fail(f"no .proto file under {proto_root}")
    outside = [proto for proto in protos if not proto.startswith("voplica/")]
    if outside:
        fail(f"{len(outside)} .proto files are outside voplica/, the import root: {outside[0]}, ...")
    return protos


def remove_output():
    if OUTPUT.is_symlink():
        fail(f"{OUTPUT} is a symlink: not removed")
    if OUTPUT.exists():
        if OUTPUT.resolve().parent != PACKAGE_DIR:
            fail(f"{OUTPUT} resolves outside {PACKAGE_DIR}: not removed")
        shutil.rmtree(OUTPUT)


def copy_beside_pyproject(name):
    """Copy the repository's file `name` beside pyproject.toml (a symlink there is replaced, never written through)."""
    source = REPO_ROOT / name
    if not source.is_file():
        fail(f"{source} is missing")
    target = PACKAGE_DIR / name
    if target.is_symlink() or target.exists():
        target.unlink()
    target.write_bytes(source.read_bytes())


def manifest():
    lines = sorted(
        f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(OUTPUT).as_posix()}"
        for path in OUTPUT.rglob("*")
        if path.is_file()
    )
    return len(lines), hashlib.sha256("".join(line + "\n" for line in lines).encode()).hexdigest()


def main(argv=None):
    parser = argparse.ArgumentParser(description="Generate the package voplica from the published definitions.")
    parser.add_argument("--proto", type=pathlib.Path, default=REPO_ROOT / "proto", help="default: %(default)s")
    proto_root = parser.parse_args(argv).proto.resolve()
    protos = published_protos(proto_root)
    version = (REPO_ROOT / "VERSION").read_text(encoding="utf-8").strip()
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        fail(f"VERSION is {version!r}, not X.Y.Z")

    from grpc_tools import protoc

    remove_output()
    well_known_types = importlib.resources.files("grpc_tools") / "_proto"  # google/protobuf/*.proto
    status = protoc.main(
        [
            "grpc_tools.protoc",
            f"--proto_path={proto_root}",
            f"--proto_path={well_known_types}",
            f"--python_out={PACKAGE_DIR}",
            f"--pyi_out={PACKAGE_DIR}",
            f"--grpc_python_out={PACKAGE_DIR}",
            "--fatal_warnings",
            *(str(proto_root / proto) for proto in protos),
        ]
    )
    if status != 0:
        fail(f"protoc exited with {status}")
    (OUTPUT / "py.typed").write_bytes(b"")

    stems = [proto[len("voplica/") : -len(".proto")] for proto in protos]
    expected = {"py.typed"} | {stem + suffix for stem in stems for suffix in SUFFIXES}
    written = {path.relative_to(OUTPUT).as_posix() for path in OUTPUT.rglob("*") if path.is_file()}
    if written != expected:
        fail(f"protoc wrote {sorted(written - expected)[:3]} and not {sorted(expected - written)[:3]}")
    header = (OUTPUT / (stems[0] + "_pb2.py")).read_text(encoding="utf-8")
    gencode = re.search(r"^# Protobuf Python Version: (\S+)$", header, re.M)
    copy_beside_pyproject("VERSION")
    copy_beside_pyproject("LICENSE")

    tools = f"grpcio-tools {importlib.metadata.version('grpcio-tools')}"
    print(f"generate.py: {tools} (protobuf gencode {gencode.group(1) if gencode else '?'}), {proto_root}")
    print(f"generate.py: voplica/ holds {len(protos)} of each of {', '.join('*' + s for s in SUFFIXES)}, and py.typed")
    print(f"generate.py: VERSION {version} and LICENSE copied beside pyproject.toml")
    count, digest = manifest()
    print(f"generate.py: manifest {count} files sha256 {digest}")


if __name__ == "__main__":
    main()
