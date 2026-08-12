param(
    [switch] $IncludeNote5Probe,
    [switch] $IncludeRippleCoreProbe,
    [switch] $Companion,
    [switch] $Tester,
    [switch] $LegacyVendorEffects,
    [ValidateRange(0, 2147483647)]
    [int] $ValidationVersionCode = 0,
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
$applicationId = if ($Tester) { "com.codex.lle64.test" } else { "com.codex.lle64" }
$launcherLabel = if ($Tester) { "L.L.E Tester" } else { "L.L.E 64" }
$testerVersionCode = if ($Tester -and $ValidationVersionCode -eq 0) { 34 } else { 0 }
$testerVersionName = if ($Tester -and $ValidationVersionCode -eq 0) { "1.0.5.7.B2" } else { "" }
if ($LegacyVendorEffects) {
    $launcherLabel += " Legacy"
}
if ($ValidationVersionCode -gt 0) {
    if (-not $Tester -or $LegacyVendorEffects -or $Companion -or
            $IncludeNote5Probe -or $IncludeRippleCoreProbe -or
            $ReleaseSigning -or $WatercolorFeedbackMode -ne "Stable") {
        throw "Validation version override is only available for a Samsung-free ARM64 tester"
    }
    if ($ValidationVersionCode -lt 27) {
        throw "Validation versionCode must be at least 27"
    }
    $launcherLabel += " Validation"
}
if ($IncludeNote5Probe -and $IncludeRippleCoreProbe) {
    throw "Choose only one native probe build"
}
if ($Companion -and ($IncludeNote5Probe -or $IncludeRippleCoreProbe)) {
    throw "The co-installable ARM64 companion does not support native probe variants"
}
if ($Tester -and ($Companion -or $IncludeRippleCoreProbe)) {
    throw "The ARM64 tester cannot be combined with companion or ripple-core probe variants"
}
if ($LegacyVendorEffects -and $Companion) {
    throw "Legacy vendor effects are not supported by the companion build"
}
if ($ReleaseSigning -and ($Companion -or $IncludeNote5Probe -or
        $IncludeRippleCoreProbe -or $Tester -or
        $WatercolorFeedbackMode -ne "Stable")) {
    throw "Stable release signing is only available for normal ARM64 product builds"
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$cachedNdk = Join-Path (Split-Path -Parent $root) "tools-cache\android-ndk-r27d"
$legacyNdk = Join-Path $root "..\unlock-effects-test\tools\android-ndk-r27d"
$ndk = if ($env:ANDROID_NDK_HOME) {
    $env:ANDROID_NDK_HOME
} elseif (Test-Path -LiteralPath $cachedNdk) {
    $cachedNdk
} else {
    $legacyNdk
}
$buildTools = Join-Path $sdk "build-tools\35.0.1"
$platform = Join-Path $sdk "platforms\android-35\android.jar"
$clang = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android23-clang.cmd"
$readelf = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
$objdump = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objdump.exe"
$strings = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strings.exe"

$out = Join-Path $root $(if ($ValidationVersionCode -gt 0) {
    "build\arm64-v8a-test-validation-vc$ValidationVersionCode"
} elseif ($Tester -and $LegacyVendorEffects) {
    "build\arm64-v8a-test-legacy"
} elseif ($Tester) {
    "build\arm64-v8a-test"
} elseif ($LegacyVendorEffects) {
    "build\arm64-v8a-legacy"
} elseif ($Companion) {
    "build\arm64-v8a-dev"
} else {
    "build\arm64-v8a"
})
$classes = Join-Path $out "classes"
$dex = Join-Path $out "dex"
$resStage = Join-Path $out "res"
$resZip = Join-Path $out "res.zip"
$unsigned = Join-Path $out "LLE64-arm64-unsigned.apk"
$assembled = Join-Path $out "LLE64-arm64-assembled.apk"
$zipaligned = Join-Path $out "LLE64-arm64-zipaligned.apk"
$signed = Join-Path $out $(if ($ValidationVersionCode -gt 0) {
    "LLE64-arm64-v8a-validation-vc$ValidationVersionCode-tester.apk"
} elseif ($ReleaseSigning -and $LegacyVendorEffects) {
    "LLE64-arm64-v8a-legacy-vendor-release.apk"
} elseif ($ReleaseSigning) {
    "LLE64-arm64-v8a-release.apk"
} elseif ($Tester -and $LegacyVendorEffects) {
    "LLE64-arm64-v8a-legacy-vendor-tester.apk"
} elseif ($Tester) {
    "LLE64-arm64-v8a-tester.apk"
} elseif ($LegacyVendorEffects) {
    "LLE64-arm64-v8a-legacy-vendor.apk"
} elseif ($Companion) {
    "LLE64-arm64-v8a.apk"
} elseif ($IncludeNote5Probe) {
    "LLE64-arm64-note5-probe.apk"
} elseif ($IncludeRippleCoreProbe) {
    "LLE64-arm64-ripple-core-probe.apk"
} elseif ($WatercolorFeedbackMode -eq "StockFeedback") {
    "LLE64-arm64-watercolor-stock-feedback.apk"
} else {
    "LLE64-arm64-v8a.apk"
})
$classesJar = Join-Path $out "classes.jar"
$nativeStage = Join-Path $out "native"
$arm64Stage = Join-Path $nativeStage "lib\arm64-v8a"
$marker = Join-Path $arm64Stage "liblle64marker.so"
$keystore = Join-Path $root ".keys\debug.keystore"
$sourceKeystore = Join-Path $root "..\unlock-effects-test\demo-apk\debug.keystore"
$releaseCertificateSha256 = "5397D6ACE3E9D2F14D8FFD2285E26E9F1B26635589CAC3A3DC95C0DEFF76B8EE"
$canonicalManifest = Join-Path $root "AndroidManifest.xml"
$canonicalManifestHashBefore = (Get-FileHash -LiteralPath $canonicalManifest `
        -Algorithm SHA256).Hash
$manifest = $canonicalManifest
$includeNativeProbeActivity = $IncludeNote5Probe -or $IncludeRippleCoreProbe
$nativeProbeSource = Join-Path $root `
        "src\com\codex\lle\Note5NativeProbeActivity.java"
$canonicalBuildFlavorSource = Join-Path $root `
        "src\com\codex\lle\BuildFlavor.java"
$generatedBuildFlavorSource = Join-Path $out `
        "gen\com\codex\lle\BuildFlavor.java"
$buildFlavorName = if ($LegacyVendorEffects) {
    "legacy-vendor"
} else {
    "samsung-free"
}

function Run($exe, $arguments) {
    if (-not (Test-Path -LiteralPath $exe) -and -not (Get-Command $exe -ErrorAction SilentlyContinue)) {
        throw "Missing build tool: $exe"
    }
    # Native tool warnings (notably JDK 21's source-8 notice) are written to stderr with a
    # successful exit code. Preserve the explicit exit-code contract below rather than letting
    # the caller's ErrorActionPreference turn those warnings into terminating PowerShell errors.
    $savedErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & $exe @arguments
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "$exe failed with exit code $exitCode"
    }
}

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, $classes, $dex, $resStage, $arm64Stage | Out-Null

Copy-Item -Path (Join-Path $root "res\*") -Destination $resStage -Recurse -Force
$stringsStage = Join-Path $resStage "values\strings.xml"
$stringsText = [IO.File]::ReadAllText($stringsStage)
$appNamePattern = '<string name="app_name">[^<]*</string>'
if ([regex]::Matches($stringsText, $appNamePattern).Count -ne 1) {
    throw "ARM64 launcher label patch point not found"
}
$stringsText = [regex]::Replace(
        $stringsText,
        $appNamePattern,
        "<string name=`"app_name`">$launcherLabel</string>",
        1)
[IO.File]::WriteAllText($stringsStage, $stringsText, [Text.UTF8Encoding]::new($false))
Run (Join-Path $buildTools "aapt2.exe") @("compile", "--dir", $resStage, "-o", $resZip)
$generatedManifest = Join-Path $out "AndroidManifest.xml"
$manifestText = [System.IO.File]::ReadAllText($canonicalManifest)
$generatedManifestText = $manifestText.Replace(
        'package="com.codex.lle"',
        "package=`"$applicationId`"")
$generatedManifestText = $generatedManifestText.Replace(
        'android:authorities="com.codex.lle.debugreports"',
        "android:authorities=`"$applicationId.debugreports`"")
$generatedManifestText = [regex]::Replace(
        $generatedManifestText,
        'android:name="\.([A-Za-z0-9_$.]+)"',
        'android:name="com.codex.lle.$1"')
if ($generatedManifestText -eq $manifestText -or
        $generatedManifestText -notmatch "package=`"$([regex]::Escape($applicationId))`"" -or
        $generatedManifestText -match 'android:name="\.') {
    throw "ARM64 LLE64 manifest patch failed"
}
if ($ValidationVersionCode -gt 0 -or $testerVersionCode -gt 0) {
    $versionCodePattern = 'android:versionCode="\d+"'
    if ([regex]::Matches($generatedManifestText, $versionCodePattern).Count -ne 1) {
        throw "Validation manifest versionCode patch point not found"
    }
    $stagedVersionCode = if ($ValidationVersionCode -gt 0) {
        $ValidationVersionCode
    } else {
        $testerVersionCode
    }
    $generatedManifestText = [regex]::Replace(
            $generatedManifestText,
            $versionCodePattern,
            "android:versionCode=`"$stagedVersionCode`"",
            1)
    if ($generatedManifestText -notmatch
            "android:versionCode=`"$stagedVersionCode`"") {
        throw "Staged manifest versionCode override failed"
    }
    $canonicalVersionName = [regex]::Match(
            $manifestText, 'android:versionName="([^"]+)"').Groups[1].Value
    if ($testerVersionName) {
        $versionNamePattern = 'android:versionName="[^"]+"'
        if ([regex]::Matches($generatedManifestText, $versionNamePattern).Count -ne 1) {
            throw "Tester manifest versionName patch point not found"
        }
        $generatedManifestText = [regex]::Replace(
                $generatedManifestText,
                $versionNamePattern,
                "android:versionName=`"$testerVersionName`"",
                1)
    }
    $generatedVersionName = [regex]::Match(
            $generatedManifestText, 'android:versionName="([^"]+)"').Groups[1].Value
    $expectedVersionName = if ($testerVersionName) {
        $testerVersionName
    } else {
        $canonicalVersionName
    }
    if ([string]::IsNullOrWhiteSpace($canonicalVersionName) -or
            $generatedVersionName -ne $expectedVersionName) {
        throw "Staged manifest versionName override failed"
    }
}
if ($includeNativeProbeActivity) {
    $probePattern = '(?s)(android:name="com\.codex\.lle\.Note5NativeProbeActivity".*?android:exported=")false(")'
    $probeManifestText = [regex]::Replace(
            $generatedManifestText, $probePattern, '${1}true$2')
    if ($probeManifestText -eq $generatedManifestText) {
        throw "Note5NativeProbeActivity manifest patch point not found"
    }
    $generatedManifestText = $probeManifestText
    if ($generatedManifestText -notmatch
            '(?s)android:name="com\.codex\.lle\.Note5NativeProbeActivity".*?android:exported="true"') {
        throw "Diagnostic manifest does not export Note5NativeProbeActivity"
    }
} else {
    $probeActivityPattern = '(?s)\s*<activity\s+android:name="com\.codex\.lle\.Note5NativeProbeActivity".*?/>'
    $probeManifestText = [regex]::Replace(
            $generatedManifestText, $probeActivityPattern, "")
    if ($probeManifestText -eq $generatedManifestText -or
            $probeManifestText -match 'Note5NativeProbeActivity') {
        throw "Note5NativeProbeActivity manifest removal failed"
    }
    $generatedManifestText = $probeManifestText
}
[System.IO.File]::WriteAllText($generatedManifest, $generatedManifestText)
$manifest = $generatedManifest
$linkArgs = @(
    "link", "-o", $unsigned,
    "-I", $platform,
    "--manifest", $manifest,
    $resZip,
    "--java", (Join-Path $out "gen"),
    "--auto-add-overlay"
)
# Keep Java/JNI classes under com.codex.lle while the permanent ARM64 Android
# application ID is com.codex.lle64. This preserves all native JNI entry points.
$linkArgs += @("--custom-package", "com.codex.lle")
if (Test-Path (Join-Path $root "assets")) {
    $linkArgs += @("-A", (Join-Path $root "assets"))
}
Run (Join-Path $buildTools "aapt2.exe") $linkArgs

$generatedBuildFlavorDirectory = Split-Path -Parent $generatedBuildFlavorSource
New-Item -ItemType Directory -Force -Path $generatedBuildFlavorDirectory |
        Out-Null
$legacyVendorLiteral = if ($LegacyVendorEffects) { "true" } else { "false" }
$generatedBuildFlavorText = @"
package com.codex.lle;

/** Generated by build-arm64.ps1. */
final class BuildFlavor {
    static final boolean LEGACY_VENDOR_EFFECTS = $legacyVendorLiteral;
    static final String NAME = "$buildFlavorName";

    private BuildFlavor() {
    }
}
"@
[IO.File]::WriteAllText(
        $generatedBuildFlavorSource,
        $generatedBuildFlavorText,
        [Text.UTF8Encoding]::new($false))

$sources = @()
$sources += Get-ChildItem (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object FullName
$sources += Get-ChildItem (Join-Path $out "gen") -Recurse -Filter *.java | ForEach-Object FullName
$sources = @($sources | Where-Object {
    -not [string]::Equals(
            $_, $canonicalBuildFlavorSource, [StringComparison]::OrdinalIgnoreCase)
})
if ($sources -contains $canonicalBuildFlavorSource -or
        $sources -notcontains $generatedBuildFlavorSource) {
    throw "ARM64 generated BuildFlavor source gating failed"
}
if ($includeNativeProbeActivity) {
    if ($sources -notcontains $nativeProbeSource) {
        throw "Note5NativeProbeActivity source is missing from diagnostic build"
    }
} else {
    $sources = @($sources | Where-Object {
        -not [string]::Equals(
                $_, $nativeProbeSource, [StringComparison]::OrdinalIgnoreCase)
    })
    if ($sources -contains $nativeProbeSource) {
        throw "Note5NativeProbeActivity source exclusion failed"
    }
}
$javacArgs = @(
    "-encoding", "UTF-8",
    "-source", "1.8",
    "-target", "1.8",
    "-bootclasspath", $platform,
    "-d", $classes
) + $sources
Run "javac.exe" $javacArgs
Run "jar.exe" @("cf", $classesJar, "-C", $classes, ".")
$classEntries = @(& jar.exe tf $classesJar)
$buildFlavorClassEntries = @($classEntries | Where-Object {
    $_ -eq "com/codex/lle/BuildFlavor.class"
})
if ($buildFlavorClassEntries.Count -ne 1) {
    throw "ARM64 build must contain exactly one generated BuildFlavor class"
}
$probeClassEntries = @($classEntries | Where-Object {
    $_ -like "com/codex/lle/Note5NativeProbeActivity*.class"
})
if ($includeNativeProbeActivity -and $probeClassEntries.Count -eq 0) {
    throw "Diagnostic build is missing Note5NativeProbeActivity classes"
}
if (-not $includeNativeProbeActivity -and $probeClassEntries.Count -ne 0) {
    throw "Ordinary build contains Note5NativeProbeActivity classes"
}
Run (Join-Path $buildTools "d8.bat") @("--lib", $platform, "--min-api", "23", "--output", $dex, $classesJar)
$classesDex = Join-Path $dex "classes.dex"
$dexStrings = @(& $strings $classesDex)
if ($dexStrings -notcontains $buildFlavorName) {
    throw "DEX build flavor marker is missing: $buildFlavorName"
}
$probeDexMarkers = @(
    $dexStrings | Where-Object {
        $_ -match 'Note5NativeProbeActivity|colour_probe|colour-(arm64|wip)'
    }
)
if ($includeNativeProbeActivity) {
    foreach ($requiredProbeMarker in @(
            'Note5NativeProbeActivity',
            'colour_probe',
            'colour-arm64-render',
            'colour-wip-render')) {
        if (@($probeDexMarkers -match [regex]::Escape(
                $requiredProbeMarker)).Count -eq 0) {
            throw "Diagnostic DEX is missing probe marker: $requiredProbeMarker"
        }
    }
}
if (-not $includeNativeProbeActivity -and $probeDexMarkers.Count -ne 0) {
    throw "Ordinary DEX contains native-probe markers: $($probeDexMarkers -join ', ')"
}

Copy-Item $unsigned $assembled
Run "jar.exe" @("uf", $assembled, "-C", $dex, "classes.dex")
# ARM64 renderers are app-owned Java hosts.  ARM32 keeps its frozen Samsung
# visual-effect DEX path in build-arm32.ps1, but the active ARM64 package must
# contain only LLE's primary DEX.

Run $clang @(
    "-shared", "-fPIC", "-O2", "-Wall", "-Werror",
    "-Wl,-soname,liblle64marker.so",
    "-o", $marker,
    (Join-Path $root "native\lle64_marker.c")
)
$markerHeader = & $readelf -h $marker
if ($LASTEXITCODE -ne 0 -or ($markerHeader -join "`n") -notmatch "Machine:\s+AArch64") {
    throw "ARM64 marker verification failed"
}

if ($LegacyVendorEffects) {
$candidateRoot = Join-Path $root "reference\arm64-candidates\note5-aoj4"
$stableNote5Hashes = @{
    "libColourDropletEffect.so" = "634DC703FF9288A4961B3E636B83DD89DDBF86DF6087D624DC19B4231E6C010C"
    "libSparklingBubblesEffect.so" = "F96E287CD20B411A863D07D012631FA61761FC35AEC50D4B4A4B454577B2C944"
    "libstlport.so" = "821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA"
}
foreach ($library in @(
    "libColourDropletEffect.so",
    "libSparklingBubblesEffect.so",
    "libstlport.so"
)) {
    $candidate = Join-Path $candidateRoot $library
    if (-not (Test-Path $candidate)) {
        throw "Missing stable Note 5 ARM64 library: $candidate"
    }
    $candidateHash = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash
    if ($candidateHash -ne $stableNote5Hashes[$library]) {
        throw "Unexpected SHA-256 for $library`: $candidateHash"
    }
    $header = (& $readelf -h $candidate) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $header -notmatch "Machine:\s+AArch64") {
        throw "$library is not an AArch64 ELF"
    }
    $dynamic = (& $readelf -d $candidate) -join "`n"
    if ($LASTEXITCODE -ne 0 -or
            $dynamic -notmatch "SONAME.*\[$([regex]::Escape($library))\]") {
        throw "$library has an unexpected or missing SONAME"
    }
    if ($library -ne "libstlport.so" -and $dynamic -notmatch "NEEDED.*\[libstlport\.so\]") {
        throw "$library no longer declares libstlport.so"
    }
    $stagedLibrary = Join-Path $arm64Stage $library
    Copy-Item $candidate $stagedLibrary -Force
    if ((Get-FileHash -LiteralPath $stagedLibrary -Algorithm SHA256).Hash -ne $candidateHash) {
        throw "Staged copy hash mismatch for $library"
    }
}
$note5PatchScript = Join-Path $root `
        "vendor\native-patches\patch-note5-arm64-transparency.ps1"
& $note5PatchScript `
        -ReferenceDirectory $candidateRoot `
        -StagedDirectory $arm64Stage `
        -ReadElfPath $readelf `
        -ObjdumpPath $objdump `
        -StringsPath $strings
if ($LASTEXITCODE -ne 0) {
    throw "Note 5 ARM64 transparency patch failed with exit code $LASTEXITCODE"
}
$stableNote5StagedHashes = @{
    "libColourDropletEffect.so" = "38FFB25ADAA178D96B981C3EC0D616EC86B2F73EC5EBDDE8437E02D610D19EE4"
    "libSparklingBubblesEffect.so" = "B96EC92493477AF9F9958A8B7A6466BB4EDD5195145D47F339BB68A9C8552FC0"
    "libstlport.so" = "821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA"
}
foreach ($library in $stableNote5StagedHashes.Keys) {
    $stagedHash = (Get-FileHash -LiteralPath (Join-Path $arm64Stage $library) `
            -Algorithm SHA256).Hash
    if ($stagedHash -ne $stableNote5StagedHashes[$library]) {
        throw "Unexpected staged SHA-256 for $library`: $stagedHash"
    }
}
}
$sparklingAppOwnedNative = Join-Path $root "ports\sparkling-bubbles\native"
$sparklingAppOwnedBuiltLibrary = Join-Path $out "liblleSparklingBubbles-built.so"
$sparklingAppOwnedLibrary = Join-Path $arm64Stage "liblleSparklingBubbles.so"
if (-not (Test-Path -LiteralPath $sparklingAppOwnedNative -PathType Container)) {
    throw "Missing app-owned Sparkling Bubbles native directory: $sparklingAppOwnedNative"
}
$sparklingAppOwnedSources = @(
    Get-ChildItem -LiteralPath $sparklingAppOwnedNative -Filter "*.c" -File |
            Sort-Object Name |
            ForEach-Object { $_.FullName }
)
if ($sparklingAppOwnedSources.Count -eq 0) {
    throw "Missing app-owned Sparkling Bubbles native sources: $sparklingAppOwnedNative"
}
$sparklingAppOwnedClangArgs = @(
    "-std=c11", "-O2", "-fPIC", "-Wall", "-Wextra", "-Werror",
    "-shared", "-Wl,--no-undefined",
    "-Wl,-soname,liblleSparklingBubbles.so",
    "-I", $sparklingAppOwnedNative
) + $sparklingAppOwnedSources + @(
    "-Wl,--no-as-needed",
    "-landroid", "-ljnigraphics", "-lGLESv2", "-llog", "-lm",
    "-o", $sparklingAppOwnedBuiltLibrary
)
Run $clang $sparklingAppOwnedClangArgs
$sparklingAppOwnedHeader = (& $readelf -h $sparklingAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $sparklingAppOwnedHeader -notmatch "Class:\s+ELF64" `
        -or $sparklingAppOwnedHeader -notmatch "Machine:\s+AArch64") {
    throw "App-owned Sparkling Bubbles library is not an ELF64 AArch64 binary"
}
$sparklingAppOwnedDynamic = (& $readelf -d $sparklingAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $sparklingAppOwnedDynamic -notmatch `
        "SONAME.*\[liblleSparklingBubbles\.so\]") {
    throw "App-owned Sparkling Bubbles library has an unexpected SONAME"
}
if ($sparklingAppOwnedDynamic -match
        "NEEDED.*\[(libstlport|libstdc\+\+|libc\+\+|libColourDropletEffect|" +
        "libSparklingBubblesEffect|libWaterDropletEffect|libsecve[^]]*)\.so\]") {
    throw "App-owned Sparkling Bubbles unexpectedly depends on a legacy Samsung runtime"
}
$sparklingAppOwnedSymbols = (& $readelf -Ws $sparklingAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "App-owned Sparkling Bubbles dynamic symbol inspection failed"
}
$expectedSparklingAppOwnedExports = @(
    "Java_com_codex_lle_SparklingBubblesNative_nativeBridgeVersion",
    "Java_com_codex_lle_SparklingBubblesNative_nativeCreate",
    "Java_com_codex_lle_SparklingBubblesNative_nativeInitGpu",
    "Java_com_codex_lle_SparklingBubblesNative_nativeAbandonGpu",
    "Java_com_codex_lle_SparklingBubblesNative_nativeDestroy",
    "Java_com_codex_lle_SparklingBubblesNative_nativeUploadBitmap",
    "Java_com_codex_lle_SparklingBubblesNative_nativeClearBitmap",
    "Java_com_codex_lle_SparklingBubblesNative_nativeReset",
    "Java_com_codex_lle_SparklingBubblesNative_nativeTouch",
    "Java_com_codex_lle_SparklingBubblesNative_nativeAffordance",
    "Java_com_codex_lle_SparklingBubblesNative_nativeUnlock",
    "Java_com_codex_lle_SparklingBubblesNative_nativeSetAdaptivePhysics",
    "Java_com_codex_lle_SparklingBubblesNative_nativeStep",
    "Java_com_codex_lle_SparklingBubblesNative_nativeStepAdaptive",
    "Java_com_codex_lle_SparklingBubblesNative_nativeDraw",
    "Java_com_codex_lle_SparklingBubblesNative_nativeIsIdle",
    "Java_com_codex_lle_SparklingBubblesNative_nativeGetLastError"
)
foreach ($export in $expectedSparklingAppOwnedExports) {
    if ($sparklingAppOwnedSymbols -notmatch "\b$([regex]::Escape($export))\b") {
        throw "Missing app-owned Sparkling Bubbles JNI export: $export"
    }
}
$sparklingAppOwnedBuiltHash = (Get-FileHash `
        -LiteralPath $sparklingAppOwnedBuiltLibrary -Algorithm SHA256).Hash
Copy-Item -LiteralPath $sparklingAppOwnedBuiltLibrary `
        -Destination $sparklingAppOwnedLibrary -Force
$sparklingAppOwnedStageHash = (Get-FileHash `
        -LiteralPath $sparklingAppOwnedLibrary -Algorithm SHA256).Hash
if ($sparklingAppOwnedStageHash -ne $sparklingAppOwnedBuiltHash) {
    throw "App-owned Sparkling Bubbles staged library hash mismatch"
}
$colourDropletAppOwnedNative = Join-Path $root `
        "ports\colour-droplet-appowned\native"
$colourDropletAppOwnedBuiltLibrary = Join-Path $out `
        "liblleColourDroplet-built.so"
$colourDropletAppOwnedLibrary = Join-Path $arm64Stage `
        "liblleColourDroplet.so"
$colourDropletAppOwnedSources = @(
    "lle_colour_sim.c",
    "lle_colour_gles.c",
    "lle_colour_jni.c"
) | ForEach-Object {
    Join-Path $colourDropletAppOwnedNative $_
}
foreach ($colourDropletAppOwnedSource in $colourDropletAppOwnedSources) {
    if (-not (Test-Path -LiteralPath $colourDropletAppOwnedSource -PathType Leaf)) {
        throw "Missing app-owned Coloured Droplet native source: $colourDropletAppOwnedSource"
    }
}
$colourDropletAppOwnedClangArgs = @(
    "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
    "-fPIC", "-Wall", "-Wextra", "-Werror",
    "-shared", "-Wl,--no-undefined",
    "-Wl,-soname,liblleColourDroplet.so",
    "-I", $colourDropletAppOwnedNative
) + $colourDropletAppOwnedSources + @(
    "-Wl,--no-as-needed",
    "-landroid", "-ljnigraphics", "-lGLESv2", "-llog", "-lm",
    "-o", $colourDropletAppOwnedBuiltLibrary
)
Run $clang $colourDropletAppOwnedClangArgs
$colourDropletAppOwnedHeader = (& $readelf -h `
        $colourDropletAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $colourDropletAppOwnedHeader -notmatch "Class:\s+ELF64" `
        -or $colourDropletAppOwnedHeader -notmatch "Machine:\s+AArch64") {
    throw "App-owned Coloured Droplet library is not an ELF64 AArch64 binary"
}
$colourDropletAppOwnedDynamic = (& $readelf -d `
        $colourDropletAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $colourDropletAppOwnedDynamic -notmatch `
        "SONAME.*\[liblleColourDroplet\.so\]") {
    throw "App-owned Coloured Droplet library has an unexpected SONAME"
}
if ($colourDropletAppOwnedDynamic -match
        "NEEDED.*\[(libstlport|libstdc\+\+|libc\+\+|libColourDropletEffect|" +
        "libSparklingBubblesEffect|libWaterDropletEffect|libsecve[^]]*)\.so\]") {
    throw "App-owned Coloured Droplet unexpectedly depends on a legacy Samsung runtime"
}
$colourDropletAppOwnedSymbols = (& $readelf -Ws `
        $colourDropletAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "App-owned Coloured Droplet dynamic symbol inspection failed"
}
$expectedColourDropletAppOwnedExports = @(
    "Java_com_codex_lle_ColourDropletNative_nativeBridgeVersion",
    "Java_com_codex_lle_ColourDropletNative_nativeCreate",
    "Java_com_codex_lle_ColourDropletNative_nativeDestroy",
    "Java_com_codex_lle_ColourDropletNative_nativeInitGpu",
    "Java_com_codex_lle_ColourDropletNative_nativeResize",
    "Java_com_codex_lle_ColourDropletNative_nativeAbandonGpu",
    "Java_com_codex_lle_ColourDropletNative_nativeUploadBitmap",
    "Java_com_codex_lle_ColourDropletNative_nativeClearBitmap",
    "Java_com_codex_lle_ColourDropletNative_nativeReset",
    "Java_com_codex_lle_ColourDropletNative_nativeTouch",
    "Java_com_codex_lle_ColourDropletNative_nativeSensor",
    "Java_com_codex_lle_ColourDropletNative_nativeAffordance",
    "Java_com_codex_lle_ColourDropletNative_nativeUnlock",
    "Java_com_codex_lle_ColourDropletNative_nativeResetBackgroundScale",
    "Java_com_codex_lle_ColourDropletNative_nativeStep",
    "Java_com_codex_lle_ColourDropletNative_nativeStepAtRefresh",
    "Java_com_codex_lle_ColourDropletNative_nativeDraw",
    "Java_com_codex_lle_ColourDropletNative_nativeIsIdle",
    "Java_com_codex_lle_ColourDropletNative_nativeGetLastError"
)
foreach ($export in $expectedColourDropletAppOwnedExports) {
    if ($colourDropletAppOwnedSymbols -notmatch "\b$([regex]::Escape($export))\b") {
        throw "Missing app-owned Coloured Droplet JNI export: $export"
    }
}
$colourDropletAppOwnedBuiltHash = (Get-FileHash `
        -LiteralPath $colourDropletAppOwnedBuiltLibrary -Algorithm SHA256).Hash
Copy-Item -LiteralPath $colourDropletAppOwnedBuiltLibrary `
        -Destination $colourDropletAppOwnedLibrary -Force
$colourDropletAppOwnedStageHash = (Get-FileHash `
        -LiteralPath $colourDropletAppOwnedLibrary -Algorithm SHA256).Hash
if ($colourDropletAppOwnedStageHash -ne $colourDropletAppOwnedBuiltHash) {
    throw "App-owned Coloured Droplet staged library hash mismatch"
}
$s6WaterDropletAppOwnedNative = Join-Path $root `
        "ports\s6-water-droplet-appowned\native"
$s6WaterDropletAppOwnedBuiltLibrary = Join-Path $out `
        "liblleS6WaterDroplet-built.so"
$s6WaterDropletAppOwnedLibrary = Join-Path $arm64Stage `
        "liblleS6WaterDroplet.so"
$s6WaterDropletAppOwnedSources = @(
    "lle_s6_water_sim.c",
    "lle_s6_water_gles.c",
    "lle_s6_water_jni.c"
) | ForEach-Object {
    Join-Path $s6WaterDropletAppOwnedNative $_
}
foreach ($s6WaterDropletAppOwnedSource in $s6WaterDropletAppOwnedSources) {
    if (-not (Test-Path -LiteralPath $s6WaterDropletAppOwnedSource -PathType Leaf)) {
        throw "Missing app-owned S6 Water Droplet native source: $s6WaterDropletAppOwnedSource"
    }
}
$s6WaterDropletAppOwnedClangArgs = @(
    "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
    "-fPIC", "-pthread", "-Wall", "-Wextra", "-Werror",
    "-shared", "-Wl,--no-undefined",
    "-Wl,-soname,liblleS6WaterDroplet.so",
    "-I", $s6WaterDropletAppOwnedNative
) + $s6WaterDropletAppOwnedSources + @(
    "-Wl,--no-as-needed",
    "-landroid", "-ljnigraphics", "-lGLESv2", "-llog", "-lm",
    "-o", $s6WaterDropletAppOwnedBuiltLibrary
)
Run $clang $s6WaterDropletAppOwnedClangArgs
$s6WaterDropletAppOwnedHeader = (& $readelf -h `
        $s6WaterDropletAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $s6WaterDropletAppOwnedHeader -notmatch "Class:\s+ELF64" `
        -or $s6WaterDropletAppOwnedHeader -notmatch "Machine:\s+AArch64") {
    throw "App-owned S6 Water Droplet library is not an ELF64 AArch64 binary"
}
$s6WaterDropletAppOwnedDynamic = (& $readelf -d `
        $s6WaterDropletAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $s6WaterDropletAppOwnedDynamic -notmatch `
        "SONAME.*\[liblleS6WaterDroplet\.so\]") {
    throw "App-owned S6 Water Droplet library has an unexpected SONAME"
}
if ($s6WaterDropletAppOwnedDynamic -match
        "NEEDED.*\[(libstlport|libstdc\+\+|libc\+\+|libColourDropletEffect|" +
        "libSparklingBubblesEffect|libWaterDropletEffect|libsecve[^]]*)\.so\]") {
    throw "App-owned S6 Water Droplet unexpectedly depends on a legacy Samsung runtime"
}
$s6WaterDropletAppOwnedSymbols = (& $readelf -Ws `
        $s6WaterDropletAppOwnedBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "App-owned S6 Water Droplet dynamic symbol inspection failed"
}
$expectedS6WaterDropletAppOwnedExports = @(
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeBridgeVersion",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeCreate",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeInitGpu",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeResize",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeAbandonGpu",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeDestroy",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeUploadBitmap",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeClearBitmap",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeReset",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeTouch",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeTilt",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeAffordance",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeUnlock",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeResetBackgroundScale",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeStep",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeStepNativeRefresh",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeDraw",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeIsIdle",
    "Java_com_codex_lle_S6WaterDropletAppOwnedNative_nativeGetLastError"
)
foreach ($export in $expectedS6WaterDropletAppOwnedExports) {
    $definedExportPattern = "(?m)^\s*\d+:\s+\S+\s+\d+\s+FUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+" `
            + [regex]::Escape($export) + "\s*$"
    if ($s6WaterDropletAppOwnedSymbols -notmatch $definedExportPattern) {
        throw "Missing app-owned S6 Water Droplet JNI export: $export"
    }
}
$s6WaterDropletAppOwnedBuiltHash = (Get-FileHash `
        -LiteralPath $s6WaterDropletAppOwnedBuiltLibrary -Algorithm SHA256).Hash
Copy-Item -LiteralPath $s6WaterDropletAppOwnedBuiltLibrary `
        -Destination $s6WaterDropletAppOwnedLibrary -Force
$s6WaterDropletAppOwnedStageHash = (Get-FileHash `
        -LiteralPath $s6WaterDropletAppOwnedLibrary -Algorithm SHA256).Hash
if ($s6WaterDropletAppOwnedStageHash -ne $s6WaterDropletAppOwnedBuiltHash) {
    throw "App-owned S6 Water Droplet staged library hash mismatch"
}

# ENB4 Note 3 Ripple Ink's app-owned, velocity-only worker. It is staged into
# the APK and loaded lazily by the production Android Ripple Ink pipeline.
$n3RippleInkNative = Join-Path $root "ports\ripple-ink\n3-native"
$n3RippleInkBuiltLibrary = Join-Path $out "liblleN3RippleInk-built.so"
$n3RippleInkLibrary = Join-Path $arm64Stage "liblleN3RippleInk.so"
$n3RippleInkSources = @(
    "lle_n3_ink_worker.c",
    "lle_n3_ink_jni.c"
) | ForEach-Object {
    Join-Path $n3RippleInkNative $_
}
foreach ($n3RippleInkSource in $n3RippleInkSources) {
    if (-not (Test-Path -LiteralPath $n3RippleInkSource -PathType Leaf)) {
        throw "Missing N3 Ripple Ink native source: $n3RippleInkSource"
    }
}
$n3RippleInkClangArgs = @(
    "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
    "-fPIC", "-pthread", "-Wall", "-Wextra", "-Werror",
    "-shared", "-Wl,--no-undefined",
    "-Wl,-soname,liblleN3RippleInk.so",
    "-I", $n3RippleInkNative
) + $n3RippleInkSources + @(
    "-Wl,--no-as-needed", "-lm",
    "-o", $n3RippleInkBuiltLibrary
)
Run $clang $n3RippleInkClangArgs
$n3RippleInkHeader = (& $readelf -h $n3RippleInkBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $n3RippleInkHeader -notmatch "Class:\s+ELF64" `
        -or $n3RippleInkHeader -notmatch "Machine:\s+AArch64") {
    throw "N3 Ripple Ink worker is not an ELF64 AArch64 binary"
}
$n3RippleInkDynamic = (& $readelf -d $n3RippleInkBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $n3RippleInkDynamic -notmatch "SONAME.*\[liblleN3RippleInk\.so\]") {
    throw "N3 Ripple Ink worker has an unexpected SONAME"
}
if ($n3RippleInkDynamic -match
        "NEEDED.*\[(libstlport|libstdc\+\+|libc\+\+|libColourDropletEffect|" +
        "libSparklingBubblesEffect|libWaterDropletEffect|libsecve[^]]*)\.so\]") {
    throw "N3 Ripple Ink worker unexpectedly depends on a legacy Samsung runtime"
}
$n3RippleInkSymbols = (& $readelf -Ws $n3RippleInkBuiltLibrary) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "N3 Ripple Ink worker dynamic symbol inspection failed"
}
$expectedN3RippleInkExports = @(
    "Java_com_codex_lle_N3RippleInkWorkerNative_nativeBridgeVersion",
    "Java_com_codex_lle_N3RippleInkWorkerNative_nativeCreate",
    "Java_com_codex_lle_N3RippleInkWorkerNative_nativeReset",
    "Java_com_codex_lle_N3RippleInkWorkerNative_nativeStep",
    "Java_com_codex_lle_N3RippleInkWorkerNative_nativeDestroy"
)
foreach ($export in $expectedN3RippleInkExports) {
    $definedExportPattern = "(?m)^\s*\d+:\s+\S+\s+\d+\s+FUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+" `
            + [regex]::Escape($export) + "\s*$"
    if ($n3RippleInkSymbols -notmatch $definedExportPattern) {
        throw "Missing N3 Ripple Ink JNI export: $export"
    }
}
$n3RippleInkBuiltHash = (Get-FileHash -LiteralPath $n3RippleInkBuiltLibrary `
        -Algorithm SHA256).Hash
Copy-Item -LiteralPath $n3RippleInkBuiltLibrary -Destination $n3RippleInkLibrary -Force
$n3RippleInkStageHash = (Get-FileHash -LiteralPath $n3RippleInkLibrary `
        -Algorithm SHA256).Hash
if ($n3RippleInkStageHash -ne $n3RippleInkBuiltHash) {
    throw "N3 Ripple Ink worker staged library hash mismatch"
}
if ($LegacyVendorEffects) {
$s6WaterDropletSource = Join-Path $root `
        "ports\s6-water-droplet\integration\native\patched\libWaterDropletEffect.so"
$s6WaterDropletExpectedHash =
        "D14BB2253E9059B055582B195ED2D70ED4D516CCDABBDC403472EAD38D99C9BC"
if (-not (Test-Path -LiteralPath $s6WaterDropletSource)) {
    throw "Missing patched S6 Water Droplet ARM64 library: $s6WaterDropletSource"
}
$s6WaterDropletSourceHash = (Get-FileHash -LiteralPath $s6WaterDropletSource `
        -Algorithm SHA256).Hash
if ($s6WaterDropletSourceHash -ne $s6WaterDropletExpectedHash) {
    throw "Unexpected S6 Water Droplet SHA-256: $s6WaterDropletSourceHash"
}
$s6WaterDropletHeader = (& $readelf -h $s6WaterDropletSource) -join "`n"
if ($LASTEXITCODE -ne 0 -or $s6WaterDropletHeader -notmatch "Machine:\s+AArch64") {
    throw "S6 Water Droplet library is not an AArch64 ELF"
}
$s6WaterDropletDynamic = (& $readelf -d $s6WaterDropletSource) -join "`n"
if ($LASTEXITCODE -ne 0 `
        -or $s6WaterDropletDynamic -notmatch `
        "SONAME.*\[libWaterDropletEffect\.so\]" `
        -or $s6WaterDropletDynamic -match "NEEDED.*\[libstlport\.so\]") {
    throw "S6 Water Droplet native dependency contract changed"
}
$s6WaterDropletLibrary = Join-Path $arm64Stage "libWaterDropletEffect.so"
Copy-Item -LiteralPath $s6WaterDropletSource `
        -Destination $s6WaterDropletLibrary -Force
$s6WaterDropletStageHash = (Get-FileHash -LiteralPath $s6WaterDropletLibrary `
        -Algorithm SHA256).Hash
if ($s6WaterDropletStageHash -ne $s6WaterDropletExpectedHash) {
    throw "S6 Water Droplet staged library hash mismatch"
}
}
$rippleNative = Join-Path $root "ports\water-ripple\native"
$rippleLibrary = Join-Path $arm64Stage "libWaterRipple.so"
$rippleSources = @(
    "ripple_core.c",
    "water_ripple_jni_core.c",
    "ripple_gles_shaders.c",
    "ripple_gles_pipeline.c",
    "ripple_gles_overlay_shader.c",
    "ripple_gles_overlay.c",
    "water_ripple_jni_lifecycle.c"
) | ForEach-Object { Join-Path $rippleNative $_ }
foreach ($rippleSource in $rippleSources) {
    if (-not (Test-Path -LiteralPath $rippleSource)) {
        throw "Missing Water Ripple native source: $rippleSource"
    }
}
$rippleClangArgs = @(
    "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
    "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror",
    "-Wl,--no-undefined", "-Wl,-soname,libWaterRipple.so"
) + $rippleSources + @(
    "-lGLESv2", "-ljnigraphics", "-llog", "-lm",
    "-o", $rippleLibrary
)
Run $clang $rippleClangArgs
$rippleHeader = (& $readelf -h $rippleLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 -or $rippleHeader -notmatch "Machine:\s+AArch64") {
    throw "Water Ripple library is not an AArch64 ELF"
}
$rippleDynamic = (& $readelf -d $rippleLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 -or
        $rippleDynamic -notmatch "SONAME.*\[libWaterRipple\.so\]" -or
        $rippleDynamic -notmatch "NEEDED.*\[libGLESv2\.so\]" -or
        $rippleDynamic -notmatch "NEEDED.*\[libjnigraphics\.so\]" -or
        $rippleDynamic -notmatch "NEEDED.*\[liblog\.so\]") {
    throw "Water Ripple dynamic dependency verification failed"
}
$rippleSymbols = (& $readelf -Ws $rippleLibrary) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "Water Ripple dynamic symbol inspection failed"
}
$expectedRippleExports = @(
    "Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_initWaters",
    "Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_move",
    "Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_moveAdaptive",
    "Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_ripple",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeBridgeVersion",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeInitGpu",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeAbandonGpu",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeDestroyGpu",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeInitInk",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeResetInk",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeAdvanceInk",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeInjectInk",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeUploadBitmap",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeFreeTexture",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeRender",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeGetLastError"
)
foreach ($export in $expectedRippleExports) {
    if ($rippleSymbols -notmatch "\b$([regex]::Escape($export))\b") {
        throw "Missing Water Ripple JNI export: $export"
    }
}
$rippleStageHash = (Get-FileHash -LiteralPath $rippleLibrary -Algorithm SHA256).Hash
if ($rippleStageHash -notmatch "^[0-9A-F]{64}$") {
    throw "Water Ripple staged SHA-256 verification failed"
}

$abstractTilesNative = Join-Path $root "ports\abstract-tiles\native"
$abstractTilesLibrary = Join-Path $arm64Stage "libsecveAbstractTile.so"
$abstractTilesSources = @(
    "abstract_tiles_core.c",
    "abstract_tiles_gles.c",
    "abstract_tiles_jni.c"
) | ForEach-Object { Join-Path $abstractTilesNative $_ }
foreach ($abstractTilesSource in $abstractTilesSources) {
    if (-not (Test-Path -LiteralPath $abstractTilesSource)) {
        throw "Missing Abstract Tiles native source: $abstractTilesSource"
    }
}
$abstractTilesClangArgs = @(
    "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
    "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror",
    "-Wl,--no-undefined", "-Wl,-soname,libsecveAbstractTile.so"
) + $abstractTilesSources + @(
    "-Wl,--no-as-needed",
    "-lGLESv2", "-ljnigraphics", "-llog", "-lm",
    "-o", $abstractTilesLibrary
)
Run $clang $abstractTilesClangArgs
$abstractTilesHeader = (& $readelf -h $abstractTilesLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 -or $abstractTilesHeader -notmatch "Machine:\s+AArch64") {
    throw "Abstract Tiles library is not an AArch64 ELF"
}
$abstractTilesDynamic = (& $readelf -d $abstractTilesLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 -or
        $abstractTilesDynamic -notmatch "SONAME.*\[libsecveAbstractTile\.so\]" -or
        $abstractTilesDynamic -notmatch "NEEDED.*\[libGLESv2\.so\]" -or
        $abstractTilesDynamic -notmatch "NEEDED.*\[libjnigraphics\.so\]" -or
        $abstractTilesDynamic -notmatch "NEEDED.*\[liblog\.so\]" -or
        $abstractTilesDynamic -notmatch "NEEDED.*\[libm\.so\]") {
    throw "Abstract Tiles dynamic dependency verification failed"
}
if ($abstractTilesDynamic -match
        "NEEDED.*\[(libsecveSrkCommon|libstlport|libstdc\+\+|libsecveAbstractTile)\.so\]") {
    throw "Abstract Tiles unexpectedly depends on a Samsung legacy runtime"
}
$abstractTilesSymbols = (& $readelf -Ws $abstractTilesLibrary) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "Abstract Tiles dynamic symbol inspection failed"
}
$expectedAbstractTilesExports = @(
    "Java_com_codex_lle_AbstractTilesNative_nativeBridgeVersion",
    "Java_com_codex_lle_AbstractTilesNative_nativeInitGpu",
    "Java_com_codex_lle_AbstractTilesNative_nativeAbandonGpu",
    "Java_com_codex_lle_AbstractTilesNative_nativeDestroyGpu",
    "Java_com_codex_lle_AbstractTilesNative_nativeUploadBitmap",
    "Java_com_codex_lle_AbstractTilesNative_nativeClearBitmap",
    "Java_com_codex_lle_AbstractTilesNative_nativeReset",
    "Java_com_codex_lle_AbstractTilesNative_nativeTouch",
    "Java_com_codex_lle_AbstractTilesNative_nativeRealign",
    "Java_com_codex_lle_AbstractTilesNative_nativeAffordance",
    "Java_com_codex_lle_AbstractTilesNative_nativeUnlock",
    "Java_com_codex_lle_AbstractTilesNative_nativeStep",
    "Java_com_codex_lle_AbstractTilesNative_nativeDraw",
    "Java_com_codex_lle_AbstractTilesNative_nativeIsIdle",
    "Java_com_codex_lle_AbstractTilesNative_nativeGetLastError"
)
foreach ($export in $expectedAbstractTilesExports) {
    $definedExportPattern = "(?m)^\s*\d+:\s+\S+\s+\d+\s+FUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+" `
            + [regex]::Escape($export) + "\s*$"
    if ($abstractTilesSymbols -notmatch $definedExportPattern) {
        throw "Missing Abstract Tiles JNI export: $export"
    }
}
$abstractTilesStageHash = (Get-FileHash -LiteralPath $abstractTilesLibrary `
        -Algorithm SHA256).Hash
if ($abstractTilesStageHash -notmatch "^[0-9A-F]{64}$") {
    throw "Abstract Tiles staged SHA-256 verification failed"
}

$watercolorNative = Join-Path $root "ports\watercolor\native"
$watercolorCommonSource = Join-Path $watercolorNative "watercolor_arm64.c"
$watercolorRefreshSource = Join-Path $watercolorNative "watercolor_refresh.c"
$watercolorEffectSource = Join-Path $watercolorNative "watercolor_effect_stub.c"
$watercolorCommonLibrary = Join-Path $arm64Stage "libsecveSrkCommon.so"
$watercolorEffectLibrary = Join-Path $arm64Stage "libsecveWaterColor.so"
foreach ($watercolorSource in @(
        $watercolorCommonSource,
        $watercolorRefreshSource,
        $watercolorEffectSource)) {
    if (-not (Test-Path -LiteralPath $watercolorSource)) {
        throw "Missing Watercolor native source: $watercolorSource"
    }
}
$watercolorCommonArgs = @(
    "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
    "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror",
    "-Wl,--no-undefined", "-Wl,-soname,libsecveSrkCommon.so"
)
if ($WatercolorFeedbackMode -eq "StockFeedback") {
    $watercolorCommonArgs += "-DLLE_WATERCOLOR_STOCK_FEEDBACK=1"
}
$watercolorCommonArgs += @(
    $watercolorCommonSource,
    $watercolorRefreshSource,
    "-lGLESv2", "-llog", "-lm",
    "-o", $watercolorCommonLibrary
)
Run $clang $watercolorCommonArgs
$watercolorEffectArgs = @(
    "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
    "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror",
    "-Wl,--no-undefined", "-Wl,-soname,libsecveWaterColor.so",
    $watercolorEffectSource,
    "-o", $watercolorEffectLibrary
)
Run $clang $watercolorEffectArgs
foreach ($watercolorLibrary in @($watercolorCommonLibrary, $watercolorEffectLibrary)) {
    $watercolorHeader = (& $readelf -h $watercolorLibrary) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $watercolorHeader -notmatch "Machine:\s+AArch64") {
        throw "Watercolor library is not an AArch64 ELF: $watercolorLibrary"
    }
}
$watercolorCommonDynamic = (& $readelf -d $watercolorCommonLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 -or
        $watercolorCommonDynamic -notmatch "SONAME.*\[libsecveSrkCommon\.so\]" -or
        $watercolorCommonDynamic -notmatch "NEEDED.*\[libGLESv2\.so\]" -or
        $watercolorCommonDynamic -notmatch "NEEDED.*\[liblog\.so\]") {
    throw "Watercolor common bridge dynamic dependency verification failed"
}
$watercolorEffectDynamic = (& $readelf -d $watercolorEffectLibrary) -join "`n"
if ($LASTEXITCODE -ne 0 -or
        $watercolorEffectDynamic -notmatch "SONAME.*\[libsecveWaterColor\.so\]") {
    throw "Watercolor effect sentinel SONAME verification failed"
}
$watercolorCommonSymbols = (& $readelf -Ws $watercolorCommonLibrary) -join "`n"
$expectedWatercolorExports = @(
    "Java_com_samsung_android_visualeffect_lock_common_Native_loadEffect",
    "Java_com_samsung_android_visualeffect_lock_common_Native_loadTexture",
    "Java_com_samsung_android_visualeffect_lock_common_Native_init",
    "Java_com_samsung_android_visualeffect_lock_common_Native_draw",
    "Java_com_codex_lle_WatercolorArm64Native_drawAdaptive",
    "Java_com_samsung_android_visualeffect_lock_common_Native_onTouch",
    "Java_com_samsung_android_visualeffect_lock_common_Native_showUnlock",
    "Java_com_samsung_android_visualeffect_lock_common_Native_showAffordance",
    "Java_com_samsung_android_visualeffect_lock_common_Native_clear",
    "Java_com_samsung_android_visualeffect_lock_common_Native_destroy",
    "Java_com_samsung_android_visualeffect_lock_common_Native_setParameters",
    "Java_com_samsung_android_visualeffect_lock_common_Native_loadModel",
    "Java_com_samsung_android_visualeffect_lock_common_Native_pauseAnimation",
    "Java_com_samsung_android_visualeffect_lock_common_Native_resumeAnimation",
    "JNI_OnLoad"
)
foreach ($export in $expectedWatercolorExports) {
    if ($watercolorCommonSymbols -notmatch "\b$([regex]::Escape($export))\b") {
        throw "Missing Watercolor JNI export: $export"
    }
}
$watercolorEffectSymbols = (& $readelf -Ws $watercolorEffectLibrary) -join "`n"
foreach ($export in @("createScene", "lle64_watercolor_effect_bridge_version")) {
    if ($watercolorEffectSymbols -notmatch "\b$([regex]::Escape($export))\b") {
        throw "Missing Watercolor effect sentinel export: $export"
    }
}
$watercolorCommonStageHash = (Get-FileHash -LiteralPath $watercolorCommonLibrary `
        -Algorithm SHA256).Hash
$watercolorEffectStageHash = (Get-FileHash -LiteralPath $watercolorEffectLibrary `
        -Algorithm SHA256).Hash
Run "jar.exe" @("uf", $assembled, "-C", $nativeStage, "lib")

if ($ReleaseSigning) {
    if ([string]::IsNullOrWhiteSpace($ReleaseKeystorePath)) {
        $ReleaseKeystorePath = $env:LLE_RELEASE_KEYSTORE
    }
    if ([string]::IsNullOrWhiteSpace($ReleaseKeystorePath) -or
            -not (Test-Path -LiteralPath $ReleaseKeystorePath)) {
        throw "Missing stable release keystore. Pass -ReleaseKeystorePath or set LLE_RELEASE_KEYSTORE."
    }
    if ([string]::IsNullOrWhiteSpace($env:LLE_RELEASE_KEY_PASSWORD)) {
        throw "Missing LLE_RELEASE_KEY_PASSWORD for stable signing."
    }
    if ([string]::IsNullOrWhiteSpace($ReleaseLineagePath) -or
            -not (Test-Path -LiteralPath $ReleaseLineagePath)) {
        throw "Missing signing lineage for stable signing."
    }
    if ([string]::IsNullOrWhiteSpace($ReleaseOldKeystorePath) -or
            -not (Test-Path -LiteralPath $ReleaseOldKeystorePath)) {
        throw "Missing previous signing keystore for stable signing."
    }
} else {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystore) | Out-Null
    if (-not (Test-Path $keystore)) {
        if (-not (Test-Path $sourceKeystore)) {
            throw "Missing compatible debug keystore: $sourceKeystore"
        }
        Copy-Item $sourceKeystore $keystore -Force
    }
}

Run (Join-Path $buildTools "zipalign.exe") @("-f", "4", $assembled, $zipaligned)
$signingArguments = if ($ReleaseSigning) {
    @("sign",
        "--ks", $ReleaseOldKeystorePath,
        "--ks-key-alias", $ReleaseOldKeyAlias,
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--next-signer",
        "--ks", $ReleaseKeystorePath,
        "--ks-key-alias", $ReleaseKeyAlias,
        "--ks-pass", "env:LLE_RELEASE_KEY_PASSWORD",
        "--key-pass", "env:LLE_RELEASE_KEY_PASSWORD",
        "--lineage", $ReleaseLineagePath,
        "--rotation-min-sdk-version", "33",
        "--out", $signed,
        $zipaligned)
} else {
    @("sign", "--ks", $keystore,
        "--ks-pass", "pass:android", "--key-pass", "pass:android",
        "--out", $signed,
        $zipaligned)
}
Run (Join-Path $buildTools "apksigner.bat") $signingArguments
Run (Join-Path $buildTools "apksigner.bat") @("verify", "--verbose", $signed)
if ($ReleaseSigning) {
    $certificateInfo = (& (Join-Path $buildTools "apksigner.bat") verify `
            --print-certs $signed) -join "`n"
    if ($LASTEXITCODE -ne 0 -or
            $certificateInfo -notmatch "(?i)certificate SHA-256 digest:\s*$releaseCertificateSha256") {
        throw "ARM64 stable certificate verification failed"
    }
}

$badging = (& (Join-Path $buildTools "aapt.exe") dump badging $signed) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "APK badging inspection failed"
}
$expectedPackage = $applicationId
if ($badging -notmatch "package: name='$([regex]::Escape($expectedPackage))'") {
    throw "Unexpected APK package; expected $expectedPackage"
}
if ($badging -notmatch "application-label:'$([regex]::Escape($launcherLabel))'") {
    throw "ARM64 launcher label verification failed"
}
if (($ValidationVersionCode -gt 0 -or $testerVersionCode -gt 0) -and
        $badging -notmatch
        "versionCode='$stagedVersionCode'.*versionName='$([regex]::Escape($expectedVersionName))'") {
    throw "Staged APK version metadata verification failed"
}

$entries = @(& "jar.exe" tf $signed)
$dexEntries = @($entries | Where-Object { $_ -match "^classes[0-9]*\.dex$" })
if ($dexEntries.Count -ne 1 -or $dexEntries[0] -ne "classes.dex") {
    throw "ARM64 APK must contain exactly one app-owned DEX: $($dexEntries -join ', ')"
}
$nativeEntries = @($entries | Where-Object { $_ -like "lib/*" -and -not $_.EndsWith("/") })
$expectedNativeEntries = @(
    "lib/arm64-v8a/liblle64marker.so",
    "lib/arm64-v8a/liblleSparklingBubbles.so",
    "lib/arm64-v8a/liblleColourDroplet.so",
    "lib/arm64-v8a/liblleS6WaterDroplet.so",
    "lib/arm64-v8a/liblleN3RippleInk.so",
    "lib/arm64-v8a/libWaterRipple.so",
    "lib/arm64-v8a/libsecveAbstractTile.so",
    "lib/arm64-v8a/libsecveSrkCommon.so",
    "lib/arm64-v8a/libsecveWaterColor.so"
)
if ($LegacyVendorEffects) {
    $expectedNativeEntries += @(
        "lib/arm64-v8a/libColourDropletEffect.so",
        "lib/arm64-v8a/libSparklingBubblesEffect.so",
        "lib/arm64-v8a/libWaterDropletEffect.so",
        "lib/arm64-v8a/libstlport.so"
    )
}
$nativeDiff = Compare-Object ($nativeEntries | Sort-Object) ($expectedNativeEntries | Sort-Object)
if ($nativeDiff) {
    throw "Unexpected APK native entries: $($nativeEntries -join ', ')"
}
if ($entries -match "armeabi|x86") {
    throw "Non-ARM64 ABI found in APK"
}
$forbiddenVendorEntries = @(
    "lib/arm64-v8a/libColourDropletEffect.so",
    "lib/arm64-v8a/libSparklingBubblesEffect.so",
    "lib/arm64-v8a/libWaterDropletEffect.so",
    "lib/arm64-v8a/libstlport.so"
)
$packagedVendorEntries = @($nativeEntries | Where-Object {
    $forbiddenVendorEntries -contains $_
})
if (-not $LegacyVendorEffects -and $packagedVendorEntries.Count -ne 0) {
    throw ("Samsung-free APK contains vendor ELF entries: " +
            ($packagedVendorEntries -join ", "))
}
if ($LegacyVendorEffects -and $packagedVendorEntries.Count -ne
        $forbiddenVendorEntries.Count) {
    throw "Legacy-vendor APK is missing expected vendor ELF entries"
}
$rippleApkVerify = Join-Path $out "verify-ripple-entry"
New-Item -ItemType Directory -Force -Path $rippleApkVerify | Out-Null
Push-Location $rippleApkVerify
try {
    Run "jar.exe" @("xf", $signed, "lib/arm64-v8a/libWaterRipple.so")
} finally {
    Pop-Location
}
$rippleApkLibrary = Join-Path $rippleApkVerify "lib\arm64-v8a\libWaterRipple.so"
if (-not (Test-Path -LiteralPath $rippleApkLibrary)) {
    throw "Water Ripple APK entry is missing"
}
$rippleApkHash = (Get-FileHash -LiteralPath $rippleApkLibrary -Algorithm SHA256).Hash
if ($rippleApkHash -ne $rippleStageHash) {
    throw "Water Ripple APK entry hash mismatch"
}
Remove-Item -Recurse -Force $rippleApkVerify

$abstractTilesApkVerify = Join-Path $out "verify-abstract-tiles-entry"
New-Item -ItemType Directory -Force -Path $abstractTilesApkVerify | Out-Null
Push-Location $abstractTilesApkVerify
try {
    Run "jar.exe" @("xf", $signed, "lib/arm64-v8a/libsecveAbstractTile.so")
} finally {
    Pop-Location
}
$abstractTilesApkLibrary = Join-Path $abstractTilesApkVerify `
        "lib\arm64-v8a\libsecveAbstractTile.so"
if (-not (Test-Path -LiteralPath $abstractTilesApkLibrary)) {
    throw "Abstract Tiles APK entry is missing"
}
$abstractTilesApkHash = (Get-FileHash -LiteralPath $abstractTilesApkLibrary `
        -Algorithm SHA256).Hash
if ($abstractTilesApkHash -ne $abstractTilesStageHash) {
    throw "Abstract Tiles APK entry hash mismatch"
}
Remove-Item -Recurse -Force $abstractTilesApkVerify

if ($LegacyVendorEffects) {
$s6WaterDropletApkVerify = Join-Path $out "verify-s6-water-droplet-entry"
New-Item -ItemType Directory -Force -Path $s6WaterDropletApkVerify | Out-Null
Push-Location $s6WaterDropletApkVerify
try {
    Run "jar.exe" @("xf", $signed,
            "lib/arm64-v8a/libWaterDropletEffect.so")
} finally {
    Pop-Location
}
$s6WaterDropletApkLibrary = Join-Path $s6WaterDropletApkVerify `
        "lib\arm64-v8a\libWaterDropletEffect.so"
if (-not (Test-Path -LiteralPath $s6WaterDropletApkLibrary)) {
    throw "S6 Water Droplet APK entry is missing"
}
$s6WaterDropletApkHash = (Get-FileHash `
        -LiteralPath $s6WaterDropletApkLibrary -Algorithm SHA256).Hash
if ($s6WaterDropletApkHash -ne $s6WaterDropletStageHash) {
    throw "S6 Water Droplet APK entry hash mismatch"
}
Remove-Item -Recurse -Force $s6WaterDropletApkVerify
}

$sparklingAppOwnedApkVerify = Join-Path $out "verify-app-owned-sparkling-entry"
New-Item -ItemType Directory -Force -Path $sparklingAppOwnedApkVerify | Out-Null
Push-Location $sparklingAppOwnedApkVerify
try {
    Run "jar.exe" @("xf", $signed,
            "lib/arm64-v8a/liblleSparklingBubbles.so")
} finally {
    Pop-Location
}
$sparklingAppOwnedApkLibrary = Join-Path $sparklingAppOwnedApkVerify `
        "lib\arm64-v8a\liblleSparklingBubbles.so"
if (-not (Test-Path -LiteralPath $sparklingAppOwnedApkLibrary)) {
    throw "App-owned Sparkling Bubbles APK entry is missing"
}
$sparklingAppOwnedApkHash = (Get-FileHash `
        -LiteralPath $sparklingAppOwnedApkLibrary -Algorithm SHA256).Hash
if ($sparklingAppOwnedApkHash -ne $sparklingAppOwnedStageHash) {
    throw "App-owned Sparkling Bubbles APK entry hash mismatch"
}
Remove-Item -Recurse -Force $sparklingAppOwnedApkVerify

$colourDropletAppOwnedApkVerify = Join-Path $out `
        "verify-app-owned-colour-droplet-entry"
New-Item -ItemType Directory -Force `
        -Path $colourDropletAppOwnedApkVerify | Out-Null
Push-Location $colourDropletAppOwnedApkVerify
try {
    Run "jar.exe" @("xf", $signed,
            "lib/arm64-v8a/liblleColourDroplet.so")
} finally {
    Pop-Location
}
$colourDropletAppOwnedApkLibrary = Join-Path `
        $colourDropletAppOwnedApkVerify `
        "lib\arm64-v8a\liblleColourDroplet.so"
if (-not (Test-Path -LiteralPath $colourDropletAppOwnedApkLibrary)) {
    throw "App-owned Coloured Droplet APK entry is missing"
}
$colourDropletAppOwnedApkHash = (Get-FileHash `
        -LiteralPath $colourDropletAppOwnedApkLibrary -Algorithm SHA256).Hash
if ($colourDropletAppOwnedApkHash -ne $colourDropletAppOwnedStageHash) {
    throw "App-owned Coloured Droplet APK entry hash mismatch"
}
Remove-Item -Recurse -Force $colourDropletAppOwnedApkVerify

$s6WaterDropletAppOwnedApkVerify = Join-Path $out `
        "verify-app-owned-s6-water-droplet-entry"
New-Item -ItemType Directory -Force `
        -Path $s6WaterDropletAppOwnedApkVerify | Out-Null
Push-Location $s6WaterDropletAppOwnedApkVerify
try {
    Run "jar.exe" @("xf", $signed,
            "lib/arm64-v8a/liblleS6WaterDroplet.so")
} finally {
    Pop-Location
}
$s6WaterDropletAppOwnedApkLibrary = Join-Path `
        $s6WaterDropletAppOwnedApkVerify `
        "lib\arm64-v8a\liblleS6WaterDroplet.so"
if (-not (Test-Path -LiteralPath $s6WaterDropletAppOwnedApkLibrary)) {
    throw "App-owned S6 Water Droplet APK entry is missing"
}
$s6WaterDropletAppOwnedApkHash = (Get-FileHash `
        -LiteralPath $s6WaterDropletAppOwnedApkLibrary -Algorithm SHA256).Hash
if ($s6WaterDropletAppOwnedApkHash -ne $s6WaterDropletAppOwnedStageHash) {
    throw "App-owned S6 Water Droplet APK entry hash mismatch"
}
Remove-Item -Recurse -Force $s6WaterDropletAppOwnedApkVerify

$n3RippleInkApkVerify = Join-Path $out "verify-n3-ripple-ink-entry"
New-Item -ItemType Directory -Force -Path $n3RippleInkApkVerify | Out-Null
Push-Location $n3RippleInkApkVerify
try {
    Run "jar.exe" @("xf", $signed,
            "lib/arm64-v8a/liblleN3RippleInk.so")
} finally {
    Pop-Location
}
$n3RippleInkApkLibrary = Join-Path $n3RippleInkApkVerify `
        "lib\arm64-v8a\liblleN3RippleInk.so"
if (-not (Test-Path -LiteralPath $n3RippleInkApkLibrary)) {
    throw "N3 Ripple Ink worker APK entry is missing"
}
$n3RippleInkApkHash = (Get-FileHash -LiteralPath $n3RippleInkApkLibrary `
        -Algorithm SHA256).Hash
