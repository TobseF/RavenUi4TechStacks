# Builds every variant from scratch and runs the UI Automation smoke test.
#   pwsh build-all.ps1            build + test
#   pwsh build-all.ps1 -NoTest    build only
# Needs: Docker Desktop (Linux containers); for java-graal additionally the
# Visual Studio 2022 C++ build tools (GraalVM itself is downloaded).
param([switch] $NoTest)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Step($name, [scriptblock] $block) {
    Write-Host "`n=== $name" -ForegroundColor Cyan
    & $block
    if ($LASTEXITCODE) { throw "$name failed" }
}

Step 'raven-lib (libraven.a, raven.dll)' { docker build -t raven-mingw raven-lib }
foreach ($dir in 'baseline-c', 'rust', 'java', 'kotlin-native') {
    Step $dir {
        if (Test-Path "$dir\dist") { Remove-Item -Recurse -Force "$dir\dist" }
        docker build --output "type=local,dest=$dir\dist" $dir
    }
}
Step 'java-graal' { pwsh -NoProfile -File java-graal\build.ps1 }

$exes = [ordered]@{
    'C baseline'    = 'baseline-c\dist\simon-c.exe'
    'Rust'          = 'rust\dist\simon-rust.exe'
    'Kotlin/Native' = 'kotlin-native\dist\simon-kotlin.exe'
    'Java GraalVM'  = 'java-graal\dist\simon-graal\simon-graal.exe'
    'Java (jlink)'  = 'java\dist\simon-java\simon-java.exe'
}
Write-Host "`n=== sizes" -ForegroundColor Cyan
foreach ($k in $exes.Keys) {
    $folder = Split-Path $exes[$k]
    $files = Get-ChildItem $folder -Recurse -File
    '{0,-14} {1,8:N0} KB in {2,3} files   ({3})' -f $k, (($files | Measure-Object Length -Sum).Sum / 1KB), $files.Count, $folder
}
if (-not $NoTest) {
    foreach ($k in $exes.Keys) {
        Write-Host "`n=== smoke test: $k" -ForegroundColor Cyan
        pwsh -NoProfile -File smoke-test.ps1 $exes[$k]
    }
}
