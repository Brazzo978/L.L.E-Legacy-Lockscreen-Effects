param(
    [Parameter(Mandatory = $true)]
    [string] $ReferenceDirectory,

    [Parameter(Mandatory = $true)]
    [string] $StagedDirectory,

    [Parameter(Mandatory = $true)]
    [string] $ReadElfPath,

    [Parameter(Mandatory = $true)]
    [string] $ObjdumpPath,

    [Parameter(Mandatory = $true)]
    [string] $StringsPath
)

$ErrorActionPreference = "Stop"

$inputHashes = @{
    "libColourDropletEffect.so" =
            "634DC703FF9288A4961B3E636B83DD89DDBF86DF6087D624DC19B4231E6C010C"
    "libSparklingBubblesEffect.so" =
            "F96E287CD20B411A863D07D012631FA61761FC35AEC50D4B4A4B454577B2C944"
    "libstlport.so" =
            "821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA"
}

$outputHashes = @{
    "libColourDropletEffect.so" =
            "38FFB25ADAA178D96B981C3EC0D616EC86B2F73EC5EBDDE8437E02D610D19EE4"
    "libSparklingBubblesEffect.so" =
            "B96EC92493477AF9F9958A8B7A6466BB4EDD5195145D47F339BB68A9C8552FC0"
    "libstlport.so" =
            "821B11D1EA2E1853D0DE0F547F9FE224100AAA53A500F69441765BB089615CCA"
}

$arm32PatchedHash =
        "2A6085607D3C7748365DDBEBCD37505FD3F13582EC1E5284E853E96FF8F66148"
$arm32ShaderHash =
        "D4DD042CA07D1D68595DB0F7B67576ABF0EE61CD404245A5B96D20256BA9698F"
$arm32ShaderOffset = 0x5c714
$arm32ShaderLength = 2785
$colourShaderOffset = 0x65268
$colourShaderCapacity = 10952

$bubblesPatches = @(
    @{
        Offset = 0x531c4
        Original = "03102E1E" # word 0x1e2e1003: fmov s3, #1.0
        Replacement = "E303271E" # word 0x1e2703e3: fmov s3, wzr
        Label = "transparent clear alpha"
    },
    @{
        Offset = 0x57144
        Original = "D716FF97" # word 0x97ff16d7: background scale call
        Replacement = "1F2003D5" # word 0xd503201f: nop
        Label = "first background scale call"
    },
    @{
        Offset = 0x5714c
        Original = "2118FF97" # word 0x97ff1821: background draw call
        Replacement = "1F2003D5"
        Label = "first background draw call"
    },
    @{
        Offset = 0x571c0
        Original = "B816FF97" # word 0x97ff16b8: background scale call
        Replacement = "1F2003D5"
        Label = "second background scale call"
    },
    @{
        Offset = 0x571c8
        Original = "0218FF97" # word 0x97ff1802: background draw call
        Replacement = "1F2003D5"
        Label = "second background draw call"
    }
)

function Get-Sha256([string] $Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Assert-Sha256([string] $Path, [string] $Expected, [string] $Label) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $Label`: $Path"
    }
    $actual = Get-Sha256 $Path
    if ($actual -ne $Expected) {
        throw "Unexpected SHA-256 for $Label`: $actual (expected $Expected)"
    }
}

function Convert-HexToBytes([string] $Hex) {
    if (($Hex.Length % 2) -ne 0) {
        throw "Odd-length hex string: $Hex"
    }
    $bytes = New-Object byte[] ($Hex.Length / 2)
    for ($i = 0; $i -lt $bytes.Length; $i++) {
        $bytes[$i] = [Convert]::ToByte($Hex.Substring($i * 2, 2), 16)
    }
    return $bytes
}

function Convert-BytesToHex([byte[]] $Bytes) {
    return (($Bytes | ForEach-Object { $_.ToString("X2") }) -join "")
}

function Get-ByteRange([byte[]] $Bytes, [int] $Offset, [int] $Length) {
    if ($Offset -lt 0 -or $Length -lt 0 -or $Offset + $Length -gt $Bytes.Length) {
        throw "Byte range outside file: offset=$Offset length=$Length size=$($Bytes.Length)"
    }
    $range = New-Object byte[] $Length
    [Array]::Copy($Bytes, $Offset, $range, 0, $Length)
    return $range
}

function Assert-Bytes(
        [byte[]] $Bytes,
        [int] $Offset,
        [string] $ExpectedHex,
        [string] $Label) {
    $expected = Convert-HexToBytes $ExpectedHex
    $actual = Get-ByteRange $Bytes $Offset $expected.Length
    $actualHex = Convert-BytesToHex $actual
    if ($actualHex -ne $ExpectedHex) {
        throw "Unexpected bytes for $Label at 0x$('{0:x}' -f $Offset): " +
                "$actualHex (expected $ExpectedHex)"
    }
}

