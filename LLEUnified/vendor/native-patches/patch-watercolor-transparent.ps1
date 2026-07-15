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

# SPDrawMixWaterBrush final pass with the effect-wide fade. Samsung rendered an
# opaque wallpaper and then replaced its alpha with uAlphaRatio. For an overlay,
# emit only the native density contribution using local brush alpha multiplied
# by that same fade. RGB is premultiplied for Android Surface composition.
$oldFadeOutput = "gl_FragColor = mix(TexColor, DensityColor, AlphaColor.a); " + "`n" +
        "`tgl_FragColor.a = uAlphaRatio;"
$newFadeOutput = "float a=AlphaColor.a*uAlphaRatio;gl_FragColor=vec4(DensityColor.rgb*a,a);"
Patch-Ascii $bytes $oldFadeOutput $newFadeOutput 1

# Alternate Watercolor mix pass. Preserve Samsung's density, saturation and
# refraction math; translate only its final opaque background mix to local alpha.
Patch-Ascii $bytes `
    "gl_FragColor = mix(TexColor, DensityColor, AlphaColor);" `
    "gl_FragColor=vec4(DensityColor.rgb,1.)*AlphaColor;" `
    1

$outputDir = Split-Path -Parent $OutputLibrary
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
[IO.File]::WriteAllBytes($OutputLibrary, $bytes)

Write-Host "Patched Watercolor local transparency: $OutputLibrary"
