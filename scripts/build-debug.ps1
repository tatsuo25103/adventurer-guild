$ErrorActionPreference = "Stop"

$env:ANDROID_HOME = "C:\Users\lf.wu\Documents\Codex\AndroidSdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = "C:\Users\lf.wu\Documents\Codex\AndroidAvd"

& "$PSScriptRoot\..\gradlew.bat" :app:assembleDebug
