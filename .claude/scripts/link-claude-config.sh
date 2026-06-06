#!/bin/bash

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
PARENT_DIR="$(dirname "$PROJECT_DIR")"
CONFIG_REPO="$PARENT_DIR/elderpath-crusade-claude"
CLAUDE_DIR="$PROJECT_DIR/.claude"

if [ ! -d "$CONFIG_REPO" ]; then
    echo "Config repo not found at $CONFIG_REPO — skipping link"
    exit 0
fi

for item in "$CONFIG_REPO"/*; do
    name=$(basename "$item")
    [ "$name" = "settings.json" ] && continue
    [ "$name" = "settings.local.json" ] && continue
    ln -sfn "$item" "$CLAUDE_DIR/$name"
done

echo "Claude config linked from $CONFIG_REPO"

# Workaround: platform may clone with stale capitalized URL instead of the local proxy.
# Repoint to the local proxy so git fetch/pull works correctly.
CURRENT_URL=$(git -C "$PROJECT_DIR" remote get-url origin 2>/dev/null)
EXPECTED_URL="http://local_proxy@127.0.0.1:41365/git/Dansan-Dev/elderpath-crusade"
if [ "$CURRENT_URL" != "$EXPECTED_URL" ]; then
    echo "Fixing git remote URL: $CURRENT_URL -> $EXPECTED_URL"
    git -C "$PROJECT_DIR" remote set-url origin "$EXPECTED_URL"
    git -C "$PROJECT_DIR" fetch origin
    git -C "$PROJECT_DIR" pull origin main
fi
