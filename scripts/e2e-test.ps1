<#
.SYNOPSIS
    Сквозной (end-to-end) автотест игры "Я знаю" на реальных Android-устройствах через adb.

.DESCRIPTION
    Прогоняет весь игровой цикл без единого ручного тапа:
      1. Установка и запуск приложения на всех подключённых устройствах.
      2. Первое устройство становится ведущим, остальные — игроками (автопоиск,
         с падением на ручной ввод IP, если автопоиск не сработал).
      3. Старт игры, открытие раунда, BUZZ от игрока, оценка ответа хостом,
         затем обычная игра на 10 раундов подряд (чередование игрока и
         верно/неверно — проверяет устойчивость при длительной игре).
      4. Хост выходит — проверяется, что все игроки автоматически возвращаются
         на экран поиска (NetworkEvent.HostConnectionLost).
      5. Повторный цикл с одним игроком, старт игры, и попытка подключения
         ВТОРЫМ игроком посреди уже идущей игры — должна быть отклонена хостом.
      6. Сервер и короткая игра (2 раунда) поочерёдно на КАЖДОМ ДРУГОМ пригодном
         для хоста устройстве — чтобы баг конкретного телефона-хоста не остался
         незамеченным, если основной прогон всегда хостит одно и то же устройство.

    Устройства ищутся по тексту элементов через `uiautomator dump` (устойчиво
    к сдвигам вёрстки), а каждый шаг подтверждается строкой в logcat (тег
    YaZnayuNetwork), а не только скриншотом — так тест не зависит от таймингов
    рендеринга экрана.

.PARAMETER AdbPath
    Путь к adb.exe. По умолчанию — стандартный путь Android SDK на этой машине.

.PARAMETER ApkPath
    Путь к debug APK. По умолчанию — стандартный выходной путь Gradle.

.PARAMETER Build
    Если указан — перед тестом собирает debug APK через ./gradlew assembleDebug.

.EXAMPLE
    .\scripts\e2e-test.ps1 -Build
#>

param(
    [string]$AdbPath = "C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$ApkPath = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk",
    [switch]$Build
)

$ErrorActionPreference = "Continue"
# Не "Stop": adb-команды регулярно возвращают ненулевой код и пишут в stderr
# как часть нормального опроса устройства (например, ip addr show ap0 на
# устройстве без такого интерфейса) — это не исключительная ситуация, а
# ожидаемый результат, который код ниже проверяет через null/пустые строки.
$PackageName = "ru.fixbyte.quizand"
$Activity = "$PackageName/.MainActivity"
$LogTag = "YaZnayuNetwork"
$Port = 5000

$scratchDir = Join-Path $env:TEMP "quizand-e2e"
New-Item -ItemType Directory -Force -Path $scratchDir | Out-Null

# ——— Результаты теста: копим сюда, печатаем сводку в конце ———
$script:results = New-Object System.Collections.Generic.List[object]

function Record-Result([string]$Name, [bool]$Passed, [string]$Detail = "") {
    $script:results.Add([pscustomobject]@{ Name = $Name; Passed = $Passed; Detail = $Detail })
    $mark = if ($Passed) { "OK  " } else { "FAIL" }
    $color = if ($Passed) { "Green" } else { "Red" }
    Write-Host "[$mark] $Name" -ForegroundColor $color -NoNewline
    if ($Detail) { Write-Host "  — $Detail" -ForegroundColor DarkGray } else { Write-Host "" }
}

function Adb([string]$Serial, [string[]]$CmdArgs) {
    & $AdbPath -s $Serial @CmdArgs 2>$null
}

# ——— UI: поиск элементов по тексту через uiautomator dump, без захардкоженных координат ———

function Get-UiXml([string]$Serial) {
    $remote = "/sdcard/e2e_dump.xml"
    $local = Join-Path $scratchDir "$Serial.xml"
    # uiautomator иногда транзиентно падает с "null root node returned by
    # UiTestAutomationBridge" сразу после смены экрана — не настоящая ошибка приложения.
    # Удаляем старый локальный файл заранее, иначе неудачный pull молча вернёт устаревший дамп.
    Remove-Item -Path $local -Force -ErrorAction SilentlyContinue
    Adb $Serial @("shell", "uiautomator", "dump", $remote) | Out-Null
    Adb $Serial @("pull", $remote, $local) | Out-Null
    if (-not (Test-Path $local)) { return $null }
    try {
        [xml](Get-Content -Path $local -Raw -Encoding UTF8)
    } catch {
        $null
    }
}

