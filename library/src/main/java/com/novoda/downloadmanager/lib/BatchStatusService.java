package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.util.SparseArrayCompat;

import com.novoda.notils.string.StringUtils;

import java.util.Arrays;
import java.util.List;

class BatchStatusService {

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

    private final ContentResolver resolver;
    private final SystemFacade systemFacade;
    private final Uri batchesUri;
    private final Uri downloadsUri;
    private final StatusesCount statusesCount = new StatusesCount();

    public BatchStatusService(ContentResolver resolver, DownloadsUriProvider downloadsUriProvider, SystemFacade systemFacade) {
        this.resolver = resolver;
        this.systemFacade = systemFacade;
        this.batchesUri = downloadsUriProvider.getBatchesUri();
        this.downloadsUri = downloadsUriProvider.getAllDownloadsUri();
    }

    public int getBatchStatus(long batchId) {
        String[] projection = {DownloadContract.Batches.COLUMN_STATUS};
        String selection = DownloadContract.Batches._ID + " = ?";
        String[] selectionArgs = {String.valueOf(batchId)};

        Cursor cursor = resolver.query(batchesUri, projection, selection, selectionArgs, null);

        if (cursor == null) {
            throw new RuntimeException("Failed to query batches for batchId = " + batchId);
        }

        try {
            cursor.moveToFirst();
            return Cursors.getInt(cursor, DownloadContract.Batches.COLUMN_STATUS);
        } finally {
            cursor.close();
        }
    }

    public int calculateBatchStatusFromDownloads(long batchId) {
        String[] projection = {DownloadContract.Downloads.COLUMN_STATUS};
        String[] selectionArgs = {String.valueOf(batchId)};
        String selection = DownloadContract.Downloads.COLUMN_BATCH_ID + " = ?";
        Cursor cursor = resolver.query(downloadsUri, projection, selection, selectionArgs, null);

        if (cursor == null) {
            throw new RuntimeException("Failed to query downloads for batchId = " + batchId);
        }

        statusesCount.clear();

        try {

            while (cursor.moveToNext()) {
                int statusCode = Cursors.getInt(cursor, DownloadContract.Downloads.COLUMN_STATUS);

                if (DownloadStatus.isError(statusCode)) {
                    return statusCode;
                }

                statusesCount.incrementCountFor(statusCode);
            }
        } finally {
            cursor.close();
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

    public int updateBatchToPendingStatus(@NonNull List<String> batchIds) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Batches.COLUMN_STATUS, DownloadStatus.PENDING);

        int batchIdsSize = batchIds.size();
        String[] whereArray = new String[batchIdsSize];
        String[] selectionArgs = new String[batchIdsSize];

        for (int i = 0; i < batchIdsSize; i++) {
            whereArray[i] = DownloadContract.Batches._ID + " = ?";
            selectionArgs[i] = batchIds.get(i);
        }

        String where = StringUtils.join(Arrays.asList(whereArray), " or ");

        return resolver.update(downloadsUri, values, where, selectionArgs);

    }

    public void cancelBatch(long batchId) {
        setBatchItemsCancelled(batchId);
        updateBatchStatus(batchId, DownloadStatus.CANCELED);
    }

    public void updateBatchStatus(long batchId, int status) {
        ContentValues values = new ContentValues();
        values.put(DownloadContract.Batches.COLUMN_STATUS, status);
        values.put(DownloadContract.Batches.COLUMN_LAST_MODIFICATION, systemFacade.currentTimeMillis());
        resolver.update(batchesUri, values, DownloadContract.Batches._ID + " = ?", new String[]{String.valueOf(batchId)});
    }

    public void setBatchItemsCancelled(long batchId) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Downloads.COLUMN_STATUS, DownloadStatus.CANCELED);
        resolver.update(downloadsUri, values, DownloadContract.Downloads.COLUMN_BATCH_ID + " = ?", new String[]{String.valueOf(batchId)});
    }

    public void setBatchItemsFailed(long batchId, long excludedDownloadId) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Downloads.COLUMN_STATUS, DownloadStatus.BATCH_FAILED);
        String selection = DownloadContract.Downloads.COLUMN_BATCH_ID + " = ? AND " + DownloadContract.Downloads._ID + " <> ? ";
        String[] selectionArgs = {String.valueOf(batchId), String.valueOf(excludedDownloadId)};
        resolver.update(downloadsUri, values, selection, selectionArgs);

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
