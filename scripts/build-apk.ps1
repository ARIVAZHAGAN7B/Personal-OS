param(
    [string]$SdkDir = "C:/Users/ariva/AppData/Local/Android/Sdk"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$buildTools = Join-Path $SdkDir "build-tools/36.0.0"
$androidJar = Join-Path $SdkDir "platforms/android-36/android.jar"
$mainDir = Join-Path $root "app/src/main"
$buildDir = Join-Path $root "app/build/manual"
$outDir = Join-Path $root "app/build/outputs/apk/debug"
$unsignedApk = Join-Path $buildDir "app-debug-unsigned.apk"
$unalignedApk = Join-Path $buildDir "app-debug-unaligned.apk"
$alignedApk = Join-Path $buildDir "app-debug-aligned.apk"
$finalApk = Join-Path $outDir "app-debug.apk"
$debugKeystore = Join-Path $root "debug.keystore"
$releaseKeystore = Join-Path $root "personalos-release.keystore"
$keytool = "C:/Program Files/Java/jdk-25/bin/keytool.exe"
$jarTool = "C:/Program Files/Java/jdk-25/bin/jar.exe"
$gradleConfig = Get-Content (Join-Path $root "app/build.gradle") -Raw

if ($gradleConfig -notmatch 'versionCode\s+(\d+)') {
    throw "versionCode is missing from app/build.gradle"
}
$versionCode = $Matches[1]
if ($gradleConfig -notmatch 'versionName\s+["'']([^"'']+)["'']') {
    throw "versionName is missing from app/build.gradle"
}
$versionName = $Matches[1]

function Invoke-Checked {
    param(
        [string]$Command,
        [object[]]$CommandArgs
    )

    & $Command @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $Command $($CommandArgs -join ' ')"
    }
}

Remove-Item $buildDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path @(
    (Join-Path $buildDir "compiled-res"),
    (Join-Path $buildDir "generated"),
    (Join-Path $buildDir "classes"),
    (Join-Path $buildDir "dex"),
    $outDir
) | Out-Null

Invoke-Checked (Join-Path $buildTools "aapt2.exe") @(
    "compile",
    "--dir", (Join-Path $mainDir "res"),
    "-o", (Join-Path $buildDir "compiled-res")
)

$compiledResources = @(Get-ChildItem (Join-Path $buildDir "compiled-res") -Filter "*.flat" | ForEach-Object { $_.FullName })

$linkArgs = @(
    "link",
    "-I", $androidJar,
    "--manifest", (Join-Path $mainDir "AndroidManifest.xml"),
    "--java", (Join-Path $buildDir "generated"),
    "--min-sdk-version", "23",
    "--target-sdk-version", "36",
    "--version-code", $versionCode,
    "--version-name", $versionName,
    "-o", $unsignedApk
) + $compiledResources
Invoke-Checked (Join-Path $buildTools "aapt2.exe") $linkArgs

$javaSources = @(Get-ChildItem (Join-Path $mainDir "java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })
$generatedSources = @(Get-ChildItem (Join-Path $buildDir "generated") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })

$sourceFiles = @($javaSources) + @($generatedSources)

$javacArgs = @(
    "-source", "17",
    "-target", "17",
    "-classpath", $androidJar,
    "-d", (Join-Path $buildDir "classes")
) + $sourceFiles
Invoke-Checked "javac" $javacArgs

$classesDir = Join-Path $buildDir "classes"
$classesJar = Join-Path $buildDir "classes.jar"
Invoke-Checked $jarTool @("cf", $classesJar, "-C", $classesDir, ".")

$d8Args = @(
    "--lib", $androidJar,
    "--output", (Join-Path $buildDir "dex"),
    $classesJar
)
Invoke-Checked (Join-Path $buildTools "d8.bat") $d8Args

Copy-Item $unsignedApk $unalignedApk -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unalignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $existing = $zip.GetEntry("classes.dex")
    if ($existing) {
        $existing.Delete()
    }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip,
        (Join-Path $buildDir "dex/classes.dex"),
        "classes.dex",
        [System.IO.Compression.CompressionLevel]::Optimal
    ) | Out-Null
} finally {
    $zip.Dispose()
}

Invoke-Checked (Join-Path $buildTools "zipalign.exe") @("-f", "-p", "4", $unalignedApk, $alignedApk)

if (!(Test-Path $debugKeystore)) {
    Invoke-Checked $keytool @(
        "-genkeypair",
        "-v",
        "-keystore", $debugKeystore,
        "-storepass", "android",
        "-alias", "androiddebugkey",
        "-keypass", "android",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Android Debug,O=Android,C=US"
    )
}

$signingKeystore = if (Test-Path $releaseKeystore) { $releaseKeystore } else { $debugKeystore }
$keystorePassword = if ($env:PERSONALOS_KEYSTORE_PASSWORD) {
    $env:PERSONALOS_KEYSTORE_PASSWORD
} else {
    "android"
}
$keyAlias = if ($env:PERSONALOS_KEY_ALIAS) { $env:PERSONALOS_KEY_ALIAS } else { "androiddebugkey" }

Invoke-Checked (Join-Path $buildTools "apksigner.bat") @(
    "sign",
    "--ks", $signingKeystore,
    "--ks-key-alias", $keyAlias,
    "--ks-pass", "pass:$keystorePassword",
    "--key-pass", "pass:$keystorePassword",
    "--out", $finalApk,
    $alignedApk
)

Invoke-Checked (Join-Path $buildTools "apksigner.bat") @("verify", $finalApk)

Write-Output $finalApk