function Find-NodeCenter($XmlDoc, [string]$Text, [switch]$Contains) {
    if (-not $XmlDoc) { return $null }
    $nodes = $XmlDoc.SelectNodes("//node[@text]")
    foreach ($node in $nodes) {
        $t = $node.GetAttribute("text")
        $match = if ($Contains) { $t -like "*$Text*" } else { $t -eq $Text }
        if ($match) {
            if ($node.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                $x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]
                $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]
                return @{ X = [int](($x1 + $x2) / 2); Y = [int](($y1 + $y2) / 2) }
            }
        }
    }
    return $null
}

function Get-PlayerScore($XmlDoc, [string]$Nickname) {
    <# Таблица очков хоста рисует строку как соседние TextView-узлы:
       [иконка][ник][число] — ищем ник, затем первое числовое значение сразу после него. #>
    if (-not $XmlDoc) { return $null }
    $nodes = @($XmlDoc.SelectNodes("//node[@text]"))
    for ($i = 0; $i -lt $nodes.Count; $i++) {
        if ($nodes[$i].GetAttribute("text") -eq $Nickname) {
            for ($j = $i + 1; $j -lt [Math]::Min($i + 4, $nodes.Count); $j++) {
                $t = $nodes[$j].GetAttribute("text")
                if ($t -match '^\d+$') { return [int]$t }
            }
        }
    }
    return $null
}

function Wait-Element([string]$Serial, [string]$Text, [int]$TimeoutSec = 15, [switch]$Contains) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $xml = Get-UiXml $Serial
        $center = Find-NodeCenter $xml $Text -Contains:$Contains
        if ($center) { return $center }
        Start-Sleep -Milliseconds 150
    }
    return $null
}

function Tap-Element([string]$Serial, [string]$Text, [int]$TimeoutSec = 15, [switch]$Contains) {
    $center = Wait-Element $Serial $Text $TimeoutSec -Contains:$Contains
    if (-not $center) {
        Write-Host "    ! элемент '$Text' не найден на $Serial за ${TimeoutSec}с" -ForegroundColor Yellow
        return $false
    }
    Adb $Serial @("shell", "input", "tap", $center.X, $center.Y) | Out-Null
    Start-Sleep -Milliseconds 125
    return $true
}

function Clear-AndTypeField([string]$Serial, [string]$LabelText, [string]$NewText, [int]$TimeoutSec = 15, [switch]$Contains) {
    if (-not (Tap-Element $Serial $LabelText $TimeoutSec -Contains:$Contains)) { return $false }
    # 60 нажатий Delete одним shell-вызовом (input keyevent принимает несколько кодов
    # за раз) вместо 60 отдельных adb round-trip'ов — самая тяжёлая часть на скорость.
    $delKeys = @("keyevent") + (@("KEYCODE_DEL") * 60)
    Adb $Serial (@("shell", "input") + $delKeys) | Out-Null
    Adb $Serial @("shell", "input", "text", $NewText) | Out-Null
    Adb $Serial @("shell", "input", "keyevent", "KEYCODE_BACK") | Out-Null
    Start-Sleep -Milliseconds 125
    return $true
}

# ——— Подтверждение шагов по logcat, а не по скриншоту/таймауту ———

function Clear-Log([string]$Serial) {
    Adb $Serial @("logcat", "-c") | Out-Null
}

function Wait-LogLine([string]$Serial, [string]$Pattern, [int]$TimeoutSec = 15) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        # -t 300: только последние строки буфера, а не весь растущий за время
        # прогона лог — на поздних шагах полный дамп заметно медленнее.
        $lines = & $AdbPath -s $Serial logcat -d -t 300 -s $LogTag 2>$null
        $hit = $lines | Where-Object { $_ -match $Pattern } | Select-Object -Last 1
        if ($hit) { return $hit }
        Start-Sleep -Milliseconds 150
    }
    return $null
}

