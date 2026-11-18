# Samples the memory and CPU of a running Elderpath Crusade on Windows (the game, ElderpathCrusade.exe, and its
# match server, a java.exe started from the game's own runtime folder) every 2 seconds into a CSV, then prints
# the peaks.  Start the game first, then in PowerShell, in this folder:
#   powershell -ExecutionPolicy Bypass -File .\sample-windows.ps1 -Seconds 300 -Out spec-samples.csv
# NOT TESTED on Windows when it was written (it was prepared on Linux) — if it errors, tell the developer the message.
param([int]$Seconds = 300, [string]$Out = "spec-samples.csv")

$cores = [Environment]::ProcessorCount
"time_s,game_ws_mb,game_cpu_pct_of_machine,server_ws_mb,server_cpu_pct_of_machine,total_ws_mb" | Out-File -Encoding ascii $Out

function Get-Totals($processes) {
    $ws = 0; $cpu = 0.0
    foreach ($p in $processes) { $ws += $p.WorkingSet64; $cpu += $p.TotalProcessorTime.TotalSeconds }
    return @{ Ws = $ws / 1MB; Cpu = $cpu }
}

$peakGame = 0; $peakServer = 0; $peakTotal = 0; $peakCpu = 0
$prevGameCpu = $null; $prevServerCpu = $null
for ($t = 0; $t -le $Seconds; $t += 2) {
    $game = @(Get-Process -Name "ElderpathCrusade" -ErrorAction SilentlyContinue)
    # the match server: a java.exe whose command line mentions ServerLauncher
    $serverIds = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -match "ServerLauncher" } | ForEach-Object { $_.ProcessId })
    $server = @(if ($serverIds.Count -gt 0) { Get-Process -Id $serverIds -ErrorAction SilentlyContinue })
    $g = Get-Totals $game; $s = Get-Totals $server
    $gameCpuPct = 0.0; $serverCpuPct = 0.0
    if ($null -ne $prevGameCpu) { $gameCpuPct = [math]::Max(0, ($g.Cpu - $prevGameCpu) / 2 / $cores * 100) }
    if ($null -ne $prevServerCpu) { $serverCpuPct = [math]::Max(0, ($s.Cpu - $prevServerCpu) / 2 / $cores * 100) }
    $prevGameCpu = $g.Cpu; $prevServerCpu = $s.Cpu
    $total = $g.Ws + $s.Ws
    ("{0},{1:N0},{2:N1},{3:N0},{4:N1},{5:N0}" -f $t, $g.Ws, $gameCpuPct, $s.Ws, $serverCpuPct, $total) | Out-File -Append -Encoding ascii $Out
    if ($g.Ws -gt $peakGame) { $peakGame = $g.Ws }
    if ($s.Ws -gt $peakServer) { $peakServer = $s.Ws }
    if ($total -gt $peakTotal) { $peakTotal = $total }
    if ($gameCpuPct -gt $peakCpu) { $peakCpu = $gameCpuPct }
    Start-Sleep -Seconds 2
}
"Samples in $Out ($cores logical processors). Peaks: game {0:N0} MB, match server {1:N0} MB, together {2:N0} MB, game CPU {3:N1}% of the whole machine." -f $peakGame, $peakServer, $peakTotal, $peakCpu
