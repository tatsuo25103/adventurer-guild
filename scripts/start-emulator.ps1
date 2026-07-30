$ErrorActionPreference = "Stop"

$env:ANDROID_HOME = "C:\Users\lf.wu\Documents\Codex\AndroidSdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = "C:\Users\lf.wu\Documents\Codex\AndroidAvd"

Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" `
    -ArgumentList "-avd", "Codex_Test_API35", "-gpu", "swiftshader_indirect" `
    -WindowStyle Normal

& "$env:ANDROID_HOME\platform-tools\adb.exe" wait-for-device
Write-Host "Codex_Test_API35 is connected."
