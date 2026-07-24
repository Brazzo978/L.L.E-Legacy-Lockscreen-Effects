param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$previewRoot = Join-Path $ProjectRoot 'res\drawable-nodpi'
$outputRoot = Join-Path $ProjectRoot 'res\drawable-nodpi'
$blindReference = Join-Path $previewRoot 'effect_preview_tabs_blind.jpg'
$cutReference = Join-Path $previewRoot 'effect_preview_tabs_brilliant_cut.jpg'
$canvasSize = 512

foreach ($reference in @($blindReference, $cutReference)) {
    if (-not (Test-Path -LiteralPath $reference)) {
        throw "Missing effect preview reference: $reference"
    }
}

function New-RoundedPath {
    param(
        [System.Drawing.RectangleF]$Bounds,
        [float]$Radius
    )

    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $diameter = $Radius * 2
    $arc = [System.Drawing.RectangleF]::new(
        $Bounds.X, $Bounds.Y, $diameter, $diameter)
    $path.AddArc($arc, 180, 90)
    $arc.X = $Bounds.Right - $diameter
    $path.AddArc($arc, 270, 90)
    $arc.Y = $Bounds.Bottom - $diameter
    $path.AddArc($arc, 0, 90)
    $arc.X = $Bounds.X
    $path.AddArc($arc, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-AlphaAttributes {
    param([float]$Alpha)

    $matrix = [System.Drawing.Imaging.ColorMatrix]::new()
    $matrix.Matrix33 = $Alpha
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    $attributes.SetColorMatrix($matrix)
    return $attributes
}

function Draw-CoverImage {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.Image]$Image,
        [System.Drawing.RectangleF]$Bounds,
        [float]$Alpha
    )

    $scale = [Math]::Max(
        $Bounds.Width / $Image.Width,
        $Bounds.Height / $Image.Height)
    $sourceWidth = [int][Math]::Round($Bounds.Width / $scale)
    $sourceHeight = [int][Math]::Round($Bounds.Height / $scale)
    $sourceX = [int][Math]::Round(($Image.Width - $sourceWidth) / 2)
    $sourceY = [int][Math]::Round(($Image.Height - $sourceHeight) / 2)
    $destination = [System.Drawing.Rectangle]::new(
        [int]$Bounds.X,
        [int]$Bounds.Y,
        [int]$Bounds.Width,
        [int]$Bounds.Height)
    $attributes = New-AlphaAttributes $Alpha
    try {
        $Graphics.DrawImage(
            $Image,
            $destination,
            $sourceX,
            $sourceY,
            $sourceWidth,
            $sourceHeight,
            [System.Drawing.GraphicsUnit]::Pixel,
            $attributes)
    }
    finally {
        $attributes.Dispose()
    }
}

