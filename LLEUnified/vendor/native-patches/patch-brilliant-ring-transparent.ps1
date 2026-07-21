param(
    [Parameter(Mandatory = $true)]
    [string]$InputLibrary,
    [Parameter(Mandatory = $true)]
    [string]$OutputLibrary
)

$ErrorActionPreference = "Stop"

$expectedShaderSha256 = "54A44B79CB19E4B4F6DBEA96942E0619919284BEAE0645ED45999934F0FEEB35"
$expectedPatchedShaderSha256 = "F47CBB18124040A9F315610507E9C28E9F271D73BDFFAA8AB4D179B1210CA7F8"
$shaderStartMarker = "precision lowp float;"
$ringMarker = "uniform sampler2D uDiamondMap;"
$oldAlphaOutput = "gl_FragColor.a = uAlpha;"
$newAlphaOutput = "gl_FragColor.a=(alpha!=0.0)?uAlpha:0.0;"

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

if (-not (Test-Path -LiteralPath $InputLibrary)) {
    throw "Missing input library: $InputLibrary"
}

$bytes = [IO.File]::ReadAllBytes($InputLibrary)
$ascii = [Text.Encoding]::ASCII
$text = $ascii.GetString($bytes)

$ringOffset = $text.IndexOf($ringMarker, [StringComparison]::Ordinal)
if ($ringOffset -lt 0 -or
        $text.IndexOf($ringMarker, $ringOffset + 1, [StringComparison]::Ordinal) -ge 0) {
    throw "Expected exactly one Brilliant Ring final shader marker"
}

$shaderStart = $text.LastIndexOf(
        $shaderStartMarker, $ringOffset, [StringComparison]::Ordinal)
$shaderEnd = $text.IndexOf([char]0, $ringOffset)
if ($shaderStart -lt 0 -or $shaderEnd -le $shaderStart) {
    throw "Unable to bound Brilliant Ring final fragment shader"
}

$shaderLength = $shaderEnd - $shaderStart
$shaderBefore = Get-Slice $bytes $shaderStart $shaderLength
$shaderHashBefore = Get-Sha256 $shaderBefore
if ($shaderHashBefore -ne $expectedShaderSha256) {
    throw "Unexpected stock Brilliant Ring shader SHA-256: $shaderHashBefore"
}

$alphaOffset = $text.IndexOf(
        $oldAlphaOutput, $shaderStart, [StringComparison]::Ordinal)
$nextAlphaOffset = if ($alphaOffset -ge 0) {
    $text.IndexOf(
            $oldAlphaOutput,
            $alphaOffset + 1,
            [StringComparison]::Ordinal)
} else {
    -1
}
if ($alphaOffset -lt $shaderStart -or $alphaOffset -ge $shaderEnd -or
        ($nextAlphaOffset -ge 0 -and $nextAlphaOffset -lt $shaderEnd)) {
    throw "Expected exactly one stock Brilliant Ring alpha output"
}

$replacement = $ascii.GetBytes($newAlphaOutput)
$oldBytes = $ascii.GetBytes($oldAlphaOutput)
if ($replacement.Length -le $oldBytes.Length) {
    throw "Brilliant Ring alpha replacement no longer exercises reserved shader padding"
}
if ($alphaOffset + $replacement.Length -gt $shaderEnd) {
    throw "Brilliant Ring alpha replacement exceeds shader bounds"
}
for ($i = $oldBytes.Length; $i -lt $replacement.Length; $i++) {
    $padding = $bytes[$alphaOffset + $i]
    if ($padding -ne [byte]0x09 -and $padding -ne [byte]0x20) {
        throw "Unexpected non-whitespace byte in Brilliant Ring shader padding"
    }
}

# Preserve Samsung's final RGB branch and every preceding CPU/radial/advect pass.
# Only the framebuffer alpha becomes transparent when that same stock branch is
# inactive; active pixels retain the original uAlpha output.
[Array]::Copy($replacement, 0, $bytes, $alphaOffset, $replacement.Length)

$shaderAfter = Get-Slice $bytes $shaderStart $shaderLength
$shaderHashAfter = Get-Sha256 $shaderAfter
if ($shaderHashAfter -ne $expectedPatchedShaderSha256) {
    throw "Unexpected patched Brilliant Ring shader SHA-256: $shaderHashAfter"
}

$outputDir = Split-Path -Parent $OutputLibrary
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
[IO.File]::WriteAllBytes($OutputLibrary, $bytes)

Write-Host "Patched Brilliant Ring inactive-branch alpha: $OutputLibrary"
Write-Host "Ring shader SHA-256: $shaderHashBefore -> $shaderHashAfter"
