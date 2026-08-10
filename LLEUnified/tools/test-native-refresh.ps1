<#
.SYNOPSIS
Builds and runs the host-only native refresh-physics regression tests through WSL gcc.

.DESCRIPTION
This runner intentionally exercises only the portable simulation cores.  It does not
build an APK, contact ADB, or load Android/JNI/GLES code.  Each binary is emitted into
a unique temporary directory and removed when the run ends.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) (
    'lle-native-refresh-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryDirectory -ErrorAction Stop | Out-Null

function Get-WslPath([string] $windowsPath) {
    $converted = & wsl.exe --exec wslpath -a $windowsPath
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($converted -join ''))) {
        throw "WSL could not resolve path: $windowsPath"
    }
    return ($converted -join "`n").Trim()
}

function Invoke-HostTest([string] $name, [string[]] $compilerArguments, [string] $binaryPath) {
    & wsl.exe --exec gcc @compilerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "$name compilation failed"
    }
    & wsl.exe --exec $binaryPath
    if ($LASTEXITCODE -ne 0) {
        throw "$name failed with exit code $LASTEXITCODE"
    }
    Write-Host "PASS $name"
}

try {
    $wslRoot = Get-WslPath $repositoryRoot
    $wslTemporaryDirectory = Get-WslPath $temporaryDirectory
    $hostStubDirectory = Join-Path $temporaryDirectory 'host-stubs'
    $glesStubDirectory = Join-Path $hostStubDirectory 'GLES2'
    New-Item -ItemType Directory -Path $glesStubDirectory -Force | Out-Null
    @'
#ifndef LLE_HOST_GL2_H
#define LLE_HOST_GL2_H
typedef unsigned int GLuint;
#endif
'@ | Set-Content -LiteralPath (Join-Path $glesStubDirectory 'gl2.h') -Encoding ascii
    @'
#ifndef LLE_HOST_JNI_H
#define LLE_HOST_JNI_H
typedef void JNIEnv;
typedef void *jobject;
#endif
'@ | Set-Content -LiteralPath (Join-Path $hostStubDirectory 'jni.h') -Encoding ascii
    $wslHostStubDirectory = Get-WslPath $hostStubDirectory

    $common = @('-std=c11', '-Wall', '-Wextra', '-Werror', '-O2', "-I$wslHostStubDirectory")

    Invoke-HostTest 'spark-refresh' ($common + @(
            '-o', "$wslTemporaryDirectory/spark-refresh",
            "$wslRoot/ports/sparkling-bubbles/tests/lle_spark_refresh_test.c",
            "$wslRoot/ports/sparkling-bubbles/native/lle_spark_sim.c",
            '-lm')) "$wslTemporaryDirectory/spark-refresh"

    Invoke-HostTest 'colour-native-refresh' ($common + @(
            '-DLLE_COLOUR_TEST_API',
            '-o', "$wslTemporaryDirectory/colour-native-refresh",
            "$wslRoot/ports/colour-droplet-appowned/tests/lle_colour_sim_native_refresh_test.c",
            "$wslRoot/ports/colour-droplet-appowned/native/lle_colour_sim.c",
            '-lm')) "$wslTemporaryDirectory/colour-native-refresh"

    Invoke-HostTest 's6-water-native-refresh' ($common + @(
            '-DLLE_S6_TEST_API',
            '-o', "$wslTemporaryDirectory/s6-water-native-refresh",
            "$wslRoot/ports/s6-water-droplet-appowned/tests/lle_s6_water_sim_stability_test.c",
            "$wslRoot/ports/s6-water-droplet-appowned/native/lle_s6_water_sim.c",
            '-lm')) "$wslTemporaryDirectory/s6-water-native-refresh"

    Invoke-HostTest 'ripple-native-refresh' ($common + @(
            '-o', "$wslTemporaryDirectory/ripple-native-refresh",
            "$wslRoot/ports/water-ripple/tests/lle_ripple_native_refresh_test.c",
            "$wslRoot/ports/water-ripple/native/ripple_core.c",
            '-lm')) "$wslTemporaryDirectory/ripple-native-refresh"

    Invoke-HostTest 'watercolor-native-refresh' ($common + @(
            '-o', "$wslTemporaryDirectory/watercolor-native-refresh",
            "$wslRoot/ports/watercolor/tests/lle_watercolor_refresh_test.c",
            "$wslRoot/ports/watercolor/native/watercolor_refresh.c",
            '-lm')) "$wslTemporaryDirectory/watercolor-native-refresh"
} finally {
    if (Test-Path -LiteralPath $temporaryDirectory) {
        Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
    }
}
