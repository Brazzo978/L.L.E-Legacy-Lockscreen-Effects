param(
    [string]$Library
)

$ErrorActionPreference = "Stop"

if (-not $Library) {
    $appRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $Library = Join-Path $appRoot "native-libs\lib\armeabi-v7a\libsecveAbstractTile.so"
}

$originalCondition = [Text.Encoding]::ASCII.GetBytes(
    "if (line_mask.a == 0.0) discard;")
$transparentCondition = [Text.Encoding]::ASCII.GetBytes(
    "if (line_mask.a != 0.0) discard;")

function Find-Pattern([byte[]]$Bytes, [byte[]]$Pattern) {
    $matches = @()
    for ($i = 0; $i -le $Bytes.Length - $Pattern.Length; $i++) {
        $equal = $true
        for ($j = 0; $j -lt $Pattern.Length; $j++) {
            if ($Bytes[$i + $j] -ne $Pattern[$j]) {
                $equal = $false
                break
            }
        }
        if ($equal) {
            $matches += $i
        }
    }
    return $matches
}

$bytes = [IO.File]::ReadAllBytes($Library)
$alreadyPatched = @(Find-Pattern $bytes $transparentCondition)
if ($alreadyPatched.Count -eq 1) {
    Write-Host "Abstract Tiles transparent line pass already patched at 0x$($alreadyPatched[0].ToString('X'))"
    exit 0
}

$matches = @(Find-Pattern $bytes $originalCondition)
if ($matches.Count -ne 1) {
    throw "Expected exactly one Abstract Tiles line-shader condition, found $($matches.Count)"
}

[Array]::Copy($transparentCondition, 0, $bytes, $matches[0], $transparentCondition.Length)
[IO.File]::WriteAllBytes($Library, $bytes)
Write-Host "Patched Abstract Tiles transparent line pass at 0x$($matches[0].ToString('X'))"
