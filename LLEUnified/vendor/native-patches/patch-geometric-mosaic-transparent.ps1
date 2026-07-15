param(
    [Parameter(Mandatory = $true)]
    [string]$InputLibrary,
    [Parameter(Mandatory = $true)]
    [string]$OutputLibrary
)

$ErrorActionPreference = "Stop"
$expectedSha256 = "A16F926D14396E2C78E50AE48089860BD9B5156FB77ECC99A3E4E7694FE06DD8"

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
        throw "Expected $ExpectedCount Geometric Mosaic shader tails, found $($matches.Count)"
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

if (-not (Test-Path -LiteralPath $InputLibrary)) {
    throw "Missing original Geometric Mosaic library: $InputLibrary"
}
$actualSha256 = (Get-FileHash -LiteralPath $InputLibrary -Algorithm SHA256).Hash
if ($actualSha256 -ne $expectedSha256) {
    throw "Unexpected original Geometric Mosaic SHA-256: $actualSha256"
}

$bytes = [IO.File]::ReadAllBytes($InputLibrary)

# Samsung renders the final scene with straight-alpha blending
# (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA). On its opaque keyguard target the
# stock shader can directly mix wallpaper and mosaic. LLE instead starts from
# a transparent target. If m is the intended mosaic coverage, output sqrt(m)
# as both the source alpha and the RGB multiplier: the fixed-function blend
# then stores exactly premultiplied RGB=m*c6 and A=m for SurfaceFlinger.
$oldTail = "gl_FragColor = alpha * texture2D(uBackground, vec2(UVNorm.x, 1.0 - UVNorm.y)) + (1.0 - alpha) * vec4(c6, 1.0); } else gl_FragColor = texture2D(uBackground, vec2(UVNorm.x, 1.0 - UVNorm.y));"
$newTail = "float m=clamp(1.0-alpha,0.0,1.0);float a=sqrt(m);gl_FragColor=vec4(c6*a,a); } else gl_FragColor=vec4(0.0);"
Patch-Ascii $bytes $oldTail $newTail 2

$outputDir = Split-Path -Parent $OutputLibrary
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
[IO.File]::WriteAllBytes($OutputLibrary, $bytes)
Write-Host "Patched Geometric Mosaic transparent compositing: $OutputLibrary"
