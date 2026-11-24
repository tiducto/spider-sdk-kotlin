#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <gh-pages-worktree> <commit-message>" >&2
    exit 2
fi

cd "$1"
message="$2"
base="$(git rev-parse HEAD)"

git add -A
if git diff --cached --quiet "$base"; then
    echo "gh-pages is unchanged; nothing to push."
    exit 0
fi

git checkout -q --orphan gh-pages-next
git -c user.name='github-actions[bot]' \
    -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
    commit -q -m "$message"
git push --force-with-lease="gh-pages:$base" origin HEAD:gh-pages
echo "Pushed $(git rev-parse --short HEAD) to gh-pages as a single commit."
