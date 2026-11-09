# Java variant compiled ahead of time with GraalVM native-image.
#
# native-image cannot cross-compile: a Windows exe has to be built on Windows
# with the MSVC toolchain. Instead of Windows containers this script downloads
# a pinned GraalVM CE into .tools\ (no global install) and uses the installed
# Visual Studio 2022 C++ build tools (found by native-image via vswhere).
#
# Sources: ..\java\src (Simon.java) + R.java of the Raven binding and raven.dll,
# both taken from the docker image raven-mingw (docker build -t raven-mingw ..\raven-lib).
#
#   pwsh java-graal\build.ps1            -> java-graal\dist\simon-graal\
#   pwsh java-graal\build.ps1 -Trace     also re-records the FFM metadata with the agent
param([switch] $Trace)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$graalVersion = '25.0.1'
$work = Join-Path $root 'build'
$dist = Join-Path $root 'dist\simon-graal'

# ---- GraalVM (pinned, downloaded to .tools\, git-ignored) --------------------
$graal = (& "$root\get-graalvm.ps1" -Version $graalVersion) | Select-Object -Last 1
$env:JAVA_HOME = $graal
$env:PATH = "$graal\bin;$env:PATH"

# ---- sources from the raven-mingw image ---------------------------------------
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force "$work\src", "$work\classes" | Out-Null
$cid = docker create raven-mingw
try {
    docker cp "${cid}:/src/raven/bindings/java/src/main/java/raven" "$work\src\raven" | Out-Null
    docker cp "${cid}:/opt/raven/shared/raven.dll" "$work\raven.dll" | Out-Null
} finally { docker rm $cid | Out-Null }
Copy-Item -Recurse "$root\..\java\src\simon" "$work\src\simon"

# ---- compile ------------------------------------------------------------------
$sources = Get-ChildItem -Recurse "$work\src" -Filter *.java | ForEach-Object FullName
& javac --release 25 -d "$work\classes" @sources
if ($LASTEXITCODE) { throw 'javac failed' }

# ---- optional: record the reachability metadata (FFM descriptors, reflection) --
# Runs the game on the JVM with the tracing agent and plays it over UI Automation.
if ($Trace) {
    $out = "$root\config"
    $p = Start-Process java -PassThru -ArgumentList @(
        "-agentlib:native-image-agent=config-output-dir=$out",
        "-Draven.library=$work\raven.dll", '--enable-native-access=ALL-UNNAMED',
        '-cp', "$work\classes", 'simon.Simon')
    Start-Sleep 3
    & "$root\..\smoke-test.ps1" -AttachPid $p.Id
}

# ---- native-image ---------------------------------------------------------------
New-Item -ItemType Directory -Force $dist | Out-Null
Push-Location $work
try {
    & native-image `
        -cp "$work\classes" `
        -H:ConfigurationFileDirectories="$root\config" `
        -H:+UnlockExperimentalVMOptions -H:+ForeignAPISupport `
        --enable-native-access=ALL-UNNAMED `
        -Os --gc=serial --no-fallback `
        -H:NativeLinkerOption=/SUBSYSTEM:WINDOWS -H:NativeLinkerOption=/ENTRY:mainCRTStartup `
        -o simon-graal simon.Simon
    if ($LASTEXITCODE) { throw 'native-image failed' }
} finally { Pop-Location }

Copy-Item "$work\simon-graal.exe", "$work\raven.dll" $dist
# native-image may write extra files (e.g. .pdb, build artifacts) next to the exe: not shipped
Get-ChildItem $dist | Format-Table Name, Length
