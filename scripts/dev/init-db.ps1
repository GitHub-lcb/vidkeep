# ===========================================
# 初始化本地 MySQL（执行 deploy/sql/init.sql）
# 用法：pwsh -File scripts/dev/init-db.ps1
# 说明：读取 .env 中的 MYSQL_* 连接信息，建库建表并写入默认管理员
# ===========================================

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

$root = Get-RepoRoot
Import-DotEnv

$mysqlClient = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysqlClient) {
    $fallback = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    if (Test-Path -LiteralPath $fallback) { $mysqlClient = $fallback }
}
if (-not $mysqlClient) {
    throw '未找到 mysql 客户端，请把 MySQL bin 目录加入 PATH 或安装 MySQL 8.0'
}

$dbHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { '127.0.0.1' }
$dbPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3306' }
$dbUser = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { 'root' }
$dbPass = $env:MYSQL_PASSWORD
$dbName = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'vidkeep' }

$sqlFile = Join-Path $root 'deploy/sql/init.sql'
if (-not (Test-Path -LiteralPath $sqlFile)) { throw "未找到 $sqlFile" }

$mysqlArgs = @('-h', $dbHost, '-P', $dbPort, '-u', $dbUser, '--default-character-set=utf8mb4')
if ($dbPass) { $mysqlArgs += "-p$dbPass" }

# Windows PowerShell 5.1 默认用 ASCII 向原生命令的 stdin 编码，中文字符会被替换成 '?'
# 这里强制 UTF-8，保证 init.sql 中的中文（nickname / 分类名等）正确入库
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

Write-Host "[db] 连接 $dbHost`:$dbPort（用户 $dbUser），执行 init.sql ..."
Get-Content -LiteralPath $sqlFile -Raw -Encoding UTF8 | & $mysqlClient @mysqlArgs
if ($LASTEXITCODE -ne 0) { throw "数据库初始化失败，exit=$LASTEXITCODE" }

Write-Host '[db] 校验表与默认管理员：'
& $mysqlClient @mysqlArgs -e "SELECT id, username, role, status FROM $dbName.user;"
if ($LASTEXITCODE -ne 0) { throw "校验失败，exit=$LASTEXITCODE" }

Write-Host '[db] 初始化完成'
