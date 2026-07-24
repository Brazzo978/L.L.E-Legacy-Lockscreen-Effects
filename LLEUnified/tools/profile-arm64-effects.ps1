param(
    [Parameter(Mandatory = $true)]
    [string] $EffectIds,
    [string] $Serial = "RFCW30S277B",
    [int] $TapCount = 4
)

$ErrorActionPreference = "Stop"
$adb = (Get-Command adb -ErrorAction Stop).Source
$packageName = "com.codex.lle64"

$effectLabels = @{
    0 = "S4 Lens Flare"
    2 = "S5 Popping Colours"
    3 = "N3 Watercolor"
    4 = "N5 Colored Droplet"
    5 = "N5 Sparkling Bubbles"
    7 = "N4 Abstract Tiles"
    8 = "N4 Geometric Mosaic"
    9 = "N5 Colored Droplet + Gyro"
    10 = "S3 Water Ripple"
    11 = "Tab S Blind"
    12 = "N2 Ink in Water"
    13 = "S5 Stone Skipping"
    14 = "S5 Brilliant Ring"
    15 = "Tab S Brilliant Cut"
    16 = "Seasonal Spring"
    17 = "Seasonal Summer"
    18 = "Seasonal Autumn"
    19 = "Seasonal Winter"
    20 = "Seasonal"
}

function Get-ThreadTicks([string] $ProcessId) {
    $raw = & $adb -s $Serial shell "cat /proc/$ProcessId/task/*/stat"
    $ticks = @{}
    foreach ($line in $raw) {
        if ($line -match '^(\d+) \((.*)\) (.*)$') {
            $fields = $Matches[3] -split ' '
            $ticks[$Matches[1]] = [pscustomobject]@{
                Name = $Matches[2]
                Ticks = [long] $fields[11] + [long] $fields[12]
            }
        }
    }
    return ,$ticks
}

function Get-RegexInt([string] $Text, [string] $Pattern) {
    $match = [regex]::Match(
        $Text,
        $Pattern,
        [Text.RegularExpressions.RegexOptions]::Multiline)
    if ($match.Success) {
        return [int] $match.Groups[1].Value
    }
    return -1
}

function Get-MaxLogValue([string] $Text, [string] $Pattern) {
    $values = @(
        [regex]::Matches($Text, $Pattern) |
            ForEach-Object { [int] $_.Groups[1].Value })
    if ($values.Count -eq 0) {
        return -1
    }
    return ($values | Measure-Object -Maximum).Maximum
}

