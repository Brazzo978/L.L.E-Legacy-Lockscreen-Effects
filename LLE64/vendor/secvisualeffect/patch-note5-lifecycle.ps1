param(
    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

$ErrorActionPreference = "Stop"

$expectedDexSha256 = "206265D2719C5223E57412871B2B778DC56A088300B52B1FEDEB548BFB7EEDB0"
$vendorRoot = $PSScriptRoot
$lleRoot = Split-Path (Split-Path $vendorRoot -Parent) -Parent
$repoRoot = Split-Path $lleRoot -Parent
$originalDex = Join-Path $vendorRoot "classes.dex"
$javaTools = Join-Path $repoRoot "unlock-effects-test\tools\java\*"
$java = (Get-Command "java.exe" -ErrorAction Stop).Source
$buildRoot = [IO.Path]::GetFullPath((Join-Path $lleRoot "build"))
$stage = [IO.Path]::GetFullPath((Join-Path $buildRoot "secvisualeffect-bounded-smali"))
$verifyStage = [IO.Path]::GetFullPath((Join-Path $buildRoot "secvisualeffect-bounded-verify"))
$targetSmaliRelative = "com\samsung\android\visualeffect\common\GLTextureView`$GLThread.smali"

function Run-Java([string] $MainClass, [string[]] $Arguments) {
    & $java "-cp" $javaTools $MainClass @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$MainClass failed with exit code $LASTEXITCODE"
    }
}

function Replace-OneMethod(
        [string] $Text,
        [string] $MethodPattern,
        [string] $Replacement) {
    $pattern = "(?ms)^\.method public $MethodPattern`r?`n.*?^\.end method"
    $matches = [regex]::Matches($Text, $pattern)
    if ($matches.Count -ne 1) {
        throw "Expected exactly one method matching $MethodPattern, found $($matches.Count)"
    }
    return [regex]::Replace(
        $Text,
        $pattern,
        [System.Text.RegularExpressions.MatchEvaluator] { param($match) $Replacement },
        1)
}

if (-not (Test-Path -LiteralPath $originalDex)) {
    throw "Missing original Samsung dex: $originalDex"
}
$actualHash = (Get-FileHash -LiteralPath $originalDex -Algorithm SHA256).Hash
if ($actualHash -ne $expectedDexSha256) {
    throw "Unexpected Samsung dex SHA-256: $actualHash"
}
if ((-not $stage.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase)) -or
        (-not $verifyStage.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase))) {
    throw "Refusing to stage outside the LLE64 build directory"
}

Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $verifyStage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage | Out-Null

Run-Java "org.jf.baksmali.Main" @(
    "disassemble", $originalDex, "--output", $stage
)

$targetSmali = Join-Path $stage $targetSmaliRelative
if (-not (Test-Path -LiteralPath $targetSmali)) {
    throw "Missing disassembled GLThread: $targetSmali"
}
$smali = [IO.File]::ReadAllText($targetSmali)

$boundedOnPause = @'
.method public onPause()V
    .registers 9

    invoke-static {}, Lcom/samsung/android/visualeffect/common/GLTextureView;->access$600()Lcom/samsung/android/visualeffect/common/GLTextureView$GLThreadManager;
    move-result-object v0
    monitor-enter v0

    :try_start_monitor
    const/4 v1, 0x1
    iput-boolean v1, p0, Lcom/samsung/android/visualeffect/common/GLTextureView$GLThread;->mRequestPaused:Z
    invoke-virtual {v0}, Ljava/lang/Object;->notifyAll()V

    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J
    move-result-wide v2
    const-wide/16 v4, 0x7d0
    add-long/2addr v2, v4

    :pause_wait_loop
    iget-boolean v1, p0, Lcom/samsung/android/visualeffect/common/GLTextureView$GLThread;->mExited:Z
    if-nez v1, :pause_done
    iget-boolean v1, p0, Lcom/samsung/android/visualeffect/common/GLTextureView$GLThread;->mPaused:Z
    if-nez v1, :pause_done

    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J
    move-result-wide v4
    sub-long v4, v2, v4
    const-wide/16 v6, 0x0
    cmp-long v1, v4, v6
    if-gtz v1, :pause_do_wait

    const-string v1, "LLE64-GLThread"
    const-string v6, "onPause timed out after 2000 ms"
    invoke-static {v1, v6}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    goto :pause_done

    :pause_do_wait
    :try_start_wait
    invoke-virtual {v0, v4, v5}, Ljava/lang/Object;->wait(J)V
    :try_end_wait
    .catch Ljava/lang/InterruptedException; {:try_start_wait .. :try_end_wait} :pause_interrupted
    goto :pause_wait_loop

    :pause_interrupted
    move-exception v1
    invoke-static {}, Ljava/lang/Thread;->currentThread()Ljava/lang/Thread;
    move-result-object v1
    invoke-virtual {v1}, Ljava/lang/Thread;->interrupt()V
    const-string v1, "LLE64-GLThread"
    const-string v6, "onPause interrupted"
    invoke-static {v1, v6}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    :pause_done
    monitor-exit v0
    :try_end_monitor
    .catchall {:try_start_monitor .. :try_end_monitor} :pause_monitor_failed
    return-void

    :pause_monitor_failed
    move-exception v1
    monitor-exit v0
    throw v1
