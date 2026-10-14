# Build a static Pages release with a bounded, reproducible Wasm optimization pass.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$taskWorkspace = Split-Path $taskRoot -Parent
$taskJava = Get-ChildItem "$taskWorkspace/toolchain/jbr21" -Filter java.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($taskJava) { $env:JAVA_HOME = $taskJava.Directory.Parent.FullName }
Push-Location $taskRoot
try {
    & ./gradlew.bat -p web :kotlinWasmBinaryenSetup :compileProductionExecutableKotlinWasmJs --console=plain --max-workers=4
    if ($LASTEXITCODE -ne 0) { throw 'Web compilation failed' }
    $taskGradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $taskBinaryen = Get-ChildItem "$taskGradleCache/binaryen" -Filter wasm-opt.exe -Recurse | Sort-Object FullName -Descending | Select-Object -First 1
    if (!$taskBinaryen) { throw 'Binaryen runtime not found' }
    $taskOutput = 'web/build/compileSync/wasmJs/main/productionExecutable'
    New-Item -ItemType Directory -Force "$taskOutput/optimized" | Out-Null
    Copy-Item "$taskOutput/kotlin/animecomic-web*" "$taskOutput/optimized/" -Force
    & $taskBinaryen.FullName --enable-gc --enable-reference-types --enable-exception-handling --enable-bulk-memory --enable-nontrapping-float-to-int -O1 "$taskOutput/kotlin/animecomic-web.wasm" -o "$taskOutput/optimized/animecomic-web.wasm"
    if ($LASTEXITCODE -ne 0) { throw 'Wasm optimization failed' }
    & ./gradlew.bat -p web :wasmJsBrowserDistribution -x :compileProductionExecutableKotlinWasmJsOptimize --console=plain --max-workers=4
    if ($LASTEXITCODE -ne 0) { throw 'Pages distribution failed' }
} finally { Pop-Location }
