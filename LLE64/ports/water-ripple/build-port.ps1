param(
    [switch] $RunOnDevice
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent (Split-Path -Parent $root)
$ndk = if ($env:ANDROID_NDK_HOME) {
    $env:ANDROID_NDK_HOME
} else {
    Join-Path $projectRoot "..\unlock-effects-test\tools\android-ndk-r27d"
}
$bin = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin"
$clang = Join-Path $bin "aarch64-linux-android23-clang.cmd"
$readelf = Join-Path $bin "llvm-readelf.exe"
$native = Join-Path $root "native"
$out = Join-Path $root "build"
$shared = Join-Path $out "libWaterRipple64Wip.so"
$jniShared = Join-Path $out "libWaterRipple.so"
$test = Join-Path $out "ripple_core_test"

function Run($exe, $arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$exe failed with exit code $LASTEXITCODE"
    }
}

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out | Out-Null
$common = @("-std=c11", "-O2", "-fno-fast-math", "-ffp-contract=off", "-Wall", "-Wextra", "-Werror")
Run $clang ($common + @(
    "-shared", "-fPIC", "-Wl,-soname,libWaterRipple64Wip.so",
    (Join-Path $native "ripple_core.c"), "-lm", "-o", $shared
))
Run $clang ($common + @(
    "-shared", "-fPIC", "-Wl,-soname,libWaterRipple.so",
    (Join-Path $native "ripple_core.c"),
    (Join-Path $native "water_ripple_jni_core.c"),
    "-lm", "-o", $jniShared
))
Run $clang ($common + @(
    (Join-Path $native "ripple_core.c"),
    (Join-Path $native "ripple_core_test.c"),
    "-lm", "-o", $test
))

$header = & $readelf -h $shared
if ($LASTEXITCODE -ne 0 -or ($header -join "`n") -notmatch "Machine:\s+AArch64") {
    throw "Water Ripple port is not AArch64"
}
$jniHeader = & $readelf -h $jniShared
if ($LASTEXITCODE -ne 0 -or ($jniHeader -join "`n") -notmatch "Machine:\s+AArch64") {
    throw "Water Ripple JNI port is not AArch64"
}
$needed = (& $readelf -d $shared | Select-String "NEEDED") -join "`n"
if ($needed -match "stlport|armeabi") {
    throw "Unexpected legacy dependency: $needed"
}
$jniSymbols = (& $readelf -Ws $jniShared) -join "`n"
foreach ($symbol in @(
    "JniWaterRippleRender_initWaters",
    "JniWaterRippleRender_move",
    "JniWaterRippleRender_ripple"
)) {
    if ($jniSymbols -notmatch [regex]::Escape($symbol)) {
        throw "Missing JNI symbol: $symbol"
    }
}

Write-Host "Built: $shared"
Write-Host "Built JNI core bridge: $jniShared"
Write-Host $needed

if ($RunOnDevice) {
    Run "adb.exe" @("push", $test, "/data/local/tmp/lle64_ripple_core_test")
    Run "adb.exe" @("shell", "chmod", "755", "/data/local/tmp/lle64_ripple_core_test")
    Run "adb.exe" @("shell", "/data/local/tmp/lle64_ripple_core_test")
}
