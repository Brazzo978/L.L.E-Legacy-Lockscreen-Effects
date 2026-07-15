param(
    [switch] $IncludeNote5Probe,
    [switch] $IncludeRippleCoreProbe,
    [ValidateSet("Stable", "StockFeedback")]
    [string] $WatercolorFeedbackMode = "Stable"
)

$ErrorActionPreference = "Stop"
if ($IncludeNote5Probe -and $IncludeRippleCoreProbe) {
    throw "Choose only one native probe build"
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$ndk = if ($env:ANDROID_NDK_HOME) {
    $env:ANDROID_NDK_HOME
} else {
    Join-Path $root "..\unlock-effects-test\tools\android-ndk-r27d"
}
$buildTools = Join-Path $sdk "build-tools\35.0.1"
$platform = Join-Path $sdk "platforms\android-35\android.jar"
$clang = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android23-clang.cmd"
$readelf = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
$objdump = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objdump.exe"
$strings = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strings.exe"

$out = Join-Path $root "build"
$classes = Join-Path $out "classes"
$dex = Join-Path $out "dex"
$resStage = Join-Path $out "res"
$resZip = Join-Path $out "res.zip"
$unsigned = Join-Path $out "LLE64-unsigned.apk"
$assembled = Join-Path $out "LLE64-assembled.apk"
$zipaligned = Join-Path $out "LLE64-zipaligned.apk"
$signed = Join-Path $out $(if ($IncludeNote5Probe) {
    "LLE64-note5-probe.apk"
} elseif ($IncludeRippleCoreProbe) {
    "LLE64-ripple-core-probe.apk"
} elseif ($WatercolorFeedbackMode -eq "StockFeedback") {
    "LLE64-watercolor-stock-feedback.apk"
} else {
    "LLE64-debug.apk"
})
$classesJar = Join-Path $out "classes.jar"
$nativeStage = Join-Path $out "native"
$arm64Stage = Join-Path $nativeStage "lib\arm64-v8a"
$marker = Join-Path $arm64Stage "liblle64marker.so"
$keystore = Join-Path $root ".keys\debug.keystore"
$sourceKeystore = Join-Path $root "..\unlock-effects-test\demo-apk\debug.keystore"
$manifest = Join-Path $root "AndroidManifest.xml"

function Run($exe, $arguments) {
    if (-not (Test-Path -LiteralPath $exe) -and -not (Get-Command $exe -ErrorAction SilentlyContinue)) {
        throw "Missing build tool: $exe"
    }
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$exe failed with exit code $LASTEXITCODE"
    }
}

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, $classes, $dex, $resStage, $arm64Stage | Out-Null

Copy-Item -Path (Join-Path $root "res\*") -Destination $resStage -Recurse -Force
Run (Join-Path $buildTools "aapt2.exe") @("compile", "--dir", $resStage, "-o", $resZip)
if ($IncludeNote5Probe -or $IncludeRippleCoreProbe) {
    $probeManifest = Join-Path $out "AndroidManifest.xml"
    $manifestText = [System.IO.File]::ReadAllText($manifest)
    $probePattern = '(?s)(android:name="\.Note5NativeProbeActivity".*?android:exported=")false(")'
    $probeManifestText = [regex]::Replace($manifestText, $probePattern, '${1}true$2')
    if ($probeManifestText -eq $manifestText) {
        throw "Note5NativeProbeActivity manifest patch point not found"
    }
    [System.IO.File]::WriteAllText($probeManifest, $probeManifestText)
    $manifest = $probeManifest
}
$linkArgs = @(
    "link", "-o", $unsigned,
    "-I", $platform,
    "--manifest", $manifest,
    $resZip,
    "--java", (Join-Path $out "gen"),
    "--auto-add-overlay"
)
if (Test-Path (Join-Path $root "assets")) {
    $linkArgs += @("-A", (Join-Path $root "assets"))
}
Run (Join-Path $buildTools "aapt2.exe") $linkArgs

