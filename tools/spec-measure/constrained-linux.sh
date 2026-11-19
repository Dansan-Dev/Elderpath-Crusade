#!/usr/bin/env bash
# Runs a command (normally the game) as if on a weaker machine, to find out whether a low minimum spec is honest:
# fewer CPU cores, a memory cap, a smaller Java heap, and/or software OpenGL (no real GPU).
#
#   tools/spec-measure/constrained-linux.sh [--cores N] [--cpu-percent P] [--mem 2G] [--heap 512m] [--software-gl] -- COMMAND...
#
# Example (the packaged Linux build on 2 cores, 2 GB, no GPU), with the sampler running in another terminal:
#   tools/spec-measure/constrained-linux.sh --cores 2 --mem 2G --software-gl -- lwjgl3/build/construo/linuxX64/roast/ElderpathCrusade
#
#   --cores N        pin the process (and its children, e.g. the match server) to the first N CPU cores (taskset)
#   --cpu-percent P  cap total CPU time, e.g. 100 = one core's worth (needs systemd-run --user; see below)
#   --mem SIZE       hard memory cap for the whole process tree (needs systemd-run --user), e.g. 2G
#   --heap SIZE      maximum Java heap via JAVA_TOOL_OPTIONS=-Xmx<SIZE> (works anywhere; the game normally takes 1/4 of RAM)
#   --software-gl    Mesa software rendering (LIBGL_ALWAYS_SOFTWARE=1), a stand-in for a very weak GPU
#
# What is honest to conclude: "it kept ~60 FPS (F3 overlay) with these limits" is a fair basis for a low minimum;
# a slow-looking run here only means this simulation is harsher than the real weak machine would be (software
# rendering is slower than most real integrated GPUs). --mem and --cpu-percent need cgroup v2 + systemd user
# sessions; if they are not available the script says so and runs without that limit rather than pretending.
set -u
CORES=""; CPU_PERCENT=""; MEM=""; HEAP=""; SOFTWARE_GL=0
while [ $# -gt 0 ]; do
  case "$1" in
    --cores) CORES="$2"; shift 2 ;;
    --cpu-percent) CPU_PERCENT="$2"; shift 2 ;;
    --mem) MEM="$2"; shift 2 ;;
    --heap) HEAP="$2"; shift 2 ;;
    --software-gl) SOFTWARE_GL=1; shift ;;
    --) shift; break ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "unknown option: $1 (use -- before the command)" >&2; exit 2 ;;
  esac
done
[ $# -gt 0 ] || { echo "no command given (put it after --)" >&2; exit 2; }

cmd=("$@")
echo "constrained run:${CORES:+ cores=$CORES}${CPU_PERCENT:+ cpu=${CPU_PERCENT}%}${MEM:+ mem=$MEM}${HEAP:+ heap=$HEAP}$([ $SOFTWARE_GL = 1 ] && echo ' software-gl') -> ${cmd[*]}" >&2

env_args=()
[ "$SOFTWARE_GL" = 1 ] && env_args+=(LIBGL_ALWAYS_SOFTWARE=1)
[ -n "$HEAP" ] && env_args+=("JAVA_TOOL_OPTIONS=-Xmx${HEAP} ${JAVA_TOOL_OPTIONS:-}")

if [ -n "$CORES" ]; then
  total="$(nproc --all)"
  if ! [[ "$CORES" =~ ^[0-9]+$ ]] || [ "$CORES" -lt 1 ] || [ "$CORES" -gt "$total" ]; then
    echo "--cores must be a number from 1 to $total" >&2; exit 2
  fi
  if command -v taskset >/dev/null; then
    cmd=(taskset -c "0-$((CORES - 1))" "${cmd[@]}")
  else
    echo "taskset not found: running without the core limit" >&2
  fi
fi

if [ -n "$MEM" ] || [ -n "$CPU_PERCENT" ]; then
  if command -v systemd-run >/dev/null && systemd-run --user --scope --quiet true 2>/dev/null; then
    props=()
    [ -n "$MEM" ] && props+=(-p "MemoryMax=$MEM" -p "MemorySwapMax=0")
    [ -n "$CPU_PERCENT" ] && props+=(-p "CPUQuota=${CPU_PERCENT}%")
    cmd=(systemd-run --user --scope --quiet "${props[@]}" "${cmd[@]}")
  else
    echo "systemd-run --user is not usable here: running WITHOUT the memory/CPU-time limit (use --heap and --cores instead)" >&2
  fi
fi

exec env "${env_args[@]}" "${cmd[@]}"
