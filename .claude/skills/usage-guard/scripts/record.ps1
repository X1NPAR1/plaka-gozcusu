# Logs one usage reading and decides whether to stop.
# Usage: record.ps1 -Percent 74 -ResetTime "5:40 AM" [-Relative] [-Threshold 98]
param(
  [Parameter(Mandatory)][int]$Percent,
  [Parameter(Mandatory)][string]$ResetTime,
  [switch]$Relative,
  [int]$Threshold = 98
)
$dir = if ($env:USAGE_GUARD_DIR) { $env:USAGE_GUARD_DIR } else { Split-Path $PSScriptRoot -Parent }
New-Item -ItemType Directory -Force $dir | Out-Null
$now = Get-Date
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
"$($now.ToString('s')) used=$Percent% reset=$($reset.ToString('s'))" | Add-Content (Join-Path $dir 'usage-log.txt')
if ($Percent -ge $Threshold) {
  # Resume one minute after the reset so the new window is certainly open.
  $resume = $reset.AddMinutes(1)
  $seconds = [math]::Max(60, [int]($resume - $now).TotalSeconds)
  "STOP $($resume.ToString('s')) $seconds"
} else { "OK $Percent%" }
