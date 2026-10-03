# Modern Xposed API 102 migration

The development branch uses `io.github.libxposed:api:102.0.0` as `compileOnly` and
`io.github.libxposed:service:102.0.0` in the standalone app. The published 1.0.x
release remains a legacy module. Android 10 is still the minimum OS version;
this branch requires an Xposed framework implementing API 102 and system-process
hooking. The API 102 service AAR requires compile SDK 37, so the build uses AGP
9.1.1 and Gradle 9.5.1. Target SDK remains 36.

## Entry and scopes

`META-INF/xposed/java_init.list`, `module.prop`, and `scope.list` replace legacy
manifest metadata and `assets/xposed_init`. The entry extends `XposedModule` and
registers provider lifecycle hooks from `onPackageReady`. The public two-argument
`ContentProvider.attachInfo` supplies the provider context before initialization.
Known AOSP, Google, and Samsung media packages and DownloadProvider are scoped;
the module app itself is not a scope. Initialization is guarded against duplicate
package callbacks, and partial hook registration is rolled back on failure.

Hot reload is disabled: existing database connections and live Binder clients
must be replaced by restarting the scoped processes after an update.

## Behavior preserved at the API boundary

- All hooks use `Hooker.intercept(Chain)`. Original invocation and exceptions
  remain outside the fail-open guards around module preparation and recording.
- Query filtering passes a copied argument array and copied Bundle to `proceed`.
  Rejected filters retry via an `ORIGIN` invoker with the caller's original
  arguments. Cancellation is propagated, and the retry's actual exception is
  unwrapped. The query hook uses `PASSTHROUGH` exception mode so the framework
  does not replace that exception with the earlier filtered-query failure.
- Insert restrictions survive recording failures. Delete operations remain
  observational. DownloadProvider directory restrictions use a Boolean return
  from the interceptor.
- Vendor reflection uses local helpers with overload resolution, bounded caches,
  and target exception unwrapping. Neither helpers nor logging call legacy APIs.
- Rules and the usage database stay in MediaProvider storage, behind the existing
  UID-checked business Binder. Existing transaction IDs are preserved; ID 1
  reports the injected Xposed API version. The standalone template model no
  longer loads hook classes to select operations.
- App activation observes the libxposed service in memory. Framework Binder
  checks run off the UI thread; a late callback cannot resurrect a dead service.
  The UI also checks the provider's API and module version, so an old injected
  module after an APK update is not reported as the current active version.

## Local checks

Use JDK 21, SDK platform 37 (37.0), and Build Tools 36.0.0:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 tools/verify_xposed_apk.py app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease
python3 tools/verify_xposed_apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

Use the signed `app-release.apk` path when signing is configured. Run Debug Lint
and Release compilation sequentially to avoid analysis of generated Hilt files
while another variant rewrites them.

The APK verifier inspects the actual DEX class definitions, entry metadata, and
public no-argument constructors. It rejects legacy API references, a bundled copy
of the framework API, a self-scope, or missing module/Room/service entry points.
CI runs it on both Debug and R8 Release artifacts.

## Opt-in device regression

A manual workflow run also builds a Release instrumentation APK signed with the
same configured certificate. The same can be built locally with signing env vars:

```sh
./gradlew assembleRelease assembleReleaseAndroidTest -PinstrumentedBuildType=release
```

The test requires an API 102 framework, enabled MediaProvider scope, and a restart
of that scope after installing the new module. Back up the installed APK and
MediaProvider's `files/root` and `files/rule` first. Do not run while the user is
editing rules. Install the module and matching test APK, then run:

```sh
adb shell am instrument -w -r \
  -e class me.gm.cleaner.plugin.Api102DeviceTest \
  me.gm.cleaner.plugin.test/androidx.test.runner.AndroidJUnitRunner
```

The test checks the actual injected API/module version and stable Binder protocol,
creates media fixtures under UUID-named Pictures directories, and verifies
projection preservation, directory filtering before LIMIT/OFFSET pagination,
cancellation, insert rejection/allowance, and delete/query/insert recording.
Other apps' template bindings stay intact. It temporarily enables usage recording
and isolates test rules to the module app; original configuration is restored in
`finally`, fixture URIs are deleted, and only empty fixture directories are removed.
Compare the configuration backups after the test, and remove the test APK.

A successful build alone does not establish device compatibility. Record the
APK digest, signing certificate, framework version, instrumentation result, and
configuration comparison when running the device regression. Check framework
logs for both MediaProvider registration and the DownloadProvider `mkdir` / `mkdirs`
registration message after restarting those scopes.
