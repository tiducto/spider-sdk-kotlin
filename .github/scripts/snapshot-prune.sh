#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "usage: $0 <snapshots-repo-dir> <sdk-git-dir> [<snapshot-version-to-replace>]" >&2
    exit 2
fi

repo="$1"
tags="$(git -C "$2" tag -l)"
replace="${3:-}"
group="eu.tiducto"

if [[ ! -d "$repo/eu/tiducto" ]]; then
    exit 0
fi
cd "$repo/eu/tiducto"

write_metadata() {
    local dir="$1" artifact="$2"
    shift 2
    local versions=("$@")
    {
        echo '<?xml version="1.0" encoding="UTF-8"?>'
        echo '<metadata>'
        echo "  <groupId>$group</groupId>"
        echo "  <artifactId>$artifact</artifactId>"
        echo '  <versioning>'
        echo "    <latest>${versions[${#versions[@]} - 1]}</latest>"
        echo '    <versions>'
        for version in "${versions[@]}"; do
            echo "      <version>$version</version>"
        done
        echo '    </versions>'
        echo "    <lastUpdated>$(date -u +%Y%m%d%H%M%S)</lastUpdated>"
        echo '  </versioning>'
        echo '</metadata>'
    } >"$dir/maven-metadata.xml"
    for algo in md5 sha1 sha256 sha512; do
        openssl dgst "-$algo" -r "$dir/maven-metadata.xml" | cut -d' ' -f1 | tr -d '\n' >"$dir/maven-metadata.xml.$algo"
    done
}

for dir in spider-sdk-kotlin-client spider-sdk-kotlin-client-*; do
    if [[ ! -d "$dir" ]]; then
        continue
    fi
    removed=0
    for version_dir in "$dir"/*-SNAPSHOT; do
        if [[ ! -d "$version_dir" ]]; then
            continue
        fi
        version="$(basename "$version_dir")"
        if [[ "$version" == "$replace" ]]; then
            echo "Replacing $dir/$version."
        elif grep -qxF "${version%-SNAPSHOT}" <<<"$tags"; then
            echo "Removing $dir/$version: ${version%-SNAPSHOT} is released."
        else
            continue
        fi
        rm -rf "$version_dir"
        removed=1
    done
    if [[ $removed -eq 0 ]]; then
        continue
    fi
    remaining="$(find "$dir" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort -V)"
    if [[ -z "$remaining" ]]; then
        echo "Removing $dir: no versions left."
        rm -rf "$dir"
    else
        versions=()
        while IFS= read -r version; do
            versions+=("$version")
        done <<<"$remaining"
        write_metadata "$dir" "$dir" "${versions[@]}"
    fi
done
