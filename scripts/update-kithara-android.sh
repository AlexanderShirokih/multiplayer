#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
KITHARA_DIR="${KITHARA_DIR:?Set KITHARA_DIR to the Kithara repository path}"
KITHARA_DIR="$(cd "$KITHARA_DIR" && pwd)"
KITHARA_OUTPUT_DIR="$KITHARA_DIR/android/lib/build/outputs/aar"
MULTIPLAYER_OUTPUT_DIR="$REPO_ROOT/android/libs"

if [[ ! -f "$KITHARA_DIR/justfile" ]]; then
    echo "Kithara justfile not found: $KITHARA_DIR/justfile" >&2
    exit 1
fi

mkdir -p "$MULTIPLAYER_OUTPUT_DIR"

just --justfile "$KITHARA_DIR/justfile" --working-directory "$KITHARA_DIR" android-aar

for artifact in kithara.aar rust-tls.aar; do
    source_path="$KITHARA_OUTPUT_DIR/$artifact"
    destination_path="$MULTIPLAYER_OUTPUT_DIR/$artifact"

    if [[ ! -f "$source_path" ]]; then
        echo "Expected artifact not found: $source_path" >&2
        exit 1
    fi

    cp "$source_path" "$destination_path"
done

echo "Copied Kithara Android artifacts to $MULTIPLAYER_OUTPUT_DIR"
