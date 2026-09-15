param(
    [switch] $IncludeNote5Probe,
    [switch] $IncludeRippleCoreProbe,
    [ValidateSet("Stable", "StockFeedback")]
    [string] $WatercolorFeedbackMode = "Stable",
    [switch] $Clean
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
    $ndkCandidate1 = Join-Path $root "..\unlock-effects-test\tools\android-ndk-r27d"
    if (Test-Path $ndkCandidate1) {
        $ndkCandidate1
    } else {
        $ndkDirs = Get-ChildItem (Join-Path $sdk "ndk") -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
        if ($ndkDirs) { $ndkDirs[-1] } else { throw "No NDK found in $sdk\ndk" }
    }
}
$buildTools = Join-Path $sdk "build-tools\35.0.1"
$platform = Join-Path $sdk "platforms\android-35\android.jar"
$clang = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android23-clang.exe"
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

function Get-LatestWriteTime([string[]] $paths) {
    $latest = [DateTime]::MinValue
    foreach ($p in $paths) {
        if (-not $p -or -not (Test-Path -LiteralPath $p)) { continue }
        $item = Get-Item -LiteralPath $p
        if ($item.PSIsContainer) {
            $maxChild = Get-ChildItem -LiteralPath $p -Recurse -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
            if ($maxChild -and $maxChild.LastWriteTimeUtc -gt $latest) {
                $latest = $maxChild.LastWriteTimeUtc
            }
        } else {
            if ($item.LastWriteTimeUtc -gt $latest) {
                $latest = $item.LastWriteTimeUtc
            }
        }
    }
    return $latest
}

function Is-UpToDate([string] $target, [string[]] $inputs) {
    if (-not (Test-Path -LiteralPath $target)) { return $false }
    $targetTime = (Get-Item -LiteralPath $target).LastWriteTimeUtc
    $latestInputTime = Get-LatestWriteTime $inputs
    return ($targetTime -ge $latestInputTime)
}

function Run($exe, $arguments) {
    if (-not (Test-Path -LiteralPath $exe) -and -not (Get-Command $exe -ErrorAction SilentlyContinue)) {
        throw "Missing build tool: $exe"
    }
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$exe failed with exit code $LASTEXITCODE"
    }
}

