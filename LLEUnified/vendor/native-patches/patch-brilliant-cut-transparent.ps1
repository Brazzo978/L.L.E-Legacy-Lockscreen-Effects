param(
    [Parameter(Mandatory = $true)]
    [string]$InputLibrary,
    [Parameter(Mandatory = $true)]
    [string]$OutputLibrary
)

$ErrorActionPreference = "Stop"

$expectedLibrarySha256 = "46B7580078F373CD5129704B8294AD1B630665F27E6877A8ECB30A41BDF039C7"
$expectedPatchedLibrarySha256 = "BE85417DF8173827312FA5153B0BD13698DCAAAB579C16608D7E7B5281A2FB15"
$shaderStartMarker = "precision highp float;"

$standardMarker = "if (vAlpha == 0.0)"
$expectedStandardShaderSha256 = "BE42521A0E96507924F8658A0E23B9A34EF7B6D089339898FE909E79E13B47FA"
$expectedPatchedStandardShaderSha256 = "57FEC04AB7E3F34B1D385F17AAAFE981A81E160AA44AB4C7EC3DA79F2588BDB4"
$standardReplacement = "precision highp float;uniform sampler2D uMaskTexture,uBGTexture;uniform int uIsAffordanceOrUnlock;varying vec3 vAuxNormal;varying vec2 vUV;varying vec4 vGlare;varying float vShift,vAlpha;void main(){bool k=uIsAffordanceOrUnlock==1;float m=k?texture2D(uMaskTexture,vec2(vUV.x,1.-vUV.y)).x:vAlpha;m=clamp(m,0.,1.);if(m==0.){gl_FragColor=vec4(0.);return;}float q=sqrt(m);vec3 b=texture2D(uBGTexture,vUV).rgb;vec2 t=vUV;if(k)t+=vAuxNormal.xy*vShift*m;vec3 c=texture2D(uBGTexture,t).rgb;vec3 p=c-(1.-m)*b+m*vGlare.xyz;gl_FragColor=vec4(p/q,q);}"

$alphaUvMarker = "texture2D(uMaskTexture, vAlphaUV).x"
$expectedAlphaUvShaderSha256 = "08AA2EE0FD1681FD42C0A827A9BACEDD45C2E46218C78FCE2BF71ED7257D24CB"
$expectedPatchedAlphaUvShaderSha256 = "F24440676716ABFF8CC3CF7E9BF3B597DF602EAA6A2FC471CB4D0F07B5E8B7AD"
$alphaUvReplacement = "precision highp float;uniform sampler2D uMaskTexture,uBGTexture;varying vec3 vAuxNormal;varying vec2 vUV,vAlphaUV;varying vec4 vGlare;varying float vShift;void main(){float m=clamp(texture2D(uMaskTexture,vAlphaUV).x,0.,1.);if(m==0.){gl_FragColor=vec4(0.);return;}float q=sqrt(m);vec3 b=texture2D(uBGTexture,vUV).rgb;vec3 c=texture2D(uBGTexture,vUV+vAuxNormal.xy*vShift*m).rgb;vec3 p=c-(1.-m)*b+m*vGlare.xyz;gl_FragColor=vec4(p/q,q);}"

function Get-Sha256([byte[]]$Data) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return (($sha.ComputeHash($Data) | ForEach-Object { $_.ToString("X2") }) -join "")
    } finally {
        $sha.Dispose()
    }
}

function Get-Slice([byte[]]$Data, [int]$Offset, [int]$Length) {
    $slice = New-Object byte[] $Length
    [Array]::Copy($Data, $Offset, $slice, 0, $Length)
    return $slice
}