function Invoke-EffectProfile([int] $EffectId) {
    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell am broadcast `
        -a com.codex.lle.DEBUG_UNLOCK_EFFECT_PROFILE `
        --ei effect $EffectId | Out-Null
    Start-Sleep -Milliseconds 1700

    $profileLog = (& $adb -s $Serial logcat -d -v brief) -join "`n"
    $profilePattern = 'debug unlock effect profile complete effect=' + $EffectId `
        + ' name=(.*?) status=(\S+) totalMs=(\d+) preloadMs=(\d+)' `
        + ' attachMs=(\d+) warmMs=(\d+) pssKb=(\d+) deltaPssKb=(-?\d+)'
    $profile = [regex]::Match(
        $profileLog,
        $profilePattern,
        [Text.RegularExpressions.RegexOptions]::Singleline)

    # Wake and validate one harmless zero-distance tap before measuring.
    & $adb -s $Serial shell input keyevent 224 | Out-Null
    Start-Sleep -Milliseconds 1100
    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell input tap 720 1600 | Out-Null
    Start-Sleep -Milliseconds 350
    $warmLog = (& $adb -s $Serial logcat -d -v brief) -join "`n"
    if (-not $warmLog.Contains("unlock effect gesture begin")) {
        Start-Sleep -Milliseconds 650
        & $adb -s $Serial shell input tap 720 1600 | Out-Null
        Start-Sleep -Milliseconds 300
    }

    # CPU/GPU/frame run: no meminfo, screenshot or screen recording in this window.
    & $adb -s $Serial shell dumpsys gfxinfo $packageName reset | Out-Null
    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell cat /sys/class/kgsl/kgsl-3d0/gpubusy | Out-Null
    $processId = (& $adb -s $Serial shell pidof $packageName).Trim()
    $before = Get-ThreadTicks $processId
    $clock = [Diagnostics.Stopwatch]::StartNew()
    for ($index = 0; $index -lt $TapCount; $index++) {
        & $adb -s $Serial shell input tap 720 1600 | Out-Null
        Start-Sleep -Milliseconds 450
    }
    Start-Sleep -Milliseconds 950
    $clock.Stop()
    $after = Get-ThreadTicks $processId

    $threadDeltas = @()
    foreach ($threadId in $after.Keys) {
        if ($before.ContainsKey($threadId)) {
            $delta = $after[$threadId].Ticks - $before[$threadId].Ticks
            if ($delta -gt 0) {
                $threadDeltas += [pscustomobject]@{
                    Name = $after[$threadId].Name
                    Ticks = $delta
                    CpuPct = [math]::Round($delta / $clock.Elapsed.TotalSeconds, 2)
                }
            }
        }
    }
    $totalTicks = ($threadDeltas | Measure-Object Ticks -Sum).Sum
    if ($null -eq $totalTicks) {
        $totalTicks = 0
    }
    $hotThread = $threadDeltas |
        Where-Object Name -ne $packageName |
        Sort-Object Ticks -Descending |
        Select-Object -First 1

    $gpuBusyRaw = (& $adb -s $Serial shell `
        cat /sys/class/kgsl/kgsl-3d0/gpubusy).Trim() -split '\s+'
    $gpuBusyPct = -1
    if ($gpuBusyRaw.Count -ge 2 -and [double] $gpuBusyRaw[1] -gt 0) {
        $gpuBusyPct = [math]::Round(
            100.0 * [double] $gpuBusyRaw[0] / [double] $gpuBusyRaw[1], 2)
    }
    $gpuMhz = [math]::Round(
        [double] ((& $adb -s $Serial shell `
            cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq).Trim()) / 1000000.0,
        0)

    $interactionLog = (& $adb -s $Serial logcat -d -v brief) -join "`n"
    $touchCount = [regex]::Matches(
        $interactionLog,
        'unlock effect gesture begin').Count
    $failureCount = [regex]::Matches(
        $interactionLog,
        'FATAL EXCEPTION|frame failed|renderer failed|SIGSEGV').Count

    # Memory is sampled only after the timing window because dumpsys meminfo is intrusive.
    $meminfo = (& $adb -s $Serial shell dumpsys meminfo $packageName) -join "`n"
    $totals = [regex]::Match(
        $meminfo,
        'TOTAL PSS:\s+(\d+)\s+TOTAL RSS:\s+(\d+)\s+TOTAL SWAP PSS:\s+(\d+)')
    $gfxinfo = (& $adb -s $Serial shell dumpsys gfxinfo $packageName) -join "`n"
    $frameStats = [regex]::Match(
        $gfxinfo,
        'Total frames rendered:\s+(\d+).*?Janky frames:\s+(\d+) \(([^)]+)\)' `
            + '.*?95th percentile:\s+(\d+)ms.*?95th gpu percentile:\s+(\d+)ms',
        [Text.RegularExpressions.RegexOptions]::Singleline)

    $rendererName = "?"
    $preloadMs = -1
    $attachMs = -1
    $initTotalMs = -1
    if ($profile.Success) {
        $rendererName = $profile.Groups[1].Value
        $initTotalMs = [int] $profile.Groups[3].Value
        $preloadMs = [int] $profile.Groups[4].Value
        $attachMs = [int] $profile.Groups[5].Value
    }

    return [pscustomobject][ordered]@{
        Id = $EffectId
        Effect = $effectLabels[$EffectId]
        Renderer = $rendererName
        PreloadMs = $preloadMs
        AttachMs = $attachMs
        InitTotalMs = $initTotalMs
        TouchCount = $touchCount
        SyncMaxMs = Get-MaxLogValue $interactionLog 'syncMs=(\d+)'
        BeginMaxMs = Get-MaxLogValue $interactionLog 'beginMs=(\d+)'
        CpuPct = [math]::Round($totalTicks / $clock.Elapsed.TotalSeconds, 2)
        HotThread = if ($null -ne $hotThread) { $hotThread.Name } else { "-" }
        HotThreadPct = if ($null -ne $hotThread) { $hotThread.CpuPct } else { 0 }
        GpuBusyPct = $gpuBusyPct
        GpuMhz = $gpuMhz
        PssMb = if ($totals.Success) {
            [math]::Round([int] $totals.Groups[1].Value / 1024.0, 1)
        } else { -1 }
        RssMb = if ($totals.Success) {
            [math]::Round([int] $totals.Groups[2].Value / 1024.0, 1)
        } else { -1 }
        NativeMb = [math]::Round(
            (Get-RegexInt $meminfo '^\s*Native Heap:\s+(\d+)') / 1024.0,
            1)
        GraphicsMb = [math]::Round(
            (Get-RegexInt $meminfo '^\s*Graphics:\s+(\d+)') / 1024.0,
            1)
        BitmapMb = [math]::Round(
            (Get-RegexInt $meminfo '^\s*Bitmap \(malloced\):\s+\d+\s+(\d+)') / 1024.0,
            1)
        GfxFrames = if ($frameStats.Success) {
            [int] $frameStats.Groups[1].Value
        } else { -1 }
        GfxJankPct = if ($frameStats.Success) {
            $frameStats.Groups[3].Value
        } else { "-" }
        GfxP95Ms = if ($frameStats.Success) {
            [int] $frameStats.Groups[4].Value
        } else { -1 }
        GfxGpuP95Ms = if ($frameStats.Success) {
            [int] $frameStats.Groups[5].Value
        } else { -1 }
        Failures = $failureCount
        Pid = $processId
        DurationMs = [math]::Round($clock.Elapsed.TotalMilliseconds, 0)
    }
}

$requestedEffectIds = @(
    $EffectIds.Split(',') |
        ForEach-Object { [int] $_.Trim() })

foreach ($effectId in $requestedEffectIds) {
    if (-not $effectLabels.ContainsKey($effectId)) {
        throw "Unknown or unavailable ARM64 effect id: $effectId"
    }
    Invoke-EffectProfile $effectId | ConvertTo-Json -Compress
}
