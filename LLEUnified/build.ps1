param(
    [switch] $IncludeNote5Probe,
    [switch] $IncludeRippleCoreProbe,
    [switch] $LegacyVendorEffects,
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

if ($ReleaseSigning -and ($IncludeNote5Probe -or $IncludeRippleCoreProbe -or
        $WatercolorFeedbackMode -ne "Stable")) {
    throw "Stable release signing does not support probe or experimental variants"
}

$arguments = @("-WatercolorFeedbackMode", $WatercolorFeedbackMode)
if ($IncludeNote5Probe) {
    $arguments += "-IncludeNote5Probe"
}
if ($IncludeRippleCoreProbe) {
    $arguments += "-IncludeRippleCoreProbe"
}
if ($LegacyVendorEffects) {
    $arguments += "-LegacyVendorEffects"
}
if ($ReleaseSigning) {
    $arguments += @(
        "-ReleaseSigning",
        "-ReleaseKeystorePath", $ReleaseKeystorePath,
        "-ReleaseKeyAlias", $ReleaseKeyAlias,
        "-ReleaseLineagePath", $ReleaseLineagePath,
        "-ReleaseOldKeystorePath", $ReleaseOldKeystorePath,
        "-ReleaseOldKeyAlias", $ReleaseOldKeyAlias
    )
}

& powershell -NoProfile -ExecutionPolicy Bypass `
        -File (Join-Path $root "build-arm64.ps1") @arguments
if ($LASTEXITCODE -ne 0) {
    throw "ARM64 build failed with exit code $LASTEXITCODE"
}

Write-Host "L.L.E ARM64 build complete."
