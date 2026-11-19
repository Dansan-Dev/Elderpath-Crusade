# Measuring the minimum specs (for the Steam store page)

What the store page needs: OS, CPU, RAM, GPU/OpenGL, disk space. Measure on the **weakest machine you have**
(or a small VM), with the real bundle, not `./gradlew run`.

1. Start the game from the bundle (`ElderpathCrusade.exe` / `roast/ElderpathCrusade`).
2. In a terminal next to it start the sampler (it writes a CSV and prints the peaks at the end):
   - Linux: `tools/spec-measure/sample-linux.sh 600 spec-samples.csv`
   - Windows: `powershell -ExecutionPolicy Bypass -File tools\spec-measure\sample-windows.ps1 -Seconds 600 -Out spec-samples.csv`
     (written without a Windows machine — untested; send the error text if it fails)
3. For those ten minutes play a normal session: main menu, a roguelike battle (the match server then runs as a second
   process), the map, another battle. Press F3 once or twice and note the FPS/frame time the overlay shows.
4. Write down, per machine:
   - OS and version, CPU model and core count, total RAM, GPU model and driver, OpenGL version
     (Linux: `glxinfo | grep "OpenGL version"`; Windows: `dxdiag`, Display tab).
   - Peak memory: game, match server, together (the sampler's last line). Peak CPU of the game.
   - F3 FPS during a battle, and in the main menu; whether it was smooth.
   - Disk: size of the unpacked bundle (`du -sh` / folder properties) plus `~/.elderpathcrusade` afterwards.
5. A minimum spec is the weakest machine where it still felt fine, plus margin — not the average of machines.

Things that change the numbers (check them before trusting a result):
- The Windows/Linux launcher config has `useZgcIfSupportedOs: true` (Construo's default): the JVM uses ZGC where the
  OS supports it, which uses more memory than the default collector. Worth comparing once with it off.
- There is no `-Xmx` in the launcher config, so the maximum heap is 1/4 of the machine's RAM; a 4 GB machine behaves
  differently from a 16 GB one even with the same game.
- `useVsync(false)` with a frame cap of refresh rate + 1: CPU/GPU use follows the monitor's refresh rate.
- The online match server runs on a remote server, not on the player's machine, so only a roguelike/solo battle
  starts the extra local process.

## Simulating a weaker machine (Linux) — `constrained-linux.sh`
You rarely own a truly weak machine. The wrapper runs the game as if on one, and the sampler measures it as usual:
```
tools/spec-measure/constrained-linux.sh --cores 2 --mem 2G --heap 1g --software-gl -- \
    lwjgl3/build/construo/linuxX64/roast/ElderpathCrusade
```
- `--cores N` pins to N cores (also the match server); `--heap` caps the Java heap (the game normally takes 1/4 of RAM);
  `--software-gl` uses Mesa software rendering (harsher than any real integrated GPU); `--mem` / `--cpu-percent` need
  cgroup v2 with `systemd-run --user` and are skipped with a message if unavailable.
- Press F3 in a battle: if the frame time stays near 16 ms with these limits, a low minimum spec is honest. Try a few
  steps (4 cores/4 GB, 2 cores/2 GB, 1 core/1 GB) and write down the weakest one that still felt fine.
- Tested only for the option handling (cores, env variables, error messages); the `--mem`/`--cpu-percent` path could
  not be tried in the sandbox it was written in.
