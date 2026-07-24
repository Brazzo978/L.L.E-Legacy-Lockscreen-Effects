param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$sourceRoot = Join-Path $ProjectRoot 'assets\note4seasonal\charging'
$outputRoot = Join-Path $ProjectRoot 'res\drawable-nodpi'
$canvasSize = 512

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

function Draw-FittedImage {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.Image]$Image,
        [System.Drawing.RectangleF]$Bounds,
        [float]$Alpha = 1.0
    )

    $scale = [Math]::Min(
        $Bounds.Width / $Image.Width,
        $Bounds.Height / $Image.Height)
    $width = [float]($Image.Width * $scale)
    $height = [float]($Image.Height * $scale)
    $destination = [System.Drawing.Rectangle]::new(
        [int][Math]::Round($Bounds.X + (($Bounds.Width - $width) / 2)),
        [int][Math]::Round($Bounds.Y + (($Bounds.Height - $height) / 2)),
        [int][Math]::Round($width),
        [int][Math]::Round($height))
    $attributes = New-AlphaAttributes $Alpha
    try {
        $Graphics.DrawImage(
            $Image,
            $destination,
            0,
            0,
            $Image.Width,
            $Image.Height,
            [System.Drawing.GraphicsUnit]::Pixel,
            $attributes)
    }
    finally {
        $attributes.Dispose()
    }
}

function Add-FacetedBackground {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.RectangleF]$Bounds,
        [System.Drawing.Color]$Light,
        [System.Drawing.Color]$Mid,
        [System.Drawing.Color]$Deep
    )

    $brush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        $Bounds,
        $Light,
        $Deep,
        48.0)
    try {
        $blend = [System.Drawing.Drawing2D.ColorBlend]::new(3)
        $blend.Colors = @($Light, $Mid, $Deep)
        $blend.Positions = @(0.0, 0.54, 1.0)
        $brush.InterpolationColors = $blend
        $Graphics.FillRectangle($brush, $Bounds)
    }
    finally {
        $brush.Dispose()
    }

    $polygons = @(
        @([System.Drawing.PointF]::new(18, 40), [System.Drawing.PointF]::new(215, 24), [System.Drawing.PointF]::new(154, 224)),
        @([System.Drawing.PointF]::new(215, 24), [System.Drawing.PointF]::new(395, 42), [System.Drawing.PointF]::new(318, 222), [System.Drawing.PointF]::new(154, 224)),
        @([System.Drawing.PointF]::new(395, 42), [System.Drawing.PointF]::new(498, 118), [System.Drawing.PointF]::new(318, 222)),
        @([System.Drawing.PointF]::new(18, 40), [System.Drawing.PointF]::new(154, 224), [System.Drawing.PointF]::new(20, 342)),
        @([System.Drawing.PointF]::new(154, 224), [System.Drawing.PointF]::new(318, 222), [System.Drawing.PointF]::new(274, 422), [System.Drawing.PointF]::new(96, 490)),
        @([System.Drawing.PointF]::new(318, 222), [System.Drawing.PointF]::new(498, 118), [System.Drawing.PointF]::new(492, 438), [System.Drawing.PointF]::new(274, 422))
    )
    $facetColors = @(
        [System.Drawing.Color]::FromArgb(28, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(18, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(22, 0, 0, 0),
        [System.Drawing.Color]::FromArgb(18, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(20, 0, 0, 0),
        [System.Drawing.Color]::FromArgb(28, 0, 0, 0)
    )
    for ($index = 0; $index -lt $polygons.Count; $index++) {
        $facet = [System.Drawing.SolidBrush]::new($facetColors[$index])
        try {
            $Graphics.FillPolygon($facet, $polygons[$index])
        }
        finally {
            $facet.Dispose()
        }
    }
}

function Add-GlossAndFrame {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.RectangleF]$Bounds,
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

    $innerPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(185, 255, 255, 255), 4)
    $middlePen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(210, 255, 255, 255), 9)
    $outerPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(115, 31, 52, 83), 2)
    try {
        $Graphics.ResetClip()
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
        $innerPen.Dispose()
        $middlePen.Dispose()
        $outerPen.Dispose()
    }
}