function Get-DeviceWifiIp([string]$Serial) {
    $out = Adb $Serial @("shell", "ip", "-f", "inet", "addr", "show", "wlan0")
    foreach ($line in $out) {
        if ($line -match 'inet (\d+\.\d+\.\d+\.\d+)/') { return $Matches[1] }
    }
    # Хотспот: собственный адрес может быть на ap0, а не wlan0.
    $out = Adb $Serial @("shell", "ip", "-f", "inet", "addr", "show", "ap0")
    foreach ($line in $out) {
        if ($line -match 'inet (\d+\.\d+\.\d+\.\d+)/') { return $Matches[1] }
    }
    return $null
}

function Test-AppInForeground([string]$Serial) {
    $focus = Adb $Serial @("shell", "dumpsys", "window") | Select-String "mCurrentFocus"
    return ($focus -join "") -match [regex]::Escape($PackageName)
}

function Restart-App([string]$Serial) {
    Adb $Serial @("shell", "am", "force-stop", $PackageName) | Out-Null
    Adb $Serial @("shell", "input", "keyevent", "KEYCODE_WAKEUP") | Out-Null

    for ($attempt = 1; $attempt -le 4; $attempt++) {
        Adb $Serial @("shell", "am", "start", "-n", $Activity) | Out-Null
        $deadline = (Get-Date).AddSeconds(2)
        while ((Get-Date) -lt $deadline) {
            if (Test-AppInForeground $Serial) { return $true }
            Start-Sleep -Milliseconds 150
        }
        Write-Host "    ! приложение не вышло на передний план на $Serial (попытка $attempt) - повтор" -ForegroundColor Yellow
        Adb $Serial @("shell", "input", "keyevent", "KEYCODE_WAKEUP") | Out-Null
    }
    Write-Host "    ! приложение так и не вышло на передний план на $Serial" -ForegroundColor Red
    return $false
}

function Join-AsPlayer([string]$Serial, [string]$Nickname, [string]$HostIp) {
    <# Возвращает $true, если игрок дошёл до экрана ожидания (нажал "Подключиться"). #>
    if (-not (Tap-Element $Serial "Я игрок" 10)) { return $false }
    if (-not (Clear-AndTypeField $Serial "Ваш ник" $Nickname)) { return $false }

    $server = Wait-Element $Serial "Ведущий" 5 -Contains
    if ($server) {
        Adb $Serial @("shell", "input", "tap", $server.X, $server.Y) | Out-Null
        Start-Sleep -Milliseconds 200
        return Tap-Element $Serial "Подключиться" 8
    }

    Write-Host "    автопоиск не нашёл хост на $Serial за 5с — пробую вручную по IP" -ForegroundColor DarkYellow
    if (-not (Clear-AndTypeField $Serial "IP:порт" "${HostIp}:${Port}" 15 -Contains)) { return $false }
    return Tap-Element $Serial "Подключиться по IP" 8
}

