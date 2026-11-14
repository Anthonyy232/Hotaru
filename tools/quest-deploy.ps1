<#
.SYNOPSIS
  Waits for a Quest headset on adb, installs the debug APK, launches its panel window (optionally signed in to a server),
  then records logcat and periodic screen captures so a session can be verified without wearing the headset.

.EXAMPLE
  tools/quest-deploy.ps1 -Server http://<server-ip>:8096 -User <user> -Password <password> -CaptureSeconds 60
#>
param(
    [string]$Server = "",
    [string]$User = "",
    [string]$Password = "",
    [int]$WaitSeconds = 0,          # 0 = wait forever
    [int]$CaptureSeconds = 45
)
$Apk = "$PSScriptRoot\..\app\quest\build\outputs\apk\debug\quest-debug.apk"
$OutDir = "$PSScriptRoot\..\build\quest-run"
$pkg = "com.anthonyla.hotaru.quest"
$activity = "$pkg/.PanelActivity"
New-Item -ItemType Directory -Force $OutDir | Out-Null

function Get-Device {
    $lines = adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne "" }
    foreach ($l in $lines) {
        $parts = $l -split "\s+"
        if ($parts[1] -eq "device") { return $parts[0] }
        if ($parts[1] -eq "unauthorized") { Write-Host "[$(Get-Date -f HH:mm:ss)] headset $($parts[0]) is UNAUTHORIZED: accept 'Allow USB debugging' in the headset" }
    }
    return $null
}

$start = Get-Date
while (-not ($serial = Get-Device)) {
    if ($WaitSeconds -gt 0 -and ((Get-Date) - $start).TotalSeconds -gt $WaitSeconds) { Write-Host "no device after $WaitSeconds s"; exit 2 }
    Start-Sleep -Seconds 3
}
# adb reads its target from ANDROID_SERIAL: every call below, the logcat child and later quest-dev.ps1 runs in this shell hit this headset.
$env:ANDROID_SERIAL = $serial
Write-Host "device: $serial  model=$(adb shell getprop ro.product.model)  os=$(adb shell getprop ro.build.version.release) sdk=$(adb shell getprop ro.build.version.sdk) abi=$(adb shell getprop ro.product.cpu.abi) build=$(adb shell getprop ro.build.display.id)"

Write-Host "installing $Apk"
adb install -r -g $Apk
if ($LASTEXITCODE -ne 0) { Write-Host "install failed"; exit 3 }

# A headset left on the desk is asleep behind the lock screen; wake it so the launch is not stuck behind an OS dialog.
adb shell am broadcast -a com.oculus.vrpowermanager.prox_close | Out-Null
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
$extras = @()
# The device's shell splits the command again, so values are quoted for it: a user name may have spaces.
if ($Server -and $User) { $extras = @("--es", "server", "'$Server'", "--es", "user", "'$User'") }
if ($Password) { $extras += @("--es", "password", "'$Password'") }
adb shell am start -S -n $activity @extras
if ($LASTEXITCODE -ne 0) { Write-Host "launch failed"; exit 4 }

$logFile = Join-Path $OutDir "logcat.txt"
$logProc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "time") -RedirectStandardOutput $logFile -NoNewWindow -PassThru
$t = 0
while ($t -lt $CaptureSeconds) {
    Start-Sleep -Seconds 5; $t += 5
    $shot = Join-Path $OutDir ("shot-{0:d3}.png" -f $t)
    # cmd's redirect keeps the PNG bytes intact; Windows PowerShell's would re-encode them as text.
    cmd /c "adb exec-out screencap -p > `"$shot`""
    $top = (adb shell dumpsys activity activities | Select-String "ResumedActivity" | Select-Object -First 1)
    Write-Host "[$t s] top: $top"
}
Stop-Process -Id $logProc.Id -Force -ErrorAction SilentlyContinue
Write-Host "done; logs in $OutDir"
