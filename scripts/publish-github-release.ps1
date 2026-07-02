param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repository,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int]$VersionCode,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+([-.][A-Za-z0-9.]+)?$')]
    [string]$VersionName,

    [string]$ReleaseNotes = "PersonalOS improvements and fixes.",
    [switch]$PrepareOnly
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$gradlePath = Join-Path $root "app/build.gradle"
$updateConfigPath = Join-Path $root "app/src/main/res/values/update_config.xml"
$metadataPath = Join-Path $root "release/latest.json"
$distDir = Join-Path $root "dist"
$apkName = "PersonalOS-$VersionName.apk"
$tag = "v$VersionName"
$feedUrl = "https://github.com/$Repository/releases/latest/download/latest.json"
$apkUrl = "https://github.com/$Repository/releases/download/$tag/$apkName"
$releasePageUrl = "https://github.com/$Repository/releases/latest"

$gradle = Get-Content $gradlePath -Raw
$gradle = [regex]::Replace($gradle, 'versionCode\s+\d+', "versionCode $VersionCode", 1)
$gradle = [regex]::Replace($gradle, 'versionName\s+["''][^"'']+["'']', "versionName `"$VersionName`"", 1)
[System.IO.File]::WriteAllText($gradlePath, $gradle, [System.Text.UTF8Encoding]::new($false))

[xml]$updateConfig = Get-Content $updateConfigPath -Raw
$feedNode = $updateConfig.SelectSingleNode("/resources/string[@name='default_update_feed_url']")
if ($null -eq $feedNode) {
    throw "default_update_feed_url is missing from update_config.xml"
}
$feedNode.InnerText = $feedUrl
$xmlSettings = [System.Xml.XmlWriterSettings]::new()
$xmlSettings.Indent = $true
$xmlSettings.Encoding = [System.Text.UTF8Encoding]::new($false)
$writer = [System.Xml.XmlWriter]::Create($updateConfigPath, $xmlSettings)
try {
    $updateConfig.Save($writer)
} finally {
    $writer.Dispose()
}

$metadata = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl = $apkUrl
    releasePageUrl = $releasePageUrl
    releaseNotes = $ReleaseNotes
}
$metadataJson = $metadata | ConvertTo-Json
[System.IO.File]::WriteAllText($metadataPath, $metadataJson + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false))

& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "build-apk.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "APK build failed."
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$builtApk = Join-Path $root "app/build/outputs/apk/debug/app-debug.apk"
$releaseApk = Join-Path $distDir $apkName
Copy-Item -LiteralPath $builtApk -Destination $releaseApk -Force
$sha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash

Write-Output "Prepared $releaseApk"
Write-Output "SHA-256 $sha256"

if ($PrepareOnly) {
    Write-Output "PrepareOnly selected; GitHub publishing skipped."
    exit 0
}

$ghCommand = Get-Command gh -ErrorAction SilentlyContinue
if (!$ghCommand) {
    $portableGh = Join-Path $env:LOCALAPPDATA "Programs/GitHubCLI/bin/gh.exe"
    if (Test-Path $portableGh) {
        $ghCommand = $portableGh
    }
}
if (!$ghCommand) {
    throw "GitHub CLI is not installed. Install it with: winget install GitHub.cli"
}

& $ghCommand auth status
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run: gh auth login --web"
}

& $ghCommand repo view $Repository | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "GitHub repository '$Repository' does not exist yet."
}

& $ghCommand release create $tag $releaseApk $metadataPath `
    --repo $Repository `
    --title "PersonalOS $VersionName" `
    --notes $ReleaseNotes `
    --latest
if ($LASTEXITCODE -ne 0) {
    throw "GitHub release creation failed."
}

Write-Output "Published $releasePageUrl"