function Invoke-QuickGame([string]$GameHostSerial, [string[]]$GamePlayerSerials, [int]$Rounds, [string]$Label) {
    <# Короткая, самодостаточная игра: старт сервера -> подключение игроков ->
       старт игры -> $Rounds раундов (чередование игрока и верно/неверно) ->
       выход хоста. Возвращает $true, если все шаги прошли без сбоев. #>
    $ok = $true

    Clear-Log $GameHostSerial
    if (-not (Tap-Element $GameHostSerial "Я ведущий" 10)) { $ok = $false }
    if (-not (Tap-Element $GameHostSerial "Запустить сервер" 10)) { $ok = $false }
    if (-not (Wait-LogLine $GameHostSerial "ServerSocket поднят" 10)) { $ok = $false }
    $gameHostIp = Get-DeviceWifiIp $GameHostSerial
    if (-not $gameHostIp) { $ok = $false }

    $gameNicknames = @{}
    $gi = 0
    foreach ($p in $GamePlayerSerials) {
        $gi++
        $nick = "R$gi-$(Get-Random -Maximum 999)"
        $gameNicknames[$p] = $nick
        Clear-Log $GameHostSerial
        if (-not (Join-AsPlayer $p $nick $gameHostIp)) { $ok = $false }
        if (-not (Wait-LogLine $GameHostSerial "kind=hello sender=$([regex]::Escape($nick))" 10)) { $ok = $false }
    }

    if (-not $ok) {
        Tap-Element $GameHostSerial "Выход" 10 | Out-Null
        Record-Result "$Label`: игра ($($GamePlayerSerials.Count) игр., $Rounds раунд(ов)) прошла без сбоев" $false
        return $false
    }

    # Чистим логи ДО тапа — см. комментарий у основного шага 4 про race с
    # почти мгновенной доставкой gameStarted.
    foreach ($p in $GamePlayerSerials) { Clear-Log $p }
    if (-not (Tap-Element $GameHostSerial "Начать игру" 10)) { $ok = $false }
    foreach ($p in $GamePlayerSerials) {
        if (-not (Wait-LogLine $p "kind=gameStarted" 10)) { $ok = $false }
    }
    if (-not $ok) {
        Write-Host "    ! $Label : игра не стартовала — раунды пропущены" -ForegroundColor Red
        Tap-Element $GameHostSerial "Выход" 10 | Out-Null
        Record-Result "$Label`: игра ($($GamePlayerSerials.Count) игр., $Rounds раунд(ов)) прошла без сбоев" $false
        return $false
    }

    $consecutiveOpenFail = 0
    for ($r = 1; $r -le $Rounds; $r++) {
        foreach ($p in $GamePlayerSerials) { Clear-Log $p }
        $roundOpenOk = Tap-Element $GameHostSerial "Открыть раунд" 10
        if (-not $roundOpenOk) {
            $ok = $false
            $consecutiveOpenFail++
            if ($consecutiveOpenFail -ge 2) {
                Write-Host "    ! $Label : два раунда подряд не открылись — прерываю" -ForegroundColor Red
                break
            }
            continue
        }
        $consecutiveOpenFail = 0
        foreach ($p in $GamePlayerSerials) {
            if (-not (Wait-LogLine $p "kind=roundOpened" 10)) { $ok = $false }
        }

        $roundBuzzer = $GamePlayerSerials[($r - 1) % $GamePlayerSerials.Count]
        Clear-Log $GameHostSerial
        if (-not (Tap-Element $roundBuzzer "Ответить" 10)) { $ok = $false }
        if (-not (Wait-LogLine $GameHostSerial "kind=buzz" 10)) { $ok = $false }

        $isCorrect = ($r % 2 -eq 1)
        Clear-Log $roundBuzzer
        $judgeText = if ($isCorrect) { "Верно" } else { "Неверно" }
        if (-not (Tap-Element $GameHostSerial $judgeText 10 -Contains)) { $ok = $false }
        if (-not (Wait-LogLine $roundBuzzer "kind=answerResult" 10)) { $ok = $false }

        # см. Шаг 5.5 выше: неверный ответ не закрывает раунд сам — нужен явный тап.
        if (-not $isCorrect) {
            if (-not (Tap-Element $GameHostSerial "Закрыть раунд" 10)) { $ok = $false }
        }
    }

    Tap-Element $GameHostSerial "Выход" 10 | Out-Null
    Record-Result "$Label`: игра ($($GamePlayerSerials.Count) игр., $Rounds раунд(ов)) прошла без сбоев" $ok
    return $ok
}

# ——— Устройства ———

Write-Host "=== Поиск устройств ===" -ForegroundColor Cyan
$deviceLines = & $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "\bdevice$" }
$devices = $deviceLines | ForEach-Object { ($_ -split "\s+")[0] }
if ($devices.Count -lt 2) {
    Write-Host "Нужно минимум 2 подключённых устройства (найдено: $($devices.Count))." -ForegroundColor Red
    exit 1
}

# Устройство, раздающее собственный хотспот (интерфейс ap0 поднят), не может быть
# хостом игры — оно не принимает входящие TCP от своих же станций (NoRouteToHostException).
# Сервер должен работать на станции сети, а не на владельце точки доступа.
function Test-IsHotspotOwner([string]$Serial) {
    $out = Adb $Serial @("shell", "ip", "-f", "inet", "addr", "show", "ap0")
    return ($out -join "") -match 'inet \d+\.\d+\.\d+\.\d+/'
}

