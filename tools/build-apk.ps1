param([string]$JdkPath = $env:JAVA_HOME, [string]$SdkPath = $env:ANDROID_HOME, [string]$BuildProxy = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$JdkPath -and (Test-Path 'G:\Android\jbr')) { $JdkPath = 'G:\Android\jbr' }
if (!$SdkPath -and (Test-Path 'G:\Android\Sdk')) { $SdkPath = 'G:\Android\Sdk' }
if (!$JdkPath -or !(Test-Path "$JdkPath/bin/java.exe")) { throw 'Set JAVA_HOME or pass -JdkPath with a JDK 25+ installation.' }
if (!$SdkPath -or !(Test-Path "$SdkPath/build-tools/36.0.0/apksigner.bat")) { throw 'Set ANDROID_HOME or pass -SdkPath. Install SDK Build Tools 36.0.0 first.' }
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = $SdkPath
if (!$BuildProxy) {
    $proxySettings = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
    if ($proxySettings.ProxyEnable -eq 1) { $BuildProxy = $proxySettings.ProxyServer }
}
if ($BuildProxy -match '^(?:https?://)?([a-zA-Z0-9.-]+):(\d+)$') {
    $proxyHostValue = $Matches[1]
    $proxyPortValue = $Matches[2]
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Dhttps.proxyHost=$proxyHostValue -Dhttps.proxyPort=$proxyPortValue -Dhttp.proxyHost=$proxyHostValue -Dhttp.proxyPort=$proxyPortValue"
}
$signingRoot = Join-Path $projectRoot '.signing'
$keyPath = Join-Path $signingRoot 'chemistry-review.jks'
$propertiesPath = Join-Path $signingRoot 'key.properties'
if (!(Test-Path $keyPath)) {
    if (Test-Path $propertiesPath) { throw 'Signing settings exist but the key is missing. Restore the existing key before building an update.' }
    New-Item -ItemType Directory -Force $signingRoot | Out-Null
    $passwordBytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
    $env:CHEM_KEY_PASSWORD = [Convert]::ToBase64String($passwordBytes)
    & "$JdkPath\bin\keytool.exe" -genkeypair -keystore $keyPath -alias chemistry-review -storetype JKS -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Chemistry Review, OU=Personal App' -storepass:env CHEM_KEY_PASSWORD -keypass:env CHEM_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Signing key creation failed.' }
    [IO.File]::WriteAllText($propertiesPath, "storePassword=$env:CHEM_KEY_PASSWORD`nkeyPassword=$env:CHEM_KEY_PASSWORD`n")
    Remove-Item Env:CHEM_KEY_PASSWORD
}
if (!(Test-Path $propertiesPath)) { throw 'Signing settings are missing. Restore .signing/key.properties.' }
$localGradle = Join-Path $projectRoot '.build/gradle-dist/gradle-9.9.2/bin/gradle.bat'
$gradleCommand = if (Test-Path $localGradle) { $localGradle } else { Join-Path $projectRoot 'gradlew.bat' }
Push-Location $projectRoot
try {
    & $gradleCommand assembleRelease lintRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    $metadata = Get-Content 'app/build/outputs/apk/release/output-metadata.json' -Raw | ConvertFrom-Json
    $apkName = "chemistry-review-$($metadata.elements[0].versionName).apk"
    $destination = Join-Path $projectRoot "dist/$apkName"
    New-Item -ItemType Directory -Force (Split-Path $destination) | Out-Null
    Copy-Item 'app/build/outputs/apk/release/app-release.apk' $destination -Force
    & "$SdkPath/build-tools/36.0.0/apksigner.bat" verify --verbose $destination
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $digest = (Get-FileHash -Algorithm SHA256 $destination).Hash.ToLower()
    [IO.File]::WriteAllText("$destination.sha256", "$digest  $apkName`n")
    Write-Output "Installable APK: $destination"
} finally { Pop-Location }
