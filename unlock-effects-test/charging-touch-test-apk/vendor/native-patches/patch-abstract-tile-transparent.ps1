param(
    [Parameter(Mandatory = $true)]
    [string]$InputLibrary,
    [Parameter(Mandatory = $true)]
    [string]$OutputLibrary
)

$ErrorActionPreference = "Stop"
$expectedSha256 = "F8E8BDF48D069F76AF9923D68474A7047C621DD763D3E6D96C4F940025643840"

function Find-All([byte[]]$Data, [byte[]]$Needle) {
    $matches = New-Object System.Collections.Generic.List[int]
    for ($i = 0; $i -le $Data.Length - $Needle.Length; $i++) {
        $equal = $true
        for ($j = 0; $j -lt $Needle.Length; $j++) {
            if ($Data[$i + $j] -ne $Needle[$j]) {
                $equal = $false
                break
            }
        }
        if ($equal) {
            $matches.Add($i)
        }
    }
    return $matches
}

function Patch-Ascii([byte[]]$Data, [string]$Old, [string]$New, [int]$ExpectedCount) {
    $ascii = [Text.Encoding]::ASCII
    $oldBytes = $ascii.GetBytes($Old)
    $newBytes = $ascii.GetBytes($New)
    if ($newBytes.Length -gt $oldBytes.Length) {
        throw "Replacement is longer than original"
    }
    $matches = @(Find-All $Data $oldBytes)
    if ($matches.Count -ne $ExpectedCount) {
        throw "Expected $ExpectedCount matches for Abstract Tiles shader, found $($matches.Count)"
    }
    foreach ($offset in $matches) {
        for ($i = 0; $i -lt $oldBytes.Length; $i++) {
            $Data[$offset + $i] = if ($i -lt $newBytes.Length) {
                $newBytes[$i]
            } else {
                [byte]0x20
            }
        }
    }
}

function Patch-BytesAt([byte[]]$Data, [int]$Offset, [byte[]]$Old, [byte[]]$New) {
    if ($Old.Length -ne $New.Length) {
        throw "ARM patch length mismatch"
    }
    for ($i = 0; $i -lt $Old.Length; $i++) {
        if ($Data[$Offset + $i] -ne $Old[$i]) {
            throw "Unexpected ARM bytes at 0x$($Offset.ToString('X'))"
        }
    }
    [Array]::Copy($New, 0, $Data, $Offset, $New.Length)
}

if (-not (Test-Path -LiteralPath $InputLibrary)) {
    throw "Missing original Abstract Tiles library: $InputLibrary"
}
$actualSha256 = (Get-FileHash -LiteralPath $InputLibrary -Algorithm SHA256).Hash
if ($actualSha256 -ne $expectedSha256) {
    throw "Unexpected original Abstract Tiles SHA-256: $actualSha256"
}

$bytes = [IO.File]::ReadAllBytes($InputLibrary)

# Omit only Samsung's opaque fullscreen Background::renderFrame call. Preserve
# the native draw order and blend modes: scatter and line use straight-alpha,
# while the final tile renderer explicitly switches to GL_ONE, GL_ONE.
Patch-BytesAt $bytes 0x13410 `
    ([byte[]](0xF1,0xB8,0xFF,0xEB)) `
    ([byte[]](0x00,0x00,0xA0,0xE1))

# The final tile pass uses GL_ONE, GL_ONE. Stock could write straight RGB because
# it rendered over Samsung's opaque keyguard background. On Android's transparent
# premultiplied TextureView, multiply tile RGB by native alpha while retaining the
# original alpha, brightness and additive overlap behavior.
$oldTileShader = "precision mediump float;`n" +
        "varying vec2 UV; varying float alpha; varying float bri; uniform sampler2D uTextureOrigin; void main() { gl_FragColor = vec4(texture2D(uTextureOrigin, UV).rgb + bri, alpha); }"
$newTileShader = "precision mediump float;varying vec2 UV;varying float alpha;varying float bri;uniform sampler2D uTextureOrigin;void main(){gl_FragColor=vec4((texture2D(uTextureOrigin,UV).rgb+bri)*alpha,alpha);}"
Patch-Ascii $bytes $oldTileShader $newTileShader 1

# The following pass is intentionally additive (GL_ONE, GL_ONE). Keep its RGB
# light delta but prevent it from making the accessibility surface opaque.
Patch-Ascii $bytes `
    "gl_FragColor = vec4(alpha, alpha, alpha, 1.0);" `
    "gl_FragColor = vec4(alpha, alpha, alpha, 0.0);" `
    1

# The stock line shader writes absolute wallpaper RGB and depends on the omitted
# opaque Background. Make its surviving fragments alpha-zero so straight-alpha
# blending discards the incompatible pass while retaining the authentic asset.
Patch-Ascii $bytes `
    "if (line_mask.a == 0.0) discard;" `
    "if (line_mask.a != 0.0) discard;" `
    1

$outputDir = Split-Path -Parent $OutputLibrary
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
[IO.File]::WriteAllBytes($OutputLibrary, $bytes)
Write-Host "Patched Abstract Tiles transparent compositing: $OutputLibrary"