function Patch-Shader(
        [byte[]]$Data,
        [string]$Marker,
        [string]$Replacement,
        [string]$ExpectedBefore,
        [string]$ExpectedAfter,
        [string]$Label) {
    $ascii = [Text.Encoding]::ASCII
    $text = $ascii.GetString($Data)
    $markerOffset = $text.IndexOf($Marker, [StringComparison]::Ordinal)
    if ($markerOffset -lt 0 -or
            $text.IndexOf($Marker, $markerOffset + 1, [StringComparison]::Ordinal) -ge 0) {
        throw "Expected exactly one $Label shader marker"
    }

    $shaderStart = $text.LastIndexOf(
            $shaderStartMarker, $markerOffset, [StringComparison]::Ordinal)
    $shaderEnd = $text.IndexOf([char]0, $markerOffset)
    if ($shaderStart -lt 0 -or $shaderEnd -le $shaderStart) {
        throw "Unable to bound $Label fragment shader"
    }

    $shaderLength = $shaderEnd - $shaderStart
    $shaderBefore = Get-Slice $Data $shaderStart $shaderLength
    $shaderHashBefore = Get-Sha256 $shaderBefore
    if ($shaderHashBefore -ne $ExpectedBefore) {
        throw "Unexpected stock $Label shader SHA-256: $shaderHashBefore"
    }

    $replacementBytes = $ascii.GetBytes($Replacement)
    if ($replacementBytes.Length -gt $shaderLength) {
        throw "$Label replacement exceeds the original shader allocation"
    }
    [Array]::Copy($replacementBytes, 0, $Data, $shaderStart, $replacementBytes.Length)
    for ($i = $replacementBytes.Length; $i -lt $shaderLength; $i++) {
        $Data[$shaderStart + $i] = [byte]0x20
    }

    $shaderAfter = Get-Slice $Data $shaderStart $shaderLength
    $shaderHashAfter = Get-Sha256 $shaderAfter
    if ($shaderHashAfter -ne $ExpectedAfter) {
        throw "Unexpected patched $Label shader SHA-256: $shaderHashAfter"
    }
    Write-Host "$Label shader SHA-256: $shaderHashBefore -> $shaderHashAfter"
}

if (-not (Test-Path -LiteralPath $InputLibrary)) {
    throw "Missing original Brilliant Cut library: $InputLibrary"
}
$actualSha256 = (Get-FileHash -LiteralPath $InputLibrary -Algorithm SHA256).Hash
if ($actualSha256 -ne $expectedLibrarySha256) {
    throw "Unexpected original Brilliant Cut SHA-256: $actualSha256"
}

$bytes = [IO.File]::ReadAllBytes($InputLibrary)

# The stock final pass produces F = C + m*G over an opaque screenshot B, where
# C is the displaced screenshot sample, G is Samsung's glare and m is either
# the mask or the per-plane alpha. LLE renders into a transparent framebuffer
# with GL_SRC_ALPHA/GL_ONE_MINUS_SRC_ALPHA before SurfaceFlinger composites it
# over the same B. Set q=sqrt(m) so the first blend stores A=m, and emit
# P/q where P=F-(1-m)B=C-(1-m)B+mG. The first blend stores premultiplied P;
# SurfaceFlinger then adds (1-m)B and reconstructs F exactly. At m=0 the pass
# writes transparent black, so the untouched lockscreen remains visible.
Patch-Shader $bytes $standardMarker $standardReplacement `
    $expectedStandardShaderSha256 $expectedPatchedStandardShaderSha256 `
    "Brilliant Cut standard composite"
Patch-Shader $bytes $alphaUvMarker $alphaUvReplacement `
    $expectedAlphaUvShaderSha256 $expectedPatchedAlphaUvShaderSha256 `
    "Brilliant Cut alpha-UV composite"

$patchedSha256 = Get-Sha256 $bytes
if ($patchedSha256 -ne $expectedPatchedLibrarySha256) {
    throw "Unexpected patched Brilliant Cut library SHA-256: $patchedSha256"
}

$outputDir = Split-Path -Parent $OutputLibrary
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
[IO.File]::WriteAllBytes($OutputLibrary, $bytes)

Write-Host "Patched Brilliant Cut transparent compositing: $OutputLibrary"
Write-Host "Library SHA-256: $actualSha256 -> $patchedSha256"
