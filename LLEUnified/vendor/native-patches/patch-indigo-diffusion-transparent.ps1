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

# Preserve Samsung's optics and expose a separate local slope term for alpha.
Patch-Ascii $bytes `
    "  vec4  waterColor = texture2D(sWaterTexture, vec2(vWaterTextureCoord.s, vWaterTextureCoord.t));" `
    "float a=length(vNormal.xy);vec4 waterColor=texture2D(sWaterTexture,vWaterTextureCoord);" `
    2

# The ink shader needs a density-aware alpha: unlike plain Ripple, the ink
# remains visible after the height wave flattens. Compact equivalent uniform
# declarations to fit the alpha macro into the original ELF string storage.
$lf = [char]10
$oldInkUniforms = "uniform sampler2D sWaterTexture;${lf}" `
        + "uniform sampler2D sBGTexture;${lf}" `
        + "uniform float alphaRatio1;${lf}" `
        + "uniform vec2 Scale;${lf}" `
        + "uniform float intensity;${lf}" `
        + "uniform vec3 ink_color;${lf}" `
        + "uniform float fresnelRatio;${lf}" `
        + "uniform float specularRatio;${lf}" `
        + "uniform float exponent;"
$newInkUniforms = "uniform sampler2D sWaterTexture,sBGTexture;${lf}" `
        + "uniform float alphaRatio1,intensity,fresnelRatio,specularRatio,exponent;${lf}" `
        + "uniform vec2 Scale;uniform vec3 ink_color;${lf}" `
        + "#define A min(max(a*t*50.,w),1.)"
Patch-Ascii $bytes $oldInkUniforms $newInkUniforms 1
Patch-Ascii $bytes `
    "  gl_FragColor = vec4(rippleRGB / (1.0+w*ink_color), 1.0);" `
    "gl_FragColor=vec4(rippleRGB/(1.+w*ink_color)*A,A);" `
    1

# Keep the other embedded paths transparent as well. Indigo normally selects
# the ink path, but patching the siblings prevents an opaque frame during setup.
Patch-Ascii $bytes `
    "uniform float viewportHeight;" `
    "#define M min(a*t*50.,1.)" `
    1
Patch-Ascii $bytes `
    "  gl_FragColor = vec4(rippleRGB , 1.0);" `
    "gl_FragColor=vec4(rippleRGB*M,M);" `
    1
$outputDir = Split-Path -Parent $OutputLibrary
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
[IO.File]::WriteAllBytes($OutputLibrary, $bytes)

Write-Host "Patched Indigo Diffusion transparency: $OutputLibrary"
