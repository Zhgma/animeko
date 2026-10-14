param([switch]$Production, [switch]$UseEnvironmentProxy)
$ErrorActionPreference = 'Stop'
$healthUrl = 'http://127.0.0.1:4173/api/health'
try { $running = Invoke-RestMethod $healthUrl -TimeoutSec 2 } catch { $running = $null }
if ($running.service -eq 'animecomic-web') {
    Write-Output "AnimeComic Web is already running (PID $($running.pid)): http://127.0.0.1:4173/"
    exit 0
}
if (Get-NetTCPConnection -LocalPort 4173 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 4173 is occupied by another service.'
}
$nodePath = (Get-Command node -ErrorAction Stop).Source
$logDirectory = Join-Path $PSScriptRoot 'build/server'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$arguments = @()
if ($UseEnvironmentProxy) { $arguments += '--use-env-proxy' }
$arguments += 'serve.mjs'
if (!$Production) { $arguments += '--development' }
$serverProcess = Start-Process -FilePath $nodePath -ArgumentList $arguments -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'stdout.log') -RedirectStandardError (Join-Path $logDirectory 'stderr.log')
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    try {
        $running = Invoke-RestMethod $healthUrl -TimeoutSec 1
        if ($running.service -eq 'animecomic-web' -and $running.pid -eq $serverProcess.Id) {
            Write-Output "AnimeComic Web is running independently (PID $($serverProcess.Id)): http://127.0.0.1:4173/"
            exit 0
        }
    } catch {}
    if ($serverProcess.HasExited) { throw "Web server exited. See $logDirectory/stderr.log" }
    Start-Sleep -Milliseconds 250
}
throw "Web server did not become ready. See $logDirectory/stderr.log"
