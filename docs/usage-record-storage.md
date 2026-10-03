# Bounded usage diagnostics

Usage history is diagnostic data. It must not fill MediaProvider's storage or
change the result of a media operation. API 102 and existing filter rules remain
in effect when recording is disabled, unavailable, or under backpressure.

## Limits and meaning

- Retain at most seven days and 5,000 groups; the UI fetches at most 500 groups.
- Group successful queries by package, media collection, filter state and minute.
  A count describes recorded calls, not returned files or identical result sets.
  Process restarts and clear operations begin new groups.
- Capture one auxiliary query sample per cached group, at most 16 entries and
  8 KiB of serialized package/detail data. Clip paths to 384 UTF-8 bytes and MIME
  types to 96 bytes. Mark shortened samples; never copy a shortened path as a
  usable full path. Samples do not reproduce every result returned to callers.
- Bound the pending queue to 256 groups and SQL write batches to 16. Under load
  records can be dropped; the status Binder exposes the drop count.
- Cap database pages at 64 MiB after legacy cleanup. For a pre-existing larger
  file, ratchet the page limit down as space is reclaimed. Stop logging when the
  WAL reaches 4 MiB or free storage drops below 16 MiB. The WAL threshold is a
  pause threshold, not an exact byte cap: one bounded transaction can overshoot.
- Perform SQL opening, migration, writing, pruning and closing on one worker.
  Notifications are serialized and throttled; queries never wait for disk writes.

## Upgrade and clear behavior

Schema v4 creates a separate bounded table without scanning old detail blobs or
building indices over the old history. Direct migrations support versions 1–3.
At most the newest 2,000 legacy rows still inside the retention period become
metadata-only summaries. Their original detail/interception results are unknown.

Cleanup deletes at most 16 old rows per pass, imports at most 16 summaries, and
prunes at most 16 expired/cleared new rows. A durable cursor makes import resumable.
FULL auto-vacuum changes to INCREMENTAL; at most 32 pages are reclaimed per pass,
keeping a small reusable reserve. No full VACUUM or full-database copy runs during
upgrade or clear. Large histories take time to reclaim. Databases originally
created with auto-vacuum NONE retain freed pages for reuse; the UI says so.

Clear immediately hides prior history using a durable ID cutoff, invalidates
pending query samples and prevents legacy import from resurrecting old records.
Physical cleanup continues in the background, including while recording is off.
Disabling recording also invalidates pending samples and waits for any current
write to finish. A restart can lose unflushed diagnostics, not filter rules.

The log file is `databases/media_provider.db` inside the scoped provider's app
storage. **Do not clear the provider app's data or delete `external.db`: that is
system media metadata.** Rules and settings are separate files under `files/`.
After schema v4 is opened, an older module cannot read the new diagnostic schema.

## Verification

Run JVM policy/filter tests, Lint and builds:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
python3 tools/verify_xposed_apk.py app/build/outputs/apk/debug/app-debug.apk
```

`RecordStorageProbe` exercises Android Room/SQLite against synthetic files only.
Create a fresh directory named `/data/local/tmp/mpm-record-test-<suffix>` and push
the Debug main/test APKs there as `main.apk` and `test.apk`, then run:

```sh
adb shell 'CLASSPATH=/data/local/tmp/mpm-record-test-<suffix>/main.apk:/data/local/tmp/mpm-record-test-<suffix>/test.apk app_process / me.gm.cleaner.plugin.RecordStorageProbe /data/local/tmp/mpm-record-test-<suffix>'
```

It verifies row/detail/file limits, retention boundaries, aggregation, oversized
legacy migration and restart, small cleanup batches, migration-time clear,
pinned-reader WAL backpressure, concurrent writes, disable and stale tickets.
Remove only that test directory afterward. The fixture uses VACUUM only on an
empty synthetic database to emulate an older FULL auto-vacuum database.

The signed API 102 device regression optionally exercises the actual hooked
provider's query burst, counts, disable/filter independence, clear and resume:

```sh
adb shell am instrument -w -r -e storageRegression true \
  me.gm.cleaner.plugin.test/me.gm.cleaner.plugin.Api102Instrumentation
```

This option **clears diagnostic history** and needs explicit authorization.
It creates and removes its own media fixtures, temporarily adjusts rules only
for the test app, and restores settings/rules in `finally`. See
[API 102 device setup](api102-migration.md#opt-in-device-regression).

## Device verification, 2026-10-04

On Samsung SM-S9280 / Android 16 (API 36), the synthetic Room/SQLite probe passed
all stages. Its oversized v3 fixture shrank from 101,834,752 to 6,565,888 bytes;
maximum observed WAL during legacy cleanup was 671,592 bytes. Restart preserved
exactly 2,000 metadata summaries without duplicating imports. Pinned readers
paused writes and releasing the read snapshot resumed them. Concurrent writes,
disable, stale-ticket invalidation and disabled restart also passed.