$hotspotOwners = @($devices | Where-Object { Test-IsHotspotOwner $_ })
$hostCandidates = @($devices | Where-Object { $_ -notin $hotspotOwners })
if ($hostCandidates.Count -eq 0) {
    Write-Host "Все устройства раздают собственный хотспот — нет станции для роли хоста." -ForegroundColor Red
    exit 1
}
$hostSerial = $hostCandidates[0]
$playerSerials = @($devices | Where-Object { $_ -ne $hostSerial })
if ($hotspotOwners -contains $hostSerial) {
    Write-Host "! $hostSerial раздаёт хотспот и выбран хостом — переподключение может не сработать." -ForegroundColor Yellow
}
Write-Host "Хост:   $hostSerial"
Write-Host "Игроки: $($playerSerials -join ', ')"
if ($hotspotOwners.Count -gt 0) {
    $ownersList = $hotspotOwners -join ', '
    Write-Host "(владелец хотспота: $ownersList - будет только игроком)" -ForegroundColor DarkGray
}
Write-Host ""

if ($Build) {
    Write-Host "=== Сборка APK ===" -ForegroundColor Cyan
    Push-Location "$PSScriptRoot\.."
    & .\gradlew.bat assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Host "Сборка не удалась" -ForegroundColor Red; exit 1 }
    Pop-Location
}

if (Test-Path $ApkPath) {
    Write-Host "=== Установка APK на все устройства (параллельно) ===" -ForegroundColor Cyan
    # Установка на разные устройства независима — запускаем как фоновые задания
    # вместо последовательного foreach, чтобы не ждать каждое устройство по очереди.
    $installJobs = $devices | ForEach-Object {
        Start-Job -ScriptBlock {
            param($adbPath, $serial, $apk)
            & $adbPath -s $serial install -r $apk 2>&1 | Out-Null
        } -ArgumentList $AdbPath, $_, $ApkPath
    }
    $installJobs | Wait-Job | Out-Null
    $installJobs | Remove-Job
    foreach ($d in $devices) { Write-Host "  -> $d" }
} else {
    Write-Host "APK не найден по пути $ApkPath — использую уже установленную версию." -ForegroundColor Yellow
}
Write-Host ""

# ——— Шаг 1: запуск всех приложений (параллельно) ———

Write-Host "=== Запуск приложения на всех устройствах (параллельно) ===" -ForegroundColor Cyan
$restartJobs = $devices | ForEach-Object {
    Start-Job -ScriptBlock {
        param($adbPath, $serial, $pkg, $activity)
        function AdbJ($cmdArgs) { & $adbPath -s $serial @cmdArgs 2>$null }
        function ForegroundJ() {
            $focus = AdbJ @("shell", "dumpsys", "window") | Select-String "mCurrentFocus"
            return ($focus -join "") -match [regex]::Escape($pkg)
        }
        AdbJ @("shell", "am", "force-stop", $pkg) | Out-Null
        AdbJ @("shell", "input", "keyevent", "KEYCODE_WAKEUP") | Out-Null
        for ($attempt = 1; $attempt -le 4; $attempt++) {
            AdbJ @("shell", "am", "start", "-n", $activity) | Out-Null
            $deadline = (Get-Date).AddSeconds(2)
            while ((Get-Date) -lt $deadline) {
                if (ForegroundJ) { return $true }
                Start-Sleep -Milliseconds 150
            }
            AdbJ @("shell", "input", "keyevent", "KEYCODE_WAKEUP") | Out-Null
        }
        return $false
    } -ArgumentList $AdbPath, $_, $PackageName, $Activity
}
$restartResults = $restartJobs | Wait-Job | Receive-Job
$restartJobs | Remove-Job
for ($i = 0; $i -lt $devices.Count; $i++) {
    if (-not $restartResults[$i]) {
        Write-Host "    ! приложение так и не вышло на передний план на $($devices[$i])" -ForegroundColor Red
    }
}

# ——— Шаг 2: хост поднимает сервер ———

Write-Host "=== Хост: запуск сервера ===" -ForegroundColor Cyan
Clear-Log $hostSerial
$ok = Tap-Element $hostSerial "Я ведущий" 10
Record-Result "Хост: выбор роли 'Я ведущий'" $ok

$ok = $ok -and (Tap-Element $hostSerial "Запустить сервер" 10)
Record-Result "Хост: нажатие 'Запустить сервер'" $ok

$serverUp = Wait-LogLine $hostSerial "ServerSocket поднят" 10
Record-Result "Хост: сервер поднят (лог)" ([bool]$serverUp) $serverUp