function Set-Bytes(
        [byte[]] $Bytes,
        [int] $Offset,
        [string] $ReplacementHex) {
    $replacement = Convert-HexToBytes $ReplacementHex
    if ($Offset -lt 0 -or $Offset + $replacement.Length -gt $Bytes.Length) {
        throw "Replacement outside file at offset 0x$('{0:x}' -f $Offset)"
    }
    [Array]::Copy($replacement, 0, $Bytes, $Offset, $replacement.Length)
}

function Get-ByteArraySha256([byte[]] $Bytes) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return (($sha.ComputeHash($Bytes) |
                ForEach-Object { $_.ToString("X2") }) -join "")
    } finally {
        $sha.Dispose()
    }
}

function Write-StagedBytes([string] $Path, [byte[]] $Bytes) {
    $temp = "$Path.lle64-patch-tmp"
    try {
        [IO.File]::WriteAllBytes($temp, $Bytes)
        Move-Item -LiteralPath $temp -Destination $Path -Force
    } finally {
        if (Test-Path -LiteralPath $temp) {
            Remove-Item -LiteralPath $temp -Force
        }
    }
}

function Invoke-Tool([string] $Path, [string[]] $Arguments, [string] $Label) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing $Label tool: $Path"
    }
    $output = & $Path @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
    return ($output -join "`n")
}

function Assert-Elf(
        [string] $Path,
        [string] $Name,
        [bool] $NeedsStlport) {
    $header = Invoke-Tool $ReadElfPath @("-h", $Path) "llvm-readelf -h"
    if ($header -notmatch "Machine:\s+AArch64") {
        throw "$Name patched output is not AArch64"
    }
    $dynamic = Invoke-Tool $ReadElfPath @("-d", $Path) "llvm-readelf -d"
    if ($dynamic -notmatch "SONAME.*\[$([regex]::Escape($Name))\]") {
        throw "$Name patched output has an unexpected SONAME"
    }
    if ($NeedsStlport -and $dynamic -notmatch "NEEDED.*\[libstlport\.so\]") {
        throw "$Name patched output lost its libstlport.so dependency"
    }
}

