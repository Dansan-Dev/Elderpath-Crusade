# Forest of Dreams — macOS Package Guide

Welcome! This archive contains the Forest of Dreams desktop build for macOS. This guide explains how to open the app, handle Gatekeeper prompts, and add it to your Applications folder and Dock.


## Contents of this ZIP
- ForestOfDreams.app (the application bundle)
- README.md (this file)


## Quick start
1) Extract the ZIP to a folder you control (for example: Desktop/ForestOfDreams-mac/).
2) Double‑click ForestOfDreams.app to start the game.

If macOS shows a security prompt, see “Opening the app the first time” below.


## Opening the app the first time (Gatekeeper)
Depending on your security settings, macOS may block apps from unidentified developers the first time you open them.

If you see a message like “ForestOfDreams.app cannot be opened because it is from an unidentified developer”:
- Method A (recommended):
  1) Control‑click (right‑click) ForestOfDreams.app
  2) Choose “Open”
  3) In the dialog, click “Open” again
- Method B (from System Settings):
  1) Try to open the app once so the warning appears
  2) Open System Settings → Privacy & Security
  3) Scroll to “Security” and click “Open Anyway” for ForestOfDreams

After the first successful open, you can launch it normally by double‑clicking.


## Move to Applications (optional but recommended)
- Drag ForestOfDreams.app into the Applications folder to keep it with your other apps.
- You can then launch it from Launchpad or Spotlight, and keep the game folder tidy.


## Add to Dock (optional)
- Start the game once, then right‑click the icon in the Dock and choose “Options” → “Keep in Dock”.


## Updating the game
- Download the new ZIP, extract it, and replace the old ForestOfDreams.app in Applications or your chosen folder.
- If the Dock icon stops working after moving/replacing the app, remove it from the Dock and add it again.


## Troubleshooting
- “App is damaged or can’t be opened”: If the download was quarantined or altered, re‑download the ZIP and re‑extract it with the built‑in Archive Utility.
- “Cannot be opened because the developer cannot be verified”: Use the Gatekeeper steps above (Control‑click → Open).
- Nothing happens on double‑click: Open Console to check logs, or run the app from Terminal to see messages.


## Notes
- The app bundle includes a Java 17 runtime; you do not need to install Java separately.
- A copy of this README is also placed inside the app bundle at: ForestOfDreams.app/Contents/MacOS/README.md
