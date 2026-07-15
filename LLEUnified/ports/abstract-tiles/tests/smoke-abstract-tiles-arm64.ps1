<#
.SYNOPSIS
Repeatable, non-destructive ADB smoke for LLE Abstract Tiles ARM64 (effect 7).

.DESCRIPTION
Plan:
  1. Confirm that one target device and com.codex.lle are reachable.
  2. Add a unique logcat marker, wake through EffectBackgroundWakeActivity and select effect 7.
  3. Inject one panel-local swipe shorter than 450 ms.
  4. Start three device-side PNG captures near 80/240/410 ms of the gesture.
  5. Pull the frames, remove only this run's /data/local/tmp files, then collect filtered
     crash/GLES logcat and dumpsys meminfo.
  6. Emit summary.json and fail the smoke if the process disappears or a crash/GL failure
     is found after the marker.

The script does not install, force-stop, clear logcat, change lock credentials, dismiss the
keyguard, or write Android global/system settings. Effect 7 remains selected unless
-RestoreEffect is supplied because the non-debuggable app has no read-only public preference
query. Run this only while the device is not being controlled by another test.

.EXAMPLE
  .\smoke-abstract-tiles-arm64.ps1 -Serial R3CW123456A

.EXAMPLE
  .\smoke-abstract-tiles-arm64.ps1 -Serial R3CW123456A `
      -StartX 720 -StartY 2100 -EndX 720 -EndY 1550 -RestoreEffect 0
#>
[CmdletBinding()]
param(
    [string] $Serial = "",
    [string] $Adb = "adb",
    [string] $OutputDirectory = "",
    [ValidateRange(1, 449)]
    [int] $GestureDurationMs = 420,
    [int] $StartX = -1,
    [int] $StartY = -1,
    [int] $EndX = -1,
    [int] $EndY = -1,
    [ValidateRange(-1, 64)]
    [int] $RestoreEffect = -1,
    [ValidateRange(250, 10000)]
    [int] $WakeSettleMs = 1500
)

$ErrorActionPreference = "Stop"
$packageName = "com.codex.lle"
$effectId = 7
$runId = "abstract_tiles_" + (Get-Date -Format "yyyyMMdd_HHmmss_fff")

$adbCommand = Get-Command $Adb -ErrorAction Stop
$adbExe = $adbCommand.Source
if ([string]::IsNullOrWhiteSpace($adbExe)) {
    $adbExe = $adbCommand.Path
}
if ([string]::IsNullOrWhiteSpace($adbExe)) {
    throw "Could not resolve adb executable: $Adb"
}

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot ("results\" + $runId)
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Get-AdbArguments {
    param([string[]] $Arguments)
    $all = @()
    if (-not [string]::IsNullOrWhiteSpace($Serial)) {
        $all += @("-s", $Serial)
    }
    $all += $Arguments
    return ,$all
}

function Invoke-AdbText {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,
        [switch] $AllowFailure
    )
    $all = Get-AdbArguments $Arguments
    $output = @(& $adbExe @all 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "adb $($Arguments -join ' ') failed ($exitCode): $($output -join ' ')"
    }
    return ,$output
}

function Start-AdbHidden {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)
    $all = Get-AdbArguments $Arguments
    return Start-Process -FilePath $adbExe -ArgumentList $all `
            -WindowStyle Hidden -PassThru
}

function Wait-AdbProcess {
    param(
        [Parameter(Mandatory = $true)]
        [Diagnostics.Process] $Process,
        [string] $Description
    )
    if (-not $Process.WaitForExit(10000)) {
        throw "Timed out waiting for adb process: $Description"
    }
    if ($Process.ExitCode -ne 0) {
        throw "adb process failed ($($Process.ExitCode)): $Description"
    }
}

$state = (Invoke-AdbText -Arguments @("get-state")) -join "`n"
if ($state.Trim() -ne "device") {
    throw "ADB target is not ready: $state"
}
if ([string]::IsNullOrWhiteSpace($Serial)) {
    $connected = @(Invoke-AdbText -Arguments @("devices") |
            Where-Object { $_ -match "\sdevice$" })
    if ($connected.Count -ne 1) {
        throw "Specify -Serial when zero or multiple ADB devices are connected"
    }
}

$packagePath = (Invoke-AdbText -Arguments @("shell", "pm", "path", $packageName)) -join "`n"
if ($packagePath -notmatch "package:") {
    throw "$packageName is not installed on the target"
}

