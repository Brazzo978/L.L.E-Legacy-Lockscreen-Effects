param(
    [switch] $ReleaseSigning,
    [string] $ReleaseKeystorePath = "",
    [string] $ReleaseKeyAlias = "lle-release"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path $root -Parent
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$buildTools = Join-Path $sdk "build-tools\35.0.1"
$platform = Join-Path $sdk "platforms\android-35\android.jar"

$out = Join-Path $root "build\armeabi-v7a"
$assets = Join-Path $root "assets"
$resStage = Join-Path $out "res"
$resZip = Join-Path $out "res.zip"
$classes = Join-Path $out "classes"
$dex = Join-Path $out "dex"
$unsigned = Join-Path $out "LLE-armeabi-v7a-unsigned.apk"
$aligned = Join-Path $out "LLE-armeabi-v7a-aligned.apk"
$signed = Join-Path $out $(if ($ReleaseSigning) {
    "LLE-armeabi-v7a-release.apk"
} else {
    "LLE-armeabi-v7a-debug.apk"
})
$keystore = Join-Path $root ".keys\debug.keystore"
$sourceKeystore = Join-Path $repoRoot "unlock-effects-test\demo-apk\debug.keystore"
$keystoreDir = Split-Path -Parent $keystore
$classesJar = Join-Path $out "classes.jar"
$samsungVisualEffectDex = Join-Path $out "classes-note5-bounded.dex"
$samsungVisualEffectSmaliStage = Join-Path $out "smali_secvisualeffect_lle"
$samsungVisualEffectDexStage = Join-Path $out "classes2.dex"
$nativeLibs = Join-Path $root "native-libs"
$s3SmaliSource = Join-Path $repoRoot "unlock-effects-test\demo-apk\smali_s3_ripple"
$s3SmaliStage = Join-Path $out "smali_s3_lle"
$s3Dex = Join-Path $out "classes3.dex"
$s3NativeLib = Join-Path $root "vendor\original-native\libWaterRipple.so"
$s3NativePatch = Join-Path $root "vendor\native-patches\patch-s3-water-ripple-transparent.ps1"
$s3PatchedNativeLib = Join-Path $out "patched-s3\libWaterRipple.so"
$watercolorCommonLib = Join-Path $nativeLibs "lib\armeabi-v7a\libsecveSrkCommon.so"
$watercolorNativePatch = Join-Path $root "vendor\native-patches\patch-watercolor-transparent.ps1"
$watercolorPatchedCommonLib = Join-Path $out "patched-watercolor\libsecveSrkCommon.so"
$brilliantRingOriginalLib = Join-Path $root "vendor\original-native\libsecveBrilliantRing.so"
$brilliantRingOriginalSha256 = "17F059922AFB2B15103EDAF817C7663890F99CDE9C153B55AC3E0CBAD27E3A79"
$brilliantRingNativePatch = Join-Path $root "vendor\native-patches\patch-brilliant-ring-transparent.ps1"
$brilliantRingPatchedCommonLib = Join-Path $out "patched-brilliant-ring\libsecveSrkCommon.so"
$brilliantCutOriginalLib = Join-Path $root "vendor\original-native\libsecveBrilliantCut.so"
$brilliantCutOriginalSha256 = "46B7580078F373CD5129704B8294AD1B630665F27E6877A8ECB30A41BDF039C7"
$brilliantCutNativePatch = Join-Path $root "vendor\native-patches\patch-brilliant-cut-transparent.ps1"
$brilliantCutPatchedLib = Join-Path $out "patched-brilliant-cut\libsecveBrilliantCut.so"
$brilliantCutExpectedCommonSha256 = "5DBE95670EAE329DF47BF746D50FE9ED250CADC3FFFA9B2255A64F50C7AD6C36"
$brilliantCutStlportLib = Join-Path $nativeLibs "lib\armeabi-v7a\libstlport.so"
$brilliantCutExpectedStlportSha256 = "B7B845F6E446E87878152D25D6DDE9657B5260B9DC47A540C87D2F6A67A97E09"
$abstractTileOriginalLib = Join-Path $root "vendor\original-native\libsecveAbstractTile.so"
$abstractTileNativePatch = Join-Path $root "vendor\native-patches\patch-abstract-tile-transparent.ps1"
$abstractTilePatchedLib = Join-Path $out "patched-abstract-tile\libsecveAbstractTile.so"
$geometricMosaicOriginalLib = Join-Path $root "vendor\original-native\libsecveGeometricMosaic.so"
$geometricMosaicNativePatch = Join-Path $root "vendor\native-patches\patch-geometric-mosaic-transparent.ps1"
$geometricMosaicPatchedLib = Join-Path $out "patched-geometric-mosaic\libsecveGeometricMosaic.so"
$indigoOriginalLib = Join-Path $root "vendor\original-native\libsecveIndigoDiffusion.so"
$indigoNativePatch = Join-Path $root "vendor\native-patches\patch-indigo-diffusion-transparent.ps1"
$indigoPatchedLib = Join-Path $out "patched-indigo\libsecveIndigoDiffusion.so"
$brilliantCutSoundSource = Join-Path $root "res\raw"
$stockWatercolorTap = Join-Path $root "res\raw\ve_watercolour_tap.ogg"
$releaseCertificateSha256 = "5397D6ACE3E9D2F14D8FFD2285E26E9F1B26635589CAC3A3DC95C0DEFF76B8EE"

function Run($exe, $arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$exe failed with exit code $LASTEXITCODE"
    }
}

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, $classes, $dex, $resStage | Out-Null
& (Join-Path $root "vendor\secvisualeffect\patch-note5-lifecycle.ps1") `
    -OutputPath $samsungVisualEffectDex `
    -ResourcePackageName "com.codex.lle"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $samsungVisualEffectDex)) {
    throw "Bounded Samsung visual-effect dex generation failed"
}

