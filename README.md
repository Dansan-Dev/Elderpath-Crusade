# Elderpath Crusade

Turn-based roguelike strategy set in a once-peaceful forest torn apart by crusades. Train, expand, and outthink your rivals.

Status: Demo (work-in-progress)

---

## Table of Contents
- [For Players](#for-players)
  - [Overview](#overview)
  - [Story](#story)
  - [Features (Demo)](#features-demo)
  - [Download / Play](#download--play)
- [For Developers](#for-developers)
  - [Tech Stack & Architecture](#tech-stack--architecture)
  - [Project Structure](#project-structure-selected)
  - [License](#license)

---

## For Players

### Overview
Elderpath Crusade is a turn-based roguelike strategy game where every decision matters. Explore a contested forest, recruit and train units, capture territory, and lead your people to survive an encroaching crusade.

This is currently a demo. Expect incomplete features, placeholder assets, and balancing that will evolve over time.

### Story
Your tribe, [INSERT NAME], has lived in harmony within the ancient forest. But the rival tribe, [INSERT ENEMY TRIBE NAME], has launched a crusade, sweeping through neighboring lands and shattering the peace. Now it’s conquer or be conquered. Rally your people, forge alliances through dominance, and turn the tide by leading a crusade of your own.

### Features (Demo)
- Turn-based tactical combat on a tile-based board
- Distinct terrain that affects movement and tactics
- Cards & Units (e.g., monsters like Goblins) with unique abilities
- Early UI and main menu; basic pause and settings

Planned for future versions:
- Roguelike runs with replayability
- Card & Unit upgrades
- Expanded unit roster and abilities
- Procedural maps and events
- Spring Boot-backed online features (accounts, leaderboards, saves)

### Download / Play
- Game Gallery: [INSERT LINK TO YOUR GAME GALLERY]
  - The latest demo builds and release notes will be hosted there.

---

## For Developers

### Tech Stack & Architecture
- Game Engine: LibGDX (Java)
- Desktop Launcher: LWJGL3
- Build: Gradle (wrapper included)
- Language: Java 17+
- Assets: Stored under `assets/` (images, audio, UI, shaders, localization)
- Planned backend: Spring Boot for user management and potential online features (leaderboards, profiles, cloud saves)

High-level structure:
- Core game logic in `core/` (rendering, input, rooms/scenes, characters, game objects)
- Platform-specific launcher in `lwjgl3/`
- Gradle multi-module project with shared assets

### Project Structure (selected)
- assets/
  - images, audio, fonts, shaders, UI, language files
- core/src/main/java/io/github/forest_of_dreams/
  - characters/ (pieces, sprites)
  - data_objects/
  - enums/
  - game_objects/
  - managers/
  - rooms/ (e.g., MainMenuRoom, DemoRoom)
  - utils/
- lwjgl3/src/main/java/io/github/forest_of_dreams/lwjgl3/
  - Lwjgl3Launcher (desktop entry point)

### License
- [INSERT LICENSE NAME OR LINK] (TBD)
