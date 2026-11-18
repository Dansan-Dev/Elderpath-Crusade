# Releasing Elderpath Crusade

> **Platforms for the first release (decision 2026-11-18): Windows and Linux only.** macOS is not shipped — that would
> need an Apple Developer ID (paid) for signing/notarisation, which the owner chose not to buy. The macOS Construo targets
> still exist and build; they are simply not uploaded. Skip every macOS line below (build, smoke test, depot, launch option).

How to turn this repository into the builds Steam serves, and the server they talk to. Written from the
build files in this repo, not from a release that has been done: **anything marked (unverified) has never
been run** — fix this file as soon as a real release shows otherwise.

Related: `deploy/README.md` (the server), `.claude/plans/steam-release-plan.md` (decisions and open items),
`.claude/plans/credits-draft.md` (third-party licences).

## 0. What you need on the build machine
- **JDK 17** (`JAVA_HOME`), and network access (Construo downloads a Temurin 17 runtime per target from GitHub).
- **The gitignored files**: `assets/` (data, images, audio, fonts, shaders, `asset_register.yaml`),
  `lwjgl3/icons/*` and `lwjgl3/src/main/resources/icon-*.png`. A clean checkout can't produce a release —
  build on the machine that holds them. `assets/` is packed into the game jar (`lwjgl3/build.gradle` adds it
  to the resources), so nothing else has to be shipped beside the game.
- Nothing from `.claude/` is needed to build.
- **Measuring minimum specs** for the store page: `tools/spec-measure/README.md` (Linux sampler tested; the Windows
  PowerShell one was written blind).
- **Moving the gitignored files to another machine** (a second checkout, a CI box): on the machine that has
  them run `./gradlew exportAssets` (writes `build/asset-bundles/elderpath-assets-<time>.zip`, or choose the file
  with `-Pout=<file>`), copy the zip over, then in the other checkout `./gradlew importAssets -Pzip=<file>`.
  Import verifies a SHA-256 per file against the bundle's manifest before writing anything, refuses unsafe paths,
  never deletes files that aren't in the bundle, and refuses to overwrite a file that differs unless
  `-Pforce=true` (the old file is then kept in `build/asset-import-backup/<time>/`). `-PdryRun=true` only reports.
  The generated `assets/assets.txt` and Windows `:Zone.Identifier` leftovers are left out. Self-test:
  `gradle/test-assets-tool.sh` (21 scenarios on throw-away fake projects).

## 1. Before every release
1. **Version**: bump `projectVersion` in `gradle.properties` (the name comes from `projectName` there too).
   The version shows bottom-left on the main menu and goes into saved deck-collection entries.
2. **All tests, local profile** (this also runs the child-process tests the cloud profile skips):
   ```sh
   ./gradlew test -PtestProfile=local --no-daemon
   ```
   Then list the results (gradle prints nothing on success) — expect `skipped` near 0:
   ```sh
   python3 - <<'E'
   import glob,re
   for m in ('core','server','cli'):
       t=f=s=0
       for x in glob.glob(f'{m}/build/test-results/test/*.xml'):
           h=open(x).read(2000)
           t+=int(re.search(r'tests="(\d+)"',h)[1]); f+=int(re.search(r'failures="(\d+)"',h)[1])+int(re.search(r'errors="(\d+)"',h)[1]); s+=int(re.search(r'skipped="(\d+)"',h)[1])
       print(m,t,'tests',f,'failed',s,'skipped')
   E
   ```
3. **Unattended soak** against the real data (catches client/server disagreements):
   ```sh
   ./gradlew :cli:run --args="--soak 20 --seed 1 --assets $PWD/assets"
   ```
4. **Asset register** (`assets/asset_register.yaml`) complete and honest; credits text up to date.
5. Commit, then tag: `git tag v<version>` and push the tag, so a build can always be traced to a commit.

