<#
.SYNOPSIS
  Drives a running debug build on the headset through the in-app debug bridge, so the app can be
  exercised and screenshotted while nobody wears the headset. Commands go to whichever activity is
  up: the panel window (browse and in-window playback) or the immersive cinema (its controls panel).
  Coordinates are dp inside that panel.

.EXAMPLE
  tools/quest-dev.ps1 capture                       # window.png or controls.png, plus eyes.png (compositor)
  tools/quest-dev.ps1 tap 120 80
  tools/quest-dev.ps1 swipe 700 600 700 200
  tools/quest-dev.ps1 back
  tools/quest-dev.ps1 recenter                      # immersive only
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory, Position = 0)][ValidateSet("capture", "tap", "swipe", "back", "recenter")][string]$Cmd,
    [Parameter(ValueFromRemainingArguments)][float[]]$Coords = @()
)
$OutDir = "$PSScriptRoot\..\build\quest-run"
$pkg = "com.anthonyla.hotaru.quest"
$panels = @("window", "controls")
# A capture only writes the panel of the activity that is up; clear both so a stale one is never pulled.
if ($Cmd -eq "capture") { adb shell run-as $pkg rm -f ($panels | ForEach-Object { "files/dev/$_.png" }) }

$bc = @("shell", "am", "broadcast", "-a", "com.anthonyla.hotaru.DEV", "-p", $pkg, "--es", "cmd", $Cmd)
$names = @("x", "y", "x2", "y2")
for ($i = 0; $i -lt $Coords.Count; $i++) { $bc += @("--ef", $names[$i], "$($Coords[$i])") }
adb @bc | Out-Null

if ($Cmd -eq "capture") {
    New-Item -ItemType Directory -Force $OutDir | Out-Null
    foreach ($p in $panels) {
        $file = Join-Path $OutDir "$p.png"
        Remove-Item $file -ErrorAction SilentlyContinue
        adb shell run-as $pkg ls "files/dev/$p.png" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { cmd /c "adb exec-out run-as $pkg cat files/dev/$p.png > `"$file`"" }
    }
    cmd /c "adb exec-out screencap -p > `"$(Join-Path $OutDir 'eyes.png')`""
    Write-Host "captured to $OutDir"
}