$sources = @()
$sources += Get-ChildItem (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object FullName
$sources += Get-ChildItem (Join-Path $out "gen") -Recurse -Filter *.java | ForEach-Object FullName
$javacArgs = @(
    "-encoding", "UTF-8",
    "-source", "1.8",
    "-target", "1.8",
    "-bootclasspath", $platform,
    "-d", $classes
) + $sources
Run "javac.exe" $javacArgs
Run "jar.exe" @("cf", $classesJar, "-C", $classes, ".")
Run (Join-Path $buildTools "d8.bat") @("--lib", $platform, "--min-api", "23", "--output", $dex, $classesJar)

Copy-Item $unsigned $assembled
Run "jar.exe" @("uf", $assembled, "-C", $dex, "classes.dex")
$samsungDex = Join-Path $root "vendor\secvisualeffect\classes.dex"
$boundedSamsungDex = Join-Path $out "classes-note5-bounded.dex"
& (Join-Path $root "vendor\secvisualeffect\patch-note5-lifecycle.ps1") `
    -OutputPath $boundedSamsungDex
$samsungDex = $boundedSamsungDex
if (-not (Test-Path $samsungDex)) {
    throw "Missing Samsung visual-effect dex: $samsungDex"
}
Copy-Item $samsungDex (Join-Path $out "classes2.dex") -Force
Run "jar.exe" @("uf", $assembled, "-C", $out, "classes2.dex")

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
    "Java_com_android_internal_policy_impl_keyguard_sec_JniWaterRippleRender_ripple",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeBridgeVersion",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeInitGpu",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeAbandonGpu",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeDestroyGpu",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeUploadBitmap",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeFreeTexture",
    "Java_com_codex_lle_S3RippleLifecycleNative_nativeRenderNormal",
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

$watercolorNative = Join-Path $root "ports\watercolor\native"
$watercolorCommonSource = Join-Path $watercolorNative "watercolor_arm64.c"
$watercolorEffectSource = Join-Path $watercolorNative "watercolor_effect_stub.c"
$watercolorCommonLibrary = Join-Path $arm64Stage "libsecveSrkCommon.so"
$watercolorEffectLibrary = Join-Path $arm64Stage "libsecveWaterColor.so"
foreach ($watercolorSource in @($watercolorCommonSource, $watercolorEffectSource)) {
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

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystore) | Out-Null
if (-not (Test-Path $keystore)) {
    if (-not (Test-Path $sourceKeystore)) {
        throw "Missing compatible debug keystore: $sourceKeystore"
    }
    Copy-Item $sourceKeystore $keystore -Force
}

Run (Join-Path $buildTools "zipalign.exe") @("-f", "4", $assembled, $zipaligned)
Run (Join-Path $buildTools "apksigner.bat") @(
    "sign",
    "--ks", $keystore,
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    "--out", $signed,
    $zipaligned
)
Run (Join-Path $buildTools "apksigner.bat") @("verify", "--verbose", $signed)

$entries = @(& "jar.exe" tf $signed)
$nativeEntries = @($entries | Where-Object { $_ -like "lib/*" -and -not $_.EndsWith("/") })
$expectedNativeEntries = @(
    "lib/arm64-v8a/liblle64marker.so",
    "lib/arm64-v8a/libColourDropletEffect.so",
    "lib/arm64-v8a/libSparklingBubblesEffect.so",
    "lib/arm64-v8a/libstlport.so",
    "lib/arm64-v8a/libWaterRipple.so",
    "lib/arm64-v8a/libsecveSrkCommon.so",
    "lib/arm64-v8a/libsecveWaterColor.so"
)
$nativeDiff = Compare-Object ($nativeEntries | Sort-Object) ($expectedNativeEntries | Sort-Object)
if ($nativeDiff) {
    throw "Unexpected APK native entries: $($nativeEntries -join ', ')"
}
if ($entries -match "armeabi|x86") {
    throw "Non-ARM64 ABI found in APK"
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

Write-Host "Built ARM64-only APK: $signed"
Write-Warning "APK contains proprietary Samsung Note 5 firmware libraries."
if ($IncludeRippleCoreProbe) {
    Write-Warning "Ripple probe filename selected; it contains the same full Early Alpha Water Ripple library as the normal APK."
}
Write-Host "Native entries: $($nativeEntries -join ', ')"
Write-Host "Water Ripple SHA-256: $rippleStageHash"
Write-Host "Watercolor common SHA-256: $watercolorCommonStageHash"
Write-Host "Watercolor effect sentinel SHA-256: $watercolorEffectStageHash"
