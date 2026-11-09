# Downloads the pinned GraalVM CE into java-graal\.tools\ (git-ignored, ~700 MB).
# Does nothing if it is already there. Called by build.ps1; can also be run alone.
#
#   pwsh java-graal\get-graalvm.ps1
param([string] $Version = '25.0.1')
$ErrorActionPreference = 'Stop'
$tools = Join-Path $PSScriptRoot '.tools'
$graal = Join-Path $tools "graalvm-community-openjdk-$Version"

if (-not (Test-Path "$graal\bin\native-image.cmd")) {
    New-Item -ItemType Directory -Force $tools | Out-Null
    $zip = Join-Path $tools 'graalvm.zip'
    $url = "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-$Version/graalvm-community-jdk-${Version}_windows-x64_bin.zip"
    Write-Host "downloading $url"
    Invoke-WebRequest $url -OutFile $zip
    Expand-Archive $zip $tools
    Remove-Item $zip
    $extracted = Get-ChildItem $tools -Directory | Where-Object Name -like 'graalvm-community-openjdk-*' | Select-Object -First 1
    if ($extracted.FullName -ne $graal) { Rename-Item $extracted.FullName $graal }
}
Write-Output $graal
