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
