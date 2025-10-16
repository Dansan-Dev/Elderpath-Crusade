#!/usr/bin/env bash
set -euo pipefail

APP_ID=forestofdreams
APP_NAME="Forest of Dreams"
INSTALL_DIR="$HOME/.local/share/$APP_ID"
BIN_PATH="$INSTALL_DIR/ForestOfDreams"
ICON_SRC="./icons/icon-256.png"
ICON_DST="$HOME/.local/share/icons/hicolor/256x256/apps/${APP_ID}.png"
DESKTOP_FILE="$HOME/.local/share/applications/${APP_ID}.desktop"

mkdir -p "$INSTALL_DIR"
mkdir -p "$(dirname "$ICON_DST")"
mkdir -p "$(dirname "$DESKTOP_FILE")"

rsync -a ./ "$INSTALL_DIR/"

cp "$ICON_SRC" "$ICON_DST"

cat > "$DESKTOP_FILE" <<EOF
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=${APP_NAME}
Exec="${BIN_PATH}"
Icon=${APP_ID}
Terminal=false
Categories=Game;
EOF

chmod +x "$BIN_PATH"
chmod +x "$DESKTOP_FILE"

if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$HOME/.local/share/applications" || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -f "$(dirname "$ICON_DST")/.." || true
fi

echo "Installed to: $INSTALL_DIR"
echo "Launcher: $DESKTOP_FILE"
echo "You can now find '${APP_NAME}' in your app menu."
