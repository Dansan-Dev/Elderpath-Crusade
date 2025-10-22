---
apply: always
---

Project guidelines for Junie (AI engineer)

Purpose
- This document teaches an AI engineer how to understand, extend, and safely maintain this LibGDX project with minimal but correct changes.
- Keep this file current. Treat it as the project’s “operator manual”.

1) LibGDX essentials used by this project
- Lifecycle: The desktop app launches via Lwjgl3Launcher, which instantiates Main (extends ApplicationAdapter).
    - create(): one-time init (assets, managers, state).
    - render(): per-frame loop; handle input, update, and draw using a SpriteBatch and optional shaders.
    - dispose(): release GPU/asset resources.
- Rendering stack in this repo:
    - GraphicsManager holds global SpriteBatch, renderable registries, and pause state. It delegates to RenderPipeline for the frame orchestration.
    - RenderPipeline performs: clear -> batch.begin() -> draw scene via GraphicsManager -> batch.end(). When paused, it executes a two-pass Gaussian blur using a FrameBuffer and ShaderProgram via ShaderManager.
    - Z-order: Renderables report their z-levels via getZs(); GraphicsManager + ZIndexRegistry iterate z buckets in order.
- Input:
    - InputManager maps physical keys/mouse to semantic InputFunction. Each function has an InputHandler. Main.render() polls with InputManager.checkInput(); pressed keys trigger handlers.
    - InteractionManager coordinates mouse interactions for Clickable objects: hit-testing, immediate and multi-target effects (ClickableEffectData types exist; multi-target flows are scaffolding for future work).
- Assets:
    - Textures and sprites are created via SpriteCreator.makeSprite(...) using Gdx.files.internal(path). Avoid reloading textures every frame; reuse when possible.
- Coordinate spaces:
    - Most objects implement Renderable and/or UIRenderable and can be composed using HigherOrderTexture/LowerOrderTexture containers. Many render methods accept absolute (x,y) for nested drawing.

2) Project structure and module responsibilities
- Entry points
    - lwjgl3/src/.../Lwjgl3Launcher: Desktop launcher and window config.
    - core/src/.../Main: ApplicationAdapter entry; initializes managers, music, and the Game.
- Managers (core/src/.../managers)
    - GameManager: Initializes SettingsManager, ShaderManager, InputManager; controls global pause/unpause.
    - GraphicsManager: Registers renderables/UI renderables; manages paused state; adds/removes clickables alongside visuals; delegates to RenderPipeline.
    - RenderPipeline: Encapsulates the draw pipeline, including blur-pass when paused.
    - InputManager: Maintains input mappings and dispatch to InputHandler implementations (input_handlers package). Keys are declared in enums (InputKey, InputFunction).
    - InteractionManager: Hit-tests Clickable elements, manages selection flows, fires click effects.
    - SettingsManager: Screen size, volumes and other configuration (consult this when placing UI or computing projection matrices).
    - ShaderManager, SoundManager: Shader and audio setup/updates (SoundManager queued music and transitions are invoked in Main.create and Main.render).
- Rooms and screens (core/src/.../rooms)
    - Room (base in supers) represents a scene; DemoRoom wires a playable/demo board + UI. Game likely swaps rooms.
    - DemoRoom: builds a Board, spawns sample pieces, configures a Hand and Deck with Cards (including WolfCard), and places a pause hint Text.
- Game objects
    - Renderables/UI:
        - SpriteObject: a texture/animation wrapper with z-layer, bounds, and alignment (SpriteBoxPos). Supports pause/unpause of animation.
        - HigherOrderTexture/LowestOrderTexture/HigherOrderUI: container-like bases that support parenting and relative coordinate rendering.
    - Board and pieces (core/.../game_objects/board)
        - Board: grid of plots; add/move/remove GamePiece instances.
        - GamePiece: base with stats, alignment, type, id, and an associated Renderable sprite; extensible via data map (GamePieceData).
        - MonsterGamePiece: concrete game piece logic: expendAction(), move, attack(), die() using Board context stored in data.
    - Cards (core/.../game_objects/cards and core/.../cards)
        - Card: a composite renderable with front/back SpriteObject sides, optional playEffect, and consume() hook.
        - Hand: a HigherOrderTexture that lays out Cards horizontally; updates child bounds and registers cards as Clickable via InteractionManager.
        - Deck: a SpriteObject showing the back; holds cards and discard pile, supports draw() and shuffle(); clicking draws a card to Hand.
        - WolfCard: a concrete Card that, when clicked, summons a Wolf onto the Board and then consume()s itself.
