0.2.38
-----
- Support large files (with blocking factor of 20) in the tar resume logic.

0.2.37
-----
- Reverts okhttp version to 2.3.0

0.2.36
-----
- Adds an immediate notification for a new status PAUSING
- Updates okhttp version to 2.5.0

0.2.35
-----
- FIXES BUG - allows the download manager to insert the current bytes into the database for addCompletedDownloads

0.2.34
-----
- FIXES BUG - sets the current download size to the total size when adding completed downloads

0.2.33
-----
- FIXES BUG - download manager deletes everything inside the application shared getCacheDir()

0.2.32
-----
 - FIXES UPGRADE CRASH - previous release still contained the sql syntax error in the migration 

0.2.31
-----
 - Reverts 0.2.30 as this was actually unnecessary and just added complexity (and a bug upgrading 1 to 2 to 3)

0.2.30
-----
 - FIXES UPGRADE CRASH - another release for the people who had already experienced the crash

0.2.29
-----
 - FIXES UPGRADE CRASH - we had a missing space in our SQL statement

0.2.28
-----
 - Makes notifications uniques by setting different data on each of them #173
 - Use the 'HIDDEN' state when hiding a notification #172

0.2.27
-----
 - Uses FLAG_CANCEL_CURRENT instead of FLAG_UPDATE_CURRENT for the clicks #171

0.2.26
-----
 - Uses multiple actions for click events on notifications so they can be handled differently #170

0.2.25
-----
 - If user clicks on a failed download then we want to hide the notification forever #169

0.2.24
-----
 - Handle failed notifications so that they get dismissed and click events work #167

0.2.23
-----
- Adds a static text for downloading so that it can be overwritten #165

0.2.22
-----
- Restart stale downloads #163

0.2.21
-----
- Moves more notification code to package #164
- Adds more fields to the Download object and passes that to the customiser #162

0.2.20
-----
- Broadcast clicks on notifications for completed downloads #160
- Sends the right values for the status in the Notification click broadcasts #160

0.2.19
-----
- Broadcast clicks on notifications for cancelled downloads #159

0.2.18
-----
- Track and broacast when a batch starts for the first time #158

0.2.17
-----
- Add a broadcast for batch failure #157

0.2.16
-----
- Adds a method to exclude deleted batches from query #156

0.2.15
-----
- Add API to customise notifications #155

0.2.14
-----
- Adds the ability to add a completed batch to the download manager #152 
- Fixes "Download Cancelled" notifications returning after being dismissed #150

0.2.13
-------
- Add client checks along with pause and cancel checks and speed up calls #147


0.2.12
-------
- Provide restart mechanism #146


0.2.11
-------
- Replace ListView with RecyclerView in demos #140
- Adds missing status as a paused batch query filter #141

0.2.10
-------

- Prevent pause a non running task and prevent resume a non paused task #135
- Reducing control reader query method allocations #138
- Reducing transfer object creation #134
- Simplifies logging #137
- Fix deleteBatchesForIds when multiple selectionArgs #133
- Resume downloads after loosing signal #128
- Fix concurrent downloads and memory leaks #130
- Avoid smashing the database while transferring bytes #129

0.2.9
-------

- Tar truncation at download time #126

0.2.6
-------

- Allow tar updates #120

0.2.5
-------

- FileDownloadInfo setter removal #118
- Fix download goes to queued #116

0.2.4
-------

- Simplify query (pull out status and control) #111
- Better support for localization #112
- Add EXTRA_DATA column to Batches table #114
- Add LAST_MODIFIED column to Batches table #117

0.2.3
-------

- Batch sizes view #105
- Notify batch modified after all downloads #109

0.2.2
-------

- flag to allow any file resume #108
- fixes to the instrumentation test build #99

0.2.1
-------

- Adds the ability to listen to changes to status changes only #97

0.2.0
-------

- download checks against the current download #89
- simplified download info object #92
- fix for no icon being set for the notification #95
- adds ability to query for extra data (api breaking!) #96

0.1.9
-------

- Adds new DELETING status #94

0.1.8
-------

- Query for batches #79
- Fix current size for batches #87
- Adds an extra column so you can pass data through the DLM #88

0.1.7
-------

- improves sort for live items #86
- update tests and injection #83

0.1.6
-------

- fix pause and resume #82
- performance improvements around download checks #84

0.1.5
-------

- adds ability to sort queries by liveness #74
- calculate entire batch size #75

0.1.4
-------

- Code cleanup #63
- Limit client knowledge of internals #59
- Adds static analysis #72

0.1.3
-------

- Incorrect DownloadBatch passed to rule checker #71
- splits demos #69
- batch id query error #67
- batch deletion bug #62
- replaces data structures #60

0.1.2
-------

- Fix bug batch deletion

0.1.1
-------

- Adds download batch pausing and resuming
- Adds download batch deletion
- Adds current and total size to batches

0.1.0
-------

- Broadcast batch completion events #47
- Optimisation - Get full batch size in one query #49
- Optimisation - Removes unnecessary full loop #50

0.0.14
-------

Adds the CollatedDownloadInfo to the downloadrulecheck (means you can check the total size of the batch in the can download check!)

0.0.13
-------

BUG FIX - Queues all downloading tasks when the downloads cannot continue

0.0.12
-------

Now allows power users to override the default database filename.

0.0.9
-------

Now allows clients to have a say if a download should go ahead or not. See #29 You hook into this through your Application class.

0.0.8
-------

Enforces passing a Context via the DownloadManagerBuilder this allows Requests created later on to not need to pass Context as a parameter. (#28)
It will also allow us to make further refactorings behind the scenes now we haz your Context bwahaha.
THIS IS AN API BREAKING CHANGE FOR DownloadManagerBuilder, DownloadManager and Request (see https://thechive.files.wordpress.com/2013/01/gifs_201.gif)

0.0.7
-------

Adds the ability to configure the maximum concurrent downloads (see #23)
Improves performance of notification image fetching (#27)

0.0.6
-------

PERFORMANCE FIX - removes verbose logging by default, this stops extra queries and string concat's that caused unneccessary GC
You can now set verbose logging on using the new `DownloadManagerBuilder.withVerboseLogging()` it is off by default. (see #20)

0.0.5
-------

Removes unused constants & minimises our public API, now we can refactor internally more safely

0.0.4
-------

Prefixes all resources with `dl__` , fixes but where the `app_name` for some clients would be "DownloadManager"

0.0.3
-------

Exposes the `DownloadManager.CONTENT_URI` so clients can query however they want (for example Loaders)

0.0.2
-------

Allows selection of content provider authority using `manifestPlaceholders = [downloadAuthority: "${applicationId}"]` and class `com.novoda.downloadmanager.Authority`

0.0.1
-------

Initial release - do not use - you will have issues with conflicting content providers if you use in more than one app (and anyone elses apps)
