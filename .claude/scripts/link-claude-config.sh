#!/bin/bash

# Only run in cloud sessions
[ "$CLAUDE_CODE_REMOTE" != "true" ] && exit 0

CONFIG_REPO="/home/user/elderpath-crusade-claude"
CLAUDE_DIR="$CLAUDE_PROJECT_DIR/.claude"

# Symlink each item from the config repo into the project's .claude/
for item in "$CONFIG_REPO"/*; do
  name=$(basename "$item")
  # Don't overwrite settings.json — it's already there and is what launched this hook
  [ "$name" = "settings.json" ] && continue
  [ "$name" = "settings.local.json" ] && continue
  ln -sfn "$item" "$CLAUDE_DIR/$name"
done

echo "Claude config linked from $CONFIG_REPO"