if ($n3RippleInkApkHash -ne $n3RippleInkStageHash) {
    throw "N3 Ripple Ink worker APK entry hash mismatch"
}
Remove-Item -Recurse -Force $n3RippleInkApkVerify

$canonicalManifestHashAfter = (Get-FileHash -LiteralPath $canonicalManifest `
        -Algorithm SHA256).Hash
if ($canonicalManifestHashAfter -ne $canonicalManifestHashBefore) {
    throw "Canonical AndroidManifest.xml changed during the ARM64 build"
}

Write-Host "Built ARM64-only APK: $signed"
Write-Host "Application ID: $expectedPackage"
Write-Host "Build flavor: $buildFlavorName"
if ($ValidationVersionCode -gt 0) {
    Write-Host "Validation-only versionCode override: $ValidationVersionCode"
    Write-Host "Canonical manifest SHA-256: $canonicalManifestHashAfter"
} elseif ($testerVersionCode -gt 0) {
    Write-Host "Tester-only version override: $testerVersionName (versionCode $testerVersionCode)"
    Write-Host "Canonical manifest SHA-256: $canonicalManifestHashAfter"
}
if ($LegacyVendorEffects) {
    Write-Warning "Legacy diagnostic APK contains proprietary Samsung Note 5 and S6 firmware libraries."
} else {
    Write-Host "Samsung-free APK: legacy vendor ELF set excluded"
}
if ($IncludeRippleCoreProbe) {
    Write-Warning "Ripple probe filename selected; it contains the same full Early Alpha Water Ripple library as the normal APK."
}
Write-Host "Native entries: $($nativeEntries -join ', ')"
Write-Host "App-owned Sparkling Bubbles SHA-256: $sparklingAppOwnedStageHash"
Write-Host "App-owned Coloured Droplet SHA-256: $colourDropletAppOwnedStageHash"
Write-Host "App-owned S6 Water Droplet SHA-256: $s6WaterDropletAppOwnedStageHash"
Write-Host "N3 Ripple Ink worker SHA-256: $n3RippleInkStageHash"
if ($LegacyVendorEffects) {
    Write-Host "S6 Water Droplet SHA-256: $s6WaterDropletStageHash"
}
Write-Host "Water Ripple SHA-256: $rippleStageHash"
Write-Host "Abstract Tiles SHA-256: $abstractTilesStageHash"
Write-Host "Watercolor common SHA-256: $watercolorCommonStageHash"
Write-Host "Watercolor effect sentinel SHA-256: $watercolorEffectStageHash"