$wmSize = (Invoke-AdbText -Arguments @("shell", "wm", "size")) -join "`n"
$sizeMatches = [regex]::Matches($wmSize, "(?m)(\d+)x(\d+)")
if ($sizeMatches.Count -eq 0) {
    throw "Could not parse active display size: $wmSize"
}
$activeSize = $sizeMatches[$sizeMatches.Count - 1]
$displayWidth = [int] $activeSize.Groups[1].Value
$displayHeight = [int] $activeSize.Groups[2].Value

$customCoordinates = @($StartX, $StartY, $EndX, $EndY) |
        Where-Object { $_ -ge 0 }
if ($customCoordinates.Count -ne 0 -and $customCoordinates.Count -ne 4) {
    throw "Supply all four gesture coordinates or leave all four at -1"
}
if ($customCoordinates.Count -eq 0) {
    $StartX = [int] [Math]::Round($displayWidth * 0.50)
    $StartY = [int] [Math]::Round($displayHeight * 0.65)
    $EndX = $StartX
    $EndY = [int] [Math]::Round($displayHeight * 0.48)
}
foreach ($coordinate in @($StartX, $EndX)) {
    if ($coordinate -lt 0 -or $coordinate -ge $displayWidth) {
        throw "X coordinate $coordinate is outside 0..$($displayWidth - 1)"
    }
}
foreach ($coordinate in @($StartY, $EndY)) {
    if ($coordinate -lt 0 -or $coordinate -ge $displayHeight) {
        throw "Y coordinate $coordinate is outside 0..$($displayHeight - 1)"
    }
}

$remoteFrames = @()
$localFrames = @()
$captureProcesses = @()
$frameOffsetsMs = @(80, 240, 410)
$startedAt = Get-Date
$failure = $null

