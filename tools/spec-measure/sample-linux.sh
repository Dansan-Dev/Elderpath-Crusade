#!/usr/bin/env bash
# Samples the memory and CPU of a running Elderpath Crusade (the game process and its match server) every
# 2 seconds into a CSV and prints the peaks at the end.  Usage (start the game first, then):
#   tools/spec-measure/sample-linux.sh [seconds=300] [out.csv=spec-samples.csv]
# Patterns can be changed with GAME_PATTERN / SERVER_PATTERN (regexes matched against the full command line).
set -u
SECONDS_TO_RUN="${1:-300}"
OUT="${2:-spec-samples.csv}"
GAME_PATTERN="${GAME_PATTERN:-ElderpathCrusade}"
SERVER_PATTERN="${SERVER_PATTERN:-ServerLauncher}"
CORES="$(nproc 2>/dev/null || echo 1)"
echo "time_s,game_rss_mb,game_cpu_pct_of_one_core,server_rss_mb,server_cpu_pct_of_one_core,total_rss_mb" > "$OUT"
peak_game=0; peak_server=0; peak_total=0; peak_game_cpu=0
for ((t = 0; t <= SECONDS_TO_RUN; t += 2)); do
  read -r game_rss game_cpu server_rss server_cpu < <(
    ps -eo rss=,pcpu=,args= | awk -v g="$GAME_PATTERN" -v s="$SERVER_PATTERN" '
      $0 ~ s            { sr += $1; sc += $2; next }
      $0 ~ g && $0 !~ /awk|sample-linux/ { gr += $1; gc += $2 }
      END { printf "%d %.1f %d %.1f\n", gr / 1024, gc, sr / 1024, sc }')
  total=$((game_rss + server_rss))
  echo "$t,$game_rss,$game_cpu,$server_rss,$server_cpu,$total" >> "$OUT"
  (( game_rss > peak_game )) && peak_game=$game_rss
  (( server_rss > peak_server )) && peak_server=$server_rss
  (( total > peak_total )) && peak_total=$total
  peak_game_cpu=$(awk -v a="$peak_game_cpu" -v b="$game_cpu" 'BEGIN { print (b > a) ? b : a }')
  sleep 2
done
echo "Samples in $OUT ($CORES CPU cores). Peaks: game ${peak_game} MB, match server ${peak_server} MB, together ${peak_total} MB, game CPU ${peak_game_cpu}% of one core."