$referenceFull = [IO.Path]::GetFullPath($ReferenceDirectory)
$stagedFull = [IO.Path]::GetFullPath($StagedDirectory)
$referencePrefix = $referenceFull.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$stagedPrefix = $stagedFull.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if ($referenceFull.Equals($stagedFull, [StringComparison]::OrdinalIgnoreCase) -or
        $stagedPrefix.StartsWith($referencePrefix,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to patch the reference directory or one of its descendants"
}
if (-not (Test-Path -LiteralPath $stagedFull)) {
    throw "Missing staged native directory: $stagedFull"
}

$lleRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$arm32PatchedDroplet = Join-Path $lleRoot `
        "reference\arm32-original\native-libs\armeabi-v7a\libColourDropletEffect.so"
Assert-Sha256 $arm32PatchedDroplet $arm32PatchedHash `
        "verified ARM32 patched Colour Droplet source"

$arm32Bytes = [IO.File]::ReadAllBytes($arm32PatchedDroplet)
if ($arm32ShaderOffset + $arm32ShaderLength -ge $arm32Bytes.Length -or
        $arm32Bytes[$arm32ShaderOffset + $arm32ShaderLength] -ne 0) {
    throw "ARM32 transparent shader length/terminator changed"
}
$shaderBytes = Get-ByteRange $arm32Bytes $arm32ShaderOffset $arm32ShaderLength
if ((Get-ByteArraySha256 $shaderBytes) -ne $arm32ShaderHash) {
    throw "ARM32 transparent shader content changed"
}
$shaderText = [Text.Encoding]::ASCII.GetString($shaderBytes)
foreach ($required in @(
    "precision mediump float;",
    "gl_FragColor = vec4(0.0, 0.0, 0.0, shadow * 0.2 + keep);",
    "gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0 + keep);",
    "gl_FragColor = vec4(mix(bg_color.rgb, color_direction.rgb, smooth) + keep, 1.0);",
    "gl_FragColor = vec4(bg_color.rgb + keep, 1.0);"
)) {
    if (-not $shaderText.Contains($required)) {
        throw "ARM32 transparent shader is missing: $required"
    }
}
if ($shaderBytes.Length -ge $colourShaderCapacity) {
    throw "Transparent shader does not fit the ARM64 shader slot"
}

foreach ($name in $inputHashes.Keys) {
    $reference = Join-Path $referenceFull $name
    $staged = Join-Path $stagedFull $name
    if (-not ([IO.Path]::GetFullPath($staged)).StartsWith(
            $stagedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Staged path escaped the build directory: $staged"
    }
    Assert-Sha256 $reference $inputHashes[$name] "AOJ4 reference $name"
    Assert-Sha256 $staged $inputHashes[$name] "unpatched staged $name"
}

$colourPath = Join-Path $stagedFull "libColourDropletEffect.so"
$colourBytes = [IO.File]::ReadAllBytes($colourPath)
if ($colourShaderOffset + $colourShaderCapacity -ge $colourBytes.Length -or
        $colourBytes[$colourShaderOffset + $colourShaderCapacity] -ne 0) {
    throw "ARM64 Colour Droplet shader slot length/terminator changed"
}
$originalShaderText = [Text.Encoding]::ASCII.GetString(
        $colourBytes, $colourShaderOffset, $colourShaderCapacity)
foreach ($required in @(
    "uniform sampler2D uBG;",
    "uniform sampler2D uDensity;",
    "uniform sampler2D uColorNDirection;",
    "gl_FragColor.a = 1.0;"
)) {
    if (-not $originalShaderText.Contains($required)) {
        throw "ARM64 original Colour Droplet shader is missing: $required"
    }
}
for ($i = 0; $i -lt $colourShaderCapacity; $i++) {
    $colourBytes[$colourShaderOffset + $i] = 0
}
[Array]::Copy($shaderBytes, 0, $colourBytes, $colourShaderOffset, $shaderBytes.Length)
Write-StagedBytes $colourPath $colourBytes

$bubblesPath = Join-Path $stagedFull "libSparklingBubblesEffect.so"
$bubblesBytes = [IO.File]::ReadAllBytes($bubblesPath)
foreach ($patch in $bubblesPatches) {
    Assert-Bytes $bubblesBytes $patch.Offset $patch.Original $patch.Label
    Set-Bytes $bubblesBytes $patch.Offset $patch.Replacement
}
Write-StagedBytes $bubblesPath $bubblesBytes

# Verify references again after writing to prove that patching stayed in build/.
foreach ($name in $inputHashes.Keys) {
    Assert-Sha256 (Join-Path $referenceFull $name) $inputHashes[$name] `
            "unchanged AOJ4 reference $name"
}

$colourPatchedBytes = [IO.File]::ReadAllBytes($colourPath)
$patchedShader = Get-ByteRange $colourPatchedBytes $colourShaderOffset $shaderBytes.Length
if ((Get-ByteArraySha256 $patchedShader) -ne $arm32ShaderHash -or
        $colourPatchedBytes[$colourShaderOffset + $shaderBytes.Length] -ne 0) {
    throw "ARM64 Colour Droplet shader patch verification failed"
}
for ($i = $shaderBytes.Length; $i -lt $colourShaderCapacity; $i++) {
    if ($colourPatchedBytes[$colourShaderOffset + $i] -ne 0) {
        throw "ARM64 Colour Droplet shader padding is not zero at relative offset $i"
    }
}

$bubblesPatchedBytes = [IO.File]::ReadAllBytes($bubblesPath)
foreach ($patch in $bubblesPatches) {
    Assert-Bytes $bubblesPatchedBytes $patch.Offset $patch.Replacement `
            "patched $($patch.Label)"
}

Assert-Elf $colourPath "libColourDropletEffect.so" $true
Assert-Elf $bubblesPath "libSparklingBubblesEffect.so" $true
Assert-Elf (Join-Path $stagedFull "libstlport.so") "libstlport.so" $false

$colourStrings = Invoke-Tool $StringsPath @("-a", $colourPath) "llvm-strings Colour Droplet"
foreach ($required in @(
    "shadow * 0.2 + keep",
    "0.0 + keep",
    "mix(bg_color.rgb, color_direction.rgb, smooth) + keep"
)) {
    if (-not $colourStrings.Contains($required)) {
        throw "Patched Colour Droplet strings are missing: $required"
    }
}
$bubblesStrings = Invoke-Tool $StringsPath @("-a", $bubblesPath) "llvm-strings Bubbles"
foreach ($required in @("uBGTexMap", "uMaskMap", "PointerAlpha")) {
    if (-not $bubblesStrings.Contains($required)) {
        throw "Patched Bubbles lost required shader string: $required"
    }
}

$clearDisassembly = Invoke-Tool $ObjdumpPath @(
    "-d", "--start-address=0x531bc", "--stop-address=0x531e4", $bubblesPath
) "llvm-objdump Bubbles clear"
if ($clearDisassembly -notmatch "531c4:\s+1e2703e3\s+fmov\s+s3,\s*wzr") {
    throw "Patched Bubbles clear alpha disassembly mismatch"
}
$drawDisassembly = Invoke-Tool $ObjdumpPath @(
    "-d", "--start-address=0x5710c", "--stop-address=0x57240", $bubblesPath
) "llvm-objdump Bubbles drawApp"
foreach ($offset in @("57144", "5714c", "571c0", "571c8")) {
    if ($drawDisassembly -notmatch "$offset`:\s+d503201f\s+nop") {
        throw "Patched Bubbles drawApp is not NOP at 0x$offset"
    }
}

foreach ($name in $outputHashes.Keys) {
    $staged = Join-Path $stagedFull $name
    $actual = Get-Sha256 $staged
    $expected = $outputHashes[$name]
    if ($expected -and $actual -ne $expected) {
        throw "Unexpected patched SHA-256 for $name`: $actual (expected $expected)"
    }
    Write-Host "Patched Note 5 ARM64 $name SHA-256: $actual"
}

Write-Host "Verified Note 5 ARM64 transparent native staging"
