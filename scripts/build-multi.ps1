<#
  build-multi.ps1 — 把两条构建线的产物打包成一个「双版本兼容」jar

  结构（沿用 litematica-printer-EMT 官方的做法，已在原版 260918 包上验证过）：
    fabric.mod.json                       id=litematica-printer-wrapper，depends.minecraft 同时列出两个版本
    META-INF/jars/<1.21.11 内层>.jar      内层 fabric.mod.json depends.minecraft = "1.21.11"
    META-INF/jars/<26.2 内层>.jar         内层 fabric.mod.json depends.minecraft = ">=26.2"
  Fabric Loader 会把两个内层都当作候选，按各自 depends 选当前游戏版本能用的那个加载。

  用法：
    powershell -ExecutionPolicy Bypass -File scripts\build-multi.ps1
    powershell -ExecutionPolicy Bypass -File scripts\build-multi.ps1 -Jar26 <jar> -Jar121 <jar> -Name <文件名>

  产物：artifacts\litematica-printer-EMT-Azusa-multi-dt<YYMMDD><a|b|...>.jar
#>
param(
    [string]$Jar26,
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

if (-not $Jar26)  { $Jar26  = Get-LatestJar (Join-Path $WS 'versions\emt-260925\dist') }
if (-not $Jar121) { $Jar121 = Get-LatestJar (Join-Path $WS 'versions\1.21.11\dist') }
if (-not (Test-Path -LiteralPath $Jar26))  { Fail "找不到 26.2 jar：$Jar26" }
if (-not (Test-Path -LiteralPath $Jar121)) { Fail "找不到 1.21.11 jar：$Jar121" }
$Jar26  = (Resolve-Path -LiteralPath $Jar26).Path
$Jar121 = (Resolve-Path -LiteralPath $Jar121).Path
Info "26.2   内层：$Jar26"
Info "1.21.11 内层：$Jar121"

# 读内层 fabric.mod.json（用 jar 解到临时目录再读，避免依赖 unzip）
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

$mod26  = Read-ModJson $Jar26
$mod121 = Read-ModJson $Jar121
if ($mod26.id -ne 'litematica-printer' -or $mod121.id -ne 'litematica-printer') {
    Fail "内层 mod id 不是 litematica-printer（$($mod26.id) / $($mod121.id)）"
}
Ok "内层校验通过：$($mod26.version) / $($mod121.version)"

# 产物名：默认按当天日期 + 字母顺延（只统计 multi-dt 前缀）
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

# 组装 stage
$stage = Join-Path $env:TEMP ('lp-multi-stage-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'META-INF\jars') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'assets\litematica-printer-wrapper') | Out-Null

Copy-Item -LiteralPath $Jar121 -Destination (Join-Path $stage ('META-INF\jars\1.21.11-' + (Split-Path -Leaf $Jar121))) -Force
Copy-Item -LiteralPath $Jar26  -Destination (Join-Path $stage ('META-INF\jars\26.2-'    + (Split-Path -Leaf $Jar26)))  -Force

# 图标（从 26.2 内层里取）
Push-Location $stage
try { & $JarExe xf $Jar26 'assets/litematica-printer/icon.png' 2>$null | Out-Null } finally { Pop-Location }
$iconSrc = Join-Path $stage 'assets\litematica-printer\icon.png'
if (Test-Path -LiteralPath $iconSrc) {
    Copy-Item -LiteralPath $iconSrc -Destination (Join-Path $stage 'assets\litematica-printer-wrapper\icon.png') -Force
    Remove-Item -LiteralPath (Join-Path $stage 'assets\litematica-printer') -Recurse -Force
}

# 外层 fabric.mod.json
$wrapperVersion = "Azusa-multi-" + (($mod26.version -replace '^Azusa-','') -replace '-26\.2$','')
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
  "name": "litematica-printer-EMT-Azusa (1.21.11 + 26.2)",
  "authors": [
    "sd_dt",
    "MoMortis",
    "deepseekfl4.1"
  ],
  "license": "AGPL-3.0",
  "environment": "*",
  "depends": {
    "minecraft": [
      "1.21.11",
      ">=26.2"
    ]
  },
  "jars": [
    {
      "file": "META-INF/jars/1.21.11-$(Split-Path -Leaf $Jar121)"
    },
    {
      "file": "META-INF/jars/26.2-$(Split-Path -Leaf $Jar26)"
    }
  ]
}
"@
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $stage 'fabric.mod.json'), $json, $utf8NoBom)

# 打包
if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out -Force }
Push-Location $stage
try {
    & $JarExe --create --file $out -C $stage .
    if ($LASTEXITCODE -ne 0) { Fail "jar 打包失败 exit=$LASTEXITCODE" }
} finally { Pop-Location }

# 校验
$entries = & $JarExe tf $out
$need = @('fabric.mod.json', "META-INF/jars/1.21.11-$(Split-Path -Leaf $Jar121)", "META-INF/jars/26.2-$(Split-Path -Leaf $Jar26)")
$missing = @()
foreach ($n in $need) { if (-not ($entries -contains $n)) { $missing += $n } }
if ($missing.Count -gt 0) { Fail "jar 缺少条目：$($missing -join ', ')" }

$outItem = Get-Item -LiteralPath $out
$hash = (Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash
Ok "产物：$out"
Write-Host ("      version=$wrapperVersion  size=$($outItem.Length)  sha256=$hash")
Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
