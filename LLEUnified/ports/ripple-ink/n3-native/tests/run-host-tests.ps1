$ErrorActionPreference = "Stop"

$appRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$native = Join-Path $appRoot "ports\ripple-ink\n3-native"
$test = Join-Path $PSScriptRoot "lle_n3_ink_worker_test.c"
$worker = Join-Path $native "lle_n3_ink_worker.c"
function ConvertTo-WslPath([string] $path) {
    $absolute = [System.IO.Path]::GetFullPath($path)
    if ($absolute -notmatch '^([A-Za-z]):\\(.*)$') {
        throw "Cannot map non-drive path to WSL: $absolute"
    }
    return "/mnt/$($Matches[1].ToLowerInvariant())/$($Matches[2].Replace('\', '/'))"
}
$wslNative = ConvertTo-WslPath $native
$wslWorker = ConvertTo-WslPath $worker
$wslTest = ConvertTo-WslPath $test
$wslOut = "/tmp/lle-n3-ripple-ink-worker-test"
$command = "cc -std=c11 -O2 -fno-fast-math -ffp-contract=off " +
    "-DLLE_N3_INK_HOST=1 -DLLE_N3_INK_WORKER_TEST_API=1 " +
    "-I '$wslNative' '$wslWorker' '$wslTest' -lm -o '$wslOut' && '$wslOut'"
wsl.exe -e sh -lc $command
if ($LASTEXITCODE -ne 0) {
    throw "N3 Ripple Ink worker host tests failed with exit code $LASTEXITCODE"
}
