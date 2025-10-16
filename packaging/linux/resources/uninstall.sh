#!/usr/bin/env bash
set -euo pipefail

# Uninstall script for Forest of Dreams (per-user install)
# This removes files that install.sh created under $HOME only.
# It is designed to be conservative and will abort if expected
# paths do not match safe patterns.

APP_ID=forestofdreams
APP_NAME="Forest of Dreams"
INSTALL_DIR="$HOME/.local/share/$APP_ID"
BIN_PATH="$INSTALL_DIR/ForestOfDreams"
ICON_DST="$HOME/.local/share/icons/hicolor/256x256/apps/${APP_ID}.png"
DESKTOP_FILE="$HOME/.local/share/applications/${APP_ID}.desktop"

abort() {
  echo "[uninstall] ERROR: $*" >&2
  exit 1
}

ensure_home_set() {
  if [[ -z "${HOME:-}" ]]; then
    abort "HOME is not set; refusing to continue."
  fi
}

# Verify a path is inside $HOME and matches an expected prefix exactly
# Arguments: $1 path, $2 expected_prefix
ensure_safe_path() {
  local p="$1"
  local expected_prefix="$2"

  # Empty guard
  [[ -n "$p" ]] || abort "Empty path given for removal."

  # Ensure path begins with $HOME/
  [[ "$p" == "$HOME"/* ]] || abort "Refusing to operate on path outside HOME: $p"

  # Ensure path matches the expected prefix exactly (to avoid typos)
  if [[ -n "$expected_prefix" ]]; then
    [[ "$p" == "$expected_prefix" ]] || abort "Path does not match expected location: expected '$expected_prefix', got '$p'"
  fi
}

remove_file_if_exists() {
  local f="$1"
  if [[ -e "$f" ]]; then
    rm -f -- "$f"
    echo "Removed: $f"
  else
    echo "Not found (skipped): $f"
  fi
}

remove_dir_if_exists() {
  local d="$1"
  if [[ -d "$d" ]]; then
    rm -rf -- "$d"
    echo "Removed directory: $d"
  else
    echo "Not found (skipped): $d"
  fi
}

ensure_home_set

# Validate targets before removal
ensure_safe_path "$INSTALL_DIR" "$HOME/.local/share/$APP_ID"
ensure_safe_path "$ICON_DST" "$HOME/.local/share/icons/hicolor/256x256/apps/${APP_ID}.png"
ensure_safe_path "$DESKTOP_FILE" "$HOME/.local/share/applications/${APP_ID}.desktop"

# Remove desktop entry and icon first (these are small and simple)
remove_file_if_exists "$DESKTOP_FILE"
remove_file_if_exists "$ICON_DST"

# Optionally clean up icon cache and desktop DB
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$HOME/.local/share/applications" || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -f "$HOME/.local/share/icons/hicolor" || true
fi

# Remove installed application directory (contains only app files copied by install.sh)
remove_dir_if_exists "$INSTALL_DIR"

# Best effort: if parent directories are now empty, leave them; do not remove parents to be safe.

cat <<MSG
Uninstall completed.
- Removed app directory: $INSTALL_DIR
- Removed desktop file:  $DESKTOP_FILE
- Removed icon file:     $ICON_DST
If the app still appears in menus, try logging out and back in, or running:
  update-desktop-database "$HOME/.local/share/applications"
  gtk-update-icon-cache -f "$HOME/.local/share/icons/hicolor"
MSG