- UI (core/.../ui_objects)
    - Text, Button, etc. Some are Clickable (registered into InteractionManager), some are informational UIRenderables.
- Utilities and data
    - utils: SpriteCreator, FontSize, Logger, GraphicUtils, etc.
    - enums: centralize constants for inputs, fonts, piece types, etc.
    - api: BackendService example calls in Main.create() to demonstrate online checks.
- Assets
    - assets/images, audio, music, fonts, shaders, ui. Paths referenced with Gdx.files.internal("assets/..."), so keep relative layout stable.

3) How to build and run
- Prerequisites: Java 17+ (typical for current LibGDX templates), Gradle wrapper included.
- Desktop run:
    - From repo root: ./gradlew :lwjgl3:run
    - In IDE: run the main class io.github.forest_of_dreams.lwjgl3.Lwjgl3Launcher.
- Packaging (desktop):
    - See lwjgl3/build.gradle for packaging tasks; typical ones: :lwjgl3:jar or platform bundles if configured.
- Shaders: If you change GLSL under assets/shaders, verify they compile at runtime via ShaderManager users (blur shader in pause flow).

4) Tests and verification
- There are currently no unit tests checked in for the core gameplay (search for test directories returns none). Preferred approach:
    - Add tests with JUnit 5 under a dedicated test source set (e.g., core/src/test/java) and wire Gradle.
    - For rendering-heavy code, isolate pure logic (e.g., Board, GamePiece) into testable units.
- For manual verification on each change:
    - Run the app (:lwjgl3:run), ensure the DemoRoom loads, input works (left/right click, ESC pause), and rendering appears correct both paused (blur) and unpaused.

5) Coding standards and design principles
- General Java style
    - Prefer immutability of fields where possible; use final for references that don’t change.
    - Use clear naming; avoid abbreviations. Match existing package conventions.
    - Keep methods short and single-responsibility; extract helpers in the same class if they are only used there.
- SOLID/OOP in practice here
    - SRP: Keep rendering, input, and domain logic separated. Don’t put input polling inside game objects; use InteractionManager/InputManager.
    - OCP: Extend via new Renderable/GamePiece/Card subclasses instead of modifying central managers. Register new types using existing extension points (e.g., InputFunction + InputHandler pair).
    - LSP: Subclasses of Renderable/UIRenderable should respect contract of getZs() and render(...); avoid assumptions about parent transforms.
    - ISP: Implement only relevant interfaces (Clickable, UIRenderable) and keep them lean.
    - DIP: Depend on interfaces like Renderable/Clickable where possible; inject collaborators through constructors (e.g., Board into card effect).
- Rendering best practices
    - Do not begin()/end() SpriteBatch inside individual Renderable implementations; the pipeline manages that.
    - Avoid allocating new objects in render() per frame (no new lists/strings if possible). Reuse sprites and buffers.
    - Manage textures carefully; SpriteCreator loads a new Texture per call—prefer sharing when many identical sprites are needed.
- Input and interactions
    - To add a new key or action: extend enums (InputKey/InputFunction) and create a handler in input_handlers; wire it in InputManager.initializeInputHandlers().
    - Clickable widgets must provide ClickableEffectData so InteractionManager knows how to proceed (immediate vs multi-target sequences).
- Error handling and logging
    - Fail fast with IllegalArgumentException for invalid domain states (e.g., MonsterGamePiece rejecting TERRAIN type).
    - Use Logger utility for runtime diagnostics; avoid System.out.println in production code.

6) Making minimal, high-quality changes (important for PR-sized tasks)
- Prefer the smallest change that fully satisfies the requirement:
    - Modify only the directly related class/module and its interfaces. Avoid broad refactors unless explicitly requested.
    - Preserve public APIs and semantics unless the task is a breaking change.
    - Add targeted tests for new behavior if feasible; otherwise, document manual verification steps.
