#!/usr/bin/env bash
# Local verification. Mirrors .github/workflows/ci.yml so agents can
# self-check before pushing. Fail fast; verbose on failure.
set -euo pipefail

echo "==> assembleDebug"
./gradlew --no-daemon assembleDebug

echo "==> testAll"
./gradlew --no-daemon testAll

echo "==> lint"
./gradlew --no-daemon lint

echo "==> no leftover Dictus package references outside third_party/"
if grep -rn "com\.dictus" --include='*.kt' --include='*.kts' --include='*.xml' \
     app ime core asr whisper 2>/dev/null | grep -v third_party; then
  echo "FAIL: found com.dictus references outside third_party/"; exit 1
fi

echo "==> no committed model binaries"
if git ls-files | grep -E '\.(gguf|onnx|bin)$' | grep -v third_party; then
  echo "FAIL: model binary committed"; exit 1
fi

echo "OK"
