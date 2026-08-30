param(
    [ValidateSet("All", "Arm32", "Arm64")]
    [string] $Target = "Arm64",
    [switch] $IncludeLegacyVendor,
    [string] $KeystorePath = "",
    [string] $KeyAlias = "lle-release"
)

$ErrorActionPreference = "Stop"

# Keep checksum generation working even when Windows PowerShell does not
# auto-load Microsoft.PowerShell.Utility/Get-FileHash.
if (-not (Get-Command -Name Get-FileHash -ErrorAction SilentlyContinue)) {
    function Get-FileHash {
        param(
            [Parameter(Mandatory = $true)]
            [string] $LiteralPath,
            [ValidateSet("SHA256")]
            [string] $Algorithm = "SHA256"
        )
        $resolvedPath = [IO.Path]::GetFullPath($LiteralPath)
        $stream = [IO.File]::Open($resolvedPath, [IO.FileMode]::Open,
                [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        try {
            $sha256 = [Security.Cryptography.SHA256]::Create()
            try {
                $digest = $sha256.ComputeHash($stream)
            } finally {
                $sha256.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
        [pscustomobject]@{
            Algorithm = $Algorithm
            Hash = [BitConverter]::ToString($digest).Replace("-", "")
            Path = $resolvedPath
        }
    }
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildTools = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools\35.0.1"
$apksigner = Join-Path $buildTools "apksigner.bat"
$oldKeystore = Join-Path $root ".keys\debug.keystore"
$sourceOldKeystore = Join-Path $root "..\unlock-effects-test\demo-apk\debug.keystore"
$signingWork = Join-Path $root "build\release-signing"
$lineagePath = Join-Path $signingWork "lle-signing-lineage.bin"

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
if (-not (Test-Path -LiteralPath $apksigner)) {
    throw "apksigner 35.0.1 not found: $apksigner"
}
if ($IncludeLegacyVendor -and $Target -eq "Arm32") {
    throw "The legacy-vendor product variant is ARM64-only"
}
if (-not (Test-Path -LiteralPath $oldKeystore)) {
    if (-not (Test-Path -LiteralPath $sourceOldKeystore)) {
        throw "Previous Beta signing keystore not found: $sourceOldKeystore"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $oldKeystore) |
            Out-Null
    Copy-Item -LiteralPath $sourceOldKeystore -Destination $oldKeystore
}

$securePassword = Read-Host "L.L.E stable signing password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:LLE_RELEASE_KEY_PASSWORD = `
            [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    New-Item -ItemType Directory -Force -Path $signingWork | Out-Null
    Remove-Item -LiteralPath $lineagePath -Force -ErrorAction SilentlyContinue
    & $apksigner rotate `
            --out $lineagePath `
            --old-signer `
            --ks $oldKeystore `
            --ks-key-alias androiddebugkey `
            --ks-pass pass:android `
            --key-pass pass:android `
            --new-signer `
            --ks $KeystorePath `
            --ks-key-alias $KeyAlias `
            --ks-pass "env:LLE_RELEASE_KEY_PASSWORD" `
            --key-pass "env:LLE_RELEASE_KEY_PASSWORD"
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $lineagePath)) {
        throw "Could not create the Beta-to-stable signing lineage"
    }
    & powershell -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root "build.ps1") `
            -Target $Target `
            -ReleaseSigning `
            -ReleaseKeystorePath $KeystorePath `
            -ReleaseKeyAlias $KeyAlias `
            -ReleaseLineagePath $lineagePath `
            -ReleaseOldKeystorePath $oldKeystore `
            -ReleaseOldKeyAlias androiddebugkey
    if ($LASTEXITCODE -ne 0) {
        throw "Stable build failed with exit code $LASTEXITCODE"
    }
    if ($IncludeLegacyVendor -and ($Target -eq "All" -or $Target -eq "Arm64")) {
        & powershell -NoProfile -ExecutionPolicy Bypass `
                -File (Join-Path $root "build.ps1") `
                -Target Arm64 `
                -LegacyVendorEffects `
                -ReleaseSigning `
                -ReleaseKeystorePath $KeystorePath `
                -ReleaseKeyAlias $KeyAlias `
                -ReleaseLineagePath $lineagePath `
                -ReleaseOldKeystorePath $oldKeystore `
                -ReleaseOldKeyAlias androiddebugkey
        if ($LASTEXITCODE -ne 0) {
            throw "Stable legacy-vendor build failed with exit code $LASTEXITCODE"
        }
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
    if ($IncludeLegacyVendor) {
        $artifacts += Join-Path $root `
                "build\arm64-v8a-legacy\LLE64-arm64-v8a-legacy-vendor-release.apk"
    }
}
foreach ($artifact in $artifacts) {
    if (-not (Test-Path -LiteralPath $artifact)) {
        throw "Expected stable artifact is missing: $artifact"
    }
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
    Write-Host "Stable APK: $artifact"
    Write-Host "SHA-256: $hash"
}

$manifest = Get-Content -LiteralPath (Join-Path $root "AndroidManifest.xml") -Raw
$versionMatch = [regex]::Match($manifest, 'android:versionName="([^"]+)"')
if (-not $versionMatch.Success) {
    throw "Could not read android:versionName from AndroidManifest.xml"
}
$version = $versionMatch.Groups[1].Value
$releaseDirectory = Join-Path $root "build\release\$version"
New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
$packagedArtifacts = @()
if ($Target -eq "All" -or $Target -eq "Arm32") {
    $destination = Join-Path $releaseDirectory "LLE-$version-32-bit.apk"
    Copy-Item -LiteralPath (Join-Path $root `
            "build\armeabi-v7a\LLE-armeabi-v7a-release.apk") `
            -Destination $destination -Force
    $packagedArtifacts += $destination
}
if ($Target -eq "All" -or $Target -eq "Arm64") {
    $destination = Join-Path $releaseDirectory "LLE64-$version-64-bit.apk"
    Copy-Item -LiteralPath (Join-Path $root `
            "build\arm64-v8a\LLE64-arm64-v8a-release.apk") `
            -Destination $destination -Force
    $packagedArtifacts += $destination
    if ($IncludeLegacyVendor) {
        $legacyDestination = Join-Path $releaseDirectory `
                "LLE64-$version-64-bit-legacy-vendor.apk"
        Copy-Item -LiteralPath (Join-Path $root `
                "build\arm64-v8a-legacy\LLE64-arm64-v8a-legacy-vendor-release.apk") `
                -Destination $legacyDestination -Force
        $packagedArtifacts += $legacyDestination
    }
}
$checksumLines = foreach ($artifact in $packagedArtifacts) {
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $(Split-Path -Leaf $artifact)"
}
$checksumLines | Set-Content -LiteralPath `
        (Join-Path $releaseDirectory "SHA256SUMS.txt") -Encoding ascii
Write-Host "Packaged stable release: $releaseDirectory"