- Keep changes readable:
    - Small, focused commits; clear messages summarizing the intent.
    - Keep method/class visibility as low as possible (package-private/private) to reduce accidental coupling.
- Performance awareness:
    - Don’t add per-frame allocations; keep render paths hot.
    - Use existing managers for registration/deregistration of renderables and clickables to avoid leaks.

7) How to update these guidelines over time
- When you add or change any of the following, update this file in the same PR:
    - New modules/packages (e.g., a new “combat” system) or new core extension points (new manager, new base interfaces).
    - Build/run changes (Gradle tasks, Java version, shader pipeline changes).
    - Input mappings or interaction patterns (e.g., enabling multi-target selection flows).
    - Asset pipeline changes (directory layout, loading strategies, atlas use, shader uniforms).
- Maintenance checklist to include in PR description:
    - What changed architecturally? Which classes/managers are involved?
    - Any new initialization order requirements?
    - Any new commands to run/build/package?
    - Do manual verification steps need updates (what to click/see)?
- Versioning and ownership:
    - Keep this file concise; link to code (paths and class names) rather than duplicating large snippets.
    - If sections grow large, factor deeper docs into README.md or a /docs folder and keep this file as an operational index.

8) Quick navigation to important classes (copy/paste paths)
- Desktop launcher: lwjgl3/src/main/java/io/github/forest_of_dreams/lwjgl3/Lwjgl3Launcher.java
- App core: core/src/main/java/io/github/forest_of_dreams/Main.java
- Rendering:
    - core/src/main/java/io/github/forest_of_dreams/managers/GraphicsManager.java
    - core/src/main/java/io/github/forest_of_dreams/managers/RenderPipeline.java
- Input/Interaction:
    - core/src/main/java/io/github/forest_of_dreams/managers/InputManager.java
    - core/src/main/java/io/github/forest_of_dreams/managers/InteractionManager.java
- Gameplay domain:
    - core/src/main/java/io/github/forest_of_dreams/game_objects/board
    - core/src/main/java/io/github/forest_of_dreams/game_objects/cards
    - core/src/main/java/io/github/forest_of_dreams/cards/WolfCard.java
    - core/src/main/java/io/github/forest_of_dreams/game_objects/sprites/SpriteObject.java
- Demo scene: core/src/main/java/io/github/forest_of_dreams/rooms/DemoRoom.java

9) How to add a new feature (example-driven)
- New keybinding and handler
    - Add enum in InputKey and InputFunction.
    - Implement InputHandler in core/.../input_handlers and register it in InputManager.initializeInputHandlers().
- New card type that summons a piece
    - Subclass Card (see WolfCard). Provide click behavior with setClickableEffect and ClickableEffectData.getImmediate().
    - Ensure consume() is called to move the card from Hand to discard via Deck hooks.
- New renderable entity
    - Create a SpriteObject (or a composite with HigherOrderTexture).
    - Register with GraphicsManager.addRenderable(...) and InteractionManager if Clickable.

10) Submission checklist for AI changes
- If you edited rendering, run paused/unpaused paths and check shader errors in logs.
- If you touched input, click paths should be verified and no null handlers should be triggered.
- If you added assets, confirm Gdx.files.internal() paths resolve from the packaged working dir.
- Build and run using ./gradlew :lwjgl3:run before submitting.


11) Asset paths and path_loaders (no hardcoded paths)
- Policy: Do not hardcode asset paths (images, audio, music, fonts, shaders, UI). Always reference a path loader enum from core/src/main/java/io/github/forest_of_dreams/path_loaders.
- Existing loaders:
    - ImagePathSpritesAndAnimations: art for sprites, animations, card fronts/backs, terrain, etc.
    - ImagePathBackgroundAndUI: background and UI images.
- How it works:
    - Each enum value wraps a relative path from the assets/ root (e.g., "images/card_front.png").
    - Call getPath() and pass that to Gdx.files.internal(...) via utilities like SpriteCreator.makeSprite(...).
