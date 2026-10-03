# Media Provider Manager

An Xposed module intended to prevent media storage abuse.

[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/+rx5V9umZI4FjMWNl)
[![Stars](https://img.shields.io/github/stars/Mzdyl/Media-Provider-Manager?label=Stars)](https://github.com/Mzdyl/Media-Provider-Manager)
[![Download](https://img.shields.io/github/v/release/Mzdyl/Media-Provider-Manager?label=Download)](https://github.com/Mzdyl/Media-Provider-Manager/releases/latest)

## Screenshots

<p><img src="https://raw.githubusercontent.com/Mzdyl/Media-Provider-Manager/Re/screenshots/about.jpg" height="400" alt="Screenshot"/>
<img src="https://raw.githubusercontent.com/Mzdyl/Media-Provider-Manager/Re/screenshots/record.jpg" height="400" alt="Screenshot"/>
<img src="https://raw.githubusercontent.com/Mzdyl/Media-Provider-Manager/Re/screenshots/template.jpg" height="400" alt="Screenshot"/></p>

## What is media store

[Media store][1] is an optimized index into media collections provided by the Android framework. When an application needs to access media files (e.g. an album application wants to display all the pictures in the device), it is more easier to [interact with the media store][2] than traversing all files in the external storage volume. In addition it reduces the number of files accessible to the app, which helps to protect user privacy.

## How media store is abused

As with native storage, Android does not offer a fine-grained management scheme for media storage.
- ~~Apps only need low-risk permissions to access all media files, and users cannot limit the scope of reading.~~
- No permission is required for applications to insert files through the media store. Writing files freewheelingly will clutter up the external storage and the media store, and it can also be used for cross-application tracking.

## Features

- Media file manager built with only media store API.
- Filter data returned from the media store to protect your privacy.
- Prevent apps from freewheelingly writing files via the media store.
- Provide a usage record feature to help you be aware of how applications use the media store.
- Prevent 💩 ROM's download manager from creating non-standard files.
- Material 3.
- Open source.

## Configuring rules and troubleshooting

Select an app in App management, then assign an existing template or create one. Tap **Save** to apply edits; navigating back prompts before discarding an unsaved draft. Templates can be shared, so changing a path affects every assigned app.

Filter paths must match actual directory names. The editor reveals invisible spaces and format characters as `[U+XXXX]`; use the folder picker or manual path editor to check them. Valid filename characters are never removed automatically. Selecting no media types or all types leaves types unrestricted; directory filters still apply.

If Google Photos or another app still shows filtered content, check template assignments and invisible path characters, then distinguish new local media queries from existing app caches and cloud copies. The module filters MediaStore queries; it does not erase cached or cloud media or isolate filesystem access.

## Source code

[https://github.com/Mzdyl/Media-Provider-Manager](https://github.com/Mzdyl/Media-Provider-Manager)

## Releases

[Github Release](https://github.com/Mzdyl/Media-Provider-Manager/releases/latest)

## Compatibility

- Android 10 (API 29) is the minimum supported version; Android 10–16 is the primary compatibility range.
- A recent LSPosed version is recommended. Only enable the module for the suggested MediaProvider, DownloadProvider, and module-app scopes.
- MediaProvider is a private system component and vendor implementations vary. Unknown method signatures and internal API failures are handled fail-open, with the reason written to the Xposed log.

## Build and verification

JDK 21 and Android SDK 36 are required:

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease
```

CI runs unit tests, Android Lint, and the Debug APK build. Core rule matching, path boundaries, and database type conversions should be covered by unit tests before merging.

## Data and privacy

Usage records are stored in MediaProvider's private database and are exposed only through a Binder interface protected by caller UID checks. The write queue is bounded, records older than 90 days are pruned automatically, and users can clear all records manually.

Rule files are written atomically. Corrupt or unsupported rules are disabled instead of preventing the system MediaProvider from starting.

## License

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

[1]: https://developer.android.com/reference/android/provider/MediaStore
[2]: https://developer.android.com/training/data-storage/use-cases#handle-media-files
