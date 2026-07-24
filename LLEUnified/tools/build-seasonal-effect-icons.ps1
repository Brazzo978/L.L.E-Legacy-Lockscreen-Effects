param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$ReviewSheet
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$particleRoot = Join-Path $ProjectRoot 'assets\note4seasonal\charging'
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

function Draw-Particle {
    param(
        [System.Drawing.Graphics]$Graphics,
        [System.Drawing.Image]$Image,
        [float]$CenterX,
        [float]$CenterY,
        [float]$Size,
        [float]$Angle,
        [float]$Alpha = 1.0
    )

    $scale = [Math]::Min($Size / $Image.Width, $Size / $Image.Height)
    $width = [float]($Image.Width * $scale)
    $height = [float]($Image.Height * $scale)
    $destination = [System.Drawing.Rectangle]::new(
        [int][Math]::Round(-$width / 2),
        [int][Math]::Round(-$height / 2),
        [int][Math]::Round($width),
        [int][Math]::Round($height))
    $state = $Graphics.Save()
    $attributes = New-AlphaAttributes $Alpha
    try {
        $Graphics.TranslateTransform($CenterX, $CenterY)
        $Graphics.RotateTransform($Angle)
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
        $Graphics.Restore($state)
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
        $Bounds, $Light, $Deep, 48.0)
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
        [System.Drawing.Color]::FromArgb(30, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(18, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(25, 0, 0, 0),
        [System.Drawing.Color]::FromArgb(16, 255, 255, 255),
        [System.Drawing.Color]::FromArgb(22, 0, 0, 0),
        [System.Drawing.Color]::FromArgb(30, 0, 0, 0)
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

    $middlePen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(210, 255, 255, 255), 9)
    $outerPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(115, 31, 52, 83), 2)
    $innerPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(185, 255, 255, 255), 4)
    try {
        $Graphics.ResetClip()
        $Graphics.DrawPath($middlePen, $ClipPath)
        $Graphics.DrawPath($outerPen, $ClipPath)
        $innerPath = New-RoundedPath ([System.Drawing.RectangleF]::new(22, 22, 468, 468)) 120
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

function New-SeasonalEffectIcon {
    param(
        [string]$OutputName,
        [System.Drawing.Color]$Light,
        [System.Drawing.Color]$Mid,
        [System.Drawing.Color]$Deep,
        [string[]]$ParticleNames,
        [hashtable[]]$Placements
    )

    $bitmap = [System.Drawing.Bitmap]::new(
        $canvasSize, $canvasSize,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $bounds = [System.Drawing.RectangleF]::new(12, 12, 488, 488)
    $clipPath = New-RoundedPath $bounds 132
    $particles = @()
    try {
        foreach ($particleName in $ParticleNames) {
            $particles += [System.Drawing.Image]::FromFile(
                (Join-Path $particleRoot $particleName))
        }

        $graphics.SetClip($clipPath)
        Add-FacetedBackground $graphics $bounds $Light $Mid $Deep

        # The original Note 4 unlock particles are deliberately arranged like
        # the effect's curved spray instead of being replaced by generic art.
        foreach ($placement in $Placements) {
            $particle = $particles[[int]$placement.Index]
            foreach ($glowOffset in @(
                    [System.Drawing.PointF]::new(-5, 0),
                    [System.Drawing.PointF]::new(5, 0),
                    [System.Drawing.PointF]::new(0, -5),
                    [System.Drawing.PointF]::new(0, 5))) {
                Draw-Particle `
                    $graphics `
                    $particle `
                    ([float]$placement.X + $glowOffset.X) `
                    ([float]$placement.Y + $glowOffset.Y) `
                    ([float]$placement.Size) `
                    ([float]$placement.Angle) `
                    0.14
            }
            Draw-Particle `
                $graphics `
                $particle `
                ([float]$placement.X) `
                ([float]$placement.Y) `
                ([float]$placement.Size) `
                ([float]$placement.Angle) `
                ([float]$placement.Alpha)
        }

        Add-GlossAndFrame $graphics $bounds $clipPath
        $outputPath = Join-Path $outputRoot $OutputName
        $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host "Wrote $outputPath"
    }
    finally {
        foreach ($particle in $particles) {
            $particle.Dispose()
        }
        $clipPath.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function New-SeasonalAutoEffectIcon {
    param([hashtable[]]$Seasons)

    $bitmap = [System.Drawing.Bitmap]::new(
        $canvasSize, $canvasSize,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $bounds = [System.Drawing.RectangleF]::new(12, 12, 488, 488)
    $clipPath = New-RoundedPath $bounds 132
    $quadrants = @(
        [System.Drawing.RectangleF]::new(12, 12, 244, 244),
        [System.Drawing.RectangleF]::new(256, 12, 244, 244),
        [System.Drawing.RectangleF]::new(12, 256, 244, 244),
        [System.Drawing.RectangleF]::new(256, 256, 244, 244)
    )
    $images = @()
    try {
        $graphics.SetClip($clipPath)
        for ($seasonIndex = 0; $seasonIndex -lt $Seasons.Count; $seasonIndex++) {
            $season = $Seasons[$seasonIndex]
            $quadrant = $quadrants[$seasonIndex]
            $brush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
                $quadrant, $season.Light, $season.Deep, 48.0)
            try {
                $graphics.FillRectangle($brush, $quadrant)
            }
            finally {
                $brush.Dispose()
            }

            $image = [System.Drawing.Image]::FromFile(
                (Join-Path $particleRoot $season.Particle))
            $images += $image
            Draw-Particle `
                $graphics `
                $image `
                ([float]$season.X) `
                ([float]$season.Y) `
                ([float]$season.Size) `
                ([float]$season.Angle) `
                1.0
        }

        $separatorPen = [System.Drawing.Pen]::new(
            [System.Drawing.Color]::FromArgb(120, 255, 255, 255), 3)
        try {
            $graphics.DrawLine($separatorPen, 256, 18, 256, 494)
            $graphics.DrawLine($separatorPen, 18, 256, 494, 256)
        }
        finally {
            $separatorPen.Dispose()
        }

        Add-GlossAndFrame $graphics $bounds $clipPath
        $outputPath = Join-Path $outputRoot 'icon_effect_seasonal_auto_lle.png'
        $bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host "Wrote $outputPath"
    }
    finally {
        foreach ($image in $images) {
            $image.Dispose()
        }
        $clipPath.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

$spring = @{
    OutputName = 'icon_effect_seasonal_spring_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 255, 221, 241)
    Mid = [System.Drawing.Color]::FromArgb(255, 238, 125, 185)
    Deep = [System.Drawing.Color]::FromArgb(255, 137, 55, 137)
    ParticleNames = @(
        'unlock_spring_particle_01.png',
        'unlock_spring_particle_02.png',
        'unlock_spring_particle_03.png',
        'unlock_spring_particle_04.png')
    Placements = @(
        @{ Index = 3; X = 192; Y = 322; Size = 116; Angle = -18; Alpha = 1.00 },
        @{ Index = 0; X = 270; Y = 256; Size = 136; Angle = 8; Alpha = 1.00 },
        @{ Index = 1; X = 353; Y = 190; Size = 106; Angle = 20; Alpha = 0.96 },
        @{ Index = 2; X = 160; Y = 198; Size = 92; Angle = -26; Alpha = 0.94 },
        @{ Index = 0; X = 346; Y = 340; Size = 78; Angle = 34; Alpha = 0.82 })
}
$summer = @{
    OutputName = 'icon_effect_seasonal_summer_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 255, 237, 153)
    Mid = [System.Drawing.Color]::FromArgb(255, 255, 174, 52)
    Deep = [System.Drawing.Color]::FromArgb(255, 214, 82, 31)
    ParticleNames = @(
        'unlock_summer_particle_01.png',
        'unlock_summer_particle_02.png',
        'unlock_summer_particle_03.png',
        'unlock_summer_particle_04.png',
        'unlock_summer_particle_05.png',
        'unlock_summer_particle_06.png')
    Placements = @(
        @{ Index = 3; X = 180; Y = 320; Size = 132; Angle = -22; Alpha = 1.00 },
        @{ Index = 1; X = 270; Y = 250; Size = 150; Angle = 12; Alpha = 1.00 },
        @{ Index = 2; X = 358; Y = 182; Size = 122; Angle = 24; Alpha = 0.98 },
        @{ Index = 5; X = 156; Y = 194; Size = 112; Angle = -30; Alpha = 0.96 },
        @{ Index = 4; X = 352; Y = 338; Size = 90; Angle = 18; Alpha = 0.90 })
}
$autumn = @{
    OutputName = 'icon_effect_seasonal_autumn_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 255, 215, 119)
    Mid = [System.Drawing.Color]::FromArgb(255, 223, 103, 37)
    Deep = [System.Drawing.Color]::FromArgb(255, 111, 42, 42)
    ParticleNames = @(
        'unlock_autumn_particle_01.png',
        'unlock_autumn_particle_02.png',
        'unlock_autumn_particle_03.png',
        'unlock_autumn_particle_04.png',
        'unlock_autumn_particle_05.png')
    Placements = @(
        @{ Index = 1; X = 178; Y = 322; Size = 128; Angle = -28; Alpha = 1.00 },
        @{ Index = 0; X = 274; Y = 254; Size = 148; Angle = 12; Alpha = 1.00 },
        @{ Index = 3; X = 360; Y = 184; Size = 116; Angle = 28; Alpha = 0.98 },
        @{ Index = 2; X = 150; Y = 190; Size = 110; Angle = -22; Alpha = 0.96 },
        @{ Index = 4; X = 354; Y = 340; Size = 84; Angle = 38; Alpha = 0.90 })
}
$winter = @{
    OutputName = 'icon_effect_seasonal_winter_lle.png'
    Light = [System.Drawing.Color]::FromArgb(255, 215, 248, 255)
    Mid = [System.Drawing.Color]::FromArgb(255, 92, 188, 227)
    Deep = [System.Drawing.Color]::FromArgb(255, 42, 73, 153)
    ParticleNames = @(
        'winter_particle_01.png',
        'winter_particle_02.png',
        'winter_particle_03.png',
        'winter_particle_04.png')
    Placements = @(
        @{ Index = 0; X = 180; Y = 320; Size = 122; Angle = -18; Alpha = 1.00 },
        @{ Index = 3; X = 270; Y = 250; Size = 150; Angle = 8; Alpha = 1.00 },
        @{ Index = 1; X = 358; Y = 182; Size = 112; Angle = 24; Alpha = 0.98 },
        @{ Index = 2; X = 156; Y = 188; Size = 102; Angle = -28; Alpha = 0.96 },
        @{ Index = 0; X = 350; Y = 340; Size = 82; Angle = 18; Alpha = 0.86 })
}

New-SeasonalEffectIcon @spring
New-SeasonalEffectIcon @summer
New-SeasonalEffectIcon @autumn
New-SeasonalEffectIcon @winter

$autoSeasons = @(
    @{
        Particle = 'unlock_spring_particle_04.png'
        Light = $spring.Light
        Deep = $spring.Deep
        X = 145
        Y = 145
        Size = 142
        Angle = -12
    },
    @{
        Particle = 'unlock_summer_particle_02.png'
        Light = $summer.Light
        Deep = $summer.Deep
        X = 367
        Y = 145
        Size = 148
        Angle = 14
    },
    @{
        Particle = 'unlock_autumn_particle_01.png'
        Light = $autumn.Light
        Deep = $autumn.Deep
        X = 145
        Y = 367
        Size = 148
        Angle = -18
    },
    @{
        Particle = 'winter_particle_04.png'
        Light = $winter.Light
        Deep = $winter.Deep
        X = 367
        Y = 367
        Size = 148
        Angle = 8
    }
)
New-SeasonalAutoEffectIcon $autoSeasons

if ($ReviewSheet) {
    $iconNames = @(
        'icon_effect_seasonal_spring_lle.png',
        'icon_effect_seasonal_summer_lle.png',
        'icon_effect_seasonal_autumn_lle.png',
        'icon_effect_seasonal_winter_lle.png',
        'icon_effect_seasonal_auto_lle.png')
    $review = [System.Drawing.Bitmap]::new(
        1280, 256,
        [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $reviewGraphics = [System.Drawing.Graphics]::FromImage($review)
    $reviewGraphics.Clear([System.Drawing.Color]::FromArgb(255, 23, 27, 34))
    $reviewGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    try {
        for ($index = 0; $index -lt $iconNames.Count; $index++) {
            $icon = [System.Drawing.Image]::FromFile(
                (Join-Path $outputRoot $iconNames[$index]))
            try {
                $reviewGraphics.DrawImage($icon, ($index * 256), 0, 256, 256)
            }
            finally {
                $icon.Dispose()
            }
        }
        $reviewPath = Join-Path $ProjectRoot 'build\seasonal-effect-icons-review.png'
        $reviewDirectory = Split-Path -Parent $reviewPath
        if (-not (Test-Path $reviewDirectory)) {
            New-Item -ItemType Directory -Path $reviewDirectory | Out-Null
        }
        $review.Save($reviewPath, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Host "Wrote $reviewPath"
    }
    finally {
        $reviewGraphics.Dispose()
        $review.Dispose()
    }
}