$hostIp = Get-DeviceWifiIp $hostSerial
Record-Result "Хост: получен IP-адрес" ([bool]$hostIp) $hostIp

# ——— Шаг 3: все игроки подключаются ———

Write-Host "=== Игроки подключаются ===" -ForegroundColor Cyan
$nicknames = @{}
$i = 0
foreach ($p in $playerSerials) {
    $i++
    $nick = "P$i-$(Get-Random -Maximum 999)"
    $nicknames[$p] = $nick
    Clear-Log $hostSerial
    $joined = Join-AsPlayer $p $nick $hostIp
    Record-Result "Игрок $p ($nick): дошёл до 'Подключиться'" $joined

    $hello = Wait-LogLine $hostSerial "kind=hello sender=$([regex]::Escape($nick))" 10
    Record-Result "Хост увидел HELLO от $nick" ([bool]$hello) $hello
}
Write-Host ""

# ——— Шаг 4: старт игры ———
# Шаги 4-7 обёрнуты в do{}while($false), чтобы одна сорвавшаяся критичная
# проверка (например, транзиентный сбой uiautomator dump ровно в момент
# 'Начать игру') не утопила весь остаток прогона в десятках заведомо
# обречённых таймаутов по 10с каждый — один break здесь экономит минуты.
do {

Write-Host "=== Старт игры ===" -ForegroundColor Cyan
Start-Sleep -Milliseconds 500  # дать списку игроков осесть после последнего HELLO
# ВАЖНО: логи игроков чистим ДО тапа, а не после. gameStarted долетает до
# игроков практически мгновенно (доли секунды) — Clear-Log, вызванный уже
# после тапа, реально успевал стереть только что появившуюся строку раньше,
# чем до неё доходил Wait-LogLine. Это и было настоящей причиной трёх
# подряд провалов этой проверки, а не сеть/тайминг устройства.
foreach ($p in $playerSerials) { Clear-Log $p }
$ok = Tap-Element $hostSerial "Начать игру" 10
if (-not $ok) {
    # Одна транзиентная попытка ещё раз, прежде чем сдаваться и пропускать всё дальнейшее.
    Start-Sleep -Milliseconds 500
    foreach ($p in $playerSerials) { Clear-Log $p }
    $ok = Tap-Element $hostSerial "Начать игру" 10
}
Record-Result "Хост: нажатие 'Начать игру'" $ok
if (-not $ok) { Write-Host "! игра не стартовала — пропускаю игровые шаги 5-7" -ForegroundColor Red; break }

$ok2 = $true
foreach ($p in $playerSerials) {
    $started = Wait-LogLine $p "kind=gameStarted" 10
    $ok2 = $ok2 -and [bool]$started
}
Record-Result "Все игроки получили gameStarted" $ok2
if (-not $ok2) { Write-Host "! gameStarted не подтверждён — пропускаю игровые шаги 5-7" -ForegroundColor Red; break }

# ——— Шаг 5: раунд + BUZZ + оценка ———

Write-Host "=== Раунд, BUZZ, оценка ответа ===" -ForegroundColor Cyan
foreach ($p in $playerSerials) { Clear-Log $p }
$ok = Tap-Element $hostSerial "Открыть раунд" 10
Record-Result "Хост: нажатие 'Открыть раунд'" $ok

$ok2 = $true
foreach ($p in $playerSerials) {
    $opened = Wait-LogLine $p "kind=roundOpened" 10
    $ok2 = $ok2 -and [bool]$opened
}
Record-Result "Все игроки получили roundOpened" $ok2

$buzzer = $playerSerials[0]
$buzzerNick = $nicknames[$buzzer]
Clear-Log $hostSerial
$ok = Tap-Element $buzzer "Ответить" 10
Record-Result "Игрок ${buzzerNick}: нажатие BUZZ" $ok

$buzzSeen = Wait-LogLine $hostSerial "kind=buzz" 10
Record-Result "Хост увидел BUZZ" ([bool]$buzzSeen) $buzzSeen

Clear-Log $buzzer
$ok = Tap-Element $hostSerial "Верно" 10 -Contains
Record-Result "Хост: оценка ответа 'Верно'" $ok

$resultSeen = Wait-LogLine $buzzer "kind=answerResult" 10
Record-Result "Игрок $buzzerNick получил answerResult" ([bool]$resultSeen) $resultSeen

# ——— Шаг 5.5: обычная игра — 10 раундов подряд, с чередованием игроков и
# верных/неверных ответов, чтобы проверить устойчивость при длительной игре ———

Write-Host "=== Обычная игра: 10 раундов подряд ===" -ForegroundColor Cyan
$expectedScores = @{}
foreach ($p in $playerSerials) { $expectedScores[$p] = 0 }
# Шаг 5 чуть выше уже сыграл один раунд и засчитал верный ответ игроку
# $buzzer (playerSerials[0]) — реальный счёт на экране хоста это учитывает,
# поэтому и ожидаемый счёт должен стартовать не с нуля, а с этой самой
# точки, иначе итоговое сравнение всегда будет расходиться на 1.
$expectedScores[$playerSerials[0]] = 1
$roundsOk = $true
$consecutiveOpenFail = 0

for ($round = 1; $round -le 10; $round++) {
    foreach ($p in $playerSerials) { Clear-Log $p }
    $ok = Tap-Element $hostSerial "Открыть раунд" 10
    if (-not $ok) {
        $roundsOk = $false
        $consecutiveOpenFail++
        Write-Host "    ! раунд $round : не удалось открыть раунд" -ForegroundColor Yellow
        if ($consecutiveOpenFail -ge 2) {
            Write-Host "    ! два раунда подряд не открылись — прерываю цикл, не жду оставшиеся" -ForegroundColor Red
            break
        }
        continue
    }
    $consecutiveOpenFail = 0

    foreach ($p in $playerSerials) {
        if (-not (Wait-LogLine $p "kind=roundOpened" 10)) {
            $roundsOk = $false
            Write-Host "    ! раунд $round : $p не получил roundOpened" -ForegroundColor Yellow
        }
    }

    $roundBuzzer = $playerSerials[($round - 1) % $playerSerials.Count]
    Clear-Log $hostSerial
    if (-not (Tap-Element $roundBuzzer "Ответить" 10)) {
        $roundsOk = $false
        Write-Host "    ! раунд $round : BUZZ не нажался на $roundBuzzer" -ForegroundColor Yellow
    }
    if (-not (Wait-LogLine $hostSerial "kind=buzz" 10)) {
        $roundsOk = $false
        Write-Host "    ! раунд $round : хост не увидел BUZZ" -ForegroundColor Yellow
    }

    $isCorrect = ($round % 2 -eq 1)
    Clear-Log $roundBuzzer
    $judgeText = if ($isCorrect) { "Верно" } else { "Неверно" }
    if (-not (Tap-Element $hostSerial $judgeText 10 -Contains)) {
        $roundsOk = $false
        Write-Host "    ! раунд $round : не удалось нажать '$judgeText'" -ForegroundColor Yellow
    }
    if ($isCorrect) { $expectedScores[$roundBuzzer]++ }
    if (-not (Wait-LogLine $roundBuzzer "kind=answerResult" 10)) {
        $roundsOk = $false
        Write-Host "    ! раунд $round : $roundBuzzer не получил answerResult" -ForegroundColor Yellow
    }

    # AppViewModel.judgeCurrentResponder: верный ответ сам закрывает раунд
    # (roundIsOpen=false + roundClosed), а неверный — сознательно оставляет
    # раунд открытым для следующего игрока, закрывает только явный тап
    # "Закрыть раунд". Без этого тапа следующий "Открыть раунд" не находится,
    # а буз того же (ещё не закрытого) раунда засчитывается ещё раз — отсюда
    # был и провал шага, и завышенный итоговый счёт при первом прогоне.
    if (-not $isCorrect) {
        if (-not (Tap-Element $hostSerial "Закрыть раунд" 10)) {
            $roundsOk = $false
            Write-Host "    ! раунд $round : не удалось нажать 'Закрыть раунд'" -ForegroundColor Yellow
        }
    }
}
Record-Result "Обычная игра: 10 раундов пройдены без сбоев" $roundsOk

$hostXml = Get-UiXml $hostSerial
foreach ($p in $playerSerials) {
    $nick = $nicknames[$p]
    $actual = Get-PlayerScore $hostXml $nick
    $expected = $expectedScores[$p]
    Record-Result "Итоговый счёт $nick`: $actual (ожидалось $expected)" ($actual -eq $expected)
}

# ——— Шаг 6: хост покидает игру — все игроки должны выйти автоматически ———

Write-Host "=== Хост покидает игру ===" -ForegroundColor Cyan
foreach ($p in $playerSerials) { Clear-Log $p }
$ok = Tap-Element $hostSerial "Выход" 10
Record-Result "Хост: нажатие 'Выход'" $ok

$ok2 = $true
foreach ($p in $playerSerials) {
    $lost = Wait-LogLine $p "HostConnectionLost" 10
    $ok2 = $ok2 -and [bool]$lost
    Record-Result "Игрок $($nicknames[$p]): авто-выход при потере хоста" ([bool]$lost) $lost
}

# ——— Шаг 7: подключение посреди уже идущей игры должно быть отклонено ———

Write-Host "=== Повторный цикл: отклонение подключения посреди игры ===" -ForegroundColor Cyan
foreach ($d in $devices) { Restart-App $d }

Clear-Log $hostSerial
Tap-Element $hostSerial "Я ведущий" 10 | Out-Null
Tap-Element $hostSerial "Запустить сервер" 10 | Out-Null
Wait-LogLine $hostSerial "ServerSocket поднят" 10 | Out-Null
$hostIp = Get-DeviceWifiIp $hostSerial

$firstPlayer = $playerSerials[0]
$firstNick = "Early-$(Get-Random -Maximum 999)"
Clear-Log $hostSerial
Join-AsPlayer $firstPlayer $firstNick $hostIp | Out-Null
Wait-LogLine $hostSerial "kind=hello sender=$([regex]::Escape($firstNick))" 10 | Out-Null

Tap-Element $hostSerial "Начать игру" 10 | Out-Null
Wait-LogLine $firstPlayer "kind=gameStarted" 10 | Out-Null

if ($playerSerials.Count -ge 2) {
    $lateJoiner = $playerSerials[1]
} else {
    # Только 2 устройства всего: используем то же самое устройство, вышедшее из игры ранее.
    $lateJoiner = $firstPlayer
    Restart-App $lateJoiner
}
$lateNick = "Late-$(Get-Random -Maximum 999)"
Clear-Log $hostSerial
Join-AsPlayer $lateJoiner $lateNick $hostIp | Out-Null

$rejected = Wait-LogLine $hostSerial "HELLO отклонён: игра уже началась" 10
Record-Result "Хост отклонил подключение посреди игры" ([bool]$rejected) $rejected

} while ($false)

