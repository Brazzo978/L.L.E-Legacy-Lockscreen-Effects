param(
    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

$ErrorActionPreference = "Stop"

$expectedDexSha256 = "206265D2719C5223E57412871B2B778DC56A088300B52B1FEDEB548BFB7EEDB0"
$expectedPatchedDexSha256 = "5BC6CFFB89208D9C4F9D80BD207E1061E92B0B4CD9D0E9CE69372EC003C4CF2B"
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
$eglChooserRelative = "com\samsung\android\visualeffect\common\GLTextureView`$SimpleEGLConfigChooser.smali"
$textureRendererRelative = "com\samsung\android\visualeffect\lock\common\GLTextureViewRenderer.smali"
$watercolorRendererRelative = "com\samsung\android\visualeffect\lock\watercolor\WaterColorRenderer.smali"

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

function Replace-OneLiteral(
        [string] $Text,
        [string] $Needle,
        [string] $Replacement,
        [string] $Label) {
    $first = $Text.IndexOf($Needle, [StringComparison]::Ordinal)
    if ($first -lt 0) {
        throw "Expected exactly one $Label block, found 0"
    }
    $second = $Text.IndexOf(
            $Needle,
            $first + $Needle.Length,
            [StringComparison]::Ordinal)
    if ($second -ge 0) {
        throw "Expected exactly one $Label block, found more than 1"
    }
    return $Text.Substring(0, $first) + $Replacement +
            $Text.Substring($first + $Needle.Length)
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
$smali = $smali.Replace("`r`n", "`n")

$rendererAccessor = 'Lcom/samsung/android/visualeffect/common/GLTextureView;->access$700(Lcom/samsung/android/visualeffect/common/GLTextureView;)Lcom/samsung/android/visualeffect/common/GLTextureView$Renderer;'
$rendererAccessorCount = [regex]::Matches(
        $smali,
        [regex]::Escape($rendererAccessor)).Count
if ($rendererAccessorCount -ne 5) {
    throw "Expected exactly five GLTextureView renderer accessor sites, found $rendererAccessorCount"
}

$unsafeRendererDestroy = @'
    move-result-object v24

    check-cast v24, Lcom/samsung/android/visualeffect/common/GLTextureView;

    # getter for: Lcom/samsung/android/visualeffect/common/GLTextureView;->mRenderer:Lcom/samsung/android/visualeffect/common/GLTextureView$Renderer;
    invoke-static/range {v24 .. v24}, Lcom/samsung/android/visualeffect/common/GLTextureView;->access$700(Lcom/samsung/android/visualeffect/common/GLTextureView;)Lcom/samsung/android/visualeffect/common/GLTextureView$Renderer;

    move-result-object v24

    invoke-interface/range {v24 .. v24}, Lcom/samsung/android/visualeffect/common/GLTextureView$Renderer;->onDestroy()V

    .line 1149
'@

$safeRendererDestroy = @'
    move-result-object v24

    check-cast v24, Lcom/samsung/android/visualeffect/common/GLTextureView;

    # LLE64: the TextureView may be collected after removeEffect() while its GL thread exits.
    if-eqz v24, :lle64_skip_renderer_destroy

    # getter for: Lcom/samsung/android/visualeffect/common/GLTextureView;->mRenderer:Lcom/samsung/android/visualeffect/common/GLTextureView$Renderer;
    invoke-static/range {v24 .. v24}, Lcom/samsung/android/visualeffect/common/GLTextureView;->access$700(Lcom/samsung/android/visualeffect/common/GLTextureView;)Lcom/samsung/android/visualeffect/common/GLTextureView$Renderer;

    move-result-object v24

    invoke-interface/range {v24 .. v24}, Lcom/samsung/android/visualeffect/common/GLTextureView$Renderer;->onDestroy()V

    :lle64_skip_renderer_destroy
    .line 1149
'@

$smali = Replace-OneLiteral `
        $smali `
        $unsafeRendererDestroy `
        $safeRendererDestroy `
        "unsafe GLTextureView renderer destroy"

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

# Samsung asks EGL for RGB888 with alphaSize=0. Watercolor is hosted in a
# transparent TextureView, so its default framebuffer must actually be RGBA8888.
$eglChooserSmali = Join-Path $stage $eglChooserRelative
if (-not (Test-Path -LiteralPath $eglChooserSmali)) {
    throw "Missing disassembled EGL chooser: $eglChooserSmali"
}
$eglChooser = [IO.File]::ReadAllText($eglChooserSmali).Replace("`r`n", "`n")
$eglChooser = Replace-OneLiteral `
        $eglChooser `
        "    const/4 v5, 0x0" `
        "    const/16 v5, 0x8" `
        "RGB-only EGL alpha size"
[IO.File]::WriteAllText($eglChooserSmali, $eglChooser, [Text.UTF8Encoding]::new($false))

# Watercolor advances its legacy state once per draw. Keep that draw cadence at
# the stock 60 Hz even when Samsung's GLTextureView is hosted on a 120 Hz panel.
$watercolorRendererSmali = Join-Path $stage $watercolorRendererRelative
if (-not (Test-Path -LiteralPath $watercolorRendererSmali)) {
    throw "Missing disassembled WaterColorRenderer: $watercolorRendererSmali"
}
$watercolorRenderer = [IO.File]::ReadAllText($watercolorRendererSmali).Replace("`r`n", "`n")
if ($watercolorRenderer.Contains(".method public onDrawFrame(")) {
    throw "Unexpected WaterColorRenderer: pacing override already present"
}
$watercolorPacedDraw = @'

.method public onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V
    .registers 2
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;

    invoke-static {}, Lcom/codex/lle/WatercolorNativeEffectView;->paceOriginalFrame()V

    invoke-super {p0, p1}, Lcom/samsung/android/visualeffect/lock/common/GLTextureViewRenderer;->onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V

    return-void
.end method
'@
$lastMethodEnd = $watercolorRenderer.LastIndexOf(
        ".end method", [StringComparison]::Ordinal)
if ($lastMethodEnd -lt 0) {
    throw "Unexpected WaterColorRenderer: constructor end not found"
}
$insertAt = $lastMethodEnd + ".end method".Length
$watercolorRenderer = $watercolorRenderer.Insert($insertAt, $watercolorPacedDraw)
[IO.File]::WriteAllText(
        $watercolorRendererSmali,
        $watercolorRenderer,
        [Text.UTF8Encoding]::new($false))

# The vendored dex is already package-relocated for LLE64. Keep this invariant
# explicit because loadSpecialTexture otherwise looks in Samsung's missing APK.
$textureRendererSmali = Join-Path $stage $textureRendererRelative
if (-not (Test-Path -LiteralPath $textureRendererSmali)) {
    throw "Missing disassembled GLTextureViewRenderer: $textureRendererSmali"
}
$textureRenderer = [IO.File]::ReadAllText($textureRendererSmali)
$llePackageLiteral = '    const-string v15, "com.codex.lle"'
if ([regex]::Matches($textureRenderer, [regex]::Escape($llePackageLiteral)).Count -ne 1) {
    throw "Unexpected GLTextureViewRenderer asset package relocation"
}

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

$verifiedAccessorCount = [regex]::Matches(
        $verifiedSmali,
        [regex]::Escape($rendererAccessor)).Count
if ($verifiedAccessorCount -ne 5) {
    throw "Patched dex verification failed: renderer accessor count=$verifiedAccessorCount"
}
$verifiedDestroyGuardPattern = '(?ms)check-cast v24, Lcom/samsung/android/visualeffect/common/GLTextureView;\s+if-eqz v24, (?<skip>:[^\s]+)\s+.*?invoke-static/range \{v24 \.\. v24\}, Lcom/samsung/android/visualeffect/common/GLTextureView;->access\$700\(Lcom/samsung/android/visualeffect/common/GLTextureView;\)Lcom/samsung/android/visualeffect/common/GLTextureView\$Renderer;\s+.*?invoke-interface/range \{v24 \.\. v24\}, Lcom/samsung/android/visualeffect/common/GLTextureView\$Renderer;->onDestroy\(\)V\s+\.line 1149\s+\k<skip>\s+monitor-exit v25'
$verifiedDestroyGuardCount = [regex]::Matches(
        $verifiedSmali,
        $verifiedDestroyGuardPattern).Count
if ($verifiedDestroyGuardCount -ne 1) {
    throw "Patched dex verification failed: guarded renderer destroy count=$verifiedDestroyGuardCount"
}

$verifiedEglChooser = [IO.File]::ReadAllText((Join-Path $verifyStage $eglChooserRelative))
if ([regex]::Matches(
        $verifiedEglChooser,
        [regex]::Escape("const/16 v5, 0x8")).Count -ne 1) {
    throw "Patched dex verification failed: RGBA8888 EGL chooser missing"
}
$verifiedWatercolorRenderer = [IO.File]::ReadAllText(
        (Join-Path $verifyStage $watercolorRendererRelative))
foreach ($required in @(
    ".method public onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V",
    "Lcom/codex/lle/WatercolorNativeEffectView;->paceOriginalFrame()V"
)) {
    if (-not $verifiedWatercolorRenderer.Contains($required)) {
        throw "Patched dex verification failed: missing Watercolor pacing $required"
    }
}
$verifiedTextureRenderer = [IO.File]::ReadAllText(
        (Join-Path $verifyStage $textureRendererRelative))
if ([regex]::Matches(
        $verifiedTextureRenderer,
        [regex]::Escape($llePackageLiteral)).Count -ne 1) {
    throw "Patched dex verification failed: LLE64 asset package missing"
}

$outputHash = (Get-FileHash -LiteralPath $outputFullPath -Algorithm SHA256).Hash
if ($outputHash -ne $expectedPatchedDexSha256) {
    throw "Unexpected bounded Samsung lifecycle dex SHA-256: $outputHash"
}
$originalHashAfterPatch = (Get-FileHash -LiteralPath $originalDex -Algorithm SHA256).Hash
if ($originalHashAfterPatch -ne $expectedDexSha256) {
    throw "Reference Samsung dex changed during patch: $originalHashAfterPatch"
}
Write-Host "Built bounded Samsung lifecycle dex: $outputFullPath"
Write-Host "SHA-256: $outputHash"
