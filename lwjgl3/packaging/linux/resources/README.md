# Forest of Dreams — Linux Package Guide

Welcome! This archive contains the Forest of Dreams desktop build for Linux (x86_64) with a per‑user installer and uninstaller. This document explains how to install, uninstall, and run the game, plus a few useful tips.


## Contents of this ZIP
- ForestOfDreams (executable launcher)
- install.sh (per‑user installer)
- uninstall.sh (per‑user uninstaller)
- icons/icon-256.png (application icon used for desktop integration)
- README.md (this file)
- Game runtime files and assets required to play


## Quick start
1) Extract the ZIP somewhere you control (e.g., ~/Desktop/ForestOfDreams-linuxX64/).
2) Open a terminal in the extracted directory.
3) Run the installer either through filesystem explorer or terminal
   1) Filesystem Explorer
      1) Right-click the install.sh file
      2) Go to properties
      3) Turn on "Executable as Program"
      4) Then right click the install.sh file again
      5) Select "run as a program"
   2) Terminal
      1) First allow file execution -> 
      chmod +x install.sh
      2) Then run the installer ->
      ./install.sh
   
4) Open your desktop/app menu and search for “Forest of Dreams”.


## Installation details
- Scope: Per‑user (no sudo required, no system files modified).
- Files are copied to your home directory:
  - App directory: ~/.local/share/forestofdreams
  - Launcher binary: ~/.local/share/forestofdreams/ForestOfDreams
  - Desktop entry: ~/.local/share/applications/forestofdreams.desktop
  - Icon: ~/.local/share/icons/hicolor/256x256/apps/forestofdreams.png
- The installer updates your local desktop and icon caches where available:
  - update-desktop-database ~/.local/share/applications
  - gtk-update-icon-cache -f ~/.local/share/icons/hicolor

Notes
- If the app does not appear immediately in your app menu, log out and back in, or run the cache update commands above manually.
- You can run the game directly without installing by executing ./ForestOfDreams from the extracted directory, but menu integration and icon will not be set up.


## Uninstall
To remove everything added by the installer:

1) Filesystem explorer
   1) right click the uninstall.sh file
   2) Go to properties
   3) Turn on "Executable as Program"
   4) Then right click the uninstall.sh file again
   5) Select "run as a program"
2) Terminal
   1) Open a terminal in this directory
   2) Run the uninstaller:
       1) First allow file execution ->
          chmod +x uninstall.sh
       2) Then run the uninstaller ->
          ./uninstall.sh
   
         Alternatively, if you deleted the extracted folder, remove the files manually:
   
         rm -rf ~/.local/share/forestofdreams
         rm -f ~/.local/share/applications/forestofdreams.desktop
         rm -f ~/.local/share/icons/hicolor/256x256/apps/forestofdreams.png
   
3) Optionally refresh caches:
   
   update-desktop-database ~/.local/share/applications || true
   gtk-update-icon-cache -f ~/.local/share/icons/hicolor || true

## Running without installing
From the extracted directory:

chmod +x ForestOfDreams
./ForestOfDreams

This launches the game directly. May not show the correct icon when running this way.

## Requirements and compatibility
- Architecture: x86_64 Linux.
- Bundled runtime: A Java 17 runtime is packaged with the build.
- Graphics: OpenGL-capable GPU and drivers compatible with LWJGL3/libGDX.
- File permissions: If you extracted the ZIP on a filesystem that strips the executable bit, install.sh will re-set it for the launcher.

## Troubleshooting
- “Permission denied” when running scripts:
  - Ensure scripts are executable: chmod +x install.sh uninstall.sh ForestOfDreams
- Game doesn’t appear in app menu after install:
  - Run cache updates (see above) or log out and back in.
- Missing icons in the menu:
  - Ensure ~/.local/share/icons/hicolor/256x256/apps/forestofdreams.png exists, then run gtk-update-icon-cache -f ~/.local/share/icons/hicolor
- App won’t start when double-clicked:
  - Try running from a terminal to see messages: ~/.local/share/forestofdreams/ForestOfDreams
- Assets not loading / crashes after manual moves:
  - Avoid moving individual files within ~/.local/share/forestofdreams. Reinstall by running install.sh again from the extracted ZIP.


## Updating the game
- Re-download the new ZIP, extract it, and run ./install.sh again. It will overwrite the previous installation under ~/.local/share/forestofdreams.


## Security notes
- install.sh and uninstall.sh only operate within your $HOME and do not require sudo.
