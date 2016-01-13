package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.util.SparseArrayCompat;

import com.novoda.downloadmanager.notifications.NotificationVisibility;
import com.novoda.notils.string.QueryUtils;
import com.novoda.notils.string.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class BatchRepository {

    private static final List<Integer> PRIORITISED_STATUSES = Arrays.asList(
            DownloadStatus.CANCELED,
            DownloadStatus.PAUSING,
            DownloadStatus.PAUSED_BY_APP,
            DownloadStatus.RUNNING,
            DownloadStatus.DELETING,

            // Paused statuses
            DownloadStatus.QUEUED_DUE_CLIENT_RESTRICTIONS,
            DownloadStatus.WAITING_TO_RETRY,
            DownloadStatus.WAITING_FOR_NETWORK,
            DownloadStatus.QUEUED_FOR_WIFI,

            DownloadStatus.SUBMITTED,
            DownloadStatus.PENDING,
            DownloadStatus.SUCCESS
    );

    private static final List<Integer> STATUSES_EXCEPT_SUCCESS_SUBMITTED = Arrays.asList(
            DownloadStatus.CANCELED,
            DownloadStatus.PAUSED_BY_APP,
            DownloadStatus.RUNNING,
            DownloadStatus.DELETING,

            // Paused statuses
            DownloadStatus.QUEUED_DUE_CLIENT_RESTRICTIONS,
            DownloadStatus.WAITING_TO_RETRY,
            DownloadStatus.WAITING_FOR_NETWORK,
            DownloadStatus.QUEUED_FOR_WIFI,

            DownloadStatus.PENDING
    );

    private static final int PRIORITISED_STATUSES_SIZE = PRIORITISED_STATUSES.size();

    private static final String[] PROJECT_BATCH_ID = {DownloadContract.Batches._ID};
    private static final String WHERE_DELETED_VALUE_IS = DownloadContract.Batches.COLUMN_DELETED + " = ?";
    private static final String[] MARKED_FOR_DELETION = {"1"};

    private final ContentResolver resolver;
    private final DownloadDeleter downloadDeleter;
    private final DownloadsUriProvider downloadsUriProvider;
    private final SystemFacade systemFacade;
    private final StatusesCount statusesCount = new StatusesCount();

    BatchRepository(ContentResolver resolver, DownloadDeleter downloadDeleter, DownloadsUriProvider downloadsUriProvider, SystemFacade systemFacade) {
        this.resolver = resolver;
        this.downloadDeleter = downloadDeleter;
        this.downloadsUriProvider = downloadsUriProvider;
        this.systemFacade = systemFacade;
    }

    void updateBatchStatus(long batchId, int status) {
        ContentValues values = new ContentValues();
        values.put(DownloadContract.Batches.COLUMN_STATUS, status);
        values.put(DownloadContract.Batches.COLUMN_LAST_MODIFICATION, systemFacade.currentTimeMillis());
        resolver.update(downloadsUriProvider.getBatchesUri(), values, DownloadContract.Batches._ID + " = ?", new String[]{String.valueOf(batchId)});
    }

    int getBatchStatus(long batchId) {
        String[] projection = {DownloadContract.Batches.COLUMN_STATUS};
        String selection = DownloadContract.Batches._ID + " = ?";
        String[] selectionArgs = {String.valueOf(batchId)};

        Cursor cursor = queryBatches(projection, selection, selectionArgs);

        try {
            cursor.moveToFirst();
            return Db.getInt(cursor, DownloadContract.Batches.COLUMN_STATUS);
        } finally {
            safeCloseCursor(cursor);
        }
    }

    int calculateBatchStatus(long batchId) {
        Cursor cursor = null;
        statusesCount.clear();
        try {
            String[] projection = {DownloadContract.Downloads.COLUMN_STATUS};
            String[] selectionArgs = {String.valueOf(batchId)};
            String selection = DownloadContract.Downloads.COLUMN_BATCH_ID + " = ?";
            cursor = queryDownloads(projection, selectionArgs, selection);

            while (cursor.moveToNext()) {
                int statusCode = Db.getInt(cursor, DownloadContract.Downloads.COLUMN_STATUS);

                if (DownloadStatus.isError(statusCode)) {
                    return statusCode;
                }

                statusesCount.incrementCountFor(statusCode);
            }
        } finally {
            safeCloseCursor(cursor);
        }

        if (onlyCompleteAndSubmittedIn(statusesCount)) {
            return DownloadStatus.RUNNING;
        }

        for (int status : PRIORITISED_STATUSES) {
            if (statusesCount.hasCountFor(status)) {
                return status;
            }
        }

        return DownloadStatus.UNKNOWN_ERROR;
    }

    private boolean onlyCompleteAndSubmittedIn(StatusesCount statusesCount) {
        boolean hasCompleteItems = statusesCount.hasCountFor(DownloadStatus.SUCCESS);
        boolean hasSubmittedItems = statusesCount.hasCountFor(DownloadStatus.SUBMITTED);
        boolean hasNotOtherItems = statusesCount.hasNoItemsWithStatuses(STATUSES_EXCEPT_SUCCESS_SUBMITTED);

        return hasCompleteItems && hasSubmittedItems && hasNotOtherItems;
    }

    boolean isBatchStartingForTheFirstTime(long batchId) {
        int hasStarted = DownloadContract.Batches.BATCH_HAS_NOT_STARTED;

        String[] projection = {DownloadContract.Batches.COLUMN_HAS_STARTED};
        String[] selectionArgs = {String.valueOf(batchId)};
        String selection = DownloadContract.Batches._ID + " = ?";

        Cursor cursor = queryBatches(projection, selection, selectionArgs);

        try {
            if (cursor.moveToFirst()) {
                hasStarted = Db.getInt(cursor, DownloadContract.Batches.COLUMN_HAS_STARTED);
            }
        } finally {
            safeCloseCursor(cursor);
        }

        return hasStarted != DownloadContract.Batches.BATCH_HAS_STARTED;
    }

    public DownloadBatch retrieveBatchFor(FileDownloadInfo download) {
        Collection<FileDownloadInfo> downloads = Collections.singletonList(download);
        List<DownloadBatch> batches = retrieveBatchesFor(downloads);

        for (DownloadBatch batch : batches) {
            if (batch.getBatchId() == download.getBatchId()) {
                return batch;
            }
        }

        return DownloadBatch.DELETED;
    }

    public List<DownloadBatch> retrieveBatchesFor(Collection<FileDownloadInfo> downloads) {
        Cursor cursor = queryBatches(null, null, null);
        try {
            return downloadBatchesFrom(downloads, cursor);
        } finally {
            safeCloseCursor(cursor);
        }
    }

    private List<DownloadBatch> downloadBatchesFrom(Collection<FileDownloadInfo> downloads, Cursor batchesCursor) {
        List<DownloadBatch> batches = new ArrayList<>(batchesCursor.getCount());
        while (batchesCursor.moveToNext()) {
            batches.add(downloadBatchFrom(downloads, batchesCursor));
        }
        return batches;
    }

    private DownloadBatch downloadBatchFrom(Collection<FileDownloadInfo> downloads, Cursor cursor) {
        long id = Db.getLong(cursor, DownloadContract.Batches._ID);
        String title = Db.getString(cursor, DownloadContract.Batches.COLUMN_TITLE);
        String description = Db.getString(cursor, DownloadContract.Batches.COLUMN_DESCRIPTION);
        String bigPictureUrl = Db.getString(cursor, DownloadContract.Batches.COLUMN_BIG_PICTURE);
        int status = Db.getInt(cursor, DownloadContract.Batches.COLUMN_STATUS);
        @NotificationVisibility.Value int visibility = Db.getInt(cursor, DownloadContract.Batches.COLUMN_VISIBILITY);
        String extraData = Db.getString(cursor, DownloadContract.Batches.COLUMN_EXTRA_DATA);
        long totalSizeBytes = Db.getLong(cursor, DownloadContract.BatchesWithSizes.COLUMN_TOTAL_BYTES);
        long currentSizeBytes = Db.getLong(cursor, DownloadContract.BatchesWithSizes.COLUMN_CURRENT_BYTES);
        BatchInfo batchInfo = new BatchInfo(title, description, bigPictureUrl, visibility, extraData);

        List<FileDownloadInfo> batchDownloads = new ArrayList<>(1);
        for (FileDownloadInfo fileDownloadInfo : downloads) {
            if (fileDownloadInfo.getBatchId() == id) {
                batchDownloads.add(fileDownloadInfo);
            }
        }

        return new DownloadBatch(id, batchInfo, batchDownloads, status, totalSizeBytes, currentSizeBytes);
    }

    public void deleteMarkedBatchesFor(Collection<FileDownloadInfo> downloads) {
        deleteBatchesForIds(batchIdsToDelete(), downloads);
    }

    private List<Long> batchIdsToDelete() {
        Cursor batchesCursor = queryBatches(PROJECT_BATCH_ID, WHERE_DELETED_VALUE_IS, MARKED_FOR_DELETION);
        List<Long> batchIdsToDelete = new ArrayList<>();
        while (batchesCursor.moveToNext()) {
            long id = batchesCursor.getLong(0);
            batchIdsToDelete.add(id);
        }
        batchesCursor.close();
        return batchIdsToDelete;
    }

    private void deleteBatchesForIds(List<Long> batchIdsToDelete, Collection<FileDownloadInfo> downloads) {
        if (batchIdsToDelete.isEmpty()) {
            return;
        }

        for (FileDownloadInfo download : downloads) {
            if (batchIdsToDelete.contains(download.getBatchId())) {
                downloadDeleter.deleteFileAndDatabaseRow(download);
            }
        }

        String selectionPlaceholders = QueryUtils.createSelectionPlaceholdersOfSize(batchIdsToDelete.size());
        String where = DownloadContract.Batches._ID + " IN (" + selectionPlaceholders + ")";
        String[] selectionArgs = StringUtils.toStringArray(batchIdsToDelete.toArray());
        resolver.delete(downloadsUriProvider.getBatchesUri(), where, selectionArgs);
    }

    public Cursor retrieveFor(BatchQuery query) {
        return resolver.query(downloadsUriProvider.getBatchesUri(), null, query.getSelection(), query.getSelectionArguments(), query.getSortOrder());
    }

    public void setBatchItemsCancelled(long batchId) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Downloads.COLUMN_STATUS, DownloadStatus.CANCELED);
        resolver.update(downloadsUriProvider.getAllDownloadsUri(), values, DownloadContract.Downloads.COLUMN_BATCH_ID + " = ?", new String[]{String.valueOf(batchId)});
    }

    public void cancelBatch(long batchId) {
        ContentValues downloadValues = new ContentValues(1);
        downloadValues.put(DownloadContract.Downloads.COLUMN_STATUS, DownloadStatus.CANCELED);
        resolver.update(
                downloadsUriProvider.getAllDownloadsUri(),
                downloadValues,
                DownloadContract.Downloads.COLUMN_BATCH_ID + " = ?",
                new String[]{String.valueOf(batchId)}
        );
        ContentValues batchValues = new ContentValues(1);
        batchValues.put(DownloadContract.Batches.COLUMN_STATUS, DownloadStatus.CANCELED);
        resolver.update(
                ContentUris.withAppendedId(downloadsUriProvider.getBatchesUri(), batchId),
                batchValues,
                null,
                null
        );
    }

    public void setBatchItemsFailed(long batchId, long downloadId) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Downloads.COLUMN_STATUS, DownloadStatus.BATCH_FAILED);
        resolver.update(
                downloadsUriProvider.getAllDownloadsUri(),
                values,
                DownloadContract.Downloads.COLUMN_BATCH_ID + " = ? AND " + DownloadContract.Downloads._ID + " <> ? ",
                new String[]{String.valueOf(batchId), String.valueOf(downloadId)}
        );
    }

    public void markBatchAsStarted(long batchId) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Batches.COLUMN_HAS_STARTED, DownloadContract.Batches.BATCH_HAS_STARTED);
        resolver.update(
                ContentUris.withAppendedId(downloadsUriProvider.getBatchesUri(), batchId),
                values,
                null,
                null
        );
    }

    /**
     * @return Number of rows updated
     */
    int updateBatchToPendingStatus(@NonNull List<String> batchIdsToBeUnlocked) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Batches.COLUMN_STATUS, DownloadStatus.PENDING);

        int batchIdsSize = batchIdsToBeUnlocked.size();
        String[] whereArray = new String[batchIdsSize];
        String[] selectionArgs = new String[batchIdsSize];

        for (int i = 0; i < batchIdsSize; i++) {
            whereArray[i] = DownloadContract.Batches._ID + " = ?";
            selectionArgs[i] = batchIdsToBeUnlocked.get(i);
        }

        String where = StringUtils.join(Arrays.asList(whereArray), " or ");

        return resolver.update(downloadsUriProvider.getBatchesUri(), values, where, selectionArgs);
    }

    private void safeCloseCursor(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    private Cursor queryBatches(String[] projection, String selection, String[] selectionArgs) {
        return resolver.query(
                downloadsUriProvider.getBatchesUri(),
                projection,
                selection,
                selectionArgs,
                null
        );
    }

    private Cursor queryDownloads(String[] projection, String[] selectionArgs, String selection) {
        return resolver.query(
                downloadsUriProvider.getAllDownloadsUri(),
                projection,
                selection,
                selectionArgs,
                null
        );
    }

    private static class Db {
        private Db() {

        }

        public static int getInt(Cursor c, String column) {
            return c.getInt(columnIndexFor(c, column));
        }

        public static long getLong(Cursor c, String column) {
            return c.getLong(columnIndexFor(c, column));
        }

        public static String getString(Cursor c, String column) {
            return c.getString(columnIndexFor(c, column));
        }

        private static int columnIndexFor(Cursor c, String column) {
            return c.getColumnIndexOrThrow(column);
        }
    }

    private static class StatusesCount {

        private final SparseArrayCompat<Integer> statusCounts = new SparseArrayCompat<>(PRIORITISED_STATUSES_SIZE);

        public boolean hasNoItemsWithStatuses(List<Integer> excludedStatuses) {
            for (int status : excludedStatuses) {
                if (hasCountFor(status)) {
                    return false;
                }
            }

            return true;
        }

        public boolean hasCountFor(int statusCode) {
            return statusCounts.get(statusCode, 0) > 0;
        }

        public void incrementCountFor(int statusCode) {
            int currentStatusCount = statusCounts.get(statusCode, 0);
            statusCounts.put(statusCode, currentStatusCount + 1);
        }

        public void clear() {
            statusCounts.clear();
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder("{");

            int size = statusCounts.size();
            for (int i = 0; i < size; i++) {
                stringBuilder.append("[status: ").append(statusCounts.keyAt(i)).append(", count: ").append(statusCounts.valueAt(i)).append("]");
            }

            return stringBuilder.append("}").toString();
        }
    }

}
