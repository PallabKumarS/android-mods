$ErrorActionPreference = "Stop"

function Invoke-Step {
    param([scriptblock]$Command, [string]$StepName)
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$StepName failed with exit code $LASTEXITCODE"
    }
}

$ProjectDir = $PSScriptRoot
$RootDir = Split-Path $ProjectDir -Parent
$BuildDir = "$ProjectDir\build"
$ClassesDir = "$BuildDir\classes"
$ResZip = "$BuildDir\res.zip"
$UnsignedApk = "$BuildDir\unsigned.apk"
$AlignedApk = "$BuildDir\aligned.apk"
$SignedApk = "$RootDir\AutoBrightnessGuard.apk"

$SdkDir = if ($env:LOCALAPPDATA) { "$env:LOCALAPPDATA\Android\Sdk" } else { "$HOME\AppData\Local\Android\Sdk" }
$BuildTools = "$SdkDir\build-tools\35.0.0"
$AndroidJar = "$SdkDir\platforms\android-36\android.jar"
$XposedJar = "$RootDir\tools\api-82.jar"
$JdkBin = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin"

if (Test-Path $BuildDir) {
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

Invoke-Step { & "$BuildTools\aapt2.exe" compile --dir "$ProjectDir\res" -o $ResZip } "aapt2 compile"
Invoke-Step {
    & "$BuildTools\aapt2.exe" link -o $UnsignedApk `
        -I $AndroidJar `
        --manifest "$ProjectDir\AndroidManifest.xml" `
        --java "$BuildDir\gen" `
        -A "$ProjectDir\assets" `
        $ResZip
} "aapt2 link"

$JavaFiles = Get-ChildItem -Recurse -Path "$ProjectDir\src", "$BuildDir\gen" -Filter "*.java" | ForEach-Object { $_.FullName }
Invoke-Step { & "$JdkBin\javac.exe" --release 8 -cp "$AndroidJar;$XposedJar" -d $ClassesDir $JavaFiles } "javac"

$ClassFiles = Get-ChildItem -Recurse -Path $ClassesDir -Filter "*.class" | ForEach-Object { $_.FullName }
Invoke-Step { & "$BuildTools\d8.bat" --lib $AndroidJar --classpath $XposedJar --output $BuildDir $ClassFiles } "d8"

Invoke-Step { & "$JdkBin\jar.exe" -uf $UnsignedApk -C $BuildDir classes.dex } "jar"
Invoke-Step { & "$BuildTools\zipalign.exe" -f -p 4 $UnsignedApk $AlignedApk } "zipalign"

$Keystore = "$HOME\.android\debug.keystore"
if (-not (Test-Path $Keystore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $Keystore) | Out-Null
    Invoke-Step {
        & "$JdkBin\keytool.exe" -genkeypair -v -keystore $Keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    } "keytool"
}

Invoke-Step {
    & "$BuildTools\apksigner.bat" sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --out $SignedApk $AlignedApk
} "apksigner"

Remove-Item -Recurse -Force $BuildDir
if (Test-Path "$SignedApk.idsig") {
    Remove-Item -Force "$SignedApk.idsig"
}

Write-Output "Built signed APK: $SignedApk ($((Get-Item $SignedApk).Length) bytes)"
