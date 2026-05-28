#!/usr/bin/env bash
# Bump version, run pre-flight checks, build a signed AAB, and optionally tag.
#
# Usage:
#   ./release.sh 1.0.1          # bumps version, commits, no tag
#   ./release.sh 1.0.1 --tag    # bumps version, commits, creates v1.0.1 tag
#
# Pushing the tag (git push --tags) is left to you so you can review the
# commit and tag locally before CI takes over.
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <version> [--tag]" >&2
    exit 2
fi

VERSION="$1"
TAG=false
if [ "${2:-}" = "--tag" ]; then TAG=true; fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "error: version must be semver (e.g. 1.0.1), got: $VERSION" >&2
    exit 2
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
GRADLE_FILE="$ROOT/app/build.gradle.kts"
CHANGELOG="$ROOT/CHANGELOG.md"

# 1. Working tree must be clean.
if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
    echo "error: working tree is dirty. Commit or stash before releasing." >&2
    git -C "$ROOT" status --short >&2
    exit 1
fi

# 2. Read current versionCode, bump by 1.
CURRENT_CODE=$(grep -E '^\s*versionCode\s*=' "$GRADLE_FILE" | head -1 | grep -oE '[0-9]+')
if [ -z "$CURRENT_CODE" ]; then
    echo "error: couldn't read versionCode from $GRADLE_FILE" >&2
    exit 1
fi
NEW_CODE=$((CURRENT_CODE + 1))

echo "→ Bumping versionCode $CURRENT_CODE → $NEW_CODE"
echo "→ Bumping versionName → $VERSION"

# In-place edit. The trailing .bak is for portable BSD/GNU sed compatibility,
# then we delete it.
sed -i.bak \
    -e "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" \
    -e "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" \
    "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

# 3. CHANGELOG: move [Unreleased] entries to a new [VERSION] - DATE heading.
if [ -f "$CHANGELOG" ]; then
    TODAY=$(date +%Y-%m-%d)
    if grep -q '^## \[Unreleased\]' "$CHANGELOG"; then
        sed -i.bak "s/^## \[Unreleased\]/## [Unreleased]\n\n## [$VERSION] - $TODAY/" "$CHANGELOG"
        rm -f "$CHANGELOG.bak"
        echo "→ Moved [Unreleased] entries to [$VERSION] - $TODAY"
    fi
fi

# 4. Pre-flight: lint + unit tests.
echo "→ Pre-flight: unit tests"
./gradlew :app:testDebugUnitTest
echo "→ Pre-flight: lint"
./gradlew :app:lintDebug

# 5. Build signed release locally as sanity check (only if keystore present).
if [ -f keystore.properties ]; then
    echo "→ Building signed release AAB locally"
    ./gradlew :app:bundleRelease
else
    echo "→ Skipping local signed build (keystore.properties not present)"
fi

# 6. Commit the version bump.
git -C "$ROOT" add app/build.gradle.kts
[ -f "$CHANGELOG" ] && git -C "$ROOT" add CHANGELOG.md
git -C "$ROOT" commit -m "chore(release): v$VERSION (versionCode $NEW_CODE)"

# 7. Tag (optional).
if $TAG; then
    git -C "$ROOT" tag -a "v$VERSION" -m "v$VERSION"
    echo "→ Tagged v$VERSION"
fi

echo
echo "Done. Next steps:"
echo "  git push"
$TAG && echo "  git push --tags  # triggers the Release workflow"
