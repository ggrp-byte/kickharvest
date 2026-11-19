Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = "Stop"
$root = "C:\Users\gucio\Documents\Default Project\Kick_MINER"
$src = [System.Drawing.Image]::FromFile("$root\assets\logo.png")

function Save-Png([int]$w, [int]$h, [string]$path) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($src, 0, 0, $w, $h)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Output "PNG OK: $path"
}

# desktop app icon (256) + ico
Save-Png 256 256 "$root\desktop\icon.png"
$bmp = New-Object System.Drawing.Bitmap(256, 256)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($src, 0, 0, 256, 256)
$hIcon = $bmp.GetHicon()
$icon = [System.Drawing.Icon]::FromHandle($hIcon)
$fs = [System.IO.FileStream]::New("$root\desktop\icon.ico", [System.IO.FileMode]::Create)
$icon.Save($fs); $fs.Close()
$g.Dispose(); $bmp.Dispose()
Write-Output "ICO OK: $root\desktop\icon.ico"

# android mipmaps
$sizes = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }
foreach ($k in $sizes.Keys) {
    Save-Png $sizes[$k] $sizes[$k] "$root\android\app\src\main\res\$k\ic_launcher.png"
}
$src.Dispose()
Write-Output "GOTOWE"
