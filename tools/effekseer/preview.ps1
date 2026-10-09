# Opens an .efkefc in the Effekseer editor, plays it and saves a strip of frames as a PNG.
# Used by gen_effects.py --preview. It moves the mouse and clicks Play inside the editor window.
param([string]$Editor, [string]$Effect, [string]$Out, [int]$Zoom = 12, [int]$Frames = 3,
      [int]$GapMs = 250, [int]$StartMs = 300)
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
if (-not ("XenoPreviewWin" -as [type])) {
Add-Type @"
using System; using System.Runtime.InteropServices;
public static class XenoPreviewWin {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int ht, bool r);
  [DllImport("user32.dll")] public static extern void mouse_event(int f, int x, int y, int d, int e);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, int f, int e);
}
"@ }
$name = [System.IO.Path]::GetFileName($Effect)
Get-Process Effekseer -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 500
Start-Process -FilePath $Editor -ArgumentList "`"$Effect`"" | Out-Null
$p = $null
foreach ($i in 1..40) {
  Start-Sleep -Milliseconds 500
  $p = Get-Process Effekseer -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "*$name*" } | Select-Object -First 1
  if ($p) { break }
}
if (-not $p) { throw "editor window for $name not found" }
Start-Sleep -Seconds 2
[XenoPreviewWin]::MoveWindow($p.MainWindowHandle, 0, 0, 1280, 720, $true) | Out-Null
# Windows only lets a background process take the foreground right after a key event; a tap of
# Alt satisfies that. If the editor still is not in front, stop: the clicks below must never land
# in whatever window the user is working in.
[XenoPreviewWin]::keybd_event(0x12, 0, 0, 0); [XenoPreviewWin]::keybd_event(0x12, 0, 2, 0)
[XenoPreviewWin]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 400
if ([XenoPreviewWin]::GetForegroundWindow() -ne $p.MainWindowHandle) {
  Get-Process Effekseer -ErrorAction SilentlyContinue | Stop-Process -Force
  throw "the Effekseer window could not be brought to the front; preview skipped (no clicks sent)"
}
[XenoPreviewWin]::SetCursorPos(418, 300) | Out-Null
foreach ($k in 1..$Zoom) { [XenoPreviewWin]::mouse_event(0x0800, 0, 0, 120, 0); Start-Sleep -Milliseconds 40 }
if ([XenoPreviewWin]::GetForegroundWindow() -ne $p.MainWindowHandle) { throw "focus left the editor; preview skipped" }
[XenoPreviewWin]::SetCursorPos(410, 652) | Out-Null   # the Play button
[XenoPreviewWin]::mouse_event(2, 0, 0, 0, 0); [XenoPreviewWin]::mouse_event(4, 0, 0, 0, 0)
Start-Sleep -Milliseconds $StartMs
$b = New-Object System.Drawing.Rectangle 10, 35, 815, 540
$strip = New-Object System.Drawing.Bitmap ($b.Width * $Frames), $b.Height
$sg = [System.Drawing.Graphics]::FromImage($strip)
foreach ($i in 0..($Frames - 1)) {
  $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
  $sg.DrawImage($bmp, $i * $b.Width, 0)
  Start-Sleep -Milliseconds $GapMs
}
$strip.Save($Out)
Get-Process Effekseer -ErrorAction SilentlyContinue | Stop-Process -Force
