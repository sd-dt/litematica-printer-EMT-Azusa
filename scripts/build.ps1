<#
  litematica-printer-EMT 构建脚本（两条构建线通用，javac 直接对标，不用 Gradle / Loom）

  用法（2026-09-25 起只构筑 26.2 线；-Line 省略即 26.2）：
    powershell -ExecutionPolicy Bypass -File scripts/build.ps1                    # = -Line 26.2
    powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -NoPackage         # 只编译不打包
    powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -OutJar $env:TEMP\rebuilt.jar   # 校验重建用
    powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -Line 1.21.11      # 1.21.11 线已冻结，仅按需显式构筑

  参数：
    -Line       构建线：26.2（默认，Mojang 官方名 / JDK 25 / class 版本 69）
                        或 1.21.11（已冻结：intermediary 名 / JDK 21 / class 版本 65）
    -Name       产物文件名（默认按下面的命名规范自动生成）
    -NoPackage  只编译，不生成 jar
    -Jdk        javac 所在 JDK 主目录；省略时按 scripts\jdk-<主版本>.path 自动探测
    -OutJar     指定 jar 输出路径（不写进 dist，供 verify-rebuild.ps1 使用）

  命名规范（2026-09-25 起统一，用户要求）：
    litematica-printer-EMT-1.4-dt<YYMMDD><a|b|c…>[-26.2].jar
      · dt 后面是当日日期（YYMMDD），字母是「当日第几次构筑」：第 1 次 a、第 2 次 b ……
      · 字母按**每条构建线各自**计数（同一天里 1.21.11 与 26.2 各自从 a 开始，方便两条线配对）
      · 26.2 线在最后追加 -26.2（1.21.11 线没有后缀）
      · 例：1.21.11 线当天第 2 次构筑 → litematica-printer-EMT-1.4-dt260925b.jar
            26.2   线当天第 3 次构筑 → litematica-printer-EMT-1.4-dt260925c-26.2.jar
      · 历史产物（dt260919a、dt260919a+26.2、…-fixed+26.2 等）保持原名不动，只规范以后的构筑

  产物：versions\<Line>\dist\<Name>

  源码 / 资源布局（两条线一致）：
    versions\<Line>\src\        *.java（1.21.11 是 intermediary 名，26.2 是官方名）
    versions\<Line>\resources\  assets、fabric.mod.json、*.mixins.json、META-INF\jars\pinyin4j-2.5.1.jar

  编码注意：本文件必须以 UTF-8 **带 BOM** 保存，Windows PowerShell 5.1 才能正确读中文；
            改成无 BOM 会导致中文变乱码（scripts\fix-encoding.ps1 可一键修复）。
#>
param(
    # 2026-09-25 用户要求：以后只构筑 26.2 线 → -Line 默认就是 26.2；
    # 1.21.11 线已冻结（源码/产物保留，仍可显式 -Line 1.21.11 构筑，但默认不再产出）
    # emt-260925：2026-10-01 新增 —— 以官方 litematica-printer-EMT+260925（26.2 内层）为基线的新线，
    #             把用户此前 25 轮改动逐项搬过去；依赖/JDK/命名规则与 26.2 线完全相同。
    [ValidateSet('1.21.11', '26.2', 'emt-260925')][string]$Line = '26.2',
    [string]$Name,
    [switch]$NoPackage,
    [string]$Jdk,
    [string]$OutJar
)

$ErrorActionPreference = 'Stop'
$Root     = Split-Path -Parent $PSScriptRoot
$LineDir  = Join-Path $Root "versions\$Line"
# emt-260925 与 26.2 共用同一套编译期依赖（同一个 MC 版本）
$DepsLine = if ($Line -eq 'emt-260925') { '26.2' } else { $Line }
$DepsDir  = Join-Path $Root "deps\mc-$DepsLine"
$SrcDir   = Join-Path $LineDir 'src'
$ResDir   = Join-Path $LineDir 'resources'
$BuildDir = Join-Path $LineDir 'build'
$Classes  = Join-Path $BuildDir 'classes'
$Stage    = Join-Path $BuildDir 'stage'
$DistDir  = Join-Path $LineDir 'dist'