# ——— Шаг 8: сервер и игра с других телефонов в роли хоста ———
# Основной прогон выше всегда хостит одно и то же устройство ($hostSerial).
# Здесь по очереди поднимаем сервер на каждом ДРУГОМ пригодном для хоста
# устройстве (не владельце хотспота) и коротко играем — так баг конкретного
# устройства-хоста (например, специфика MIUI/Samsung) не останется незамеченным.
$otherHostCandidates = @($hostCandidates | Where-Object { $_ -ne $hostSerial })
if ($otherHostCandidates.Count -eq 0) {
    Write-Host "=== Другие устройства для роли хоста отсутствуют — шаг 8 пропущен ===" -ForegroundColor DarkGray
} else {
    foreach ($altHost in $otherHostCandidates) {
        Write-Host "=== Хост на другом устройстве: $altHost ===" -ForegroundColor Cyan
        $altPlayers = @($devices | Where-Object { $_ -ne $altHost })
        foreach ($d in $devices) { Restart-App $d }
        Invoke-QuickGame $altHost $altPlayers 2 "Хост $altHost" | Out-Null
    }
}

# ——— Сводка ———

Write-Host ""
Write-Host "=== Итог ===" -ForegroundColor Cyan
$failed = $script:results | Where-Object { -not $_.Passed }
$total = $script:results.Count
$passed = $total - $failed.Count
Write-Host "$passed / $total проверок пройдено" -ForegroundColor $(if ($failed.Count -eq 0) { "Green" } else { "Red" })
if ($failed.Count -gt 0) {
    Write-Host "Провалились:" -ForegroundColor Red
    $failed | ForEach-Object { Write-Host "  - $($_.Name)" -ForegroundColor Red }
    exit 1
}
exit 0