# Stage resources from the tracked canonical Samsung assets. Geometric Mosaic
# uses the Brilliant Cut interaction trio on stock S4/Note4; Watercolor uses
# the S5-era 24,881-byte tap shared by S4/Note3/Note4/S5, not the Tab variant.
Copy-Item -Path (Join-Path $root "res\*") -Destination $resStage -Recurse -Force
foreach ($soundName in @("brilliantcut_tap.ogg", "brilliantcut_drag.ogg", "brilliantcut_unlock.ogg")) {
    $source = Join-Path $brilliantCutSoundSource $soundName
    if (-not (Test-Path $source)) {
        throw "Missing stock Geometric Mosaic interaction sound: $source"
    }
    Copy-Item $source (Join-Path $resStage "raw\$soundName") -Force
}
if (-not (Test-Path $stockWatercolorTap)) {
    throw "Missing stock S5 Watercolor tap sound: $stockWatercolorTap"
}
Copy-Item $stockWatercolorTap (Join-Path $resStage "raw\ve_watercolour_tap.ogg") -Force

Run (Join-Path $buildTools "aapt2.exe") @("compile", "--dir", $resStage, "-o", $resZip)
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
Run (Join-Path $buildTools "d8.bat") @("--lib", $platform, "--min-api", "23", "--output", $dex, $classesJar)

