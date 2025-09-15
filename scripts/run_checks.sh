#!/usr/bin/env bash
set -euo pipefail

./gradlew --console=plain clean lint test
