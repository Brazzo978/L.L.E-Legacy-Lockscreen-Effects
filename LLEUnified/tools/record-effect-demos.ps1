param(
    [string] $Serial = "",
    [string] $EffectIds = "",
    [string] $PackageName = "",
    [string] $OutputRoot = "",
    [ValidateRange(4, 180)]
    [int] $DurationSeconds = 5,
    [ValidateRange(1000000, 100000000)]
    [int] $BitRate = 20000000
)

$ErrorActionPreference = "Stop"
$adb = (Get-Command adb -ErrorAction Stop).Source

$effects = [ordered]@{
    "10" = "s3-water-ripple"
    "12" = "n2-ink-in-water"
    "0" = "s4-lens-flare"
    "3" = "n3-watercolor"
    "14" = "s5-brilliant-ring"
    "2" = "s5-popping-colours"
    "13" = "s5-stone-skipping"
    "11" = "tabs-blind"
    "15" = "tabs-brilliant-cut"
    "7" = "n4-abstract-tiles"
    "8" = "n4-geometric-mosaic"
    "4" = "n5-colored-droplet"
    "9" = "n5-colored-droplet-gyro"
    "5" = "n5-sparkling-bubbles"
}

function Invoke-Adb([string[]] $Arguments) {
    $result = & $adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($Arguments -join ' ')"
    }
    return $result
}

function Resolve-Serial {
    if (-not [string]::IsNullOrWhiteSpace($Serial)) {
        return $Serial
    }
    $devices = @(
        & $adb devices |
            Select-Object -Skip 1 |
            Where-Object { $_ -match '^([^\s]+)\s+device$' } |
            ForEach-Object { $Matches[1] })
    if ($devices.Count -ne 1) {
        throw "Pass -Serial when zero or multiple adb devices are connected."
    }
    return $devices[0]
}

function Test-PackageInstalled([string] $Candidate) {
    $path = (& $adb -s $script:DeviceSerial shell pm path $Candidate 2>$null) -join "`n"
    return $LASTEXITCODE -eq 0 -and $path.Contains("package:")
}

