# ===========================================
# 本地开发用便携 Redis（必须 >= 5.0，项目用 Redis Stream 做转码队列）
# 用法：powershell -ExecutionPolicy Bypass -File scripts/dev/start-redis-dev.ps1
# 说明：Windows 自带的 Redis 3.x 不支持 XADD/XREADGROUP，因此开发期使用便携版；
#       生产环境由 docker compose 的 redis:7 提供，无需本脚本。
# ===========================================

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

$root = Get-RepoRoot
Import-DotEnv

$port = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { '6379' }

$listening = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
if ($listening) {
    Write-Host "[redis] 端口 $port 已在监听，跳过启动"
    exit 0
}

# 在工具目录中查找 redis-server.exe（支持 VIDKEEP_TOOLS 自定义）
$searchRoots = @(
    $env:VIDKEEP_TOOLS,
    (Join-Path $root '..\..\tools')
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

$server = $null
foreach ($searchRoot in $searchRoots) {
    $server = Get-ChildItem -LiteralPath $searchRoot -Recurse -Depth 3 -Filter 'redis-server.exe' -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    if ($server) { break }
}
if (-not $server) {
    throw '未找到 redis-server.exe（需要 Redis >= 5），请设置 VIDKEEP_TOOLS 指向工具目录，或下载便携 Redis 5 解压到工具目录'
}

$dataDir = Join-Path $root 'data'
if (-not (Test-Path -LiteralPath $dataDir)) {
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
}

Write-Host "[redis] 启动便携 Redis：$server（端口 $port，数据目录 $dataDir）"
Start-Process -FilePath $server `
    -ArgumentList @('--port', $port, '--appendonly', 'no') `
    -WorkingDirectory $dataDir `
    -WindowStyle Hidden | Out-Null

Start-Sleep -Seconds 1
$ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue).TcpTestSucceeded
Write-Host "[redis] 启动结果：$(if ($ok) { 'OK' } else { '失败' })"
