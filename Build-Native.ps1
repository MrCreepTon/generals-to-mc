param(
    [string]$NativeRoot = (Join-Path $env:LOCALAPPDATA 'GeneralsMinecraft'),
    [string]$ZeroHour = 'C:/Games/Generals ZH',
    [string]$Generals = 'C:/Games/Generals',
    [switch]$Rebuild,
    [switch]$NoStage
)
$ErrorActionPreference = 'Stop'
$env:PYTHONUTF8 = '1'
$sourcePath = Join-Path $NativeRoot 'native-src/GeneralsGameCode'
$buildPath = Join-Path $sourcePath 'build/minecraft-bridge'
$pin = 'f746af7e626c96e693cb74a032da47a602c1a039'
foreach ($command in @('git','cmake','python')) { if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "Required command is absent: $command" } }
New-Item -ItemType Directory -Force -Path $NativeRoot | Out-Null
$lockPath = Join-Path $NativeRoot '.native-build.lock'
try {
    $buildLock = [IO.File]::Open($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
} catch [IO.IOException] {
    throw "Another native build is using $NativeRoot; wait for it to finish before starting this script again"
}
try {
if (-not (Test-Path -LiteralPath $sourcePath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $sourcePath -Parent) | Out-Null
    & git clone https://github.com/TheSuperHackers/GeneralsGameCode.git $sourcePath
    if ($LASTEXITCODE -ne 0) { throw 'Original source clone failed' }
    & git -C $sourcePath checkout --detach $pin
    if ($LASTEXITCODE -ne 0) { throw 'Pinned source checkout failed' }
}
& python -X utf8 (Join-Path $PSScriptRoot 'tools/prepare_engine.py') $sourcePath
if ($LASTEXITCODE -ne 0) { throw 'Source integration failed; existing source must be at the pinned revision' }
& cmake -S $sourcePath -B $buildPath -G 'Visual Studio 17 2022' -A Win32 `
    -DRTS_BUILD_GENERALS=OFF -DRTS_BUILD_CORE_TOOLS=OFF -DRTS_BUILD_ZEROHOUR_TOOLS=OFF `
    -DRTS_BUILD_OPTION_RETAIL_COMPATIBLE_GAME=ON -DRTS_DEBUG_LOGGING=ON -DRTS_DEBUG_CRASHING=OFF `
    -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded
if ($LASTEXITCODE -ne 0) { throw 'Native configure failed; VS2022 C++ and Windows SDK are required' }
$headerPaths = @(& git -c core.autocrlf=false -C $sourcePath diff --name-only -- '*.h')
$headerHashes = @($headerPaths | Sort-Object | ForEach-Object { (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $sourcePath $_)).Hash })
$headerHashes += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'native') -Filter '*.h' | Sort-Object Name | ForEach-Object { (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash })
$headerSignature = $headerHashes -join ':'
$signatureFile = Join-Path $buildPath 'minecraft-header-signature.txt'
$forceRebuild = $Rebuild -or -not (Test-Path -LiteralPath $signatureFile) -or ([IO.File]::ReadAllText($signatureFile) -ne $headerSignature)
$buildArguments = @('--build', $buildPath, '--config', 'Release', '--target', 'z_generals', '--parallel', '1')
if ($forceRebuild) {
    # Rebuild can clean shared generated headers while another project compiles;
    # VS Clean also leaves some referenced engine objects intact. Invalidate only
    # generated compiler outputs, preserving fetched SDK libraries and source files.
    $buildPrefix = [IO.Path]::GetFullPath($buildPath).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
    $compilerOutputs = @(Get-ChildItem -LiteralPath $buildPath -Recurse -File | Where-Object {
        $_.Extension -in @('.obj', '.pch') -and $_.FullName -match '\.dir[\\/]'
    })
    foreach ($output in $compilerOutputs) {
        $outputPath = [IO.Path]::GetFullPath($output.FullName)
        if (-not $outputPath.StartsWith($buildPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Compiler output escaped the native build directory: $outputPath"
        }
        Remove-Item -LiteralPath $outputPath -Force
    }
    # Invalidation is complete even if a later compile fails; retries may reuse the new objects.
    [IO.File]::WriteAllText($signatureFile, $headerSignature)
    Write-Host "Cleared $($compilerOutputs.Count) generated compiler outputs for a full rebuild"
}
# This target generates EABrowserDispatch/BrowserDispatch.h from the original IDL.
# Build it explicitly before the engine, including when recovering a missing header.
& cmake --build $buildPath --config Release --target core_browserdispatchwin --parallel 1
if ($LASTEXITCODE -ne 0) { throw "BrowserDispatch IDL generation failed (exit $LASTEXITCODE); see MIDL/MSBuild output above" }
# Preserve the proven ordering from Build-Native-fixed.ps1: finish the device
# library, verify the output, and only then build/link the executable serially.
& cmake --build $buildPath --config Release --target z_gameenginedevice --parallel 1
if ($LASTEXITCODE -ne 0) { throw "GameEngineDevice library build failed (exit $LASTEXITCODE); see MSBuild output above" }
$deviceLibrary = Join-Path $buildPath 'GeneralsMD/Code/GameEngineDevice/Release/z_gameenginedevice.lib'
if (-not (Test-Path -LiteralPath $deviceLibrary -PathType Leaf)) {
    throw "GameEngineDevice library was not produced: $deviceLibrary"
}
& cmake @buildArguments
if ($LASTEXITCODE -ne 0) { throw "Native compilation or linking failed (exit $LASTEXITCODE); see the first compiler/linker error above" }
[IO.File]::WriteAllText($signatureFile, $headerSignature)
if (-not $NoStage) {
    & python -X utf8 (Join-Path $PSScriptRoot 'tools/stage_native.py') --zh $ZeroHour --base $Generals --lab (Join-Path $NativeRoot 'native-lab')
    if ($LASTEXITCODE -ne 0) { throw 'Private asset staging failed' }
}
$bundleDirectory=Join-Path $PSScriptRoot 'build/native/windows-x86'
New-Item -ItemType Directory -Force -Path $bundleDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $buildPath 'GeneralsMD/Release/generalszh.exe') -Destination (Join-Path $bundleDirectory 'generalszh.exe') -Force
$symbolFile=Join-Path $buildPath 'GeneralsMD/Release/generalszh.pdb'
if (Test-Path -LiteralPath $symbolFile -PathType Leaf) {
    # Keep matching symbols privately for reports from older packaged runtimes.
    $engineHash=(Get-FileHash -LiteralPath (Join-Path $bundleDirectory 'generalszh.exe') -Algorithm SHA256).Hash.ToLowerInvariant()
    $symbolDirectory=Join-Path $NativeRoot "private/symbols/$engineHash"
    New-Item -ItemType Directory -Force -Path $symbolDirectory | Out-Null
    Copy-Item -LiteralPath $symbolFile -Destination (Join-Path $symbolDirectory 'generalszh.pdb') -Force
}
$installedRuntime = Join-Path $NativeRoot 'runtime/generalszh.exe'
if (Test-Path -LiteralPath $installedRuntime -PathType Leaf) {
    Copy-Item -LiteralPath (Join-Path $buildPath 'GeneralsMD/Release/generalszh.exe') -Destination $installedRuntime -Force
}
Write-Host "Native engine ready: $(Join-Path $buildPath 'GeneralsMD/Release/generalszh.exe')"
} finally {
    $buildLock.Dispose()
}
