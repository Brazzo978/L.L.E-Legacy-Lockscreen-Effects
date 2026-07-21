[CmdletBinding()]
param(
    [string]$LibraryPath = "F:\New project\unlock-effects-test\tabs\T705_ANF8_brilliantcut_native\libBrilliantCutEffect.so",
    [string]$KeyguardApk = "F:\New project\unlock-effects-test\extracted\tab_t705_anf8_system_files\priv-app\Keyguard.apk",
    [string]$ObjdumpPath = "F:\New project\unlock-effects-test\tools\android-ndk-r27d\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objdump.exe",
    [string]$SevenZipPath = "C:\Program Files\7-Zip\7z.exe",
    [string]$OutputJson,
    [switch]$ExtractBrush
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $PSCommandPath
$lleRoot = (Resolve-Path (Join-Path $scriptRoot "..\..\..")).Path
$brushDestination = Join-Path $lleRoot "res\drawable-nodpi\brilliantcut_light_brush.png"

$expectedLibraryHash = "694E860290A277570992142E965B858DBB8D75FF168030AC0661EDB01B426EC2"
$expectedBrushHash = "D4A6C4E27203812A506B3C78AA4833D3BEBB09FDEE23171045F8BC87EB6C3151"

foreach ($required in @($LibraryPath, $KeyguardApk, $ObjdumpPath)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required input not found: $required"
    }
}

$actualLibraryHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $LibraryPath).Hash
if ($actualLibraryHash -ne $expectedLibraryHash) {
    throw "Unexpected Brilliant Cut oracle hash: $actualLibraryHash"
}

if ($ExtractBrush) {
    if (-not (Test-Path -LiteralPath $SevenZipPath)) {
        throw "7-Zip not found: $SevenZipPath"
    }

    $brushDirectory = Split-Path -Parent $brushDestination
    New-Item -ItemType Directory -Force -Path $brushDirectory | Out-Null
    & $SevenZipPath e -y "-o$brushDirectory" $KeyguardApk `
        "res/drawable-nodpi/brilliantcut_light_brush.png" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip failed while extracting the Brilliant Cut brush"
    }
}

if (-not (Test-Path -LiteralPath $brushDestination)) {
    throw "Canonical brush is missing: $brushDestination. Run again with -ExtractBrush."
}

$actualBrushHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $brushDestination).Hash
if ($actualBrushHash -ne $expectedBrushHash) {
    throw "Unexpected Brilliant Cut brush hash: $actualBrushHash"
}

# Stock CreateGeometry() uses a computed four-way jump at ELF VA 0xaaf8.
# Each branch is straight-line construction of Vector3 values and Plane objects.
# The branch end is the epilogue immediately before the next branch.
$geometries = @(
    [ordered]@{ id = 0; name = "portrait_special";  start = "0x20c74"; stop = "0x29190" },
    [ordered]@{ id = 1; name = "portrait_normal";   start = "0x1ae10"; stop = "0x20c74" },
    [ordered]@{ id = 2; name = "landscape_special"; start = "0x0ab28"; stop = "0x134d4" },
    [ordered]@{ id = 3; name = "landscape_normal";  start = "0x134e0"; stop = "0x1ae10" }
)

function Count-Call {
    param(
        [string[]]$Disassembly,
        [string]$Address
    )

    return @($Disassembly | Select-String -Pattern ("\bbl\s+0x" + $Address + "\b")).Count
}

$results = foreach ($geometry in $geometries) {
    $disassembly = & $ObjdumpPath -d --no-show-raw-insn `
        "--start-address=$($geometry.start)" `
        "--stop-address=$($geometry.stop)" `
        $LibraryPath
    if ($LASTEXITCODE -ne 0) {
        throw "llvm-objdump failed for geometry $($geometry.id)"
    }

    $plane3 = Count-Call $disassembly "8448"
    $plane6 = Count-Call $disassembly "895c"
    $plane9 = Count-Call $disassembly "8b74"
    $plane12 = Count-Call $disassembly "8588"
    $addPlane = Count-Call $disassembly "a79c"
    $vectorCount = Count-Call $disassembly "79d4"
    $expectedVectors = 3 * $plane3 + 6 * $plane6 + 9 * $plane9 + 12 * $plane12

    if ($addPlane -ne ($plane3 + $plane6 + $plane9 + $plane12)) {
        throw "Plane/AddPlane mismatch in geometry $($geometry.id)"
    }
    if ($vectorCount -ne $expectedVectors) {
        throw "Vector/Plane mismatch in geometry $($geometry.id)"
    }

    [ordered]@{
        id = $geometry.id
        name = $geometry.name
        elf_range = [ordered]@{ start = $geometry.start; stop = $geometry.stop }
        plane_count = $addPlane
        vertex_count = $vectorCount
        plane_arity = [ordered]@{
            "3" = $plane3
            "6" = $plane6
            "9" = $plane9
            "12" = $plane12
        }
    }
}

$report = [ordered]@{
    schema = "lle.brilliant_cut.stock_mesh_call_counts.v1"
    source = [ordered]@{
        library = $LibraryPath
        sha256 = $actualLibraryHash
        create_geometry_elf_va = "0x0aaf8"
        brush = $brushDestination
        brush_sha256 = $actualBrushHash
    }
    constructors = [ordered]@{
        vector3 = "0x079d4"
        plane3 = "0x08448"
        plane6 = "0x0895c"
        plane9 = "0x08b74"
        plane12 = "0x08588"
        add_plane = "0x0a79c"
    }
    geometries = @($results)
}

$json = $report | ConvertTo-Json -Depth 8
if ($OutputJson) {
    $parent = Split-Path -Parent $OutputJson
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    Set-Content -LiteralPath $OutputJson -Value $json -Encoding utf8
}

$json
