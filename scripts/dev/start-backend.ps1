# ===========================================
# 本地启动后端（Windows）
# 用法：pwsh -File scripts/dev/start-backend.ps1
# 说明：自动加载仓库根目录 .env、切换到 JDK 21，再执行 mvnw spring-boot:run
# ===========================================

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')

$root = Get-RepoRoot
Import-DotEnv

$env:JAVA_HOME = Find-Jdk21

$serverDir = Join-Path $root 'video-server'
$mvnw = Join-Path $serverDir 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $mvnw)) {
    throw "未找到 $mvnw，请先在 video-server 目录执行：mvn -N wrapper:wrapper"
}

Write-Host "[run] JAVA_HOME = $env:JAVA_HOME"
Write-Host '[run] 启动 Spring Boot（http://localhost:8080）...'

Set-Location $serverDir
& $mvnw spring-boot:run