## 2. Build the bundles
One task per platform; each downloads its runtime, makes a minimal runtime with jlink, and zips the result:
```sh
./gradlew :lwjgl3:packageWinX64 --no-daemon
./gradlew :lwjgl3:packageLinuxX64 --no-daemon
./gradlew :lwjgl3:packageMacX64 :lwjgl3:packageMacM1 --no-daemon
```
- **Verified 2026-11-16** (all four built from a Linux/WSL host, ~3 min total, each downloads its Temurin 17.0.15 JDK):
  - Zips: `lwjgl3/build/construo/dist/ElderpathCrusade-{linuxX64,winX64,macX64,macM1}.zip` (~160 MB each). Each holds one
    folder `ElderpathCrusade-<version>-<target>/` (so the version is in the folder name, not the zip name).
  - **For SteamPipe, upload the staged folders, not the zips** — Construo's zip stores no file modes (everything is
    `0000` except the main launcher), so an unzipped Linux/macOS `install.sh` or `java` is not executable. Staged
    folders: `lwjgl3/build/construo/linuxX64/roast/`, `.../winX64/roast/`, `.../macX64/ElderpathCrusade.app`,
    `.../macM1/ElderpathCrusade.app`. (The game also sets the exec bit on its own `runtime/bin/java` if missing.)
  - Executables (for Steam launch options): Linux `ElderpathCrusade` (next to `ElderpathCrusade-<version>.jar`,
    `runtime/`, `app/ElderpathCrusade.json`, `install.sh`, `uninstall.sh`, `icons/icon-256.png`, `README.md`);
    Windows `ElderpathCrusade.exe`; macOS `ElderpathCrusade.app/Contents/MacOS/ElderpathCrusade`. The launcher is
    a native "roast" program that loads the runtime's libjvm in-process — it is **not** a `java`.
  - Fixed 2026-11-16: the jar no longer carries `asset_register.yaml`, `images_old/`, `*Zone.Identifier`, pack `.url`
    and `Preview.ogg`, the unused numbered `audio/NN_*` files or the four unused music tracks (136 MB → 89 MB
    uncompressed jar). See `jar { exclude ... }` in `lwjgl3/build.gradle`. Add a track/sfx to the used set by
    removing its exclude.
  - Fixed 2026-11-16: **the runtime had no `java` executable**, so `ServerProcess` (which used the process
    command = the native launcher) could not start a match server from a bundle. `injectJava<Target>` now copies
    `bin/java[.exe]` from the Temurin archive into `runtime/bin`, and `ServerProcess.javaBinary()` prefers
    `java.home/bin/java`. And **`java.logging` was missing from the module list** (SnakeYAML needs it; the child
    server died at startup). Verified by starting `ServerLauncher` (match mode, and lobby mode with `/healthz`) using
    only the Linux bundle's own `runtime/bin/java` with no `java` on `PATH`.
- The runtime's module list is in `lwjgl3/build.gradle` (`jlink { modules ... }`). If you add a dependency that
  needs another JDK module, add it — `jdeps` over our fat jar only reports `java.base` and cannot be trusted; instead start the server from the bundle's own runtime (see below) — a missing module only shows as a
  `NoClassDefFoundError` at runtime (that is how `java.management`/`jdk.httpserver` were found).
- Construo **does** build both macOS targets from Linux (verified 2026-11-16). Whether the resulting `.app` runs on a Mac is not tested (no Mac here).
- Solo, hotseat and hosted play start a **child server process** from inside the game: the bundle's runtime must
  be able to run it (`java -cp <the game jar> ...ServerLauncher`). Step 3 checks exactly that.

## 3. Smoke-test every bundle (per platform, on a clean machine or account if you can)
Unzip it somewhere with no Java installed, then:
- [ ] Starts; window title is **Elderpath Crusade**; the main menu shows `v<version>` bottom-left.
- [ ] Music plays, and a volume change in Settings is still there after quitting and relaunching.
- [ ] **Roguelike Run**: start one, play a battle through to the end. During the battle there must be a second
      `java` process (the match server); after leaving the battle it must be gone (Task Manager / `ps`).
