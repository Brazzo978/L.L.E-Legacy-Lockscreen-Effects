$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
# The workspace is shared by parallel agents.  A fixed temp directory lets one
# runner delete another runner's freshly compiled classes between javac and java.
$classes = Join-Path ([System.IO.Path]::GetTempPath()) "lle-ripple-ink-host-tests-$PID"

if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
New-Item -ItemType Directory -Path $classes | Out-Null

javac -d $classes `
    (Join-Path $repoRoot "LLEUnified\src\com\codex\lle\RippleInkVanillaWaterAdapter.java") `
    (Join-Path $repoRoot "LLEUnified\src\com\codex\lle\RippleInkPortEngine.java") `
    (Join-Path $repoRoot "LLEUnified\src\com\codex\lle\N3RippleInkWorkerNative.java") `
    (Join-Path $repoRoot "LLEUnified\src\com\codex\lle\RippleInkPortFluidPipeline.java") `
    (Join-Path $repoRoot "LLEUnified\src\com\codex\lle\RippleInkPortCompositor.java") `
    (Join-Path $repoRoot "LLEUnified\src\com\codex\lle\RippleInkPortGlesShaders.java") `
    (Join-Path $PSScriptRoot "RippleInkPortEngineTest.java") `
    (Join-Path $PSScriptRoot "RippleInkPortFluidPipelineTest.java") `
    (Join-Path $PSScriptRoot "RippleInkN3OracleGoldenTraceTest.java") `
    (Join-Path $PSScriptRoot "RippleInkN3NativeWiringTest.java") `
    (Join-Path $PSScriptRoot "RippleInkPortCompositorTest.java")
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

java -cp $classes com.codex.lle.RippleInkPortEngineTest
if ($LASTEXITCODE -ne 0) {
    throw "RippleInkPortEngineTest failed with exit code $LASTEXITCODE"
}

java -cp $classes com.codex.lle.RippleInkPortFluidPipelineTest
if ($LASTEXITCODE -ne 0) {
    throw "RippleInkPortFluidPipelineTest failed with exit code $LASTEXITCODE"
}

java -cp $classes com.codex.lle.RippleInkN3OracleGoldenTraceTest
if ($LASTEXITCODE -ne 0) {
    throw "RippleInkN3OracleGoldenTraceTest failed with exit code $LASTEXITCODE"
}

java "-Dlle.repoRoot=$repoRoot" -cp $classes com.codex.lle.RippleInkN3NativeWiringTest
if ($LASTEXITCODE -ne 0) {
    throw "RippleInkN3NativeWiringTest failed with exit code $LASTEXITCODE"
}

java "-Dlle.repoRoot=$repoRoot" -cp $classes com.codex.lle.RippleInkPortCompositorTest
if ($LASTEXITCODE -ne 0) {
    throw "RippleInkPortCompositorTest failed with exit code $LASTEXITCODE"
}
