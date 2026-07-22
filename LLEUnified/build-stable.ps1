param(
    [ValidateSet("All", "Arm32", "Arm64")]
    [string] $Target = "All",
    [string] $KeystorePath = "",
    [string] $KeyAlias = "lle-release"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

if ([string]::IsNullOrWhiteSpace($KeystorePath)) {
    if (-not [string]::IsNullOrWhiteSpace($env:LLE_RELEASE_KEYSTORE)) {
        $KeystorePath = $env:LLE_RELEASE_KEYSTORE
    } else {
        $KeystorePath = Join-Path $env:USERPROFILE `
                "Documents\LLE-signing-private\lle-release.p12"
    }
}
if (-not (Test-Path -LiteralPath $KeystorePath)) {
    throw "Stable release keystore not found: $KeystorePath"
}

$securePassword = Read-Host "L.L.E stable signing password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:LLE_RELEASE_KEY_PASSWORD = `
            [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    & powershell -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root "build.ps1") `
            -Target $Target `
            -ReleaseSigning `
            -ReleaseKeystorePath $KeystorePath `
            -ReleaseKeyAlias $KeyAlias
    if ($LASTEXITCODE -ne 0) {
        throw "Stable build failed with exit code $LASTEXITCODE"
    }
} finally {
    Remove-Item Env:LLE_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}

$artifacts = @()
if ($Target -eq "All" -or $Target -eq "Arm32") {
    $artifacts += Join-Path $root "build\armeabi-v7a\LLE-armeabi-v7a-release.apk"
}
if ($Target -eq "All" -or $Target -eq "Arm64") {
    $artifacts += Join-Path $root "build\arm64-v8a\LLE64-arm64-v8a-release.apk"
}
foreach ($artifact in $artifacts) {
    if (-not (Test-Path -LiteralPath $artifact)) {
        throw "Expected stable artifact is missing: $artifact"
    }
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
    Write-Host "Stable APK: $artifact"
    Write-Host "SHA-256: $hash"
}