Copy-Item $unsigned $aligned
Run "jar.exe" @("uf", $aligned, "-C", $dex, "classes.dex")
if (Test-Path $samsungVisualEffectDex) {
    $smaliCp = (Get-ChildItem -LiteralPath (Join-Path $repoRoot "unlock-effects-test\tools\java") -Filter "*.jar" | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator
    Run "java.exe" @("-cp", $smaliCp, "org.jf.baksmali.Main", "disassemble", $samsungVisualEffectDex, "-o", $samsungVisualEffectSmaliStage)
    # Abstract Tiles advances its physics once per rendered frame. The stock S4
    # renderer runs at ~30 fps even though the panel is 60 Hz; without pacing a
    # modern 120 Hz device consumes the animation about four times too quickly.
    # Override only AbstractTileRenderer.onDrawFrame so Watercolor, Geometric
    # Mosaic and the rest of Samsung's shared GLTextureView remain untouched.
    $abstractRendererSmali = Join-Path $samsungVisualEffectSmaliStage "com\samsung\android\visualeffect\lock\abstracttile\AbstractTileRenderer.smali"
    $abstractRendererContent = Get-Content -LiteralPath $abstractRendererSmali -Raw
    $abstractRendererEnd = ".end method"
    $abstractPacedDraw = @"

.method public onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V
    .registers 2
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;

    invoke-static {}, Lcom/codex/lle/SamsungLockBgEffectView;->paceAbstractTileFrame()V

    invoke-super {p0, p1}, Lcom/samsung/android/visualeffect/lock/common/GLTextureViewRenderer;->onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V

    return-void
.end method
"@
    if ($abstractRendererContent.Contains(".method public onDrawFrame(")) {
        throw "Unexpected AbstractTileRenderer: pacing override already present"
    }
    $lastMethodEnd = $abstractRendererContent.LastIndexOf($abstractRendererEnd)
    if ($lastMethodEnd -lt 0) {
        throw "Unexpected AbstractTileRenderer: constructor end not found"
    }
    $insertAt = $lastMethodEnd + $abstractRendererEnd.Length
    $abstractRendererContent = $abstractRendererContent.Insert($insertAt, $abstractPacedDraw)
    [IO.File]::WriteAllText($abstractRendererSmali, $abstractRendererContent, (New-Object Text.UTF8Encoding($false)))

    # The real stock S4 SystemUI lockscreen measures Geometric Mosaic at ~30 fps. Keep its
    # own cadence independent from Abstract Tiles' 30 Hz pacing and from the
    # physical display refresh rate. A standalone harness reaches 60 fps but is
    # not representative of Samsung's keyguard integration.
    $geometricRendererSmali = Join-Path $samsungVisualEffectSmaliStage "com\samsung\android\visualeffect\lock\geometricmosaic\GeometricMosaicRenderer.smali"
    $geometricRendererContent = Get-Content -LiteralPath $geometricRendererSmali -Raw
    $geometricPacedDraw = @"

.method public onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V
    .registers 2
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;

    invoke-static {}, Lcom/codex/lle/SamsungLockBgEffectView;->paceGeometricMosaicFrame()V

    invoke-super {p0, p1}, Lcom/samsung/android/visualeffect/lock/common/GLTextureViewRenderer;->onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V

    return-void
.end method
"@
    if ($geometricRendererContent.Contains(".method public onDrawFrame(")) {
        throw "Unexpected GeometricMosaicRenderer: pacing override already present"
    }
    $lastMethodEnd = $geometricRendererContent.LastIndexOf($abstractRendererEnd)
    if ($lastMethodEnd -lt 0) {
        throw "Unexpected GeometricMosaicRenderer: constructor end not found"
    }
    $insertAt = $lastMethodEnd + $abstractRendererEnd.Length
    $geometricRendererContent = $geometricRendererContent.Insert($insertAt, $geometricPacedDraw)
    [IO.File]::WriteAllText($geometricRendererSmali, $geometricRendererContent, (New-Object Text.UTF8Encoding($false)))

    Run "java.exe" @("-cp", $smaliCp, "org.jf.smali.Main", "assemble", $samsungVisualEffectSmaliStage, "-o", $samsungVisualEffectDexStage)
    Run "jar.exe" @("uf", $aligned, "-C", $out, "classes2.dex")
} else {
    throw "Missing Samsung visual effect dex: $samsungVisualEffectDex"
}
if (Test-Path $s3SmaliSource) {
    Copy-Item $s3SmaliSource $s3SmaliStage -Recurse -Force
    Get-ChildItem $s3SmaliStage -Recurse -Filter *.smali | ForEach-Object {
        $content = Get-Content -LiteralPath $_.FullName -Raw
        $content = $content.Replace("Lcom/codex/s4unlockfx/R`$", "Lcom/codex/lle/R`$")
        $content = $content.Replace("R`$drawable;->keyguard_default_wallpaper", "R`$drawable;->s3_keyguard_default_wallpaper")
        [IO.File]::WriteAllText($_.FullName, $content, (New-Object Text.UTF8Encoding($false)))
    }
    # The original activity rendered an opaque full-screen surface and Ripple_Render does not
    # clear the default framebuffer per frame.  On a translucent accessibility SurfaceView that
    # leaves stale/opaque regions in alternating swap-chain buffers.  Clear the active default
    # framebuffer to transparent immediately before every original Samsung draw.
    $s3RendererSmali = Join-Path $s3SmaliStage "com\android\internal\policy\impl\keyguard\sec\CircleUnlockRippleRenderer.smali"
    $s3RendererContent = Get-Content -LiteralPath $s3RendererSmali -Raw
    $s3DrawFrameNeedle = ".method public onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V`r`n    .registers 32`r`n    .param p1, `"gl`"    # Ljavax/microedition/khronos/opengles/GL10;`r`n`r`n    .prologue"
    $s3DrawFramePatch = "$s3DrawFrameNeedle`r`n    invoke-static {}, Lcom/codex/lle/S3NativeRippleEffectView;->paceOriginalFrame()V`r`n`r`n    const/4 v0, 0x0`r`n`r`n    invoke-static {v0, v0, v0, v0}, Landroid/opengl/GLES20;->glClearColor(FFFF)V`r`n`r`n    const/16 v0, 0x4100`r`n`r`n    invoke-static {v0}, Landroid/opengl/GLES20;->glClear(I)V"
    if (-not $s3RendererContent.Contains($s3DrawFrameNeedle)) {
        throw "Unexpected S3 renderer smali: onDrawFrame patch point not found"
    }
    $s3RendererContent = $s3RendererContent.Replace($s3DrawFrameNeedle, $s3DrawFramePatch)
    [IO.File]::WriteAllText($s3RendererSmali, $s3RendererContent, (New-Object Text.UTF8Encoding($false)))
    $smaliCp = (Get-ChildItem -LiteralPath (Join-Path $repoRoot "unlock-effects-test\tools\java") -Filter "*.jar" | ForEach-Object { $_.FullName }) -join [IO.Path]::PathSeparator
    Run "java.exe" @("-cp", $smaliCp, "org.jf.smali.Main", "assemble", $s3SmaliStage, "-o", $s3Dex)
    if (-not (Test-Path $s3Dex)) {
        throw "S3 ripple smali assembly did not produce $s3Dex"
    }
    Run "jar.exe" @("uf", $aligned, "-C", $out, "classes3.dex")
} else {
    throw "Missing S3 ripple smali: $s3SmaliSource"
}
if (Test-Path $nativeLibs) {
    Run "jar.exe" @("uf", $aligned, "-C", $nativeLibs, ".")
}
if (Test-Path $abstractTileOriginalLib) {
    if (-not (Test-Path $abstractTileNativePatch)) {
        throw "Missing Abstract Tiles native transparency patch: $abstractTileNativePatch"
    }
    & powershell -ExecutionPolicy Bypass -File $abstractTileNativePatch `
        -InputLibrary $abstractTileOriginalLib `
        -OutputLibrary $abstractTilePatchedLib
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $abstractTilePatchedLib)) {
        throw "Abstract Tiles native transparency patch failed"
    }
    $abstractTileNativeStage = Join-Path $out "abstract-tile-native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $abstractTileNativeStage | Out-Null
    Copy-Item $abstractTilePatchedLib (Join-Path $abstractTileNativeStage "libsecveAbstractTile.so") -Force
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "abstract-tile-native"), "lib")
} else {
    throw "Missing original Abstract Tiles library: $abstractTileOriginalLib"
}
if (Test-Path $geometricMosaicOriginalLib) {
    if (-not (Test-Path $geometricMosaicNativePatch)) {
        throw "Missing Geometric Mosaic native transparency patch: $geometricMosaicNativePatch"
    }
    & powershell -ExecutionPolicy Bypass -File $geometricMosaicNativePatch `
        -InputLibrary $geometricMosaicOriginalLib `
        -OutputLibrary $geometricMosaicPatchedLib
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $geometricMosaicPatchedLib)) {
        throw "Geometric Mosaic native transparency patch failed"
    }
    $geometricMosaicNativeStage = Join-Path $out "geometric-mosaic-native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $geometricMosaicNativeStage | Out-Null
    Copy-Item $geometricMosaicPatchedLib (Join-Path $geometricMosaicNativeStage "libsecveGeometricMosaic.so") -Force
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "geometric-mosaic-native"), "lib")
} else {
    throw "Missing original Geometric Mosaic library: $geometricMosaicOriginalLib"
}
if (Test-Path $indigoOriginalLib) {
    if (-not (Test-Path $indigoNativePatch)) {
        throw "Missing Indigo Diffusion native transparency patch: $indigoNativePatch"
    }
    & powershell -ExecutionPolicy Bypass -File $indigoNativePatch `
        -InputLibrary $indigoOriginalLib `
        -OutputLibrary $indigoPatchedLib
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $indigoPatchedLib)) {
        throw "Indigo Diffusion native transparency patch failed"
    }
    $indigoNativeStage = Join-Path $out "indigo-native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $indigoNativeStage | Out-Null
    Copy-Item $indigoPatchedLib (Join-Path $indigoNativeStage "libsecveIndigoDiffusion.so") -Force
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "indigo-native"), "lib")
} else {
    throw "Missing original Indigo Diffusion library: $indigoOriginalLib"
}
if (Test-Path $watercolorCommonLib) {
    if (-not (Test-Path $watercolorNativePatch)) {
        throw "Missing Watercolor native transparency patch: $watercolorNativePatch"
    }
    & powershell -ExecutionPolicy Bypass -File $watercolorNativePatch `
        -InputLibrary $watercolorCommonLib `
        -OutputLibrary $watercolorPatchedCommonLib
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $watercolorPatchedCommonLib)) {
        throw "Watercolor native transparency patch failed"
    }
    if (-not (Test-Path $brilliantRingNativePatch)) {
        throw "Missing Brilliant Ring native transparency patch: $brilliantRingNativePatch"
    }
    & powershell -ExecutionPolicy Bypass -File $brilliantRingNativePatch `
        -InputLibrary $watercolorPatchedCommonLib `
        -OutputLibrary $brilliantRingPatchedCommonLib
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $brilliantRingPatchedCommonLib)) {
        throw "Brilliant Ring common transparency patch failed"
    }
    $watercolorNativeStage = Join-Path $out "watercolor-native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $watercolorNativeStage | Out-Null
    Copy-Item $brilliantRingPatchedCommonLib (Join-Path $watercolorNativeStage "libsecveSrkCommon.so") -Force
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "watercolor-native"), "lib")
} else {
    throw "Missing Watercolor common library: $watercolorCommonLib"
}
if (Test-Path $brilliantRingOriginalLib) {
    $brilliantRingActualSha256 =
        (Get-FileHash -LiteralPath $brilliantRingOriginalLib -Algorithm SHA256).Hash
    if ($brilliantRingActualSha256 -ne $brilliantRingOriginalSha256) {
        throw "Unexpected original Brilliant Ring SHA-256: $brilliantRingActualSha256"
    }
    $brilliantRingNativeStage = Join-Path $out "brilliant-ring-native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $brilliantRingNativeStage | Out-Null
    Copy-Item $brilliantRingOriginalLib `
        (Join-Path $brilliantRingNativeStage "libsecveBrilliantRing.so") -Force
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "brilliant-ring-native"), "lib")
} else {
    throw "Missing original Brilliant Ring library: $brilliantRingOriginalLib"
}
if (Test-Path $brilliantCutOriginalLib) {
    $brilliantCutActualSha256 =
        (Get-FileHash -LiteralPath $brilliantCutOriginalLib -Algorithm SHA256).Hash
    if ($brilliantCutActualSha256 -ne $brilliantCutOriginalSha256) {
        throw "Unexpected original Brilliant Cut SHA-256: $brilliantCutActualSha256"
    }
    if (-not (Test-Path $brilliantCutNativePatch)) {
        throw "Missing Brilliant Cut native transparency patch: $brilliantCutNativePatch"
    }
    if (-not (Test-Path $watercolorCommonLib)) {
        throw "Missing Brilliant Cut S4 common library: $watercolorCommonLib"
    }
    $brilliantCutCommonSha256 =
        (Get-FileHash -LiteralPath $watercolorCommonLib -Algorithm SHA256).Hash
    if ($brilliantCutCommonSha256 -ne $brilliantCutExpectedCommonSha256) {
        throw "Unexpected Brilliant Cut S4 common SHA-256: $brilliantCutCommonSha256"
    }
    if (-not (Test-Path $brilliantCutStlportLib)) {
        throw "Missing Brilliant Cut stlport library: $brilliantCutStlportLib"
    }
    $brilliantCutStlportSha256 =
        (Get-FileHash -LiteralPath $brilliantCutStlportLib -Algorithm SHA256).Hash
    if ($brilliantCutStlportSha256 -ne $brilliantCutExpectedStlportSha256) {
        throw "Unexpected Brilliant Cut stlport SHA-256: $brilliantCutStlportSha256"
    }
    & powershell -ExecutionPolicy Bypass -File $brilliantCutNativePatch `
        -InputLibrary $brilliantCutOriginalLib `
        -OutputLibrary $brilliantCutPatchedLib
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $brilliantCutPatchedLib)) {
        throw "Brilliant Cut native transparency patch failed"
    }
    $brilliantCutNativeStage = Join-Path $out "brilliant-cut-native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $brilliantCutNativeStage | Out-Null
    Copy-Item $brilliantCutPatchedLib `
        (Join-Path $brilliantCutNativeStage "libsecveBrilliantCut.so") -Force
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "brilliant-cut-native"), "lib")
} else {
    throw "Missing original Brilliant Cut library: $brilliantCutOriginalLib"
}
if (Test-Path $s3NativeLib) {
    if (-not (Test-Path $s3NativePatch)) {
        throw "Missing S3 native transparency patch: $s3NativePatch"
    }
    & powershell -ExecutionPolicy Bypass -File $s3NativePatch `
        -InputLibrary $s3NativeLib `
        -OutputLibrary $s3PatchedNativeLib
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $s3PatchedNativeLib)) {
        throw "S3 native transparency patch failed"
    }
    $s3NativeStage = Join-Path $out "s3-native\lib\armeabi-v7a"
    New-Item -ItemType Directory -Force -Path $s3NativeStage | Out-Null
    Copy-Item $s3PatchedNativeLib (Join-Path $s3NativeStage "libWaterRipple.so") -Force
    Run "jar.exe" @("uf", $aligned, "-C", (Join-Path $out "s3-native"), "lib")
} else {
    throw "Missing S3 native library: $s3NativeLib"
}

if ($ReleaseSigning) {
    if ([string]::IsNullOrWhiteSpace($ReleaseKeystorePath)) {
        $ReleaseKeystorePath = $env:LLE_RELEASE_KEYSTORE
    }
    if ([string]::IsNullOrWhiteSpace($ReleaseKeystorePath) -or
            -not (Test-Path -LiteralPath $ReleaseKeystorePath)) {
        throw "Missing stable release keystore. Pass -ReleaseKeystorePath or set LLE_RELEASE_KEYSTORE."
    }
    if ([string]::IsNullOrWhiteSpace($env:LLE_RELEASE_KEY_PASSWORD)) {
        throw "Missing LLE_RELEASE_KEY_PASSWORD for stable signing."
    }
} else {
    if (-not (Test-Path $keystore)) {
        New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
        if (-not (Test-Path $sourceKeystore)) {
            throw "Missing compatible debug keystore: $sourceKeystore"
        }
        Copy-Item $sourceKeystore $keystore -Force
    }
}

Run (Join-Path $buildTools "zipalign.exe") @("-f", "4", $aligned, (Join-Path $out "LLE-armeabi-v7a-zipaligned.apk"))
$signingArguments = if ($ReleaseSigning) {
    @("sign", "--ks", $ReleaseKeystorePath,
        "--ks-key-alias", $ReleaseKeyAlias,
        "--ks-pass", "env:LLE_RELEASE_KEY_PASSWORD",
        "--key-pass", "env:LLE_RELEASE_KEY_PASSWORD",
        "--out", $signed,
        (Join-Path $out "LLE-armeabi-v7a-zipaligned.apk"))
} else {
    @("sign", "--ks", $keystore,
        "--ks-pass", "pass:android", "--key-pass", "pass:android",
        "--out", $signed,
        (Join-Path $out "LLE-armeabi-v7a-zipaligned.apk"))
}
Run (Join-Path $buildTools "apksigner.bat") $signingArguments
Run (Join-Path $buildTools "apksigner.bat") @("verify", "--verbose", $signed)
if ($ReleaseSigning) {
    $certificateInfo = (& (Join-Path $buildTools "apksigner.bat") verify `
            --print-certs $signed) -join "`n"
    if ($LASTEXITCODE -ne 0 -or
            $certificateInfo -notmatch "(?i)certificate SHA-256 digest:\s*$releaseCertificateSha256") {
        throw "ARM32 stable certificate verification failed"
    }
}

