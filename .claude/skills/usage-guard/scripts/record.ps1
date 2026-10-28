# Logs one usage reading and decides whether to stop.
# Usage: record.ps1 -Percent 74 -ResetTime "5:40 AM" [-Relative] [-Threshold 98] [-IntervalMinutes 1]
param(
  [Parameter(Mandatory)][int]$Percent,
  [Parameter(Mandatory)][string]$ResetTime,
  [switch]$Relative,
  [int]$Threshold = 98,
  [int]$IntervalMinutes = 1
)
$dir = if ($env:USAGE_GUARD_DIR) { $env:USAGE_GUARD_DIR } else { Split-Path $PSScriptRoot -Parent }
New-Item -ItemType Directory -Force $dir | Out-Null
$log = Join-Path $dir 'usage-log.txt'
$now = Get-Date

# Minutes since the previous reading, so a missed 5-minute check is visible.
$since = $null
if (Test-Path $log) {
  $last = Get-Content $log -Tail 1
  if ($last -match '^(\S+) ') {
    $prev = [datetime]::Parse($Matches[1], [Globalization.CultureInfo]::InvariantCulture)
    $since = [int]($now - $prev).TotalMinutes
  }
}

if ($Relative) {
  # e.g. "2 hr 10 min" or "45 min"
  $h = if ($ResetTime -match '(\d+)\s*h') { [int]$Matches[1] } else { 0 }
  $m = if ($ResetTime -match '(\d+)\s*m') { [int]$Matches[1] } else { 0 }
  $reset = $now.AddHours($h).AddMinutes($m)
} else {
  # The usage page shows an English clock time such as "5:40 AM".
  $reset = [datetime]::Parse($ResetTime, [Globalization.CultureInfo]::GetCultureInfo('en-US'))
  if ($reset -lt $now) { $reset = $reset.AddDays(1) }
}
"$($now.ToString('s')) used=$Percent% reset=$($reset.ToString('s'))" | Add-Content $log
if ($Percent -ge $Threshold) {
  # Resume one minute after the reset so the new window is certainly open.
  $resume = $reset.AddMinutes(1)
  $seconds = [math]::Max(60, [int]($resume - $now).TotalSeconds)
  "STOP $($resume.ToString('s')) $seconds"
} else {
  $note = if ($since -eq $null) { 'first check' } elseif ($since -gt $IntervalMinutes) { "OVERDUE: $since min since last check (limit $IntervalMinutes)" } else { "$since min since last check" }
  "OK $Percent% ($note)"
}