function Get-CurrentEffect([string] $TargetPackage) {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $xml = (& $adb -s $script:DeviceSerial shell run-as $TargetPackage cat `
            shared_prefs/overlay_prefs.xml 2>&1) -join "`n"
        $runAsExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($runAsExitCode -eq 0 -and
            $xml -match '<int name="unlock_effect" value="(\d+)"') {
        return [int] $Matches[1]
    }

    # Release builds reject run-as. Query the same dynamic debug endpoint without
    # an explicit effect: the service profiles/logs the currently selected one.
    Invoke-Adb @("-s", $script:DeviceSerial, "logcat", "-c") | Out-Null
    Invoke-Adb @(
        "-s", $script:DeviceSerial,
        "shell", "am", "broadcast",
        "-a", "com.codex.lle.DEBUG_UNLOCK_EFFECT_PROFILE") | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(12)
    do {
        Start-Sleep -Milliseconds 350
        $log = (& $adb -s $script:DeviceSerial logcat -d -v brief) -join "`n"
        if ($log -match 'debug unlock effect profile complete effect=(\d+)\b') {
            return [int] $Matches[1]
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    return $null
}

function Select-Effect([int] $EffectId) {
    Invoke-Adb @("-s", $script:DeviceSerial, "logcat", "-c") | Out-Null
    Invoke-Adb @(
        "-s", $script:DeviceSerial,
        "shell", "am", "broadcast",
        "-a", "com.codex.lle.DEBUG_UNLOCK_EFFECT_PROFILE",
        "--ei", "effect", "$EffectId") | Out-Null

    $deadline = [DateTime]::UtcNow.AddSeconds(12)
    do {
        Start-Sleep -Milliseconds 350
        $log = (& $adb -s $script:DeviceSerial logcat -d -v brief) -join "`n"
        if ($log -match "debug unlock effect profile complete effect=$EffectId\b") {
            return
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Effect $EffectId did not initialize. Is the L.L.E accessibility service enabled?"
}

function Get-DisplaySize {
    $sizeText = (Invoke-Adb @("-s", $script:DeviceSerial, "shell", "wm", "size")) -join "`n"
    $matches = [regex]::Matches($sizeText, '(?:Override|Physical) size:\s*(\d+)x(\d+)')
    if ($matches.Count -eq 0) {
        throw "Could not read the active display size."
    }
    $match = $matches[$matches.Count - 1]
    return [pscustomobject]@{
        Width = [int] $match.Groups[1].Value
        Height = [int] $match.Groups[2].Value
    }
}

function Invoke-DemoGesture {
    Invoke-Adb @("-s", $script:DeviceSerial, "logcat", "-c") | Out-Null
    Invoke-Adb @(
        "-s", $script:DeviceSerial,
        "shell", "am", "broadcast",
        "-a", "com.codex.lle.DEBUG_UNLOCK_EFFECT_DEMO_GESTURE") | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(6)
    do {
        Start-Sleep -Milliseconds 200
        $log = (& $adb -s $script:DeviceSerial logcat -d -v brief) -join "`n"
        if ($log.Contains("debug demo gesture end")) {
            Start-Sleep -Milliseconds 650
            return
        }
        if ($log.Contains("debug demo gesture ignored")) {
            throw "The renderer was not visible when the demo gesture started."
        }
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "The renderer did not finish the programmatic demo gesture."
}

$script:DeviceSerial = Resolve-Serial
if ([string]::IsNullOrWhiteSpace($PackageName)) {
    if (Test-PackageInstalled "com.codex.lle64") {
        $PackageName = "com.codex.lle64"
    } elseif (Test-PackageInstalled "com.codex.lle") {
        $PackageName = "com.codex.lle"
    } else {
        throw "Neither L.L.E package is installed on $script:DeviceSerial."
    }
} elseif (-not (Test-PackageInstalled $PackageName)) {
    throw "$PackageName is not installed on $script:DeviceSerial."
}

$enabledServices = (& $adb -s $script:DeviceSerial shell settings get secure `
    enabled_accessibility_services) -join "`n"
if (-not $enabledServices.Contains($PackageName)) {
    throw "Enable the $PackageName accessibility service before recording."
}

$requested = if ([string]::IsNullOrWhiteSpace($EffectIds)) {
    @($effects.Keys | ForEach-Object { [int] $_ })
} else {
    @($EffectIds.Split(',') | ForEach-Object { [int] $_.Trim() })
}
foreach ($effectId in $requested) {
    if (-not $effects.Contains("$effectId")) {
        throw "Unknown effect id: $effectId"
    }
}

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path (Split-Path $PSScriptRoot -Parent) `
        "captures\effect-demos"
}
$session = Join-Path $OutputRoot (Get-Date -Format "yyyyMMdd-HHmmss")
New-Item -ItemType Directory -Path $session -Force | Out-Null
$originalEffect = Get-CurrentEffect $PackageName
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

try {
    foreach ($effectId in $requested) {
        $label = $effects["$effectId"]
        Write-Host "Recording [$effectId] $label..."
        Select-Effect $effectId

        Invoke-Adb @("-s", $script:DeviceSerial, "shell", "input", "keyevent", "223") |
            Out-Null
        Start-Sleep -Milliseconds 850
        Invoke-Adb @("-s", $script:DeviceSerial, "shell", "input", "keyevent", "224") |
            Out-Null
        Start-Sleep -Milliseconds 1250

        $remote = "/sdcard/Download/lle-demo-$stamp-$effectId.mp4"
        $local = Join-Path $session ("{0:D2}-{1}.mp4" -f $effectId, $label)
        $recordArguments = @(
            "-s", $script:DeviceSerial,
            "shell", "screenrecord",
            "--bit-rate", "$BitRate",
            "--time-limit", "$DurationSeconds",
            $remote)
        $recording = Start-Process -FilePath $adb -ArgumentList $recordArguments `
            -NoNewWindow -PassThru
        Start-Sleep -Milliseconds 650
        Invoke-DemoGesture
        $recording.WaitForExit()
        $recording.Refresh()
        if ($null -ne $recording.ExitCode -and $recording.ExitCode -ne 0) {
            throw "screenrecord failed for effect $effectId."
        }

        Invoke-Adb @("-s", $script:DeviceSerial, "pull", $remote, $local) | Out-Null
        if (-not (Test-Path -LiteralPath $local) -or
                (Get-Item -LiteralPath $local).Length -lt 1024) {
            throw "The recording for effect $effectId was not pulled."
        }
        Invoke-Adb @("-s", $script:DeviceSerial, "shell", "rm", "-f", $remote) | Out-Null
        Write-Host "Saved $local"
    }
} finally {
    if ($null -ne $originalEffect -and $effects.Contains("$originalEffect")) {
        try {
            Select-Effect ([int] $originalEffect)
            Write-Host "Restored effect $originalEffect."
        } catch {
            Write-Warning "Could not restore effect $originalEffect`: $($_.Exception.Message)"
        }
    }
}

Write-Host "Demo recordings complete: $session"