$badging = (& (Join-Path $buildTools "aapt.exe") dump badging $signed) -join "`n"
if ($LASTEXITCODE -ne 0 -or
        $badging -notmatch "package: name='com\.codex\.lle'" -or
        $badging -notmatch "application-label:'L\.L\.E'") {
    throw "ARM32 identity verification failed; expected L.L.E / com.codex.lle"
}

$entries = @(& "jar.exe" tf $signed)
$nativeEntries = @($entries | Where-Object { $_ -like "lib/*" -and -not $_.EndsWith("/") })
$expectedNativeEntries = @(
    "lib/armeabi-v7a/libColourDropletEffect.so",
    "lib/armeabi-v7a/libSparklingBubblesEffect.so",
    "lib/armeabi-v7a/libWaterRipple.so",
    "lib/armeabi-v7a/libsecveAbstractTile.so",
    "lib/armeabi-v7a/libsecveBrilliantCut.so",
    "lib/armeabi-v7a/libsecveBrilliantRing.so",
    "lib/armeabi-v7a/libsecveGeometricMosaic.so",
    "lib/armeabi-v7a/libsecveIndigoDiffusion.so",
    "lib/armeabi-v7a/libsecveSrkCommon.so",
    "lib/armeabi-v7a/libsecveWaterColor.so",
    "lib/armeabi-v7a/libstlport.so"
)
$nativeDiff = Compare-Object ($nativeEntries | Sort-Object) ($expectedNativeEntries | Sort-Object)
if ($nativeDiff) {
    throw "Unexpected ARM32 APK native entries: $($nativeEntries -join ', ')"
}
if ($entries -match "arm64|x86") {
    throw "Non-ARM32 ABI found in APK"
}

Write-Host "Built ARM32-only APK: $signed"
Write-Host "Native entries: $($nativeEntries -join ', ')"
