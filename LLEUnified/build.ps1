param(
    [ValidateSet("All", "Arm32", "Arm64")]
    [string] $Target = "All",
    [switch] $IncludeNote5Probe,
    [switch] $IncludeRippleCoreProbe,
    [ValidateSet("Stable", "StockFeedback")]
    [string] $WatercolorFeedbackMode = "Stable",
    [switch] $ReleaseSigning,
    [string] $ReleaseKeystorePath = "",
    [string] $ReleaseKeyAlias = "lle-release",
    [string] $ReleaseLineagePath = "",
    [string] $ReleaseOldKeystorePath = "",
    [string] $ReleaseOldKeyAlias = "androiddebugkey"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

if ($Target -ne "Arm64" -and
        ($IncludeNote5Probe -or $IncludeRippleCoreProbe -or
        $WatercolorFeedbackMode -ne "Stable")) {
    throw "ARM64 diagnostic options require -Target Arm64"
}
if ($ReleaseSigning -and ($IncludeNote5Probe -or $IncludeRippleCoreProbe -or
        $WatercolorFeedbackMode -ne "Stable")) {
    throw "Stable release signing does not support diagnostic ARM64 variants"
}

function Run-Target([string] $Script, [string[]] $Arguments) {
    & powershell -ExecutionPolicy Bypass -File $Script @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Script failed with exit code $LASTEXITCODE"
    }
}

if ($Target -eq "All" -or $Target -eq "Arm32") {
    $arm32Arguments = @()
    if ($ReleaseSigning) {
        $arm32Arguments += @("-ReleaseSigning",
            "-ReleaseKeystorePath", $ReleaseKeystorePath,
            "-ReleaseKeyAlias", $ReleaseKeyAlias,
            "-ReleaseLineagePath", $ReleaseLineagePath,
            "-ReleaseOldKeystorePath", $ReleaseOldKeystorePath,
            "-ReleaseOldKeyAlias", $ReleaseOldKeyAlias)
    }
    Run-Target (Join-Path $root "build-arm32.ps1") $arm32Arguments
}

if ($Target -eq "All" -or $Target -eq "Arm64") {
    $arm64Arguments = @("-WatercolorFeedbackMode", $WatercolorFeedbackMode)
    if ($IncludeNote5Probe) {
        $arm64Arguments += "-IncludeNote5Probe"
    }
    if ($IncludeRippleCoreProbe) {
        $arm64Arguments += "-IncludeRippleCoreProbe"
    }
    if ($ReleaseSigning) {
        $arm64Arguments += @("-ReleaseSigning",
            "-ReleaseKeystorePath", $ReleaseKeystorePath,
            "-ReleaseKeyAlias", $ReleaseKeyAlias,
            "-ReleaseLineagePath", $ReleaseLineagePath,
            "-ReleaseOldKeystorePath", $ReleaseOldKeystorePath,
            "-ReleaseOldKeyAlias", $ReleaseOldKeyAlias)
    }
    Run-Target (Join-Path $root "build-arm64.ps1") $arm64Arguments
}

Write-Host "Unified LLE build complete for target: $Target"
