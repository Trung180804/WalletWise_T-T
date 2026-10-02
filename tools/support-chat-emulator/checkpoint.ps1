param(
  [ValidateSet('StartFirebase','StopFirebase','Suite','StartStaff','FinishStaff','StartAndroid','StopAndroid','Gradle','Install','Runtime','Inspect')]
  [string]$Action,
  [ValidateSet(':shared:testAndroidHostTest',':app:testDebugUnitTest',':shared:compileAndroidMain',':app:assembleDebug',':app:lintDebug',':app:assembleDebugAndroidTest')]
  [string]$Task,
  [switch]$Emulator,
  [string]$Label = 'task'
)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out = Join-Path $root '.artifacts/support-chat-emulator'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$nodeExe = 'C:\Program Files\nodejs\node.exe'
$adbExe = 'C:\Users\MSI\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$serial = 'emulator-5556'
Set-Location -LiteralPath $root
function Adb {
  & $adbExe -s $serial @args
  if ($LASTEXITCODE -ne 0) { throw 'ADB checkpoint command failed' }
}
function Wait-Port([int]$port) {
  $deadline = [DateTime]::UtcNow.AddSeconds(90)
  do {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listener) { return }
    Start-Sleep -Seconds 1
  } while ([DateTime]::UtcNow -lt $deadline)
  throw ('Emulator port did not start: ' + $port)
}
function Stop-OwnedTree([string]$pidFile) {
  if (!(Test-Path -LiteralPath $pidFile)) { return }
  $ownedPid = [int](Get-Content -LiteralPath $pidFile -Raw)
  $processList = @(Get-CimInstance Win32_Process)
  $rootProcess = @($processList | Where-Object { $_.ProcessId -eq $ownedPid })[0]
  if ($null -eq $rootProcess) { return }
  if ($rootProcess.CommandLine -notmatch 'firebase-tools|support-chat-emulator') { throw 'Refusing to stop an unrelated process' }
  $targets = [System.Collections.Generic.List[int]]::new()
  $targets.Add($ownedPid)
  for ($i = 0; $i -lt $targets.Count; $i++) {
    foreach ($child in @($processList | Where-Object { $_.ParentProcessId -eq $targets[$i] })) { $targets.Add([int]$child.ProcessId) }
  }
  for ($i = $targets.Count - 1; $i -ge 0; $i--) { Stop-Process -Id $targets[$i] -Force -ErrorAction SilentlyContinue }
}
switch ($Action) {
  StartFirebase {
    $occupied = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in @(8080,9099,4400,4500,9150) })
    if ($occupied.Count -gt 0) { throw 'Refusing to replace existing Emulator services' }
    $env:FIREBASE_EMULATORS_PATH = Join-Path $root '.artifacts/firebase-emulators'
    $env:FIREBASE_CLI_DISABLE_USAGE = 'true'
    $env:FIREBASE_CLI_DISABLE_UPDATE_CHECK = 'true'
    $env:NO_UPDATE_NOTIFIER = '1'
    $env:CI = 'true'
    $env:SUPPORT_AUDIT_FILE = Join-Path $out 'firebase-network-audit.json'
    Remove-Item Env:FIREBASE_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:GOOGLE_APPLICATION_CREDENTIALS -ErrorAction SilentlyContinue
    $cli = Join-Path $root '.artifacts/npm-cache/_npx/7750544ccf494d8b/node_modules/firebase-tools/lib/bin/firebase.js'
    $args = @('--require',(Join-Path $PSScriptRoot 'network-guard.cjs'),$cli,'emulators:start','--only','auth,firestore','--project','demo-walletwise','--config',(Join-Path $root 'firebase.online-support.json'),'--non-interactive')
    $service = Start-Process -FilePath $nodeExe -ArgumentList $args -WorkingDirectory $out -WindowStyle Hidden -RedirectStandardOutput (Join-Path $out 'firebase-out.log') -RedirectStandardError (Join-Path $out 'firebase-err.log') -PassThru
    $service.Id | Set-Content -LiteralPath (Join-Path $out 'firebase.pid')
    Wait-Port 9099; Wait-Port 8080
    Write-Output ('Firebase Emulator started PID=' + $service.Id)
  }
  StopFirebase {
    Stop-OwnedTree (Join-Path $out 'firebase.pid')
    Start-Sleep -Seconds 2
    $remaining = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in @(8080,9099,4400,4500,9150,8787) })
    $firebasePid = [int](Get-Content -LiteralPath (Join-Path $out 'firebase.pid'))
    $firebaseStopped = $null -eq (Get-Process -Id $firebasePid -ErrorAction SilentlyContinue)
    if (!$firebaseStopped) { throw 'Owned Firebase process still running' }
    [ordered]@{ firebase_process_stopped = $firebaseStopped; test_ports_remaining = $remaining.Count } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out 'process-cleanup.json')
    if ($remaining.Count -gt 0) { throw 'Test ports remain open' }
    Write-Output 'Firebase process stopped; all test ports closed'
  }
  Suite {
    $env:SUPPORT_AUDIT_FILE = Join-Path $out 'suite-network-audit.json'
    $ErrorActionPreference = 'Continue'
    & $nodeExe --require (Join-Path $PSScriptRoot 'network-guard.cjs') (Join-Path $PSScriptRoot 'harness.mjs') suite 2>&1 | Tee-Object -FilePath (Join-Path $out 'suite.log')
    $suiteExit = $LASTEXITCODE
    exit $suiteExit
  }
  StartStaff {
    if (Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue) { throw 'Refusing to replace an existing staff harness' }
    foreach ($name in @('serve-report.json','serve-cleanup.json','mobile-staff-metrics.json')) {
      $artifact = Join-Path $out $name
      if (Test-Path -LiteralPath $artifact) { Remove-Item -LiteralPath $artifact }
    }
    $env:SUPPORT_AUDIT_FILE = Join-Path $out 'staff-network-audit.json'
    $staff = Start-Process -FilePath $nodeExe -ArgumentList @('--require',(Join-Path $PSScriptRoot 'network-guard.cjs'),(Join-Path $PSScriptRoot 'harness.mjs'),'serve') -WorkingDirectory $out -WindowStyle Hidden -RedirectStandardOutput (Join-Path $out 'staff-out.log') -RedirectStandardError (Join-Path $out 'staff-err.log') -PassThru
    $staff.Id | Set-Content -LiteralPath (Join-Path $out 'staff.pid')
    Wait-Port 8787
    Write-Output ('Staff harness started PID=' + $staff.Id)
  }
  FinishStaff {
    $staffPid = [int](Get-Content -LiteralPath (Join-Path $out 'staff.pid'))
    $staffProcess = Get-Process -Id $staffPid -ErrorAction SilentlyContinue
    if ($staffProcess) {
      Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/finish' | Out-Null
      if (!$staffProcess.WaitForExit(30000)) { throw 'Staff cleanup did not finish' }
      # Get-Process may expose a null ExitCode after a separately launched process exits.
      if ($null -ne $staffProcess.ExitCode -and $staffProcess.ExitCode -ne 0) { throw 'Staff harness failed' }
    }
    if (Get-Process -Id $staffPid -ErrorAction SilentlyContinue) { throw 'Staff process remains running' }
    $report = Get-Content -LiteralPath (Join-Path $out 'serve-report.json') -Raw | ConvertFrom-Json
    $cleanup = Get-Content -LiteralPath (Join-Path $out 'serve-cleanup.json') -Raw | ConvertFrom-Json
    if ($report.failed -ne 0 -or $report.phase -ne 'cleanup-verify') { throw 'Staff report failed' }
    if ($cleanup.test_accounts_remaining -ne 0 -or $cleanup.test_conversations_remaining -ne 0 -or $cleanup.test_messages_remaining -ne 0) { throw 'Staff fixtures remain' }
    if (Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue) { throw 'Staff port remains open' }
    Get-Content -LiteralPath (Join-Path $out 'serve-cleanup.json')
    Write-Output 'Staff process stopped; fixture port closed; cleanup verified'
  }
  StartAndroid {
    $emulatorExe = 'C:\Users\MSI\AppData\Local\Android\Sdk\emulator\emulator.exe'
    $image = 'C:\Users\MSI\AppData\Local\Android\Sdk\system-images\android-34\google_apis_playstore\x86_64'
    if (!(Test-Path -LiteralPath (Join-Path $image 'system.img'))) { throw 'Installed Android system image missing' }
    $avdHome = Join-Path $out 'avd'
    $avdDir = Join-Path $avdHome 'WalletWiseSupport.avd'
    New-Item -ItemType Directory -Path $avdDir -Force | Out-Null
    if (!(Test-Path -LiteralPath (Join-Path $avdDir 'config.ini'))) {
      @('AvdId=WalletWiseSupport','avd.ini.encoding=UTF-8','PlayStore.enabled=true','abi.type=x86_64','hw.cpu.arch=x86_64','hw.cpu.ncore=2','hw.ramSize=2048','hw.lcd.width=1080','hw.lcd.height=1920','hw.lcd.density=420','hw.gpu.enabled=yes','hw.gpu.mode=auto','hw.keyboard=yes','hw.device.name=pixel_5','disk.dataPartition.size=4G',('image.sysdir.1=' + $image),'tag.id=google_apis_playstore','tag.display=Google Play','target=android-34') | Set-Content -LiteralPath (Join-Path $avdDir 'config.ini') -Encoding ASCII
      @('avd.ini.encoding=UTF-8',('path=' + $avdDir),'target=android-34') | Set-Content -LiteralPath (Join-Path $avdHome 'WalletWiseSupport.ini') -Encoding ASCII
    }
    $env:ANDROID_AVD_HOME = $avdHome
    $android = Start-Process -FilePath $emulatorExe -ArgumentList @('-avd','WalletWiseSupport','-port','5556','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader_indirect','-memory','2048','-camera-back','none','-camera-front','none') -WorkingDirectory $out -WindowStyle Hidden -RedirectStandardOutput (Join-Path $out 'android-out.log') -RedirectStandardError (Join-Path $out 'android-err.log') -PassThru
    $android.Id | Set-Content -LiteralPath (Join-Path $out 'android.pid')
    Write-Output ('Android Emulator launched PID=' + $android.Id + ' serial=' + $serial)
  }
  StopAndroid {
    $avdName = (& $adbExe -s $serial emu avd name 2>$null) -join ''
    if ($avdName -notmatch 'WalletWiseSupport') { throw 'Refusing to stop an unrelated Android Emulator' }
    Adb shell am force-stop com.example.walletwise
    Adb shell pm clear com.example.walletwise
    Adb emu kill
  }
  Gradle {
    if ([string]::IsNullOrWhiteSpace($Task)) { throw 'One Gradle task is required' }
    $env:USE_FIREBASE_EMULATOR = if ($Emulator) { 'true' } else { 'false' }
    $gradleArgs = @($Task,'--no-daemon','--console=plain')
    if ($Task -in @(':shared:testAndroidHostTest',':app:testDebugUnitTest')) { $gradleArgs += '--rerun-tasks' }
    if ($Emulator) { $gradleArgs += '-PuseFirebaseEmulator=true' }
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $ErrorActionPreference = 'Continue'
    & (Join-Path $root 'gradlew.bat') @gradleArgs 2>&1 | Tee-Object -FilePath (Join-Path $out ($Label + '.log'))
    $taskExit = $LASTEXITCODE
    $timer.Stop()
    [ordered]@{ command = '.\gradlew.bat ' + ($gradleArgs -join ' '); duration_seconds = $timer.Elapsed.TotalSeconds; exit_code = $taskExit } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out ($Label + '-result.json'))
    Write-Output ('COMMAND_RESULT duration_seconds={0:N3} exit_code={1}' -f $timer.Elapsed.TotalSeconds, $taskExit)
    exit $taskExit
  }
  Install {
    $config = Get-Content -LiteralPath (Join-Path $root 'app/build/generated/source/buildConfig/debug/com/example/walletwise/BuildConfig.java') -Raw
    if ($config -notmatch 'USE_FIREBASE_EMULATOR = true') { throw 'Refusing non-emulator APK' }
    Adb install -r -t (Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk')
    Adb install -r -t (Join-Path $root 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
  }
  Runtime {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $ErrorActionPreference = 'Continue'
    & $adbExe -s $serial shell am instrument -w -e class com.example.walletwise.reliability.SupportChatTwoWayEmulatorTest com.example.walletwise.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Tee-Object -FilePath (Join-Path $out 'android-runtime.log')
    $adbExit = $LASTEXITCODE
    $timer.Stop()
    $runtimeLog = Get-Content -LiteralPath (Join-Path $out 'android-runtime.log') -Raw
    $runtimePassed = $adbExit -eq 0 -and $runtimeLog -match 'OK \(1 test\)' -and $runtimeLog -notmatch 'FAILURES|INSTRUMENTATION_FAILED'
    [ordered]@{ duration_seconds = $timer.Elapsed.TotalSeconds; adb_exit_code = $adbExit; passed = $runtimePassed } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out 'android-runtime-result.json')
    Write-Output ('RUNTIME_RESULT duration_seconds={0:N3} passed={1}' -f $timer.Elapsed.TotalSeconds, $runtimePassed)
    if (!$runtimePassed) { exit 1 }
  }
  Inspect {
    & $adbExe devices -l
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in @(8080,9099,4400,4500,9150,8787) } | Select-Object LocalAddress,LocalPort,OwningProcess
    Get-Content -LiteralPath (Join-Path $out 'firebase-out.log') -Tail 12 -ErrorAction SilentlyContinue
  }
}
