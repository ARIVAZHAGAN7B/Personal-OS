# Releasing PersonalOS

The local `personalos-release.keystore` is the permanent signing identity. Back it up
privately. Never commit or share it. Losing it prevents future APKs from updating the
installed app without uninstalling and losing local app data.

Publish a new version with a strictly higher version code:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\publish-github-release.ps1 `
  -Repository "ARIVAZHAGAN7B/Personal-OS" `
  -VersionCode 2 `
  -VersionName "1.1.0" `
  -ReleaseNotes "Describe the changes."
```

The script:

1. updates the app version and release-feed URL;
2. builds and signs the APK;
3. creates `dist/PersonalOS-<version>.apk`;
4. writes `release/latest.json`;
5. publishes both files in a GitHub Release.

Use `-PrepareOnly` to build and validate artifacts without publishing.
