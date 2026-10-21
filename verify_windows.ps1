param(
    [Parameter(Mandatory = $true)][string]$ImageDirectory,
    [Parameter(Mandatory = $true)][string]$LogDirectory
)
$ErrorActionPreference = 'Stop'
$imageRoot = (Resolve-Path -LiteralPath $ImageDirectory).Path
$exe = Join-Path $imageRoot 'Ani.exe'
if (!(Test-Path -LiteralPath $exe)) { throw 'Missing packaged Ani.exe' }
New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
$logRoot = (Resolve-Path -LiteralPath $LogDirectory).Path
$results = @()
$oldTask = $env:ANIMEKO_DESKTOP_TEST_TASK
try {
    foreach ($task in @('anitorrent-load-test', 'mediamp-ffmpeg-smoke-test', 'sqlite-bundled-load-test', 'jcef-init-test')) {
        $env:ANIMEKO_DESKTOP_TEST_TASK = $task
        $process = Start-Process -FilePath $exe -WorkingDirectory $logRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logRoot "$task.stdout.log") `
            -RedirectStandardError (Join-Path $logRoot "$task.stderr.log")
        if (!$process.WaitForExit(120000)) {
            Stop-Process -Id $process.Id -ErrorAction SilentlyContinue
            throw "Packaged runtime test timed out: $task"
        }
        $process.WaitForExit()
        $code = $process.ExitCode
        if ($null -eq $code -or $code -ne 0) { throw "Packaged runtime test failed: $task, exit $code" }
        $results += [pscustomobject]@{test = $task; exitCode = $code}
        Write-Output "$task passed"
    }
    $results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logRoot 'runtime-verification.json') -Encoding utf8
} finally {
    $env:ANIMEKO_DESKTOP_TEST_TASK = $oldTask
}
