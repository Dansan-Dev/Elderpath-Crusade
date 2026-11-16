# Releasing Elderpath Crusade

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
- Output is under `lwjgl3/build/construo/` (exact file names: check after the first build) (unverified).
- The runtime's module list is in `lwjgl3/build.gradle` (`jlink { modules ... }`). If you add a dependency that
  needs another JDK module, run `jdeps` over the classes and add it — a missing module only shows as a
  `NoClassDefFoundError` at runtime (that is how `java.management`/`jdk.httpserver` were found).
- Whether Construo can build the macOS targets from Windows/Linux is (unverified); if it can't, build them on a Mac.
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
in particular the `proxy_proto` handler's PROXY version (the server parses v1) and the memory size (`1gb`).
To roll back: `fly releases` and redeploy the previous image, or `fly deploy --config deploy/fly/fly.toml`
(that restores the old one-bot-match server, which the new client cannot use).

## 5. Steam (SteamPipe) — outline, (unverified) in detail; follow Valve's docs for the exact files
1. Steamworks → your app → **SteamPipe → Depots**: one depot per OS (Windows, Linux, macOS).
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
