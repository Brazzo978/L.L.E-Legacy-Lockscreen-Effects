param(
    [switch] $IncludeNote5Probe,
    [switch] $IncludeRippleCoreProbe
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

if ($IncludeNote5Probe) {
    $probeRoot = Join-Path $root "tools\dlopen-probe"
    $candidateRoot = Join-Path $root "reference\arm64-candidates\note5-aoj4"
    Run $clang @(
        "-std=c11", "-O2", "-fPIC", "-shared", "-Wall", "-Wextra", "-Werror",
        "-Wl,-soname,libstlport.so",
        (Join-Path $probeRoot "libstlport_probe_shim.c"),
        "-o", (Join-Path $arm64Stage "libstlport.so")
    )
    foreach ($library in @("libColourDropletEffect.so", "libSparklingBubblesEffect.so")) {
        $candidate = Join-Path $candidateRoot $library
        if (-not (Test-Path $candidate)) {
            throw "Missing Note 5 ARM64 candidate: $candidate"
        }
        Copy-Item $candidate (Join-Path $arm64Stage $library) -Force
    }
}
if ($IncludeRippleCoreProbe) {
    $rippleNative = Join-Path $root "ports\water-ripple\native"
    Run $clang @(
        "-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off",
        "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror",
        "-Wl,-soname,libWaterRipple.so",
        (Join-Path $rippleNative "ripple_core.c"),
        (Join-Path $rippleNative "water_ripple_jni_core.c"),
        "-lm", "-o", (Join-Path $arm64Stage "libWaterRipple.so")
    )
}
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
$expectedNativeEntries = @("lib/arm64-v8a/liblle64marker.so")
if ($IncludeNote5Probe) {
    $expectedNativeEntries += @(
        "lib/arm64-v8a/libColourDropletEffect.so",
        "lib/arm64-v8a/libSparklingBubblesEffect.so",
        "lib/arm64-v8a/libstlport.so"
    )
}
if ($IncludeRippleCoreProbe) {
    $expectedNativeEntries += "lib/arm64-v8a/libWaterRipple.so"
}
$nativeDiff = Compare-Object ($nativeEntries | Sort-Object) ($expectedNativeEntries | Sort-Object)
if ($nativeDiff) {
    throw "Unexpected APK native entries: $($nativeEntries -join ', ')"
}
if ($entries -match "armeabi|x86") {
    throw "Non-ARM64 ABI found in APK"
}

Write-Host "Built ARM64-only APK: $signed"
if ($IncludeNote5Probe) {
    Write-Warning "Probe build contains an ABI-incomplete STLport shim; do not distribute."
}
if ($IncludeRippleCoreProbe) {
    Write-Warning "Probe build contains only the Water Ripple core JNI subset; GPU methods are intentionally absent."
}
Write-Host "Native entries: $($nativeEntries -join ', ')"
