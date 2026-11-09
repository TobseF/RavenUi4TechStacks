# Smoke test over UI Automation (Raven exposes its widgets to UIA):
# starts an exe (or attaches to a running process), presses Start, waits for
# the sequence to play, presses a pad, prints the status texts and the memory
# use, then closes the window like a user would.
#   pwsh smoke-test.ps1 rust\dist\simon-rust.exe
#   pwsh smoke-test.ps1 -AttachPid 1234
param([string] $Exe, [int] $AttachPid)

Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
$A = [System.Windows.Automation.AutomationElement]
$T = [System.Windows.Automation.TreeScope]

$sw = [Diagnostics.Stopwatch]::StartNew()
$p = if ($AttachPid) { Get-Process -Id $AttachPid } else { Start-Process (Resolve-Path $Exe) -PassThru }
$win = $null
while (-not $win -and $sw.Elapsed.TotalSeconds -lt 30) {
    Start-Sleep -Milliseconds 20
    $cond = New-Object System.Windows.Automation.PropertyCondition($A::ProcessIdProperty, $p.Id)
    $win = $A::RootElement.FindFirst($T::Children, $cond)
}
if (-not $win) { Write-Error "no window"; $p | Stop-Process; exit 1 }
$startup = $sw.ElapsedMilliseconds

function All { $win.FindAll($T::Descendants, [System.Windows.Automation.Condition]::TrueCondition) }
function Status { (All | Where-Object { $_.Current.ControlType.ProgrammaticName -eq 'ControlType.Text' } | Select-Object -First 1).Current.Name }
function Press($name) {
    $buttons = @(All | Where-Object { $_.Current.ControlType.ProgrammaticName -eq 'ControlType.Button' })
    # the pads have no text: padN is the N-th button without a name
    $b = if ($name -match '^pad(\d)$') { @($buttons | Where-Object { $_.Current.Name -eq '' })[[int]$Matches[1]] }
         else { $buttons | Where-Object { $_.Current.Name -eq $name } | Select-Object -First 1 }
    if (-not $b) { return "($name not found)" }
    $b.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
}

"title          : $($win.Current.Name)"
if (-not $AttachPid) { "window after   : $startup ms" }
"status         : $(Status)"
Press 'Start' | Out-Null
Start-Sleep -Milliseconds 300
"after Start    : $(Status)"
Start-Sleep -Milliseconds 1500
"after playback : $(Status)"
Press 'pad0' | Out-Null
Start-Sleep -Milliseconds 300
"after pad0     : $(Status)"
$p.Refresh()
"working set    : $([math]::Round($p.WorkingSet64 / 1MB, 1)) MB, private: $([math]::Round($p.PrivateMemorySize64 / 1MB, 1)) MB"

# close the window (a normal exit, so that e.g. the tracing agent writes its files)
try { $win.GetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern).Close() } catch { }
if (-not $p.WaitForExit(10000)) { "did not exit: killed"; $p | Stop-Process }
else { "exit code      : $($p.ExitCode)" }