- Example (correct):
    - SpriteCreator.makeSprite(ImagePathSpritesAndAnimations.CARD_FRONT.getPath(), 0, 0, 1024, 1536, 125, 200)
- Example (avoid):
    - SpriteCreator.makeSprite("assets/images/card_front.png", ...)
- Adding a new asset path:
    1. Pick or create the appropriate enum under path_loaders (keep similar assets together; prefer existing enums).
    2. Add a constant with the filename only; the constructor should prepend the directory (e.g., "images/").
    3. Use the enum value everywhere instead of hardcoded strings.
- Migration checklist when touching assets:
    - Replace any remaining hardcoded paths with the corresponding enum.
    - If a path doesn’t fit existing enums, create a new enum (e.g., AudioPath, MusicPath, FontPath, ShaderPath) with a similar pattern and update this section.

12) Color and font size standardization (no hardcoded styling)
- Goal: Avoid hardcoded colors and font sizes scattered in code. Use centralized enums/utilities.
- Colors:
    - ColorScheme (io.github.forest_of_dreams.utils.ColorScheme): Defines the project’s palette (what colors exist). Update this when adding or curating the palette.
    - ColorSettings (io.github.forest_of_dreams.utils.ColorSettings): Functional mapping that applies palette colors to semantic roles (TEXT_DEFAULT, BUTTON_PRIMARY, PLOT_GREEN, etc.).
- Long‑term approach:
    1. Define allowed colors in ColorScheme.java (the palette layer).
    2. Map palette entries to functional roles in ColorSettings.java.
    3. Use ColorSettings.<ROLE>.getColor() throughout the code (UI, board, pieces) instead of Color.WHITE or hex literals.
- Example usage:
    - Text title = new Text("Hello", FontType.SILKSCREEN, x, y, z, ColorSettings.TEXT_DEFAULT.getColor());
    - Button border on hover -> ColorSettings.BUTTON_BORDER_HOVER.getColor().
- Adding/changing colors:
    - First add/update ColorScheme, then update ColorSettings to map the appropriate role to the new scheme color. Avoid introducing new ad‑hoc roles without need; prefer reusing roles.
- Fonts and sizes:
    - Use io.github.forest_of_dreams.utils.FontSize for standard sizing (e.g., BODY_SMALL, BODY_MEDIUM, TITLE_LARGE).
    - Prefer .withFontSize(FontSize.X) or computed sizes derived from the standard tokens where available; avoid magic numbers.
    - If a dynamic size is necessary (responsive to container), compute it from a base FontSize and document rationale.
- PR checklist for styling changes:
    - No raw new Color(...) or Color.valueOf("#...") scattered in code; replace with ColorSettings where applicable.
    - No hardcoded path strings for assets; use path_loader enums.
    - Update ColorScheme and ColorSettings together when introducing new palette entries or roles.
    - Update this guidelines file if you add new path_loader enums or significant styling roles.


13) Rooms system (detailed)
- What is a Room:
    - Base class: core/src/main/java/io/github/forest_of_dreams/supers/Room.java.
    - A Room represents a self-contained scene (e.g., Main Menu, Settings, Demo gameplay). It owns two lists:
        - contents: Renderable scene objects (Board, Hand, Deck, pieces, non-UI sprites).
        - ui: UIRenderable elements (Text, Button, overlays). These are rendered in the UI layer and can be allowed to receive clicks while the game is paused.
    - API:
        - addContent(Renderable): register a visual/game object with this room.
        - addUI(UIRenderable): register a UI element with this room.
        - showContent(): pushes all contents to GraphicsManager.addRenderables(...). This also registers Clickable items and nested clickables via ClickableRegistryUtil.
        - showUI(): pushes all UI to GraphicsManager.addUIRenderable(...), which similarly handles Clickable registration.
        - onScreenResize(): override to realign or resize room elements when screen size changes.

