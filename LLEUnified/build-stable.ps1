param(
    [switch] $IncludeLegacyVendor,
    [string] $KeystorePath = "",
    [string] $KeyAlias = "lle-release"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildTools = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools\35.0.1"
$apksigner = Join-Path $buildTools "apksigner.bat"
$oldKeystore = Join-Path $root ".keys\debug.keystore"
$expectedOldKeystoreSha256 = "DC310956BC5BB0A210950D68F4D2A24177D30DDE2CAF547C61C3F6CFD52B6AC8"
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
if (-not (Test-Path -LiteralPath $oldKeystore)) {
    throw "Compatible legacy signer is required at $oldKeystore. Restore it from the private release environment; never commit it."
}
$oldKeystoreSha256 = (Get-FileHash -LiteralPath $oldKeystore -Algorithm SHA256).Hash
if ($oldKeystoreSha256 -ne $expectedOldKeystoreSha256) {
    throw "Legacy signer hash mismatch. Refusing to create an incompatible release lineage."
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

    $buildArguments = @(
        "-ReleaseSigning",
        "-ReleaseKeystorePath", $KeystorePath,
        "-ReleaseKeyAlias", $KeyAlias,
        "-ReleaseLineagePath", $lineagePath,
        "-ReleaseOldKeystorePath", $oldKeystore,
        "-ReleaseOldKeyAlias", "androiddebugkey"
    )
    if ($IncludeLegacyVendor) {
        $buildArguments += "-LegacyVendorEffects"
    }
    & powershell -NoProfile -ExecutionPolicy Bypass `
            -File (Join-Path $root "build.ps1") @buildArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Stable ARM64 build failed with exit code $LASTEXITCODE"
    }
} finally {
    Remove-Item Env:LLE_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}

$artifacts = @(
    (Join-Path $root "build\arm64-v8a\LLE64-arm64-v8a-release.apk")
)
if ($IncludeLegacyVendor) {
    $artifacts += Join-Path $root `
            "build\arm64-v8a-legacy\LLE64-arm64-v8a-legacy-vendor-release.apk"
}
foreach ($artifact in $artifacts) {
    if (-not (Test-Path -LiteralPath $artifact)) {
        throw "Expected stable artifact is missing: $artifact"
    }
    Write-Host "Stable APK: $artifact"
    Write-Host "SHA-256: $((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash)"
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
$destination = Join-Path $releaseDirectory "LLE64-$version-64-bit.apk"
Copy-Item -LiteralPath $artifacts[0] -Destination $destination -Force
$packagedArtifacts += $destination
if ($IncludeLegacyVendor) {
    $legacyDestination = Join-Path $releaseDirectory `
            "LLE64-$version-64-bit-legacy-vendor.apk"
    Copy-Item -LiteralPath $artifacts[1] -Destination $legacyDestination -Force
    $packagedArtifacts += $legacyDestination
}

$checksumLines = foreach ($artifact in $packagedArtifacts) {
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $(Split-Path -Leaf $artifact)"
}
$checksumLines | Set-Content -LiteralPath `
        (Join-Path $releaseDirectory "SHA256SUMS.txt") -Encoding ascii
Write-Host "Packaged stable ARM64 release: $releaseDirectory"
