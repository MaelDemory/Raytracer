#!/bin/sh
#
# Projet POO Ray Tracing - compilation du pont natif Metal
#
# (c) 2025 Maël DEMORY
#
# Produit target/native/libraytracer_metal.dylib. Le script sort en succès même
# quand la compilation est impossible : le moteur retombe alors sur le CPU.
#

set -u

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=$(cd "$SCRIPT_DIR/../../.." && pwd)
OUT_DIR="$PROJECT_DIR/target/native"
OUT="$OUT_DIR/libraytracer_metal.dylib"

if [ "$(uname -s)" != "Darwin" ]; then
    echo "[metal] système non-macOS, pont natif ignoré"
    exit 0
fi

if ! command -v clang >/dev/null 2>&1; then
    echo "[metal] clang introuvable, pont natif ignoré"
    exit 0
fi

if [ ! -d /System/Library/Frameworks/Metal.framework ]; then
    echo "[metal] framework Metal absent, pont natif ignoré"
    exit 0
fi

mkdir -p "$OUT_DIR"

if clang -dynamiclib -fobjc-arc -O2 \
        -mmacosx-version-min=11.0 \
        -framework Foundation -framework Metal \
        -o "$OUT" "$SCRIPT_DIR/raytracer_metal.m"; then
    echo "[metal] $OUT"
else
    echo "[metal] compilation échouée, pont natif ignoré"
    rm -f "$OUT"
fi

exit 0
