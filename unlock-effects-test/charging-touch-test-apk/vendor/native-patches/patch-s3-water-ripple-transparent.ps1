param(
    [Parameter(Mandatory = $true)]
    [string]$InputLibrary,
    [Parameter(Mandatory = $true)]
    [string]$OutputLibrary
)

$ErrorActionPreference = "Stop"

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
        throw "Replacement is longer than original: $New"
    }
    $matches = @(Find-All $Data $oldBytes)
    if ($matches.Count -ne $ExpectedCount) {
        throw "Expected $ExpectedCount matches for '$Old', found $($matches.Count)"
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
    throw "Missing input library: $InputLibrary"
}

$bytes = [IO.File]::ReadAllBytes($InputLibrary)

# Keep Samsung's original t/height calculation untouched because it controls
# the native reflection and specular look. Introduce a separate slope alpha
# while compacting the equivalent water texture lookup to fit in-place.
Patch-Ascii $bytes `
    "  vec4  waterColor = texture2D(sWaterTexture, vec2(vWaterTextureCoord.s, vWaterTextureCoord.t));" `
    "float a=length(vNormal.xy);vec4 waterColor=texture2D(sWaterTexture,vWaterTextureCoord);" `
    2

# Normal/ink-capable fragment path. Samsung adds the opaque background and
# writes alpha 1.0. Preserve its computed rippleRGB but premultiply it by the
# recovered local height energy t and use the same value as Surface alpha.
Patch-Ascii $bytes `
    "  gl_FragColor = vec4(rippleRGB / (1.0+w*ink_color), 1.0);" `
    "a=min(a*t*50.,1.);gl_FragColor=vec4(rippleRGB*a,a);" `
    1

# Secondary normal/gravity fragment path.
Patch-Ascii $bytes `
    "uniform float viewportHeight;" `
    "#define M min(a*t*50.,1.)" `
    1
Patch-Ascii $bytes `
    "  gl_FragColor = vec4(rippleRGB , 1.0);" `
    "gl_FragColor=vec4(rippleRGB*M,M);" `
    1

# Complex gravity path: preserve dirtyAlpha and use an otherwise-unused shader
# uniform declaration slot for a compact slope macro.
Patch-Ascii $bytes `
    "uniform float uWaterbrightness;" `
    "#define a length(vNormal.xy)*4." `
    1
Patch-Ascii $bytes `
    "  gl_FragColor = vec4(rippleRGB,1.0);" `
    "gl_FragColor=vec4(rippleRGB*a,a);" `
    1

$outputDir = Split-Path -Parent $OutputLibrary
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
[IO.File]::WriteAllBytes($OutputLibrary, $bytes)

Write-Host "Patched S3 WaterRipple transparency: $OutputLibrary"