- Room lifecycle and switching:
    - Game entry: Game.initialize() -> gotoRoom(MainMenuRoom::get). Path: core/src/main/java/io/github/forest_of_dreams/managers/Game.java.
    - Switching: Game.gotoRoom(Supplier<Room>) clears current scene, constructs the next Room, and calls room.showContent() + room.showUI(). Clearing uses:
        - GraphicsManager.clearRenderables(); GraphicsManager.clearUIRenderables(); InteractionManager.clearClickables();
    - Construction pattern: Rooms usually have a private constructor and a static get() factory that returns a new instance each time (e.g., DemoRoom.get(), MainMenuRoom.get(), SettingsRoom.get()). This ensures a fresh scene on each visit and avoids carrying state across scenes unless explicitly persisted elsewhere.

- Rendering and input integration:
    - GraphicsManager orchestrates drawing. It iterates z-level buckets and calls render(...) on each Renderable. UI elements render in GraphicsManager.renderUI(...).
    - Z-order: Provide meaningful z values from each Renderable/UIRenderable; GraphicsManager + ZIndexRegistry handle draw order.
    - Clickables: When a Renderable or UI element implements Clickable (or is a HigherOrderTexture/HigherOrderUI container), registration happens automatically via GraphicsManager when you add content/UI. InteractionManager handles hit-testing and click flows.
    - Pause behavior: When paused, GraphicsManager.blurredDraw applies the blur pass to the scene; InteractionManager allows only UI elements flagged as pause UI to receive clicks. Room content itself does not manage pause—keep it data/UI driven.

- Screen resize flow:
    - ScreenSize updates call Game.currentRoom.onScreenResize() when resolution changes: core/src/main/java/io/github/forest_of_dreams/data_objects/settings/ScreenSize.java.
    - Override onScreenResize in your Room to reposition anchors, centers, and dependent child bounds based on SettingsManager.screenSize values.

- Directory layout and examples:
    - Rooms live in core/src/main/java/io/github/forest_of_dreams/rooms.
        - DemoRoom.java: builds a Board, spawns sample pieces, sets up a Hand/Deck with Cards (including WolfCard), and adds a pause hint Text. Demonstrates layout recomputation in onScreenResize().
        - MainMenuRoom.java: constructs main menu UI and uses Game.gotoRoom(DemoRoom::get) etc. for navigation.
        - SettingsRoom.java: settings UI; buttons navigate back to MainMenuRoom.
        - rooms/main_menu/MainMenuNavbar.java: an example UI container used by MainMenuRoom.

- How to create a new Room (pattern):
    1. Create a class NewFeatureRoom extends Room under core/src/main/java/io/github/forest_of_dreams/rooms.
    2. Use a private constructor and a public static NewFeatureRoom get() factory.
    3. In the constructor:
        - Build your scene objects (Boards, sprites, containers) and call addContent(...) for each.
        - Build your UI (Text, Button, panels) and call addUI(...). For buttons that navigate, use .withOnClick((e) -> Game.gotoRoom(OtherRoom::get), ClickableEffectData.getImmediate()).
        - Compute initial layout using SettingsManager.screenSize (e.g., centers, screen height/width); set bounds on parents and children accordingly.
    4. Override onScreenResize() to recompute positions and sizes when the screen changes.
    5. To navigate to this room, call Game.gotoRoom(NewFeatureRoom::get) from any click handler or room logic.

- Layout tips specific to this project:
    - Use HigherOrderTexture/HigherOrderUI to parent child renderables; set child.setParent(parentBox) so relative coordinates apply. Many render methods accept absolute x,y for nested drawing.
    - For card/hand layouts, see Hand.updateBounds(): it positions cards relative to the hand container and registers each card as Clickable with InteractionManager.
    - For board-centric rooms, center the Board similarly to DemoRoom.layoutBoard() using SettingsManager.screenSize.getScreenCenter().

- Room do’s and don’ts:
    - Do not call SpriteBatch.begin()/end() inside room code; the RenderPipeline manages this.
    - Do not directly register clickables with InteractionManager when adding via GraphicsManager; rely on addContent/addUI to route through GraphicsManager so nested clickables are handled correctly.
    - Keep per-frame allocations out of render paths; build lists once in the constructor and reuse objects. Adjust bounds rather than recreating renderables.
    - Respect the asset path loader policy (see section 11) and color/font standards (see section 12) for any UI elements added in rooms.
