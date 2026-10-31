<#
  build-multi.ps1 — 把三条构建线的产物打包成一个「三版本兼容」jar（融合包 / wrapper jar）

  结构（沿用 litematica-printer-EMT 官方的做法，已在原版 260918 包上验证过）：
    fabric.mod.json                       id=litematica-printer-wrapper，depends.minecraft 列出三个版本
    META-INF/jars/1.21.11-<内层>.jar      内层 fabric.mod.json depends.minecraft = "1.21.11"
    META-INF/jars/26.2-<内层>.jar         内层 fabric.mod.json depends.minecraft = ">=26.2 <26.3"
    META-INF/jars/26.3-<内层>.jar         内层 fabric.mod.json depends.minecraft = ">=26.3 <26.4"
  Fabric Loader 会把三个内层都当作候选，按各自 depends 选当前游戏版本能用的那个加载。

  ★ 三版本与双版本的唯一实质差异：depends.minecraft 必须**两两互斥**。
    ">=26.2" 会连带匹配 26.3、27.x —— 26.3 游戏下 26.2 与 26.3 两个内层都满足依赖
    → Duplicate mod id 直接崩。所以 26.x 内层必须写上界（见 versions\emt-*\resources\fabric.mod.json）。
    本脚本会把这件事当成硬校验：任何一个内层在某个已知 MC 版本下「多个内层同时匹配」就直接 Fail。

  用法：
    powershell -ExecutionPolicy Bypass -File scripts\build-multi.ps1
    powershell -ExecutionPolicy Bypass -File scripts\build-multi.ps1 -Jar26 <jar> -Jar263 <jar> -Jar121 <jar> -Name <文件名>

  产物：artifacts\litematica-printer-EMT-Azusa-multi-dt<YYMMDD><a|b|...>.jar
#>
param(
    [string]$Jar26,
    [string]$Jar263,
    [string]$Jar121,
    [string]$Name
)

$ErrorActionPreference = 'Stop'
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }
function Ok($msg)   { Write-Host "[ OK ] $msg" -ForegroundColor Green }
function Info($msg) { Write-Host "[ .. ] $msg" -ForegroundColor Cyan }

$WS = Split-Path -Parent $PSScriptRoot

# JDK（用 26.2 线的 JDK 25 里的 jar.exe 即可，打包不涉及 class 版本）
$jdkPathFile = Join-Path $PSScriptRoot 'jdk-25.path'
if (-not (Test-Path -LiteralPath $jdkPathFile)) { Fail "找不到 $jdkPathFile" }
$jdk = (Get-Content -LiteralPath $jdkPathFile -Raw).Trim()
$JarExe = Join-Path $jdk 'bin\jar.exe'
if (-not (Test-Path -LiteralPath $JarExe)) { Fail "找不到 jar.exe：$JarExe" }

function Get-LatestJar([string]$dir) {
    # 只认 dt<YYMMDD><字母> 命名的正式产物，并按修改时间取最新（按文件名排序会挑到别的变体）
    $j = Get-ChildItem -LiteralPath $dir -Filter '*.jar' |
         Where-Object { $_.Name -match 'dt\d{6}[a-z]' } |
         Sort-Object LastWriteTime |
         Select-Object -Last 1
    if (-not $j) { Fail "目录里没有 dt 命名的 jar：$dir" }
    return $j.FullName
}

# 三条线 → (标签, dist 目录, 该线唯一支持的 MC 版本)
$Lines = @(
    @{ Tag = '1.21.11'; Dir = 'versions\1.21.11\dist';  Mc = '1.21.11' },
    @{ Tag = '26.2';    Dir = 'versions\emt-260925\dist'; Mc = '26.2' },
    @{ Tag = '26.3';    Dir = 'versions\emt-263\dist';    Mc = '26.3' }
)

