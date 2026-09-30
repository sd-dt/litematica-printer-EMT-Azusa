<#
  重建校验：用当前工作区的源码 + 资源 + 依赖重新构建，与 dist 里的发布件逐条目比对

  用途：确认迁移后的工作区能**原样复现**已发布的 jar（源码/资源/依赖/JDK/编译参数都对齐）。

  用法：
    powershell -ExecutionPolicy Bypass -File scripts/verify-rebuild.ps1 -Line 1.21.11
    powershell -ExecutionPolicy Bypass -File scripts/verify-rebuild.ps1 -Line 26.2
    powershell -ExecutionPolicy Bypass -File scripts/verify-rebuild.ps1 -Line 1.21.11 -Against versions\1.21.11\dist\xxx.jar
    # 历史发布件（2026-09-25 之前）里夹带了 4 个被替换掉的旧 utils/bedrock 类，比那些老件时按下面用法豁免：
    powershell -ExecutionPolicy Bypass -File scripts/verify-rebuild.ps1 -Line 1.21.11 `
        -Against versions\1.21.11\dist\litematica-printer-EMT-1.4-dt260919a.jar `
        -AllowOrphan 'me/aleksilassila/litematica/printer/utils/bedrock/*'

  说明：默认基准取 dist 里该构建线**文件名排序最后**的 jar（新命名 dt<日期><字母>[-26.2] 天然按日期+字母递增）。
        2026-09-25 起源码有改动（挖掘栏新增「迭代形状」），比 09-25 之前的老发布件会报 Configs$Mine 不同 —— 这是预期的，
        要比就显式 -Against 并自行判断。
#>
param(
    [Parameter(Mandatory = $true)][ValidateSet('1.21.11', '26.2', 'emt-260925')][string]$Line,
    [string]$Against,
    [string[]]$AllowOrphan = @()
)

$ErrorActionPreference = 'Stop'
$Root    = Split-Path -Parent $PSScriptRoot
$LineDir = Join-Path $Root "versions\$Line"
$DistDir = Join-Path $LineDir 'dist'

