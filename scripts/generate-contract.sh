#!/usr/bin/env bash
# Regenerate the :contract module's Kotlin models from the wire contract.
#
# The single source of truth for contract codegen — the generate-contract workflow just calls this, so
# local and CI runs produce byte-identical output. The generated classes are committed on purpose (see
# contract/build.gradle.kts): the module carries the classes, not the spec, and has no codegen in its build.
#
# Usage:
#   scripts/generate-contract.sh                      # fetch main from the contract repo
#   scripts/generate-contract.sh --ref v1.2.0         # a specific ref/tag/branch
#   scripts/generate-contract.sh --spec path/to.json  # a local spec, no fetch
#
# CONTRACT_REPO_TOKEN reads the private contract repo (CI); locally falls back to ambient `gh` auth.
# GENERATOR_VERSION pins the OpenAPI Generator tag (default below).
set -euo pipefail

CONTRACT_REPO="${CONTRACT_REPO:-tiducto/spider-contract}"
CONTRACT_REF="main"
GENERATOR_VERSION="${GENERATOR_VERSION:-7.14.0}"
LOCAL_SPEC=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --ref) CONTRACT_REF="$2"; shift 2 ;;
        --spec) LOCAL_SPEC="$2"; shift 2 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODELS_DIR="$REPO_ROOT/contract/src/commonMain/kotlin/cz/davidkurzica/contract/models"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [[ -n "$LOCAL_SPEC" ]]; then
    echo "==> Using local spec: $LOCAL_SPEC"
    cp "$LOCAL_SPEC" "$WORK_DIR/openapi.json"
else
    echo "==> Fetching openapi.json from $CONTRACT_REPO@$CONTRACT_REF"
    GH_TOKEN="${CONTRACT_REPO_TOKEN:-${GH_TOKEN:-}}" \
        gh api "repos/$CONTRACT_REPO/contents/openapi.json?ref=$CONTRACT_REF" --jq '.content' \
        | base64 -d > "$WORK_DIR/openapi.json"
fi

echo "==> Generating models with openapi-generator v$GENERATOR_VERSION"
# Flag rationale (keep in sync with the client's expectations):
#   dateLibrary=string          date-time fields stay String, so the wire bytes are unchanged (no
#                               kotlinx-datetime serializer semantics leaking in).
#   enumUnknownDefaultCase=true every enum gets a custom serializer that decodes an unrecognized value
#                               to an UNKNOWN_DEFAULT_OPEN_API member instead of throwing — so a mode/
#                               state OTP adds after this contract was pinned can't fail the parse.
#   sourceFolder=...commonMain  emit straight into the KMP commonMain layout (no post-move).
#   default library (not multiplatform): the multiplatform library emits a duplicate @Serializable
#                               annotation that won't compile; the models are pure common Kotlin anyway.
docker run --rm -v "$WORK_DIR:/local" "openapitools/openapi-generator-cli:v$GENERATOR_VERSION" generate \
    -i /local/openapi.json \
    -g kotlin \
    --global-property models,modelDocs=false,modelTests=false \
    --additional-properties=serializationLibrary=kotlinx_serialization,packageName=cz.davidkurzica.contract,dateLibrary=string,enumPropertyNaming=UPPERCASE,enumUnknownDefaultCase=true,sourceFolder=src/commonMain/kotlin \
    -o /local/out >/dev/null

GENERATED="$WORK_DIR/out/src/commonMain/kotlin/cz/davidkurzica/contract/models"
if [[ ! -d "$GENERATED" ]]; then
    echo "ERROR: generator produced no models at $GENERATED" >&2
    exit 1
fi

echo "==> Syncing into $MODELS_DIR (replacing existing)"
rm -rf "$MODELS_DIR"
mkdir -p "$MODELS_DIR"
cp -R "$GENERATED/." "$MODELS_DIR/"

echo "==> Done. $(find "$MODELS_DIR" -name '*.kt' | wc -l | tr -d ' ') model files."
