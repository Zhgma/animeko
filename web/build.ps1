param([string]$Task = 'wasmJsBrowserDistribution')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$java = Get-ChildItem -LiteralPath "$workspace/toolchain/jbr21" -Filter java.exe -Recurse | Select-Object -First 1
if ($java) { $env:JAVA_HOME = Split-Path (Split-Path $java.FullName -Parent) -Parent }
Push-Location $PSScriptRoot
try {
    & ../gradlew.bat $Task '--console=plain' '--max-workers=4'
    if ($LASTEXITCODE -ne 0) { throw "Web build failed ($LASTEXITCODE)" }
    if (Get-Command node -ErrorAction SilentlyContinue) {
        & node ./audit-shared-sources.mjs
        if ($LASTEXITCODE -ne 0) { throw "Shared source audit failed ($LASTEXITCODE)" }
    }
} finally { Pop-Location }