.end method
'@

$boundedRequestExit = @'
.method public requestExitAndWait()V
    .registers 5

    invoke-static {}, Lcom/samsung/android/visualeffect/common/GLTextureView;->access$600()Lcom/samsung/android/visualeffect/common/GLTextureView$GLThreadManager;
    move-result-object v0
    monitor-enter v0

    :try_start_monitor
    const/4 v1, 0x1
    iput-boolean v1, p0, Lcom/samsung/android/visualeffect/common/GLTextureView$GLThread;->mShouldExit:Z
    invoke-virtual {v0}, Ljava/lang/Object;->notifyAll()V
    monitor-exit v0
    :try_end_monitor
    .catchall {:try_start_monitor .. :try_end_monitor} :exit_monitor_failed
    goto :exit_monitor_done

    :exit_monitor_failed
    move-exception v1
    monitor-exit v0
    throw v1

    :exit_monitor_done
    invoke-static {}, Ljava/lang/Thread;->currentThread()Ljava/lang/Thread;
    move-result-object v0
    if-eq v0, p0, :exit_done

    const-wide/16 v1, 0x7d0
    :try_start_join
    invoke-virtual {p0, v1, v2}, Ljava/lang/Thread;->join(J)V
    :try_end_join
    .catch Ljava/lang/InterruptedException; {:try_start_join .. :try_end_join} :exit_interrupted

    invoke-virtual {p0}, Ljava/lang/Thread;->isAlive()Z
    move-result v0
    if-eqz v0, :exit_done
    const-string v0, "LLE64-GLThread"
    const-string v1, "requestExitAndWait timed out after 2000 ms"
    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    goto :exit_done

    :exit_interrupted
    move-exception v0
    invoke-static {}, Ljava/lang/Thread;->currentThread()Ljava/lang/Thread;
    move-result-object v0
    invoke-virtual {v0}, Ljava/lang/Thread;->interrupt()V
    const-string v0, "LLE64-GLThread"
    const-string v1, "requestExitAndWait interrupted"
    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    :exit_done
    return-void
.end method
'@

$smali = Replace-OneMethod $smali "onPause\(\)V" $boundedOnPause
$smali = Replace-OneMethod $smali "requestExitAndWait\(\)V" $boundedRequestExit
[IO.File]::WriteAllText($targetSmali, $smali, [Text.UTF8Encoding]::new($false))

$outputFullPath = [IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $outputFullPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
Run-Java "org.jf.smali.Main" @(
    "assemble", "--jobs", "1", $stage, "--output", $outputFullPath
)

New-Item -ItemType Directory -Force -Path $verifyStage | Out-Null
Run-Java "org.jf.baksmali.Main" @(
    "disassemble", $outputFullPath, "--output", $verifyStage
)
$verifiedSmali = [IO.File]::ReadAllText((Join-Path $verifyStage $targetSmaliRelative))
foreach ($required in @(
    "onPause timed out after 2000 ms",
    "requestExitAndWait timed out after 2000 ms",
    "Ljava/lang/Object;->wait(J)V",
    "Ljava/lang/Thread;->join(J)V"
)) {
    if (-not $verifiedSmali.Contains($required)) {
        throw "Patched dex verification failed: missing $required"
    }
}

$outputHash = (Get-FileHash -LiteralPath $outputFullPath -Algorithm SHA256).Hash
Write-Host "Built bounded Samsung lifecycle dex: $outputFullPath"
Write-Host "SHA-256: $outputHash"