function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }
function Ok($msg)   { Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Info($msg) { Write-Host "[ .. ] $msg" -ForegroundColor Cyan }

if (-not $Against) {
    # 26.2 线：新旧两种后缀都收（新 -26.2 / 旧 +26.2）；1.21.11 线：排除掉带 26.2 后缀的
    if ($Line -ne '1.21.11') {
        $cand = @(Get-ChildItem -LiteralPath $DistDir -Filter 'litematica-printer-EMT-*dt*.jar' -File |
                  Where-Object { $_.Name -like '*-26.2.jar' -or $_.Name -like '*+26.2.jar' } | Sort-Object Name)
    } else {
        $cand = @(Get-ChildItem -LiteralPath $DistDir -Filter 'litematica-printer-EMT-*dt*.jar' -File |
                  Where-Object { $_.Name -notlike '*-26.2.jar' -and $_.Name -notlike '*+26.2.jar' } | Sort-Object Name)
    }
    if ($cand.Count -eq 0) { Fail "dist 里没有可比的 jar：$DistDir" }
    $Against = $cand[-1].FullName
}
$Against = (Resolve-Path -LiteralPath $Against).Path
Info "比对基准：$Against"

$major   = if ($Line -eq '1.21.11') { 21 } else { 25 }
$jdkFile = Join-Path $PSScriptRoot "jdk-$major.path"
$jdk     = if (Test-Path -LiteralPath $jdkFile) { (Get-Content -LiteralPath $jdkFile -Raw).Trim() } else { $null }
$jarExe  = if ($jdk -and (Test-Path -LiteralPath (Join-Path $jdk 'bin\jar.exe'))) { Join-Path $jdk 'bin\jar.exe' } else { 'jar' }

$work   = Join-Path $env:TEMP ("lp-verify-" + [guid]::NewGuid().ToString('N'))
$dirOld = Join-Path $work 'old'
$dirNew = Join-Path $work 'new'
New-Item -ItemType Directory -Force -Path $dirOld, $dirNew | Out-Null

try {
    # 1) 重建到临时目录（不覆盖 dist 里的发布件）
    $rebuilt = Join-Path $work 'rebuilt.jar'
    & (Join-Path $PSScriptRoot 'build.ps1') -Line $Line -OutJar $rebuilt
    if ($LASTEXITCODE -ne 0) { Fail "重建失败" }
    if (-not (Test-Path -LiteralPath $rebuilt)) { Fail "重建没有产出 jar" }

    # 2) 展开两边（jar 的 -C 只对 create/update 有效，解包必须切到目标目录）
    Push-Location $dirOld
    & $jarExe --extract --file $Against 2>&1 | Out-Null
    Pop-Location
    Push-Location $dirNew
    & $jarExe --extract --file $rebuilt 2>&1 | Out-Null
    Pop-Location

    # 3) 逐条目比对
    $filesOld = @(Get-ChildItem -LiteralPath $dirOld -Recurse -File | ForEach-Object { $_.FullName.Substring($dirOld.Length + 1).Replace('\', '/') } | Sort-Object)
    $filesNew = @(Get-ChildItem -LiteralPath $dirNew -Recurse -File | ForEach-Object { $_.FullName.Substring($dirNew.Length + 1).Replace('\', '/') } | Sort-Object)
    if ($filesOld.Count -eq 0 -or $filesNew.Count -eq 0) {
        Fail "解包结果为空（发布件 $($filesOld.Count) 条 / 重建 $($filesNew.Count) 条），无法比对"
    }

    $onlyOld = @($filesOld | Where-Object { $filesNew -notcontains $_ })
    $onlyNew = @($filesNew | Where-Object { $filesOld -notcontains $_ })
    $diff    = @()
    foreach ($f in ($filesOld | Where-Object { $filesNew -contains $_ })) {
        $h1 = (Get-FileHash -LiteralPath (Join-Path $dirOld $f) -Algorithm SHA256).Hash
        $h2 = (Get-FileHash -LiteralPath (Join-Path $dirNew $f) -Algorithm SHA256).Hash
        if ($h1 -ne $h2) { $diff += $f }
    }

    # 发布件里历史遗留的孤儿条目（例如 1.21.11 线 dt260919a 里被替换掉的 utils/bedrock 旧实现），
    # 当前源码树已不含它们，重建自然不会产出 —— 用 -AllowOrphan 明确豁免，其余差异仍然报错
    $orphan = @()
    if ($AllowOrphan.Count -gt 0 -and $onlyOld.Count -gt 0) {
        $orphan  = @($onlyOld | Where-Object { $p = $_; @($AllowOrphan | Where-Object { $p -like $_ }).Count -gt 0 })
        $onlyOld = @($onlyOld | Where-Object { $orphan -notcontains $_ })
    }

    Info "条目数：发布件 $($filesOld.Count) / 重建 $($filesNew.Count)"
    if ($orphan.Count)  { Write-Host "      已豁免的孤儿条目（历史遗留）：$($orphan -join ', ')" -ForegroundColor DarkYellow }
    if ($onlyOld.Count) { Write-Host "      仅发布件有：$($onlyOld -join ', ')" -ForegroundColor Yellow }
    if ($onlyNew.Count) { Write-Host "      仅重建有：$($onlyNew -join ', ')" -ForegroundColor Yellow }
    if ($diff.Count)    { Write-Host "      内容不同：$($diff -join ', ')" -ForegroundColor Yellow }

    if ($onlyOld.Count -eq 0 -and $onlyNew.Count -eq 0 -and $diff.Count -eq 0) {
        Ok "重建产物与发布件逐条目逐字节一致（发布件 $($filesOld.Count) 条，其中孤儿 $($orphan.Count) 条已豁免）"
        Ok "说明：本条构建线的源码/资源/依赖/JDK 与本工作区完全自洽"
    } else {
        Fail "重建产物与发布件不一致，见上方差异"
    }
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
}