function Add-Background {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.RectangleF]$Bounds,
        [System.Drawing.Image]$Reference,
        [System.Drawing.Color]$Light,
        [System.Drawing.Color]$Mid,
        [System.Drawing.Color]$Deep
    )

    Draw-CoverImage $Graphics $Reference $Bounds 0.26

    $gradient = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        $Bounds,
        $Light,
        $Deep,
        48.0)
    try {
        $blend = [System.Drawing.Drawing2D.ColorBlend]::new(3)
        $blend.Colors = @($Light, $Mid, $Deep)
        $blend.Positions = @(0.0, 0.53, 1.0)
        $gradient.InterpolationColors = $blend
        $Graphics.FillRectangle($gradient, $Bounds)
    }
    finally {
        $gradient.Dispose()
    }

    # Keep the same low-poly material used by the established LLE icons.
    $facets = @(
        @{
            Points = @(
                [System.Drawing.PointF]::new(18, 42),
                [System.Drawing.PointF]::new(214, 24),
                [System.Drawing.PointF]::new(154, 224))
            Color = [System.Drawing.Color]::FromArgb(25, 255, 255, 255)
        },
        @{
            Points = @(
                [System.Drawing.PointF]::new(214, 24),
                [System.Drawing.PointF]::new(395, 42),
                [System.Drawing.PointF]::new(318, 222),
                [System.Drawing.PointF]::new(154, 224))
            Color = [System.Drawing.Color]::FromArgb(16, 255, 255, 255)
        },
        @{
            Points = @(
                [System.Drawing.PointF]::new(395, 42),
                [System.Drawing.PointF]::new(498, 118),
                [System.Drawing.PointF]::new(318, 222))
            Color = [System.Drawing.Color]::FromArgb(24, 0, 0, 0)
        },
        @{
            Points = @(
                [System.Drawing.PointF]::new(18, 42),
                [System.Drawing.PointF]::new(154, 224),
                [System.Drawing.PointF]::new(20, 342))
            Color = [System.Drawing.Color]::FromArgb(19, 255, 255, 255)
        },
        @{
            Points = @(
                [System.Drawing.PointF]::new(154, 224),
                [System.Drawing.PointF]::new(318, 222),
                [System.Drawing.PointF]::new(274, 422),
                [System.Drawing.PointF]::new(96, 490))
            Color = [System.Drawing.Color]::FromArgb(21, 0, 0, 0)
        },
        @{
            Points = @(
                [System.Drawing.PointF]::new(318, 222),
                [System.Drawing.PointF]::new(498, 118),
                [System.Drawing.PointF]::new(492, 438),
                [System.Drawing.PointF]::new(274, 422))
            Color = [System.Drawing.Color]::FromArgb(27, 0, 0, 0)
        }
    )
    foreach ($facet in $facets) {
        $brush = [System.Drawing.SolidBrush]::new($facet.Color)
        try {
            $Graphics.FillPolygon($brush, $facet.Points)
        }
        finally {
            $brush.Dispose()
        }
    }
}

function Add-GlossAndFrame {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.Drawing2D.GraphicsPath]$ClipPath
    )

    $glossBounds = [System.Drawing.RectangleF]::new(29, 24, 454, 222)
    $glossPath = New-RoundedPath $glossBounds 105
    $glossBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        $glossBounds,
        [System.Drawing.Color]::FromArgb(112, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(8, 255, 255, 255),
        90.0)
    try {
        $Graphics.FillPath($glossBrush, $glossPath)
    }
    finally {
        $glossBrush.Dispose()
        $glossPath.Dispose()
    }

    $Graphics.ResetClip()
    $middlePen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(210, 255, 255, 255), 9)
    $outerPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(115, 31, 52, 83), 2)
    $innerPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(185, 255, 255, 255), 4)
    try {
        $Graphics.DrawPath($middlePen, $ClipPath)
        $Graphics.DrawPath($outerPen, $ClipPath)
        $innerBounds = [System.Drawing.RectangleF]::new(22, 22, 468, 468)
        $innerPath = New-RoundedPath $innerBounds 120
        try {
            $Graphics.DrawPath($innerPen, $innerPath)
        }
        finally {
            $innerPath.Dispose()
        }
    }
    finally {
        $middlePen.Dispose()
        $outerPen.Dispose()
        $innerPen.Dispose()
    }
}

function Add-BlindMotif {
    param([System.Drawing.Graphics]$Graphics)

    # The real Tab S effect is a bank of translucent vertical slices. Their
    # stepped top/bottom edges reproduce the opening phase visible in the MP4.
    for ($glow = 28; $glow -ge 4; $glow -= 6) {
        $glowBrush = [System.Drawing.SolidBrush]::new(
            [System.Drawing.Color]::FromArgb(
                [Math]::Max(2, 18 - [int]($glow / 2)),
                112,
                245,
                255))
        try {
            $Graphics.FillEllipse(
                $glowBrush,
                82 - $glow,
                106 - $glow,
                348 + ($glow * 2),
                306 + ($glow * 2))
        }
        finally {
            $glowBrush.Dispose()
        }
    }

    $panelCount = 9
    $panelWidth = 33
    $startX = 108
    for ($index = 0; $index -lt $panelCount; $index++) {
        $x = $startX + ($index * $panelWidth)
        $top = 118 + ($index * 6)
        $bottom = 410 - (($panelCount - 1 - $index) * 7)
        $panelBounds = [System.Drawing.RectangleF]::new(
            $x,
            $top,
            $panelWidth + 7,
            $bottom - $top)
        $light = [System.Drawing.Color]::FromArgb(
            154,
            200 - ($index * 6),
            253,
            255)
        $deep = [System.Drawing.Color]::FromArgb(
            60,
            55,
            124 + ($index * 7),
            232)
        $panelBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
            $panelBounds,
            $light,
            $deep,
            0.0)
        try {
            $Graphics.FillRectangle($panelBrush, $panelBounds)
        }
        finally {
            $panelBrush.Dispose()
        }

        $edgePen = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(115, 230, 255, 255),
            1.5)
        try {
            $Graphics.DrawLine(
                $edgePen,
                [float]$x,
                [float]$top,
                [float]$x,
                [float]$bottom)
        }
        finally {
            $edgePen.Dispose()
        }
    }

}

