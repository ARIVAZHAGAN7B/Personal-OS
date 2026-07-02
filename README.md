# PersonalOS

PersonalOS is a local-first Android app for expense tracking and digital usage analytics.

## Download

Download the latest signed APK from the
[latest release](https://github.com/ARIVAZHAGAN7B/Personal-OS/releases/latest).

Android may ask you to allow installations from your browser the first time. Future APKs
must be installed over the existing app so local data is preserved.

## Updates

Open **App settings** from the PersonalOS home screen to:

- check for updates manually;
- enable or disable automatic update alerts;
- view the installed version;
- change the HTTPS release feed.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release

See [RELEASING.md](RELEASING.md).
