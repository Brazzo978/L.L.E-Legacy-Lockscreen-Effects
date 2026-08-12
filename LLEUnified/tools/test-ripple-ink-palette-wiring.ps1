<#
.SYNOPSIS
Verifies the production ARM64 Ripple Ink picker wiring without building an APK.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$control = Get-Content -LiteralPath (Join-Path $repositoryRoot 'src/com/codex/lle/ControlActivity.java') -Raw
$engine = Get-Content -LiteralPath (Join-Path $repositoryRoot 'src/com/codex/lle/RippleInkPortEngine.java') -Raw
$prefs = Get-Content -LiteralPath (Join-Path $repositoryRoot 'src/com/codex/lle/OverlayPrefs.java') -Raw
$availability = Get-Content -LiteralPath (Join-Path $repositoryRoot 'src/com/codex/lle/EffectAvailability.java') -Raw
$service = Get-Content -LiteralPath (Join-Path $repositoryRoot 'src/com/codex/lle/ChargingAccessibilityService.java') -Raw

function Require-Source([string] $source, [string] $needle, [string] $description) {
    if (-not $source.Contains($needle)) {
        throw "Missing ${description}: $needle"
    }
}

Require-Source $prefs 'RIPPLE_INK_PALETTE_DEFAULT = 4' 'palette default'
Require-Source $prefs 'RIPPLE_INK_PALETTE_MIN = 1' 'palette minimum'
Require-Source $prefs 'RIPPLE_INK_PALETTE_MAX = 8' 'palette maximum'
Require-Source $prefs 'Math.max(RIPPLE_INK_PALETTE_MIN, Math.min(RIPPLE_INK_PALETTE_MAX, palette))' 'palette clamp'
Require-Source $availability 'case OverlayPrefs.EFFECT_RIPPLE_INK:' 'production availability case'
if ($availability -notmatch 'case OverlayPrefs\.EFFECT_RIPPLE_INK:\s*return ARM64_PROCESS;') {
    throw 'Ripple Ink must be available only through the production ARM64 path.'
}
if ($availability.Contains('RIPPLE_INK_TESTER_PACKAGE') -or
        $availability.Contains('isRippleInkTesterBuild')) {
    throw 'Ripple Ink must not retain a tester-package availability gate.'
}
Require-Source $control 'if (EffectAvailability.isAvailable(this, OverlayPrefs.EFFECT_RIPPLE_INK)) {' 'production picker availability gate'
Require-Source $control '"N3 Ripple Ink"' 'Note 3 picker title'
Require-Source $prefs 'return "N3 Ripple Ink";' 'Note 3 persisted effect label'
$lensIndex = $control.IndexOf('"S4 Lens Flare"')
$rippleIndex = $control.IndexOf('effects.addView(rippleInkEffectOption(current));')
$watercolorIndex = $control.IndexOf('"N3 Watercolor"')
if ($lensIndex -lt 0 -or $rippleIndex -le $lensIndex -or $watercolorIndex -le $rippleIndex) {
    throw 'N3 Ripple Ink must appear immediately after S4 Lens Flare and before N3 Watercolor.'
}
Require-Source $control 'new HorizontalScrollView(this)' 'overflow-safe swatch scroller'
Require-Source $control 'new RippleInkPaletteSwatchView(' 'real circular swatches'
Require-Source $control 'new LinearLayout.LayoutParams(0, dp(40), 1.0f)' 'eight-slot weighted swatch row'
Require-Source $control 'HorizontalScrollView.LayoutParams.MATCH_PARENT' 'full-width palette row'
Require-Source $control 'setContentDescription("N3 Ripple Ink palette " + slot + ", " + name' 'swatch accessibility label'
Require-Source $service 'OverlayPrefs.RIPPLE_INK_PALETTE.equals(key)' 'live palette preference callback'
Require-Source $service '.setInkPaletteSlot(' 'live palette renderer setter'
Require-Source $service 'new RippleInkPortEffectView(' 'production renderer factory'
Require-Source $service '.isProductionReady()) {' 'first-frame production readiness gate'
if ($service.Contains('Ripple Ink is tester-only')) {
    throw 'Ripple Ink factory must not retain its old tester-only guard.'
}
if ($service -notmatch '(?s)private boolean effectUsesScreenshotBackground\(int effect\).*?effect == OverlayPrefs\.EFFECT_RIPPLE_INK') {
    throw 'Ripple Ink must receive the shared lockscreen background texture.'
}
Require-Source $control '|| effect == OverlayPrefs.EFFECT_RIPPLE_INK' 'Ripple Ink colormap controls'

