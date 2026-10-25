$ErrorActionPreference = "Stop"

$ProjectDir = $PSScriptRoot
$RootDir = Split-Path $ProjectDir -Parent
$BuildDir = "$ProjectDir\build"
$ClassesDir = "$BuildDir\classes"
$ResZip = "$BuildDir\res.zip"
$UnsignedApk = "$BuildDir\unsigned.apk"
$AlignedApk = "$BuildDir\aligned.apk"
$SignedApk = "$RootDir\PasskeyEnabler.apk"

$SdkDir = if ($env:LOCALAPPDATA) { "$env:LOCALAPPDATA\Android\Sdk" } else { "$HOME\AppData\Local\Android\Sdk" }
$BuildTools = "$SdkDir\build-tools\35.0.0"
$AndroidJar = "$SdkDir\platforms\android-36\android.jar"
$XposedJar = "$RootDir\tools\api-82.jar"
$JdkBin = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin"

if (Test-Path $BuildDir) {
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

& "$BuildTools\aapt2.exe" compile --dir "$ProjectDir\res" -o $ResZip
& "$BuildTools\aapt2.exe" link -o $UnsignedApk `
    -I $AndroidJar `
    --manifest "$ProjectDir\AndroidManifest.xml" `
    --java "$BuildDir\gen" `
    -A "$ProjectDir\assets" `
    $ResZip

$JavaFiles = Get-ChildItem -Recurse -Path "$ProjectDir\src", "$BuildDir\gen" -Filter "*.java" | ForEach-Object { $_.FullName }
& "$JdkBin\javac.exe" --release 8 -cp "$AndroidJar;$XposedJar" -d $ClassesDir $JavaFiles

$ClassFiles = Get-ChildItem -Recurse -Path $ClassesDir -Filter "*.class" | ForEach-Object { $_.FullName }
& "$BuildTools\d8.bat" --lib $AndroidJar --classpath $XposedJar --output $BuildDir $ClassFiles

& "$JdkBin\jar.exe" -uf $UnsignedApk -C $BuildDir classes.dex

& "$BuildTools\zipalign.exe" -f -p 4 $UnsignedApk $AlignedApk

$Keystore = "$HOME\.android\debug.keystore"
if (-not (Test-Path $Keystore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Keystore) | Out-Null
    & "$JdkBin\keytool.exe" -genkeypair -v -keystore $Keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
}

& "$BuildTools\apksigner.bat" sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --out $SignedApk $AlignedApk
Write-Output "Built signed APK: $SignedApk"