function Add-BrilliantCutMotif {
    param([System.Drawing.Graphics]$Graphics)

    # Brilliant Cut reveals a circular patch of the diamond lattice and runs a
    # small white glint over it. Keep the lattice clipped to that light pool.
    $poolPath = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $poolPath.AddEllipse([System.Drawing.RectangleF]::new(92, 92, 328, 328))
    $oldClip = $Graphics.Clip
    try {
        for ($size = 332; $size -ge 244; $size -= 22) {
            $alpha = 8 + [int](($size - 244) / 6)
            $poolBrush = [System.Drawing.SolidBrush]::new(
                [System.Drawing.Color]::FromArgb(
                    $alpha,
                    182,
                    244,
                    255))
            try {
                $offset = (512 - $size) / 2
                $Graphics.FillEllipse($poolBrush, $offset, $offset, $size, $size)
            }
            finally {
                $poolBrush.Dispose()
            }
        }

        $Graphics.SetClip($poolPath, [System.Drawing.Drawing2D.CombineMode]::Intersect)
        $meshPen = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(118, 226, 251, 255),
            2.1)
        $meshPen2 = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(58, 255, 255, 255),
            5.0)
        try {
            for ($offset = -256; $offset -le 256; $offset += 54) {
                $Graphics.DrawLine(
                    $meshPen2,
                    [float](64 + $offset),
                    448.0,
                    [float](448 + $offset),
                    64.0)
                $Graphics.DrawLine(
                    $meshPen2,
                    [float](64 + $offset),
                    64.0,
                    [float](448 + $offset),
                    448.0)
                $Graphics.DrawLine(
                    $meshPen,
                    [float](64 + $offset),
                    448.0,
                    [float](448 + $offset),
                    64.0)
                $Graphics.DrawLine(
                    $meshPen,
                    [float](64 + $offset),
                    64.0,
                    [float](448 + $offset),
                    448.0)
            }
        }
        finally {
            $meshPen.Dispose()
            $meshPen2.Dispose()
        }

        $facetBrushes = @(
            [System.Drawing.Color]::FromArgb(58, 255, 255, 255),
            [System.Drawing.Color]::FromArgb(40, 113, 225, 255),
            [System.Drawing.Color]::FromArgb(38, 225, 176, 255),
            [System.Drawing.Color]::FromArgb(46, 255, 255, 255)
        )
        $diamonds = @(
            @([System.Drawing.PointF]::new(256, 112), [System.Drawing.PointF]::new(355, 256), [System.Drawing.PointF]::new(256, 400), [System.Drawing.PointF]::new(157, 256)),
            @([System.Drawing.PointF]::new(256, 112), [System.Drawing.PointF]::new(256, 256), [System.Drawing.PointF]::new(157, 256)),
            @([System.Drawing.PointF]::new(355, 256), [System.Drawing.PointF]::new(256, 400), [System.Drawing.PointF]::new(256, 256)),
            @([System.Drawing.PointF]::new(157, 256), [System.Drawing.PointF]::new(256, 256), [System.Drawing.PointF]::new(256, 400))
        )
        for ($index = 0; $index -lt $diamonds.Count; $index++) {
            $facetBrush = [System.Drawing.SolidBrush]::new($facetBrushes[$index])
            try {
                $Graphics.FillPolygon($facetBrush, $diamonds[$index])
            }
            finally {
                $facetBrush.Dispose()
            }
        }
    }
    finally {
        $Graphics.Clip = $oldClip
        $oldClip.Dispose()
        $poolPath.Dispose()
    }

    # Multi-pass star without a bitmap blur: the subdued outer rays match the
    # distorted stock glint while the compact white core remains readable.
    $glintX = 338
    $glintY = 174
    foreach ($ray in @(
            @{ Length = 72; Width = 8.0; Alpha = 32 },
            @{ Length = 54; Width = 4.0; Alpha = 86 },
            @{ Length = 36; Width = 2.0; Alpha = 220 })) {
        $pen = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(
                $ray.Alpha,
                255,
                255,
                255),
            $ray.Width)
        try {
            $Graphics.DrawLine(
                $pen,
                [float]($glintX - $ray.Length),
                [float]$glintY,
                [float]($glintX + $ray.Length),
                [float]$glintY)
            $Graphics.DrawLine(
                $pen,
                [float]$glintX,
                [float]($glintY - $ray.Length),
                [float]$glintX,
                [float]($glintY + $ray.Length))
            $diagonal = [int][Math]::Round($ray.Length * 0.68)
            $Graphics.DrawLine(
                $pen,
                [float]($glintX - $diagonal),
                [float]($glintY - $diagonal),
                [float]($glintX + $diagonal),
                [float]($glintY + $diagonal))
            $Graphics.DrawLine(
                $pen,
                [float]($glintX - $diagonal),
                [float]($glintY + $diagonal),
                [float]($glintX + $diagonal),
                [float]($glintY - $diagonal))
        }
        finally {
            $pen.Dispose()
        }
    }
    $core = [System.Drawing.SolidBrush]::new(
        [System.Drawing.Color]::FromArgb(245, 255, 255, 255))
    try {
        $Graphics.FillEllipse($core, $glintX - 8, $glintY - 8, 16, 16)
    }
    finally {
        $core.Dispose()
    }
}