try {
    [void](Invoke-AdbText -Arguments @(
            "shell", "log", "-p", "i", "-t", "LLESmoke", "BEGIN_$runId"))
    [void](Invoke-AdbText -Arguments @("shell", "input", "keyevent", "KEYCODE_WAKEUP"))
    [void](Invoke-AdbText -Arguments @(
            "shell", "am", "start", "-W",
            "-n", "$packageName/.EffectBackgroundWakeActivity",
            "--ei", "effect", "$effectId"))
    Start-Sleep -Milliseconds $WakeSettleMs

    $pidBefore = ((Invoke-AdbText -Arguments @(
            "shell", "pidof", $packageName) -AllowFailure) -join " ").Trim()
    if ([string]::IsNullOrWhiteSpace($pidBefore)) {
        throw "$packageName process is not running after wake/effect selection"
    }

    $swipeProcess = Start-AdbHidden -Arguments @(
            "shell", "input", "swipe",
            "$StartX", "$StartY", "$EndX", "$EndY", "$GestureDurationMs")

    $previousOffsetMs = 0
    for ($index = 0; $index -lt $frameOffsetsMs.Count; $index++) {
        $offsetMs = $frameOffsetsMs[$index]
        Start-Sleep -Milliseconds ($offsetMs - $previousOffsetMs)
        $previousOffsetMs = $offsetMs
        $remote = "/data/local/tmp/$($runId)_frame$($index + 1).png"
        $local = Join-Path $OutputDirectory ("frame{0}_{1}ms.png" -f
                ($index + 1), $offsetMs)
        $remoteFrames += $remote
        $localFrames += $local
        $captureProcesses += Start-AdbHidden -Arguments @(
                "shell", "screencap", "-p", $remote)
    }

    Wait-AdbProcess -Process $swipeProcess -Description "420 ms Abstract Tiles swipe"
    for ($index = 0; $index -lt $captureProcesses.Count; $index++) {
        Wait-AdbProcess -Process $captureProcesses[$index] `
                -Description "frame $($index + 1) screencap"
        [void](Invoke-AdbText -Arguments @(
                "pull", $remoteFrames[$index], $localFrames[$index]))
        if (-not (Test-Path -LiteralPath $localFrames[$index]) -or
                (Get-Item -LiteralPath $localFrames[$index]).Length -le 8) {
            throw "Frame $($index + 1) was not pulled as a non-empty PNG"
        }
    }

    Start-Sleep -Milliseconds 700
    $pidAfter = ((Invoke-AdbText -Arguments @(
            "shell", "pidof", $packageName) -AllowFailure) -join " ").Trim()

    $meminfo = Invoke-AdbText -Arguments @(
            "shell", "dumpsys", "meminfo", $packageName)
    $meminfoPath = Join-Path $OutputDirectory "meminfo.txt"
    $meminfo | Set-Content -LiteralPath $meminfoPath -Encoding UTF8

    [void](Invoke-AdbText -Arguments @(
            "shell", "log", "-p", "i", "-t", "LLESmoke", "END_$runId"))
    $logcat = Invoke-AdbText -Arguments @(
            "logcat", "-d", "-v", "threadtime", "-s",
            "LLESmoke:I", "ChargingA11y:V", "LLE64AbstractTiles:V",
            "AndroidRuntime:E", "libc:F", "DEBUG:F", "OpenGLRenderer:E",
            "GLConsumer:E", "EGL:E", "ActivityManager:E", "*:S")
    $markerIndex = -1
    for ($index = 0; $index -lt $logcat.Count; $index++) {
        if ($logcat[$index] -match [regex]::Escape("BEGIN_$runId")) {
            $markerIndex = $index
        }
    }
    $runLog = if ($markerIndex -ge 0) {
        @($logcat[$markerIndex..($logcat.Count - 1)])
    } else {
        @($logcat)
    }
    $logPath = Join-Path $OutputDirectory "logcat-filtered.txt"
    $runLog | Set-Content -LiteralPath $logPath -Encoding UTF8

    $failurePattern = "(?i)(FATAL EXCEPTION|Fatal signal|ANR in com\.codex\.lle|" +
            "LLE64AbstractTiles.*(failed|error|unavailable)|" +
            "ChargingA11y.*Abstract Tiles ARM64 failed|" +
            "\bEGL.*(?:error|failed)|\bGL(?:_| )?ERROR\b|" +
            "OpenGLRenderer.*(?:error|failed))"
    $findings = @($runLog | Where-Object { $_ -match $failurePattern })
    $findingsPath = Join-Path $OutputDirectory "crash-gl-findings.txt"
    if ($findings.Count -eq 0) {
        @("No crash/GL failure matched after BEGIN_$runId") |
                Set-Content -LiteralPath $findingsPath -Encoding UTF8
    } else {
        $findings | Set-Content -LiteralPath $findingsPath -Encoding UTF8
    }

    $frameResults = @()
    for ($index = 0; $index -lt $localFrames.Count; $index++) {
        $item = Get-Item -LiteralPath $localFrames[$index]
        $frameResults += [ordered]@{
            offset_ms = $frameOffsetsMs[$index]
            path = $item.FullName
            bytes = $item.Length
            sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash
        }
    }
    $summary = [ordered]@{
        run_id = $runId
        started_at = $startedAt.ToString("o")
        finished_at = (Get-Date).ToString("o")
        serial = $Serial
        package = $packageName
        effect = $effectId
        display = "${displayWidth}x${displayHeight}"
        gesture = [ordered]@{
            start = @($StartX, $StartY)
            end = @($EndX, $EndY)
            duration_ms = $GestureDurationMs
        }
        pid_before = $pidBefore
        pid_after = $pidAfter
        process_survived = -not [string]::IsNullOrWhiteSpace($pidAfter)
        crash_gl_finding_count = $findings.Count
        frames = $frameResults
        meminfo = $meminfoPath
        logcat = $logPath
        findings = $findingsPath
    }
    $summaryPath = Join-Path $OutputDirectory "summary.json"
    $summary | ConvertTo-Json -Depth 6 |
            Set-Content -LiteralPath $summaryPath -Encoding UTF8

    if ([string]::IsNullOrWhiteSpace($pidAfter)) {
        $failure = "$packageName process disappeared during smoke"
    } elseif ($findings.Count -gt 0) {
        $failure = "Crash/GL findings detected: $($findings.Count)"
    }
} finally {
    foreach ($remote in $remoteFrames) {
        [void](Invoke-AdbText -Arguments @(
                "shell", "rm", "-f", $remote) -AllowFailure)
    }
    if ($RestoreEffect -ge 0) {
        [void](Invoke-AdbText -Arguments @(
                "shell", "am", "start",
                "-n", "$packageName/.EffectBackgroundWakeActivity",
                "--ei", "effect", "$RestoreEffect") -AllowFailure)
    }
}

if ($failure) {
    throw "$failure. Results: $OutputDirectory"
}
Write-Host "Abstract Tiles ARM64 smoke PASS"
Write-Host "Results: $OutputDirectory"