# ---------- 版本比较 / 区间匹配（Fabric depends 用的是 ">=a <b" 这类写法） ----------
function Compare-Version([string]$a, [string]$b) {
    $aa = $a.Split('.'); $bb = $b.Split('.')
    $n = [Math]::Max($aa.Length, $bb.Length)
    for ($i = 0; $i -lt $n; $i++) {
        $x = if ($i -lt $aa.Length) { [int]$aa[$i] } else { 0 }
        $y = if ($i -lt $bb.Length) { [int]$bb[$i] } else { 0 }
        if ($x -ne $y) { if ($x -lt $y) { return -1 } else { return 1 } }
    }
    return 0
}

# 单个 depends.minecraft 值（字符串或单元素数组）→ 是否匹配某个 MC 版本
function Test-McRange([object]$raw, [string]$ver) {
    if ($null -eq $raw) { return $false }
    if ($raw -is [System.Array]) {
        if ($raw.Count -ne 1) { Fail "depends.minecraft 是多值数组，本脚本只支持单值：$($raw -join ', ')" }
        $raw = $raw[0]
    }
    $range = ([string]$raw).Trim()
    if ($range -eq '' -or $range -eq '*') { return $true }
    # 没有比较运算符 → 精确版本号
    if ($range -notmatch '[<>=~^]') { return $range -eq $ver }
    foreach ($tok in ($range -split '\s+' | Where-Object { $_ })) {
        $m = [regex]::Match($tok, '^(>=|<=|>|<|=)?(.+)$')
        $op = $m.Groups[1].Value
        if ($op -eq '') { $op = '=' }
        $bound = $m.Groups[2].Value
        $c = Compare-Version $ver $bound
        if ($op -eq '>=' -and $c -lt 0) { return $false }
        if ($op -eq '>'  -and $c -le 0) { return $false }
        if ($op -eq '<=' -and $c -gt 0) { return $false }
        if ($op -eq '<'  -and $c -ge 0) { return $false }
        if ($op -eq '='  -and $c -ne 0) { return $false }
    }
    return $true
}

# 从 ">=a <b" 里取出 (下界, 上界)，用来合成外层的并集区间
function Get-RangeBounds([object]$raw) {
    if ($raw -is [System.Array]) { $raw = $raw[0] }
    $low = $null; $high = $null
    foreach ($tok in ((([string]$raw).Trim()) -split '\s+' | Where-Object { $_ })) {
        $m = [regex]::Match($tok, '^(>=|<=|>|<)?(.+)$')
        $op = $m.Groups[1].Value; $b = $m.Groups[2].Value
        if ($op -eq '>=' -or $op -eq '>') {
            if ($null -eq $low -or (Compare-Version $b $low) -lt 0) { $low = $b }
        }
        if ($op -eq '<=' -or $op -eq '<') {
            if ($null -eq $high -or (Compare-Version $b $high) -gt 0) { $high = $b }
        }
    }
    return @($low, $high)
}

# ---------- 定位三条内层 ----------
$JarOf = @{}
foreach ($l in $Lines) {
    $p = $null
    switch ($l.Tag) {
        '1.21.11' { if ($Jar121) { $p = $Jar121 } }
        '26.2'    { if ($Jar26)  { $p = $Jar26 } }
        '26.3'    { if ($Jar263) { $p = $Jar263 } }
    }
    if (-not $p) { $p = Get-LatestJar (Join-Path $WS $l.Dir) }
    if (-not (Test-Path -LiteralPath $p)) { Fail "找不到 $($l.Tag) jar：$p" }
    $JarOf[$l.Tag] = (Resolve-Path -LiteralPath $p).Path
    Info "$($l.Tag.PadRight(7)) 内层：$($JarOf[$l.Tag])"
}