$hfrSectionStart = $prefs.IndexOf('static boolean supportsExperimentalNativeRefreshPhysics(int effect)')
$hfrSectionEnd = $prefs.IndexOf('/** Only these renderers consume', $hfrSectionStart)
if ($hfrSectionStart -lt 0 -or $hfrSectionEnd -lt 0) {
    throw 'Could not locate the per-effect HFR support declaration.'
}
if (-not $prefs.Substring($hfrSectionStart, $hfrSectionEnd - $hfrSectionStart).Contains('EFFECT_RIPPLE_INK')) {
    throw 'Ripple Ink must opt into its production native-refresh/HFR support.'
}
Require-Source $control 'supportsPerEffectHighFrameRate(value) && hasInternalHighRefreshDisplay()' 'normal per-effect HFR UI gate'
if ($control.Contains('value == OverlayPrefs.EFFECT_RIPPLE_INK || hasInternalHighRefreshDisplay()')) {
    throw 'Ripple Ink must use, not bypass, the normal per-effect HFR UI gate.'
}
if ($service -notmatch '(?s)new RippleInkPortEffectView\(\s*rendererContext\(\),\s*OverlayPrefs\.rippleInkPalette\(this\),\s*OverlayPrefs\.experimentalNativeRefreshPhysicsEnabled\(this, effect\)\)') {
    throw 'Ripple Ink factory must pass its live HFR preference to the renderer.'
}
if ($service -notmatch '(?s)changedEffect == OverlayPrefs\.EFFECT_RIPPLE_INK\s*&& unlockEffectRenderer instanceof RippleInkPortEffectView.*?\.setHighFrameRateEnabled\(\s*nativeRefreshEnabled\s*\)') {
    throw 'Ripple Ink HFR preference must use the live renderer setter.'
}
if ($prefs.Substring($hfrSectionEnd).Contains('EFFECT_RIPPLE_INK')) {
    throw 'Ripple Ink must not expose a native-refresh speed control.'
}

Require-Source $control 'final int slot = index + 1;' 'one-based palette slot mapping'
Require-Source $control 'RippleInkPortEngine.paletteCount()' 'all stock palette slots'
Require-Source $control 'rippleInkPreviewColor(slot)' 'rendered-ink swatch color'
Require-Source $control 'RippleInkPortEngine.paletteComponent(selector, channel)' 'engine-owned palette components'
Require-Source $control 'component / (component + (1.5f - component))' 'stock w=1 white-preview formula'

$previewColors = @(
    @(130, 80, 93), @(127, 73, 20), @(47, 87, 20), @(7, 57, 167),
    @(0, 20, 60), @(60, 40, 120), @(37, 17, 7), @(53, 107, 120))
$paletteBits = @(
    @('3f43c3b5', '3ef0f0e9', '3f0c8c82'), @('3f3ebebe', '3edcdcca', '3df0f0e9'),
    @('3e8c8c72', '3f028273', '3df0f0e9'), @('3d209fe8', '3eaaaa9f', '3f7afaf8'),
    @('00000000', '3df0f0e9', '3eb4b4af'), @('3eb4b4af', '3e70f0e9', '3f34b4af'),
    @('3e5cdcca', '3dc8c8ac', '3d209fe8'), @('3ea0a090', '3f20a090', '3f34b4af'))
if ($previewColors.Count -ne 8) {
    throw 'Expected exactly eight rendered Ripple Ink swatch previews.'
}
for ($index = 0; $index -lt $previewColors.Count; $index++) {
    $slot = $index + 1
    Require-Source $control "slot, rippleInkPreviewColor(slot), RIPPLE_INK_PALETTE_NAMES[index]" "slot $slot rendered swatch mapping"
    $rendered = @()
    foreach ($bits in $paletteBits[$index]) {
        Require-Source $engine "0x$bits" "stock palette component for slot $slot"
        $component = [BitConverter]::ToSingle([BitConverter]::GetBytes([Convert]::ToUInt32($bits, 16)), 0)
        $rendered += [Math]::Max(0, [Math]::Min(255, [Math]::Round(($component / 1.5) * 255)))
    }
    if (($rendered -join ',') -ne ($previewColors[$index] -join ',')) {
        throw "Rendered RGB preview mismatch for slot ${slot}: expected $($previewColors[$index] -join ', '), got $($rendered -join ', ')"
    }
}
Write-Host 'PASS Ripple Ink production palette/HFR wiring assertions'
