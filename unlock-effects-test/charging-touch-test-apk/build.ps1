$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$buildTools = Join-Path $sdk "build-tools\35.0.1"
$platform = Join-Path $sdk "platforms\android-35\android.jar"

$out = Join-Path $root "build"
$assets = Join-Path $root "assets"
$resZip = Join-Path $out "res.zip"
$classes = Join-Path $out "classes"
$dex = Join-Path $out "dex"
$unsigned = Join-Path $out "ChargingTouchTest-unsigned.apk"
$aligned = Join-Path $out "ChargingTouchTest-aligned.apk"
$signed = Join-Path $out "ChargingTouchTest-debug.apk"
$keystore = Join-Path $root "..\demo-apk\debug.keystore"
$keystoreDir = Split-Path -Parent $keystore
$classesJar = Join-Path $out "classes.jar"
$samsungVisualEffectDex = Join-Path $root "..\extracted\secvisualeffect_hybrid_dex\classes.dex"
$nativeLibs = Join-Path $root "native-libs"

function Run($exe, $arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$exe failed with exit code $LASTEXITCODE"
    }
}

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, $classes, $dex | Out-Null

Run (Join-Path $buildTools "aapt2.exe") @("compile", "--dir", (Join-Path $root "res"), "-o", $resZip)
$linkArgs = @("link", "-o", $unsigned, "-I", $platform, "--manifest", (Join-Path $root "AndroidManifest.xml"), $resZip, "--java", (Join-Path $out "gen"), "--auto-add-overlay")
if (Test-Path $assets) {
    $linkArgs += @("-A", $assets)
}
Run (Join-Path $buildTools "aapt2.exe") $linkArgs

$sources = @()
$sources += Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem -Path (Join-Path $out "gen") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$javacArgs = @("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-bootclasspath", $platform, "-d", $classes) + $sources
Run "javac.exe" $javacArgs
Run "jar.exe" @("cf", $classesJar, "-C", $classes, ".")
Run (Join-Path $buildTools "d8.bat") @("--lib", $platform, "--output", $dex, $classesJar)

Copy-Item $unsigned $aligned
Run "jar.exe" @("uf", $aligned, "-C", $dex, "classes.dex")
if (Test-Path $samsungVisualEffectDex) {
    Copy-Item $samsungVisualEffectDex (Join-Path $out "classes2.dex")
    Run "jar.exe" @("uf", $aligned, "-C", $out, "classes2.dex")
} else {
    throw "Missing Samsung visual effect dex: $samsungVisualEffectDex"
}
if (Test-Path $nativeLibs) {
    Run "jar.exe" @("uf", $aligned, "-C", $nativeLibs, ".")
}

if (-not (Test-Path $keystore)) {
    New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
    Run "keytool.exe" @("-genkeypair", "-keystore", $keystore, "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Android Debug,O=Codex,C=US")
}

Run (Join-Path $buildTools "zipalign.exe") @("-f", "4", $aligned, (Join-Path $out "ChargingTouchTest-zipaligned.apk"))
Run (Join-Path $buildTools "apksigner.bat") @("sign", "--ks", $keystore, "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", $signed, (Join-Path $out "ChargingTouchTest-zipaligned.apk"))
Run (Join-Path $buildTools "apksigner.bat") @("verify", $signed)

Write-Host $signed