if ($Clean) {
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $out, $classes, $dex, $resStage, $arm64Stage | Out-Null

# Short-circuit if the final signed APK is completely up to date
$allSourceInputs = @(
    (Join-Path $root "src"),
    (Join-Path $root "res"),
    (Join-Path $root "native"),
    (Join-Path $root "ports"),
    (Join-Path $root "vendor"),
    $manifest
)
if (Test-Path (Join-Path $root "assets")) {
    $allSourceInputs += (Join-Path $root "assets")
}

if (-not $Clean -and (Is-UpToDate $signed $allSourceInputs)) {
    Write-Host "[UP-TO-DATE] $signed is up to date." -ForegroundColor Green
    exit 0
}

# 1. AAPT2 Compile & Link
$resInputs = @((Join-Path $root "res"), $manifest)
if (Test-Path (Join-Path $root "assets")) { $resInputs += (Join-Path $root "assets") }

if ($Clean -or -not (Is-UpToDate $unsigned $resInputs)) {
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
}

# 2. Java Compilation & DEX
$srcInputs = @((Join-Path $root "src"))
if (Test-Path (Join-Path $out "gen")) { $srcInputs += (Join-Path $out "gen") }
$dexOutput = Join-Path $dex "classes.dex"

if ($Clean -or -not (Is-UpToDate $dexOutput $srcInputs)) {
    $sources = @()
    $sources += Get-ChildItem (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object FullName
    if (Test-Path (Join-Path $out "gen")) {
        $sources += Get-ChildItem (Join-Path $out "gen") -Recurse -Filter *.java | ForEach-Object FullName
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
    Run (Join-Path $buildTools "d8.bat") @("--lib", $platform, "--min-api", "23", "--output", $dex, $classesJar)
}

# 3. Assembled APK & Samsung Lifecycle DEX
Copy-Item $unsigned $assembled -Force
Run "jar.exe" @("uf", $assembled, "-C", $dex, "classes.dex")

$samsungDexOriginal = Join-Path $root "vendor\secvisualeffect\classes.dex"
$boundedSamsungDex = Join-Path $out "classes-note5-bounded.dex"
$patchScript = Join-Path $root "vendor\secvisualeffect\patch-note5-lifecycle.ps1"

if ($Clean -or -not (Is-UpToDate $boundedSamsungDex @($samsungDexOriginal, $patchScript))) {
    & $patchScript -OutputPath $boundedSamsungDex
}
if (-not (Test-Path $boundedSamsungDex)) {
    throw "Missing Samsung visual-effect dex: $boundedSamsungDex"
}
Copy-Item $boundedSamsungDex (Join-Path $out "classes2.dex") -Force
Run "jar.exe" @("uf", $assembled, "-C", $out, "classes2.dex")

# 4. Native Marker Compilation
$markerSource = Join-Path $root "native\lle64_marker.c"
if ($Clean -or -not (Is-UpToDate $marker @($markerSource))) {
    Run $clang @(
        "-shared", "-fPIC", "-O2", "-Wall", "-Werror",
        "-Wl,-soname,liblle64marker.so",
        "-o", $marker,
        $markerSource
    )
}
$markerHeader = & $readelf -h $marker
if ($LASTEXITCODE -ne 0 -or ($markerHeader -join "`n") -notmatch "Machine:\s+AArch64") {
    throw "ARM64 marker verification failed"
}

# 5. Note 5 ARM64 Candidates Verification & Staging
$candidateRoot = Join-Path $root "reference\arm64-candidates\note5-aoj4"
$stableNote5Hashes = @{
    "libColourDropletEffect.so"   = "634DC703FF9288A4961B3E636B83DD89DDBF86DF6087D624DC19B4231E6C010C"
    "libSparklingBubblesEffect.so" = "F96E287CD20B411A863D07D012631FA61761FC35AEC50D4B4A4B454577B2C944"
    "libstlport.so"               = "821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA"
}
foreach ($library in @("libColourDropletEffect.so", "libSparklingBubblesEffect.so", "libstlport.so")) {
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
    if ($LASTEXITCODE -ne 0 -or $dynamic -notmatch "SONAME.*\[$([regex]::Escape($library))\]") {
        throw "Missing or invalid SONAME for $library"
    }

    $stagedLibrary = Join-Path $arm64Stage $library
    if ($Clean -or -not (Test-Path $stagedLibrary)) {
        Copy-Item $candidate $stagedLibrary -Force
        if ($library -ne "libstlport.so") {
            $bytes = [System.IO.File]::ReadAllBytes($stagedLibrary)
            if ($library -eq "libColourDropletEffect.so") {
                $patchedBytes = & (Join-Path $root "vendor\secvisualeffect\patch-note5-droplet.ps1") -InputBytes $bytes
                [System.IO.File]::WriteAllBytes($stagedLibrary, $patchedBytes)
            } elseif ($library -eq "libSparklingBubblesEffect.so") {
                $patchedBytes = & (Join-Path $root "vendor\secvisualeffect\patch-note5-bubbles.ps1") -InputBytes $bytes
                [System.IO.File]::WriteAllBytes($stagedLibrary, $patchedBytes)
            }
        }
    }
}

# 6. Water Ripple Native Engine
$rippleSourceDir = Join-Path $root "ports\water-ripple"
$rippleBuildScript = Join-Path $rippleSourceDir "build-port.ps1"
$stagedWaterRipple = Join-Path $arm64Stage "libWaterRipple.so"

if ($Clean -or -not (Is-UpToDate $stagedWaterRipple @($rippleSourceDir))) {
    & $rippleBuildScript `
        -NdkPath $ndk `
        -OutPath $stagedWaterRipple `
        -ProbeMode:($IncludeRippleCoreProbe)
}

# 7. Watercolor Native Engine Staging
$watercolorSourceDir = Join-Path $root "ports\watercolor"
$watercolorBuildScript = Join-Path $watercolorSourceDir "build-port.ps1"
$stagedSecveCommon = Join-Path $arm64Stage "libsecveSrkCommon.so"
$stagedSecveWaterColor = Join-Path $arm64Stage "libsecveWaterColor.so"

if ($Clean -or -not (Is-UpToDate $stagedSecveWaterColor @($watercolorSourceDir))) {
    & $watercolorBuildScript `
        -NdkPath $ndk `
        -CommonOutPath $stagedSecveCommon `
        -EffectOutPath $stagedSecveWaterColor `
        -FeedbackMode $WatercolorFeedbackMode
}

# 8. Package & Sign Final APK
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

Write-Host "Built ARM64-only APK: $signed" -ForegroundColor Green
