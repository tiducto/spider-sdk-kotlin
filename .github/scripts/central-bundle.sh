#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: $0 <maven-repo-dir> <version> <bundle.zip>" >&2
    exit 2
fi

repo="$1"
version="$2"
out="$(cd "$(dirname "$3")" && pwd)/$(basename "$3")"
base="eu/tiducto/spider-sdk-kotlin-client"

cd "$repo"
if [[ ! -d "$base/$version" ]]; then
    echo "::error::$repo has no $base/$version" >&2
    exit 1
fi

dirs=()
for dir in "$base/$version" "$base"-*/"$version"; do
    if [[ -d "$dir" ]]; then
        dirs+=("$dir")
    fi
done

files="$(find "${dirs[@]}" -type f \
    ! -name 'maven-metadata*' \
    ! -name '*.sha256' ! -name '*.sha512' \
    ! -name '*.asc.md5' ! -name '*.asc.sha1' \
    | LC_ALL=C sort)"

digest() {
    openssl dgst "-$1" -r "$2" | cut -d' ' -f1
}

status=0
artifacts=0
while IFS= read -r file; do
    case "$file" in
        *.asc | *.md5 | *.sha1) continue ;;
    esac
    artifacts=$((artifacts + 1))
    for ext in asc md5 sha1; do
        if ! grep -qxF "$file.$ext" <<<"$files"; then
            echo "::error::$file.$ext is missing" >&2
            status=1
        fi
    done
    for algo in md5 sha1; do
        if [[ -f "$file.$algo" && "$(tr -d ' \n' <"$file.$algo")" != "$(digest "$algo" "$file")" ]]; then
            echo "::error::$file.$algo does not match $file" >&2
            status=1
        fi
    done
done <<<"$files"
if [[ $status -ne 0 ]]; then
    exit 1
fi

rm -f "$out"
zip -X -q "$out" -@ <<<"$files"
count="$(wc -l <<<"$files" | tr -d ' ')"
echo "Bundled ${#dirs[@]} publications, $artifacts artifacts, $count files of $version into $out."
