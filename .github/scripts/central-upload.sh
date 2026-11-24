#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <bundle.zip> <version>" >&2
    exit 2
fi
: "${SONATYPE_CENTRAL_TOKEN_USER:?SONATYPE_CENTRAL_TOKEN_USER is not set}"
: "${SONATYPE_CENTRAL_TOKEN_PASSWORD:?SONATYPE_CENTRAL_TOKEN_PASSWORD is not set}"

bundle="$1"
version="$2"
api="${CENTRAL_API:-https://central.sonatype.com/api/v1/publisher}"
pom="${CENTRAL_REPO:-https://repo1.maven.org/maven2}/eu/tiducto/spider-sdk-kotlin-client/$version/spider-sdk-kotlin-client-$version.pom"
timeout="${CENTRAL_TIMEOUT_SECONDS:-3600}"
interval="${CENTRAL_POLL_SECONDS:-20}"

if curl -sfI -o /dev/null "$pom"; then
    echo "$version is already on Maven Central ($pom); nothing to upload."
    exit 0
fi

token="$(printf '%s:%s' "$SONATYPE_CENTRAL_TOKEN_USER" "$SONATYPE_CENTRAL_TOKEN_PASSWORD" | base64 | tr -d '\n')"
auth="Authorization: Bearer $token"

id="$(curl -sS --fail-with-body -X POST \
    "$api/upload?publishingType=AUTOMATIC&name=spider-sdk-kotlin-client-$version" \
    -H "$auth" \
    -F "bundle=@$bundle;type=application/octet-stream")"
echo "Uploaded $bundle as deployment $id."

deadline=$((SECONDS + timeout))
response=""
while :; do
    if response="$(curl -sS --fail-with-body -X POST "$api/status?id=$id" -H "$auth")"; then
        state="$(jq -r '.deploymentState' <<<"$response")"
        echo "Deployment $id: $state"
        case "$state" in
            PUBLISHED)
                exit 0
                ;;
            FAILED)
                jq '.errors' <<<"$response"
                echo "::error::Central deployment $id failed validation."
                exit 1
                ;;
        esac
    else
        echo "::warning::Status request for deployment $id failed: $response"
    fi
    if ((SECONDS >= deadline)); then
        echo "::error::Deployment $id did not reach PUBLISHED within ${timeout}s; check https://central.sonatype.com/publishing/deployments."
        exit 1
    fi
    sleep "$interval"
done
