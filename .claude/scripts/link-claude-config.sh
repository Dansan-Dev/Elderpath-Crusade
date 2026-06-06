#!/bin/bash

echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] SessionStart hook started" >> "${CLAUDE_PROJECT_DIR:-$(pwd)}/.claude/session-start.log"

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
PARENT_DIR="$(dirname "$PROJECT_DIR")"
CONFIG_REPO="$PARENT_DIR/elderpath-crusade-claude"
CLAUDE_DIR="$PROJECT_DIR/.claude"

if [ ! -d "$CONFIG_REPO" ]; then
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Config repo not found at $CONFIG_REPO — skipping link" >> "$CLAUDE_DIR/session-start.log"
    exit 0
fi

for item in "$CONFIG_REPO"/*; do
    name=$(basename "$item")
    [ "$name" = "settings.json" ] && continue
    [ "$name" = "settings.local.json" ] && continue
    ln -sfn "$item" "$CLAUDE_DIR/$name"
done

echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] Claude config linked from $CONFIG_REPO" >> "$CLAUDE_DIR/session-start.log"