function New-SeasonIcon {
    param(
        [string]$SourceName,
        [string]$OutputName,
        [System.Drawing.Color]$Light,
        [System.Drawing.Color]$Mid,
        [System.Drawing.Color]$Deep,
        [System.Drawing.RectangleF]$MotifBounds
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
    $source = [System.Drawing.Image]::FromFile((Join-Path $sourceRoot $SourceName))
    try {
        $graphics.SetClip($clipPath)
        Add-FacetedBackground $graphics $bounds $Light $Mid $Deep

        # A soft multi-pass copy preserves the original Samsung artwork while
        # giving it the same luminous lift as the existing LLE effect icons.
        foreach ($offset in @(
                [System.Drawing.PointF]::new(-8, 0),
                [System.Drawing.PointF]::new(8, 0),
                [System.Drawing.PointF]::new(0, -8),
                [System.Drawing.PointF]::new(0, 8))) {
            $glowBounds = [System.Drawing.RectangleF]::new(
                $MotifBounds.X + $offset.X,
                $MotifBounds.Y + $offset.Y,
                $MotifBounds.Width,
                $MotifBounds.Height)
            Draw-FittedImage $graphics $source $glowBounds 0.16
        }
        Draw-FittedImage $graphics $source $MotifBounds 1.0
        Add-GlossAndFrame $graphics $bounds $clipPath

        $outputPath = Join-Path $outputRoot $OutputName
        $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host "Wrote $outputPath"
    }
    finally {
        $source.Dispose()
        $clipPath.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function New-AutoSeasonIcon {
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
    $sources = @(
        [System.Drawing.Image]::FromFile((Join-Path $sourceRoot 'spring_charging_100p.png')),
        [System.Drawing.Image]::FromFile((Join-Path $sourceRoot 'summer_charging_05.png')),
        [System.Drawing.Image]::FromFile((Join-Path $sourceRoot 'autumn_charging_01.png')),
        [System.Drawing.Image]::FromFile((Join-Path $sourceRoot 'winter_charging_100p.png'))
    )
    try {
        $graphics.SetClip($clipPath)
        $quadrants = @(
            @{ Rect = [System.Drawing.RectangleF]::new(12, 12, 244, 244); Color = [System.Drawing.Color]::FromArgb(255, 221, 105, 172) },
            @{ Rect = [System.Drawing.RectangleF]::new(256, 12, 244, 244); Color = [System.Drawing.Color]::FromArgb(255, 250, 169, 41) },
            @{ Rect = [System.Drawing.RectangleF]::new(12, 256, 244, 244); Color = [System.Drawing.Color]::FromArgb(255, 200, 80, 34) },
            @{ Rect = [System.Drawing.RectangleF]::new(256, 256, 244, 244); Color = [System.Drawing.Color]::FromArgb(255, 69, 145, 204) }
        )
        foreach ($quadrant in $quadrants) {
            $quadrantBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
                $quadrant.Rect,
                [System.Drawing.Color]::FromArgb(
                    255,
                    [Math]::Min(255, $quadrant.Color.R + 38),
                    [Math]::Min(255, $quadrant.Color.G + 38),
                    [Math]::Min(255, $quadrant.Color.B + 38)),
                $quadrant.Color,
                55.0)
            try {
                $graphics.FillRectangle($quadrantBrush, $quadrant.Rect)
            }
            finally {
                $quadrantBrush.Dispose()
            }
        }

        $separatorPen = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(115, 255, 255, 255), 3)
        try {
            $graphics.DrawLine($separatorPen, 256, 18, 256, 494)
            $graphics.DrawLine($separatorPen, 18, 256, 494, 256)
        }
        finally {
            $separatorPen.Dispose()
        }

        $motifBounds = @(
            [System.Drawing.RectangleF]::new(62, 62, 150, 150),
            [System.Drawing.RectangleF]::new(300, 68, 158, 142),
            [System.Drawing.RectangleF]::new(60, 298, 158, 158),
            [System.Drawing.RectangleF]::new(298, 296, 160, 160)
        )
        for ($index = 0; $index -lt $sources.Count; $index++) {
            Draw-FittedImage $graphics $sources[$index] $motifBounds[$index] 1.0
        }

        Add-GlossAndFrame $graphics $bounds $clipPath
        $outputPath = Join-Path $outputRoot 'icon_doodle_seasonal_auto_lle.png'
        $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host "Wrote $outputPath"
    }
    finally {
        foreach ($source in $sources) {
            $source.Dispose()
        }
        $clipPath.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$spring = @{
    SourceName = 'spring_charging_100p.png'
    OutputName = 'icon_doodle_seasonal_spring_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 251, 210, 235)
    Mid = [System.Drawing.Color]::FromArgb(255, 236, 126, 184)
    Deep = [System.Drawing.Color]::FromArgb(255, 151, 55, 133)
    MotifBounds = [System.Drawing.RectangleF]::new(104, 108, 304, 304)
}
$summer = @{
    SourceName = 'summer_charging_05.png'
    OutputName = 'icon_doodle_seasonal_summer_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 255, 235, 151)
    Mid = [System.Drawing.Color]::FromArgb(255, 255, 177, 52)
    Deep = [System.Drawing.Color]::FromArgb(255, 219, 91, 25)
    MotifBounds = [System.Drawing.RectangleF]::new(88, 112, 336, 300)
}
$autumn = @{
    SourceName = 'autumn_charging_01.png'
    OutputName = 'icon_doodle_seasonal_autumn_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 255, 207, 111)
    Mid = [System.Drawing.Color]::FromArgb(255, 224, 105, 34)
    Deep = [System.Drawing.Color]::FromArgb(255, 119, 45, 39)
    MotifBounds = [System.Drawing.RectangleF]::new(92, 100, 328, 328)
}
$winter = @{
    SourceName = 'winter_charging_100p.png'
    OutputName = 'icon_doodle_seasonal_winter_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 207, 246, 255)
    Mid = [System.Drawing.Color]::FromArgb(255, 86, 183, 225)
    Deep = [System.Drawing.Color]::FromArgb(255, 46, 75, 155)
    MotifBounds = [System.Drawing.RectangleF]::new(90, 90, 332, 332)
}

New-SeasonIcon @spring
New-SeasonIcon @summer
New-SeasonIcon @autumn
New-SeasonIcon @winter
New-AutoSeasonIcon
