# Prototype of the menu-strip compositor. Validates z-order + edge-clip + anchor rules
# against the real template/card assets, producing a montage for visual inspection.
Add-Type -AssemblyName System.Drawing
$code = @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public class Bbox {
  public int minX, minY, maxX, maxY; public bool empty;
}
public static class Comp {
  // Bounding box of alpha>16 pixels within a frame slice [fy, fy+fh) of a strip bitmap.
  public static Bbox Box(Bitmap bmp, int frame, int fh) {
    int W=bmp.Width;
    var d=bmp.LockBits(new Rectangle(0,0,W,bmp.Height),ImageLockMode.ReadOnly,PixelFormat.Format32bppArgb);
    int stride=d.Stride; byte[] buf=new byte[stride*bmp.Height];
    Marshal.Copy(d.Scan0,buf,0,buf.Length); bmp.UnlockBits(d);
    var bb=new Bbox(); bb.minX=99999; bb.minY=99999; bb.maxX=-1; bb.maxY=-1;
    int y0=frame*fh, y1=y0+fh;
    for(int y=y0;y<y1;y++) for(int x=0;x<W;x++){
      if(buf[y*stride+x*4+3]>16){ if(x<bb.minX)bb.minX=x; if(x>bb.maxX)bb.maxX=x; if(y<bb.minY)bb.minY=y; if(y>bb.maxY)bb.maxY=y; }
    }
    bb.empty = bb.maxX<0;
    if(!bb.empty){ bb.minY-=y0; bb.maxY-=y0; }
    return bb;
  }
}
'@
Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$tpl  = Join-Path $repoRoot 'src/main/resources/assets/dex_cosmetics/textures/gui/template'
$card = Join-Path $repoRoot 'src/main/resources/assets/dex_cosmetics/textures/gui/cards'
$out  = Join-Path $PSScriptRoot 'output'
New-Item -ItemType Directory -Path $out -Force | Out-Null

function L($p){ New-Object System.Drawing.Bitmap((Join-Path $tpl $p)) }
function C($p){ New-Object System.Drawing.Bitmap((Join-Path $card $p)) }

$background = C "background.png"
$frame      = C "frame.png"
$arrows     = if (Test-Path -LiteralPath (Join-Path $tpl "arrows.png")) { L "arrows.png" } else { $null }
$top1=L "top1.png"; $top2=L "top2.png"; $left1=L "left1.png"; $left2=L "left2.png"
$right=L "right.png"; $wayleft=L "wayleft.png"

# faces by category
$faces = @{ HATS=(C "hats.png"); ITEMS=(C "items.png"); EFFECTS=(C "effects.png"); BACK=(C "back.png") }

# ring + cycle-right-from-HATS mapping
# top=focus(HATS), left=leftNeighbor(ITEMS), right=rightNeighbor(BACK), wayleft=two-left(EFFECTS)
$faceTop=$faces["HATS"]; $faceLeft=$faces["ITEMS"]; $faceRight=$faces["BACK"]; $faceWay=$faces["EFFECTS"]

# Draw a face clipped to the template silhouette's horizontal span, anchored per rule.
function DrawCard($g, $tplStrip, $tframe, $face, $anchorRight){
  $bb=[Comp]::Box($tplStrip,$tframe,256)
  if($bb.empty){ return }
  $w=78; $h=92
  $faceX = if($anchorRight){ $bb.maxX-($w-1) } else { $bb.minX }
  $faceY = $bb.minY
  $g.SetClip((New-Object System.Drawing.Rectangle($bb.minX,0,($bb.maxX-$bb.minX+1),256)))
  $g.DrawImage($face,(New-Object System.Drawing.Rectangle($faceX,$faceY,$w,$h)))
  $g.ResetClip()
}

$N=8
# montage: 4 columns x 2 rows of 256x256 frames
$cols=4; $rows=2
$montage=New-Object System.Drawing.Bitmap(($cols*256),($rows*256))
$mg=[System.Drawing.Graphics]::FromImage($montage)
$mg.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor

for($f=0;$f -lt $N;$f++){
  $fr=New-Object System.Drawing.Bitmap(256,256)
  $g=[System.Drawing.Graphics]::FromImage($fr)
  $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
  $g.DrawImage($background,0,0)
  # bottom -> top card layers
  DrawCard $g $wayleft $f $faceWay $true            # wayleft: enters from left, right-anchored
  DrawCard $g $right   $f $faceRight $false          # right: exits right, left-anchored
  if($f -lt 4){
    DrawCard $g $left1 $f $faceLeft $false           # second layer
    DrawCard $g $top1  $f $faceTop  $false           # top layer
  } else {
    DrawCard $g $top2 ($f-4) $faceTop  $false        # second layer
    DrawCard $g $left2 ($f-4) $faceLeft $false       # top layer (becomes focus)
  }
  if ($null -ne $arrows) { $g.DrawImage($arrows,(New-Object System.Drawing.Rectangle(0,0,256,256)),0,($f*256),256,256,[System.Drawing.GraphicsUnit]::Pixel) }
  $g.DrawImage($frame,0,0)
  $g.Dispose()
  $cx=($f % $cols)*256; $cy=[math]::Floor($f/$cols)*256
  $mg.DrawImage($fr,$cx,$cy)
  $fr.Dispose()
}
$mg.Dispose()
$montage.Save((Join-Path $out "cycleright_hats.png"),[System.Drawing.Imaging.ImageFormat]::Png)
Write-Output "wrote cycleright_hats.png"
$montage.Dispose()
foreach ($bitmap in @($background, $frame, $arrows, $top1, $top2, $left1, $left2, $right, $wayleft) + @($faces.Values)) {
  if ($null -ne $bitmap) { $bitmap.Dispose() }
}
