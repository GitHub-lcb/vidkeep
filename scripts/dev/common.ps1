# ===========================================
# 本地开发公共函数（Windows / PowerShell 7）
# 被 scripts/dev 下其他脚本 dot-source 引用
# ===========================================

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

function Get-RepoRoot {
    return $script:RepoRoot
}

# 把仓库根目录 .env 中的 KEY=VALUE 注入当前进程环境变量
function Import-DotEnv {
    param([string]$Path = (Join-Path $script:RepoRoot '.env'))

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "缺少环境变量文件：$Path，请先复制 .env.example 为 .env 并填写"
    }

    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if ($value.Length -ge 2) {
            $first = $value.Substring(0, 1)
            $last = $value.Substring($value.Length - 1, 1)
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }

    Write-Host "[env] 已加载 $Path"
}

# 项目要求 JDK 21；默认 PATH 可能是老版本 JDK，这里显式定位
# 优先级：VIDKEEP_JDK21 → JDK21_HOME → 常见安装目录自动探测
function Find-Jdk21 {
    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($explicit in @($env:VIDKEEP_JDK21, $env:JDK21_HOME)) {
        if ($explicit) { $candidates.Add($explicit) }
    }

    $searchRoots = @(
        'C:\Program Files\Java',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Zulu',
        (Join-Path $env:USERPROFILE '.jdks')
    )
    foreach ($root in $searchRoots) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '21' } |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    $jdkHome = $candidates |
        Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')) } |
        Select-Object -First 1
    if (-not $jdkHome) {
        throw '未找到 JDK 21，请设置环境变量 VIDKEEP_JDK21 或 JDK21_HOME 指向 JDK 21 安装目录'
    }
    return $jdkHome
}