- [ ] **Play Online**: connect to the real server, host a lobby, join it from a second client, play a few turns,
      drop one client's network (unplug/kill) and watch the reconnect screen bring it back.
- [ ] F3 shows the diagnostics overlay (this needs `java.management` in the runtime).
- [ ] Quit via the pause menu and via the window close button: no leftover processes.
- [ ] `~/.elderpathcrusade/logs/game.log` exists and has the start line; a deliberate bad action shows nothing scary.
- [ ] Art, sounds and cards all present (a missing file is skipped silently and logged — read the log for
      `missing`).
- Windows: expect a SmartScreen warning and some antivirus false positives for an unsigned exe (unverified until
  tried). macOS: an unsigned/unnotarized app is blocked by Gatekeeper when it didn't come through Steam;
  signing and notarising (Apple Developer ID) is **not** done by Construo (unverified whether Steam needs it).
- Linux: tested on the distributions you care about (Steam Deck is Arch-based).

## 4. The lobby server (do this before publishing the client)
The client's **Play Online** talks to `elderpath-server.fly.dev` over TLS. The lobby protocol (2) and match
protocol (4) are incompatible with older clients, so the server and the first public client ship together.
```sh
fly deploy --config deploy/fly/fly.lobby.toml          # from the repo root; REPLACES the running bot-match server
fly scale count 1 --config deploy/fly/fly.lobby.toml   # lobbies live in one process's memory
fly logs --config deploy/fly/fly.lobby.toml            # expect "lobby service listening" and a health line
```
Then connect with a real client (step 3). Everything in `fly.lobby.toml` is (unverified) until the first deploy —
in particular the handler order and the memory size (`1gb`). The PROXY version is confirmed: Fly's `proxy_proto` sends v1 by default (docs.fly.io/networking/services, checked 2026-11-16), which is what the server parses — never set `proxy_proto_options` to v2.
To roll back: `fly releases` and redeploy the previous image, or `fly deploy --config deploy/fly/fly.toml`
(that restores the old one-bot-match server, which the new client cannot use).

## 5. Steam (SteamPipe) — outline, (unverified) in detail; follow Valve's docs for the exact files
1. Steamworks → your app → **SteamPipe → Depots**: one depot per OS — **Windows and Linux** (no macOS depot for now).
2. Put each bundle's contents in its content folder; write a depot build script (`depot_build_<id>.vdf`)
   and an app build script (`app_build.vdf`) mapping depots → content folders.
3. Upload with `steamcmd +login <build account> +run_app_build app_build.vdf +quit`, **to a private branch**
   (not `default`), then set a branch password and install from that branch in the Steam client to test.
4. **Launch options** (Installation → General): the executable per OS — Windows `ElderpathCrusade.exe`,
   Linux `ElderpathCrusade`, macOS `ElderpathCrusade.app` (names from Construo's `name`; check the real files).
5. When the build and the store page are approved, set the build live on `default`.
No Steamworks API is used in this release (decision 2026-11-15), so no `steam_appid.txt` or native Steam
libraries are needed.

## 6. Store side (in Steamworks, not in this repo)
- Store page assets, "Coming Soon" public **at least 2 weeks** before release; pricing; release date.
- **AI-content disclosure**: answer honestly — the art is AI-generated (Retro Diffusion). Don't describe it as
  hand-made anywhere (Replicate's terms forbid it).
- Content / age-rating questionnaire; tax, banking and identity set-up; the $100 app fee.
- Valve reviews the store page and the build separately; allow several days.

## 7. After release
- Keep the server and the client in step: a protocol bump (`MessageCodec.PROTOCOL_VERSION`,
  `LobbyCodec.LOBBY_PROTOCOL_VERSION`) means deploying the server and publishing the client together.
- Players' bug reports: ask for `~/.elderpathcrusade/logs/game.log` (and `game.log.1`).
