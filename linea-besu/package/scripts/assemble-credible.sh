#!/usr/bin/env bash

set -euo pipefail

COMMIT="f47675d020065e2f81153172f768dd7ca7214297"
VERSION="0.3.0-${COMMIT:0:8}"
SOURCE_DIR=$(mktemp -d)
OUTPUT="./linea-besu/besu/plugins/credible-layer-besu-plugin-$VERSION.jar"
trap 'rm -rf "$SOURCE_DIR"' EXIT

curl -fsSL "https://api.github.com/repos/phylaxsystems/credible-layer-besu-plugin/tarball/$COMMIT" \
  | tar -xz --strip-components=1 -C "$SOURCE_DIR"
"$SOURCE_DIR/gradlew" -p "$SOURCE_DIR" shadowJar -PcommitHash="${COMMIT:0:8}"
mkdir -p "$(dirname "$OUTPUT")"
cp "$SOURCE_DIR/build/libs/credible-layer-besu-plugin-$VERSION.jar" "$OUTPUT"
