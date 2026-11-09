# Captures docs/img/screenshot.png: the game window only, while a pad is lit.
#   pwsh docs/capture-screenshot.ps1 [exe]
param([string] $Exe = "$PSScriptRoot\..\rust\dist\simon-rust.exe")
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Drawing
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class Win { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
'@
[Win]::SetProcessDPIAware() | Out-Null
$A = [System.Windows.Automation.AutomationElement]
$p = Start-Process (Resolve-Path $Exe) -PassThru
$win = $null
for ($i = 0; -not $win -and $i -lt 200; $i++) {
    Start-Sleep -Milliseconds 50
    $win = $A::RootElement.FindFirst('Children', (New-Object System.Windows.Automation.PropertyCondition($A::ProcessIdProperty, $p.Id)))
}
[Win]::SetForegroundWindow([IntPtr]$win.Current.NativeWindowHandle) | Out-Null
Start-Sleep -Milliseconds 400
$start = $win.FindAll('Descendants', [System.Windows.Automation.Condition]::TrueCondition) | Where-Object { $_.Current.Name -eq 'Start' } | Select-Object -First 1
$start.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
Start-Sleep -Milliseconds 850          # first pad lights up at 0.6 s for 0.55 s
$r = $win.Current.BoundingRectangle
$bmp = New-Object System.Drawing.Bitmap ([int]$r.Width), ([int]$r.Height)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen([int]$r.X, [int]$r.Y, 0, 0, $bmp.Size)
$file = Join-Path $PSScriptRoot 'img\screenshot.png'
$bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
$p | Stop-Process
"saved $file ($([int]$r.Width)x$([int]$r.Height))"
