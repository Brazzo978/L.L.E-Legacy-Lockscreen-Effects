param([string] $BaseClasses)
$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
if ([string]::IsNullOrWhiteSpace($BaseClasses)) {
    $BaseClasses = Join-Path $repoRoot "LLEUnified\build\arm64-v8a-test\classes"
    if (-not (Test-Path -LiteralPath $BaseClasses)) {
        $BaseClasses = Join-Path $repoRoot "LLEUnified\build\arm64-v8a\classes"
    }
}
if (-not (Test-Path -LiteralPath $BaseClasses)) { throw "An existing ARM64 Java build is required for R and shared Android interfaces." }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$platform = Join-Path $sdk "platforms\android-35\android.jar"
$classes = Join-Path $env:TEMP "lle-sony-workshop-$PID"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sourceRoot = Join-Path $repoRoot "LLEUnified\src\com\codex\lle"
$sources = @("XperiaBlindsDynamics", "RevolvingGlassOptics", "RevolvingGlassScene", "XperiaBlindsEffectView", "RevolvingGlassEffectView") | ForEach-Object { Join-Path $sourceRoot "$_.java" }
$schemas = Get-ChildItem -LiteralPath $sourceRoot -Filter "EffectWorkshop*Parameters.java" | ForEach-Object FullName
$testRoot = Join-Path $repoRoot "LLEUnified\tests\com\codex\lle"
$tests = @("SonyWorkshopTest", "XperiaBlindsEffectViewTest", "RevolvingGlassSceneTest") | ForEach-Object { Join-Path $testRoot "$_.java" }
& javac -encoding UTF-8 -source 1.8 -target 1.8 -Xlint:-options -bootclasspath $platform -classpath $BaseClasses -d $classes (Join-Path $sourceRoot "EffectWorkshopConfig.java") @schemas @sources @tests
if ($LASTEXITCODE -ne 0) { throw "Sony focused Java compilation failed" }
foreach ($test in @("SonyWorkshopTest", "XperiaBlindsEffectViewTest", "RevolvingGlassSceneTest")) {
    & java -cp "$classes;$BaseClasses;$platform" "com.codex.lle.$test" $repoRoot
    if ($LASTEXITCODE -ne 0) { throw "$test failed" }
    Write-Output "$test PASS"
}
