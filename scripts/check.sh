#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${JAVA_HOME:-}" && -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
fi

if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

if [[ "${1:-}" == "--format" ]]; then
    bash gradlew spotlessApply --console=plain
    exit 0
fi

if [[ "${1:-}" != "" ]]; then
    echo "Usage: $0 [--format]" >&2
    exit 2
fi

python3 -c '
import hashlib, pathlib
expected = pathlib.Path("gradle/wrapper/gradle-wrapper.jar.sha256").read_text().strip()
actual = hashlib.sha256(pathlib.Path("gradle/wrapper/gradle-wrapper.jar").read_bytes()).hexdigest()
assert actual == expected, f"gradle-wrapper.jar SHA-256 mismatch: {actual} != {expected}"
print("gradle/wrapper/gradle-wrapper.jar: OK")
'

bash gradlew spotlessCheck testDebugUnitTest lintDebug assembleDebug --console=plain
python3 -m py_compile scripts/obtainium.py scripts/release.py
python3 scripts/obtainium.py --check