function New-EffectIcon {
    param(
        [string]$ReferencePath,
        [string]$OutputName,
        [System.Drawing.Color]$Light,
        [System.Drawing.Color]$Mid,
        [System.Drawing.Color]$Deep,
        [scriptblock]$DrawMotif
    )

    $bitmap = [System.Drawing.Bitmap]::new(
        $canvasSize,
        $canvasSize,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $bounds = [System.Drawing.RectangleF]::new(12, 12, 488, 488)
    $clipPath = New-RoundedPath $bounds 132
    $reference = [System.Drawing.Image]::FromFile($ReferencePath)
    try {
        $graphics.SetClip($clipPath)
        Add-Background $graphics $bounds $reference $Light $Mid $Deep
        & $DrawMotif $graphics
        Add-GlossAndFrame $graphics $clipPath

        $outputPath = Join-Path $outputRoot $OutputName
        $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host "Wrote $outputPath"
    }
    finally {
        $reference.Dispose()
        $clipPath.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

New-EffectIcon `
    -ReferencePath $blindReference `
    -OutputName 'icon_effect_tabs_blind_lle.png' `
    -Light ([System.Drawing.Color]::FromArgb(212, 137, 222, 255)) `
    -Mid ([System.Drawing.Color]::FromArgb(218, 60, 145, 229)) `
    -Deep ([System.Drawing.Color]::FromArgb(226, 38, 40, 121)) `
    -DrawMotif ${function:Add-BlindMotif}

New-EffectIcon `
    -ReferencePath $cutReference `
    -OutputName 'icon_effect_tabs_brilliant_cut_lle.png' `
    -Light ([System.Drawing.Color]::FromArgb(210, 203, 172, 255)) `
    -Mid ([System.Drawing.Color]::FromArgb(216, 108, 86, 205)) `
    -Deep ([System.Drawing.Color]::FromArgb(228, 31, 52, 122)) `
    -DrawMotif ${function:Add-BrilliantCutMotif}
