param([switch]$ConfigureHttps)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = 'C:\ProgramData\AnimeComicKomiicApi'
$taskName = 'AnimeComic-KomiicApi'
$runtime = Join-Path $root 'runtime\node.exe'
$caddyExe = 'C:\ProgramData\WatchTogether\runtime\caddy\caddy.exe'
$caddyConfig = 'C:\ProgramData\WatchTogether\config\Caddyfile'
$httpsTask = 'WatchTogether-HTTPS'
$backupDir = Join-Path $root ('backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
foreach ($path in @((Split-Path $runtime -Parent), (Join-Path $root 'logs'), $backupDir)) {
    New-Item -ItemType Directory -Path $path -Force | Out-Null
}
if (-not (Test-Path -LiteralPath $runtime)) {
    Copy-Item -LiteralPath 'C:\ProgramData\AnimeComicBooksApi\runtime\node.exe' -Destination $runtime
}
& $runtime --check (Join-Path $PSScriptRoot 'server.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Invalid Komiic server' }
& $runtime --test (Join-Path $PSScriptRoot 'server.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Komiic server tests failed' }
& icacls.exe $root /grant '*S-1-5-19:(OI)(CI)RX' | Out-Null
& icacls.exe (Join-Path $root 'logs') /grant '*S-1-5-19:(OI)(CI)M' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot set service permissions' }
$configDir = Join-Path $root 'config'
New-Item -ItemType Directory -Path $configDir -Force | Out-Null
& icacls.exe $configDir /inheritance:r /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' '*S-1-5-19:(OI)(CI)RX' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot secure signing configuration' }
$keyFile = Join-Path $configDir 'image-signing.key'
if (-not (Test-Path -LiteralPath $keyFile)) {
    $keyBytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($keyBytes); [IO.File]::WriteAllBytes($keyFile, $keyBytes) } finally { $random.Dispose() }
}
if ((Get-Item -LiteralPath $keyFile).Length -ne 32) { throw 'Invalid signing key' }
$oldTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($oldTask) {
    Export-ScheduledTask -TaskName $taskName | Set-Content -LiteralPath (Join-Path $backupDir 'task.xml')
    Stop-ScheduledTask -TaskName $taskName
    Start-Sleep -Seconds 2
}
$original = $null
$caddyChanged = $false
try {
    $action = New-ScheduledTaskAction -Execute $runtime -Argument "--max-old-space-size=128 `"$(Join-Path $PSScriptRoot 'run.mjs')`"" -WorkingDirectory $PSScriptRoot
    $principal = New-ScheduledTaskPrincipal -UserId 'NT AUTHORITY\LOCAL SERVICE' -LogonType ServiceAccount
    $settings = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable -MultipleInstances IgnoreNew
    Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Settings $settings -Trigger (New-ScheduledTaskTrigger -AtStartup) -Force | Out-Null
    Start-ScheduledTask -TaskName $taskName
    $healthy = $false
    for ($i = 0; $i -lt 15; $i++) {
        try {
            $result = Invoke-RestMethod 'http://127.0.0.1:4181/api/komiic/health' -TimeoutSec 2
            if ($result.service -eq 'animecomic-komiic' -and $result.version -eq 1) { $healthy = $true; break }
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $healthy) { throw 'Komiic health check failed' }
    if ($ConfigureHttps) {
        $original = [IO.File]::ReadAllText($caddyConfig)
        if (-not $original.Contains('# BEGIN ANIMECOMIC KOMIIC')) {
            $needle = '    # BEGIN ANIMECOMIC BOOKS'
            if (-not $original.Contains($needle)) { throw 'Unexpected Caddy configuration' }
            $route = @'
    # BEGIN ANIMECOMIC KOMIIC
    handle /api/komiic/* {
        reverse_proxy 127.0.0.1:4181 {
            header_up X-Real-IP {remote_host}
        }
    }
    # END ANIMECOMIC KOMIIC
'@
            $candidate = Join-Path $backupDir 'Caddyfile.candidate'
            [IO.File]::WriteAllText($candidate, $original.Replace($needle, $route + [Environment]::NewLine + $needle), [Text.UTF8Encoding]::new($false))
            & $caddyExe validate --config $candidate --adapter caddyfile
            if ($LASTEXITCODE -ne 0) { throw 'Invalid Caddy configuration' }
            if ([IO.File]::ReadAllText($caddyConfig) -ne $original) { throw 'Caddy configuration changed concurrently' }
            Copy-Item -LiteralPath $caddyConfig -Destination (Join-Path $backupDir 'Caddyfile')
            [IO.File]::WriteAllText($caddyConfig, [IO.File]::ReadAllText($candidate), [Text.UTF8Encoding]::new($false))
            $caddyChanged = $true
            Stop-ScheduledTask -TaskName $httpsTask
            Start-Sleep -Seconds 2
            Start-ScheduledTask -TaskName $httpsTask
        }
        $tlsReady = $false
        for ($i = 0; $i -lt 15; $i++) {
            Start-Sleep -Seconds 1
            $code = & curl.exe -q --noproxy '*' --connect-timeout 2 --max-time 4 -s -o NUL -w '%{http_code}' 'https://119.28.149.19:8443/api/komiic/health'
            if ($LASTEXITCODE -eq 0 -and $code -eq '200') { $tlsReady = $true; break }
        }
        if (-not $tlsReady) { throw 'Komiic HTTPS health check failed' }
    }
} catch {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($oldTask) {
        Register-ScheduledTask -TaskName $taskName -Xml ([IO.File]::ReadAllText((Join-Path $backupDir 'task.xml'))) -Force | Out-Null
        Start-ScheduledTask -TaskName $taskName
    }
    if ($caddyChanged) {
        [IO.File]::WriteAllText($caddyConfig, $original, [Text.UTF8Encoding]::new($false))
        Stop-ScheduledTask -TaskName $httpsTask
        Start-Sleep -Seconds 2
        Start-ScheduledTask -TaskName $httpsTask
    }
    throw
}
Write-Output 'DEPLOYED: https://119.28.149.19:8443/api/komiic/health'
Write-Output "BACKUP: $backupDir"
