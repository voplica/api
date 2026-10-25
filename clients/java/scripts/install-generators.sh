#!/usr/bin/env bash
# install-generators.sh <bin dir>: the two generators the build runs, put into <bin dir> only once their sha256 is the
# one .github/workflows/java.yml pins: protoc $PROTOC_VERSION (the binary of the release's
# protoc-<version>-linux-x86_64.zip, $PROTOC_SHA256) and protoc-gen-grpc-java $GRPC_JAVA_VERSION (the release's
# linux-x86_64 executable on Maven Central, $GRPC_JAVA_SHA256). For the workflow's Linux x86-64 runners.
set -euo pipefail

[ "$#" -eq 1 ] || { echo "usage: install-generators.sh <bin dir>" >&2; exit 2; }
bin=$1
: "${PROTOC_VERSION:?}" "${PROTOC_SHA256:?}" "${GRPC_JAVA_VERSION:?}" "${GRPC_JAVA_SHA256:?}"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fetch() { curl --fail --silent --show-error --location --retry 3 --output "$1" "$2"; }

fetch "$work/protoc.zip" \
  "https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/protoc-${PROTOC_VERSION}-linux-x86_64.zip"
unzip -q "$work/protoc.zip" bin/protoc -d "$work"
echo "$PROTOC_SHA256  $work/bin/protoc" | sha256sum --check --strict

fetch "$work/protoc-gen-grpc-java" \
  "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/${GRPC_JAVA_VERSION}/protoc-gen-grpc-java-${GRPC_JAVA_VERSION}-linux-x86_64.exe"
echo "$GRPC_JAVA_SHA256  $work/protoc-gen-grpc-java" | sha256sum --check --strict

mkdir -p "$bin"
install -m 755 "$work/bin/protoc" "$work/protoc-gen-grpc-java" "$bin/"
"$bin/protoc" --version