function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red; exit 1 }
function Step($msg) { Write-Host "[ .. ] $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "[ OK ] $msg" -ForegroundColor Green }

foreach ($d in @($LineDir, $DepsDir, $SrcDir, $ResDir)) {
    if (-not (Test-Path -LiteralPath $d)) { Fail "找不到目录：$d" }
}

# ---------- 0. 构建线参数 ----------
if ($Line -eq '1.21.11') { $JdkMajor = 21; $ReleaseArgs = @() }   # JDK 21 默认 target 就是 21
else                     { $JdkMajor = 25; $ReleaseArgs = @('--release', '25') }

# ---------- 1. 定位 JDK ----------
$candidates = @()
if ($Jdk) { $candidates += $Jdk }
$jdkPathFile = Join-Path $PSScriptRoot "jdk-$JdkMajor.path"
if (Test-Path -LiteralPath $jdkPathFile) { $candidates += (Get-Content -LiteralPath $jdkPathFile -Raw).Trim() }
$candidates += @(
    "D:\1sd_dt\mc\-shot 2.6.8\PCL\zulu$JdkMajor.*-ca-jdk$JdkMajor*-win_x64\zulu$JdkMajor.*"
    "D:\1sd_dt\mc\-shot 2.6.8\PCL\zulu$JdkMajor*-win_x64"
    "C:\Program Files\BellSoft\LibericaJDK-$JdkMajor"
    "C:\Program Files\Zulu\zulu-$JdkMajor"
    "C:\Program Files\Eclipse Adoptium\jdk-$JdkMajor*"
    "$env:JAVA_HOME"
)
$JdkHome = $null
foreach ($c in $candidates) {
    if (-not $c) { continue }
    foreach ($p in (Resolve-Path -Path $c -ErrorAction SilentlyContinue)) {
        $javacExe = Join-Path $p.Path 'bin\javac.exe'
        if (Test-Path -LiteralPath $javacExe) {
            $verOut = & $javacExe -version 2>&1 | Out-String
            if ($verOut -match 'javac\s+(\d+)') {
                if ([int]$Matches[1] -eq $JdkMajor) { $JdkHome = $p.Path; break }
            }
        }
    }
    if ($JdkHome) { break }
}
if (-not $JdkHome) { Fail "找不到 JDK $JdkMajor。请用 -Jdk <JDK主目录> 指定，或把路径写进 scripts\jdk-$JdkMajor.path" }

$JavacExe = Join-Path $JdkHome 'bin\javac.exe'
$JarExe   = Join-Path $JdkHome 'bin\jar.exe'
Ok "构建线 $Line ：JDK $JdkMajor @ $JdkHome"

# ---------- 2. 生成 sources.txt（UTF-8 无 BOM） ----------
$srcFiles = @(Get-ChildItem -LiteralPath $SrcDir -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { $_.FullName.Replace('\', '/') })
if ($srcFiles.Count -eq 0) { Fail "源码目录里没有 .java：$SrcDir" }
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
$sourcesTxt = Join-Path $BuildDir 'sources.txt'
[System.IO.File]::WriteAllLines($sourcesTxt, [string[]]$srcFiles, (New-Object System.Text.UTF8Encoding($false)))
Ok "sources.txt：$($srcFiles.Count) 个源文件"

# ---------- 3. 组装 classpath（避开 Windows 命令行长度限制，写进 argfile） ----------
$cpJars = @()
$cpJars += Get-ChildItem -LiteralPath $DepsDir -Filter '*.jar' -File -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }
foreach ($sub in 'mods', 'fapi', 'libs') {
    $d = Join-Path $DepsDir $sub
    if (Test-Path -LiteralPath $d) { $cpJars += Get-ChildItem -LiteralPath $d -Filter '*.jar' -File | ForEach-Object { $_.FullName } }
}
if ($cpJars.Count -eq 0) { Fail "依赖目录里没有 jar：$DepsDir" }
$cp = ($cpJars | ForEach-Object { $_.Replace('\', '/') }) -join ';'

$argsFile = Join-Path $BuildDir 'javac-args.txt'
# 注意 1：javac 的 -J 选项不能写在 argfile 里，只能放命令行（见下方调用）
# 注意 2：javac 不支持 argfile 嵌套，所以源文件列表直接写进本文件
$argLines = @(
    '-nowarn'
    '-proc:none'
    '-encoding', 'UTF-8'
) + $ReleaseArgs + @(
    '-d', $Classes.Replace('\', '/')
    '-cp', ('"' + $cp + '"')
) + $srcFiles
[System.IO.File]::WriteAllLines($argsFile, [string[]]$argLines, (New-Object System.Text.UTF8Encoding($false)))
Ok "classpath：$($cpJars.Count) 个 jar（argfile: build\javac-args.txt）"

# ---------- 4. 编译 ----------
if (Test-Path -LiteralPath $Classes) { Remove-Item -LiteralPath $Classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
Step "javac ..."
$javacLog = Join-Path $BuildDir 'javac.log'
# 注意：javac 会往 stderr 写 "Note: ...deprecated API" 之类的提示，PowerShell 5.1 在
# $ErrorActionPreference='Stop' 下会把它当成致命错误（NativeCommandError）→ 临时放宽到 Continue
$eap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$out  = & $JavacExe '-J-Duser.language=en' '-J-Duser.country=US' "@$argsFile" 2>&1
$exit = $LASTEXITCODE
$ErrorActionPreference = $eap
$out | Set-Content -LiteralPath $javacLog -Encoding UTF8
if ($exit -ne 0) {
    $out | Select-Object -First 40 | ForEach-Object { Write-Host $_ }
    Fail "javac exit=$exit（完整日志：$javacLog）"
}
$classFiles = @(Get-ChildItem -LiteralPath $Classes -Recurse -Filter '*.class')
if ($classFiles.Count -eq 0) { Fail "javac exit=0 但没有产出 class" }
Ok "javac exit=0，$($classFiles.Count) 个 class"

# ---------- 5. 打包 ----------
if ($NoPackage) { Ok "（-NoPackage）跳过打包"; exit 0 }

if (-not $OutJar) {
    if (-not $Name) {
        $d = Get-Date
        $stamp  = 'dt' + $d.ToString('yyMMdd')
        $prefix = "litematica-printer-EMT-1.4-$stamp"
        # emt-260925 线是用户的改版分支（litematica-printer-EMT-Azusa），文件名统一带这个品牌；
        # 字母仍按「当天第几次构筑」递增：老前缀与新前缀的产物一起数，避免重名。
        $prefixAlt = "litematica-printer-EMT-1.4-$stamp"
        if ($Line -eq 'emt-260925') { $prefix = "litematica-printer-EMT-Azusa-$stamp" }
        $used = @()
        $used += @(Get-ChildItem -LiteralPath $DistDir -Filter "$prefix*.jar" -File -ErrorAction SilentlyContinue |
            ForEach-Object { $_.BaseName.Substring($prefix.Length, 1) })
        if ($prefixAlt -ne $prefix) {
            $used += @(Get-ChildItem -LiteralPath $DistDir -Filter "$prefixAlt*.jar" -File -ErrorAction SilentlyContinue |
                ForEach-Object { $_.BaseName.Substring($prefixAlt.Length, 1) })
        }
        $used = @($used | Where-Object { $_ -match '^[a-z]$' } | Sort-Object)
        $letters = 'abcdefghijklmnopqrstuvwxyz'.ToCharArray()
        if ($used.Count -eq 0) { $next = 'a' }
        else {
            $last = [string]$used[-1]
            $idx  = [array]::IndexOf($letters, [char]$last)
            $next = if ($idx -ge 0 -and $idx + 1 -lt $letters.Length) { [string]$letters[$idx + 1] } else { 'z' }
        }
        $Name = "$prefix$next"
        # 26.2 线追加 -26.2 后缀（2026-09-25 起的统一命名规范；此前是 +26.2）
        if ($Line -ne '1.21.11') { $Name = "$Name-26.2" }
    }
    $Name = [System.IO.Path]::GetFileName($Name)
    if ($Name -notlike '*.jar') { $Name = "$Name.jar" }
    New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
    $jarPath = Join-Path $DistDir $Name
} else {
    $jarPath = $OutJar
    $outDir = Split-Path -Parent $jarPath
    if ($outDir) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
}

if (Test-Path -LiteralPath $Stage) { Remove-Item -LiteralPath $Stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Stage | Out-Null
Copy-Item -Path (Join-Path $ResDir '*') -Destination $Stage -Recurse -Force
Copy-Item -Path (Join-Path $Classes '*') -Destination $Stage -Recurse -Force
if (Test-Path -LiteralPath $jarPath) { Remove-Item -LiteralPath $jarPath -Force }
# 只用 jar.exe --create（.NET ZipFile 写出的 zip 有时 Fabric 读不到条目）
& $JarExe --create --file $jarPath -C $Stage .
if ($LASTEXITCODE -ne 0) { Fail "jar 打包失败 exit=$LASTEXITCODE" }

# ---------- 6. 校验 ----------
$entries = & $JarExe --list --file $jarPath
$need = @('fabric.mod.json', 'litematica-printer.mixins.json', 'litematica-printer.minihud.mixins.json',
          'me/aleksilassila/litematica/printer/LitematicaPrinterMod.class')
if ($Line -ne '1.21.11') { $need += 'litematica-printer.tweakeroo.mixins.json' }
$missing = @($need | Where-Object { $entries -notcontains $_ })
if ($missing.Count -gt 0) { Fail "jar 缺少条目：$($missing -join ', ')" }

$jarClasses = @($entries | Where-Object { $_ -like '*.class' }).Count
if ($jarClasses -lt 290) { Fail "jar 里只有 $jarClasses 个 class（门禁 290），疑似打包不完整" }
$size = (Get-Item -LiteralPath $jarPath).Length
$sha  = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash

Ok "产物：$jarPath"
Write-Host "       class=$jarClasses  size=$size  sha256=$sha" -ForegroundColor Green

# 构建记录（追加，便于回看每次构建）
$logFile = Join-Path $BuildDir 'build-log.txt'
# 变量名不能叫 $line —— PowerShell 变量不区分大小写，会撞上 -Line 参数
$logLine = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') line=$Line jar=$jarPath class=$jarClasses size=$size sha256=$sha"
[System.IO.File]::AppendAllLines($logFile, [string[]]@($logLine), (New-Object System.Text.UTF8Encoding($false)))