# ---------- 读内层 fabric.mod.json（用 jar 解到临时目录再读，避免依赖 unzip） ----------
function Read-ModJson([string]$jar) {
    $tmp = Join-Path $env:TEMP ('lp-multi-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    Push-Location $tmp
    try {
        & $JarExe xf $jar fabric.mod.json 2>$null | Out-Null
        if (-not (Test-Path (Join-Path $tmp 'fabric.mod.json'))) { Fail "jar 里没有 fabric.mod.json：$jar" }
        $json = Get-Content -LiteralPath (Join-Path $tmp 'fabric.mod.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        return $json
    } finally { Pop-Location; Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
}

$ModOf = @{}
foreach ($l in $Lines) { $ModOf[$l.Tag] = Read-ModJson $JarOf[$l.Tag] }

foreach ($l in $Lines) {
    if ($ModOf[$l.Tag].id -ne 'litematica-printer') {
        Fail "内层 mod id 不是 litematica-printer（$($l.Tag)：$($ModOf[$l.Tag].id)）"
    }
}
Ok "内层 id 校验通过（三条线都是 litematica-printer）"
foreach ($l in $Lines) {
    Info "  $($l.Tag.PadRight(7)) version=$($ModOf[$l.Tag].version)  minecraft=$($ModOf[$l.Tag].depends.minecraft)"
}

# ---------- 硬校验：三个内层在三个已知 MC 版本下必须「正好各命中一个」 ----------
# 这就是 Duplicate mod id 的防线：任何两个内层的 minecraft 区间一旦在某个版本上重叠，这里直接 Fail
$KnownMc = @($Lines | ForEach-Object { $_.Mc })
foreach ($mc in $KnownMc) {
    $hit = @($Lines | Where-Object { Test-McRange $ModOf[$_.Tag].depends.minecraft $mc })
    if ($hit.Count -ne 1) {
        $desc = ($hit | ForEach-Object { "$($_.Tag)($($ModOf[$_.Tag].depends.minecraft))" }) -join ' + '
        if ($hit.Count -eq 0) {
            Fail "MC $mc 没有任何内层匹配 —— 装进这个版本会『完全没有打印机』。命中：$desc"
        } else {
            Fail "MC $mc 有 $($hit.Count) 个内层同时匹配（$desc）→ Duplicate mod id 崩溃。`n      请把各线 resources\fabric.mod.json 的 depends.minecraft 改成两两互斥的区间（26.x 必须写上界，如 "">=26.2 <26.3""）。"
        }
    }
    Ok "MC $mc → 只有 $($hit[0].Tag) 内层生效"
}

# ---------- 产物名：默认按当天日期 + 字母顺延（只统计 multi-dt 前缀） ----------
$date = Get-Date -Format 'yyMMdd'
if (-not $Name) {
    $existing = Get-ChildItem -LiteralPath (Join-Path $WS 'artifacts') -Filter "*-multi-dt$date*.jar" -ErrorAction SilentlyContinue | ForEach-Object { $_.Name }
    $letters = @('a','b','c','d','e','f','g','h')
    $idx = 0
    foreach ($l in $letters) {
        if ($existing -like "*-multi-dt$date$l*.jar") { $idx++ } else { break }
    }
    if ($idx -ge $letters.Count) { Fail "今天的字母用完了，请用 -Name 指定" }
    $Name = "litematica-printer-EMT-Azusa-multi-dt$date$($letters[$idx]).jar"
}
$outDir = Join-Path $WS 'artifacts'
if (-not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
$out = Join-Path $outDir $Name
# 外层版本号与文件名保持一致（游戏里显示的版本 = 文件名）
$wrapperVersion = 'Azusa-multi-' + ($Name -replace '^litematica-printer-EMT-Azusa-multi-', '' -replace '\.jar$', '')

# ---------- 组装 stage ----------
$stage = Join-Path $env:TEMP ('lp-multi-stage-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'META-INF\jars') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'assets\litematica-printer-wrapper') | Out-Null

foreach ($l in $Lines) {
    Copy-Item -LiteralPath $JarOf[$l.Tag] -Destination (Join-Path $stage ("META-INF\jars\$($l.Tag)-" + (Split-Path -Leaf $JarOf[$l.Tag]))) -Force
}

# 图标（从 26.2 内层里取）
Push-Location $stage
try { & $JarExe xf $JarOf['26.2'] 'assets/litematica-printer/icon.png' 2>$null | Out-Null } finally { Pop-Location }
$iconSrc = Join-Path $stage 'assets\litematica-printer\icon.png'
if (Test-Path -LiteralPath $iconSrc) {
    Copy-Item -LiteralPath $iconSrc -Destination (Join-Path $stage 'assets\litematica-printer-wrapper\icon.png') -Force
    Remove-Item -LiteralPath (Join-Path $stage 'assets\litematica-printer') -Recurse -Force
}

# ---------- 外层 fabric.mod.json ----------
# depends.minecraft = 三个内层区间的并集：1.21.11 是精确版本；26.x 各段取下界最小、上界最大合成一段
$mcUnion = @('1.21.11')
$lows = @(); $highs = @()
foreach ($l in ($Lines | Where-Object { $_.Tag -like '26.*' })) {
    $b = Get-RangeBounds $ModOf[$l.Tag].depends.minecraft
    if ($b[0]) { $lows  += $b[0] }
    if ($b[1]) { $highs += $b[1] }
}
if ($lows.Count -gt 0) {
    $lo = $lows[0];  foreach ($x in $lows)  { if ((Compare-Version $x $lo) -lt 0) { $lo = $x } }
    $hi = $highs[0]; foreach ($x in $highs) { if ((Compare-Version $x $hi) -gt 0) { $hi = $x } }
    $mcUnion += ">=$lo <$hi"
}
$mcUnionJson = ($mcUnion | ForEach-Object { "      `"$_`"" }) -join ",`n"

$jarsJson = ($Lines | ForEach-Object { "      { `"file`": `"META-INF/jars/$($_.Tag)-$(Split-Path -Leaf $JarOf[$_.Tag])`" }" }) -join ",`n"

$iconLine = ''
if (Test-Path -LiteralPath (Join-Path $stage 'assets\litematica-printer-wrapper\icon.png')) {
    $iconLine = '  "icon": "assets/litematica-printer-wrapper/icon.png",'
}
$json = @"
{
  "schemaVersion": 1,
  "id": "litematica-printer-wrapper",
  "version": "$wrapperVersion",
  $iconLine
  "name": "Litematica Printer EMT Azusa! 超多功能优化打印机！",
  "authors": [
    "sd_dt",
    "MoMortis",
    "deepseekfl4.1"
  ],
  "license": "AGPL-3.0",
  "environment": "*",
  "depends": {
    "minecraft": [
$mcUnionJson
    ]
  },
  "jars": [
$jarsJson
  ]
}
"@
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $stage 'fabric.mod.json'), $json, $utf8NoBom)

# ---------- 打包 ----------
if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Force }
Push-Location $stage
try {
    & $JarExe --create --file $out -C $stage .
    if ($LASTEXITCODE -ne 0) { Fail "jar 打包失败 exit=$LASTEXITCODE" }
} finally { Pop-Location }

# ---------- 校验 ----------
$entries = & $JarExe tf $out
$need = @('fabric.mod.json')
foreach ($l in $Lines) { $need += "META-INF/jars/$($l.Tag)-$(Split-Path -Leaf $JarOf[$l.Tag])" }
$missing = @()
foreach ($n in $need) { if (-not ($entries -contains $n)) { $missing += $n } }
if ($missing.Count -gt 0) { Fail "jar 缺少条目：$($missing -join ', ')" }

$outItem = Get-Item -LiteralPath $out
$hash = (Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash
Ok "产物：$out"
Write-Host ("      version=$wrapperVersion  size=$($outItem.Length)  sha256=$hash")
Write-Host ("      depends.minecraft = " + (($mcUnion -join ' , ')))
Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
