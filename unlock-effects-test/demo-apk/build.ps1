$Native32 = $false
if ($args -contains "-Native32") {
    $Native32 = $true
}
$UseNote5Dex = $false
if ($args -contains "-Note5Dex") {
    $UseNote5Dex = $true
}
$UseS4SystemVisualEffect = $false
if ($args -contains "-S4SystemVisualEffect") {
    $UseS4SystemVisualEffect = $true
}

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$buildTools = Join-Path $sdk "build-tools\35.0.1"
$platform = Join-Path $sdk "platforms\android-35\android.jar"

$out = Join-Path $root "build"
$resZip = Join-Path $out "res.zip"
$classes = Join-Path $out "classes"
$dex = Join-Path $out "dex"
$suffix = if ($UseS4SystemVisualEffect) { "-s4-system" } elseif ($Native32) { "-native32" } else { "" }
$unsigned = Join-Path $out "S4UnlockFxTest$suffix-unsigned.apk"
$aligned = Join-Path $out "S4UnlockFxTest$suffix-aligned.apk"
$signed = Join-Path $out "S4UnlockFxTest$suffix-debug.apk"
$keystore = Join-Path $root "debug.keystore"
$classesJar = Join-Path $out "classes.jar"
$rawSamsungDex = Join-Path $root "..\extracted\secvisualeffect_dex\classes.dex"
$hybridSamsungDex = Join-Path $root "..\extracted\secvisualeffect_hybrid_dex\classes.dex"
$patchedSamsungDex = Join-Path $root "..\extracted\secvisualeffect_patched_dex\classes.dex"
$note5PatchedSamsungDex = Join-Path $root "..\extracted\note5_old_secvisualeffect_patched_dex\classes.dex"
$samsungDex = if ($UseNote5Dex -and (Test-Path $note5PatchedSamsungDex)) { $note5PatchedSamsungDex } elseif (Test-Path $hybridSamsungDex) { $hybridSamsungDex } elseif (Test-Path $patchedSamsungDex) { $patchedSamsungDex } else { $rawSamsungDex }
$smaliMassDir = Join-Path $root "smali_s4_mass"
$smaliMassDex = Join-Path $out "classes3.dex"
$smaliS3RippleDir = Join-Path $root "smali_s3_ripple"
$smaliS3RippleDex = Join-Path $out "classes4.dex"

function Run($exe, $arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$exe failed with exit code $LASTEXITCODE"
    }
}

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, $classes, $dex | Out-Null

Run (Join-Path $buildTools "aapt2.exe") @("compile", "--dir", (Join-Path $root "res"), "-o", $resZip)
$manifest = if ($UseS4SystemVisualEffect) { Join-Path $root "AndroidManifest.s4-system.xml" } else { Join-Path $root "AndroidManifest.xml" }
Run (Join-Path $buildTools "aapt2.exe") @("link", "-o", $unsigned, "-I", $platform, "--manifest", $manifest, "-A", (Join-Path $root "assets"), $resZip, "--java", (Join-Path $out "gen"), "--auto-add-overlay")
$qmgDumpAssets = Join-Path $root "assets\qmgdump"
if (Test-Path $qmgDumpAssets) {
    # aapt2 on Windows can store asset names with backslashes; AssetManager may
    # then fail to open them by normal Android-style paths. Re-add these with
    # jar so the APK also contains forward-slash ZIP entries.
    Run "jar.exe" @("uf", $unsigned, "-C", $root, "assets/qmgdump")
}
$note4SeasonalAssets = Join-Path $root "assets\note4seasonal"
if (Test-Path $note4SeasonalAssets) {
    Run "jar.exe" @("uf", $unsigned, "-C", $root, "assets/note4seasonal")
}

$sources = @()
$sources += Get-ChildItem -Path (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem -Path (Join-Path $out "gen") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$javacArgs = @("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-bootclasspath", $platform, "-d", $classes) + $sources
Run "javac.exe" $javacArgs
Run "jar.exe" @("cf", $classesJar, "-C", $classes, ".")
Run (Join-Path $buildTools "d8.bat") @("--lib", $platform, "--output", $dex, $classesJar)

Copy-Item $unsigned $aligned
Run "jar.exe" @("uf", $aligned, "-C", $dex, "classes.dex")
if ($UseS4SystemVisualEffect) {
    Write-Host "Using the S4 system secvisualeffect shared library (no bundled Samsung dex)"
} elseif (Test-Path $samsungDex) {
    Copy-Item $samsungDex (Join-Path $out "classes2.dex")
    Run "jar.exe" @("uf", $aligned, "-C", $out, "classes2.dex")
}
if (Test-Path $smaliMassDir) {
    $smaliCp = (Get-ChildItem -LiteralPath (Join-Path $root "..\tools\java") -Filter "*.jar" | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator
    Run "java.exe" @("-cp", $smaliCp, "org.jf.smali.Main", "assemble", $smaliMassDir, "-o", $smaliMassDex)
    Run "jar.exe" @("uf", $aligned, "-C", $out, "classes3.dex")
}
if (Test-Path $smaliS3RippleDir) {
    $smaliCp = (Get-ChildItem -LiteralPath (Join-Path $root "..\tools\java") -Filter "*.jar" | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator
    Run "java.exe" @("-cp", $smaliCp, "org.jf.smali.Main", "assemble", $smaliS3RippleDir, "-o", $smaliS3RippleDex)
    Run "jar.exe" @("uf", $aligned, "-C", $out, "classes4.dex")
}
if ($Native32) {
    $nativeOut = Join-Path $out "native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $nativeOut | Out-Null
    Copy-Item (Join-Path $root "..\extracted\s4_system_files\lib\*.so") $nativeOut
    $s3WaterRipple = Join-Path $root "..\extracted\s3_system_files\lib\libWaterRipple.so"
    if (Test-Path $s3WaterRipple) {
        Copy-Item $s3WaterRipple $nativeOut -Force
    }
    foreach ($note5NativeDir in @(
        (Join-Path $root "..\extracted\note5_aoj4_system_files\lib"),
        (Join-Path $root "..\extracted\note5_old_system_files\lib")
    )) {
        foreach ($note5NativeLib in @("libWaterDropletEffect.so", "libSparklingBubblesEffect.so", "libColourDropletEffect.so")) {
            $candidate = Join-Path $note5NativeDir $note5NativeLib
            if (Test-Path $candidate) {
                Copy-Item $candidate $nativeOut -Force
            }
        }
    }
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "native"), "lib")
}

if (-not (Test-Path $keystore)) {
    Run "keytool.exe" @("-genkeypair", "-keystore", $keystore, "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Android Debug,O=Codex,C=US")
}

Run (Join-Path $buildTools "zipalign.exe") @("-f", "4", $aligned, (Join-Path $out "S4UnlockFxTest-zipaligned.apk"))
Run (Join-Path $buildTools "apksigner.bat") @("sign", "--ks", $keystore, "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", $signed, (Join-Path $out "S4UnlockFxTest-zipaligned.apk"))
Run (Join-Path $buildTools "apksigner.bat") @("verify", $signed)

Write-Host $signed
