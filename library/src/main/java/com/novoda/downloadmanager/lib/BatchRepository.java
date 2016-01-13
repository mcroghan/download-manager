package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.novoda.downloadmanager.notifications.NotificationVisibility;
import com.novoda.notils.string.QueryUtils;
import com.novoda.notils.string.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class BatchRepository {

    private static final String[] PROJECT_BATCH_ID = {DownloadContract.Batches._ID};
    private static final String WHERE_DELETED_VALUE_IS = DownloadContract.Batches.COLUMN_DELETED + " = ?";
    private static final String[] MARKED_FOR_DELETION = {"1"};

    private final ContentResolver resolver;
    private final DownloadDeleter downloadDeleter;
    private final DownloadsUriProvider downloadsUriProvider;
    private final BatchStatusService batchStatusService;
    private final BatchStartingService batchStartingService;

    BatchRepository(ContentResolver resolver, DownloadDeleter downloadDeleter, DownloadsUriProvider downloadsUriProvider, SystemFacade systemFacade) {
        this.resolver = resolver;
        this.downloadDeleter = downloadDeleter;
        this.downloadsUriProvider = downloadsUriProvider;
        this.batchStatusService = new BatchStatusService(resolver, downloadsUriProvider, systemFacade);
        batchStartingService = new BatchStartingService(resolver, downloadsUriProvider);
    }

    void updateBatchStatus(long batchId, int status) {
        batchStatusService.updateBatchStatus(batchId, status);
    }

    int getBatchStatus(long batchId) {
        return batchStatusService.getBatchStatus(batchId);
    }

    int calculateBatchStatus(long batchId) {
        return batchStatusService.calculateBatchStatusFromDownloads(batchId);
    }

    public void setBatchItemsCancelled(long batchId) {
        batchStatusService.setBatchItemsCancelled(batchId);
    }

    public void cancelBatch(long batchId) {
        batchStatusService.cancelBatch(batchId);
    }

    public void setBatchItemsFailed(long batchId, long excludedDownloadId) {
        batchStatusService.setBatchItemsFailed(batchId, excludedDownloadId);
    }

    /**
     * @return Number of rows updated
     */
    int updateBatchesToPendingStatus(@NonNull List<String> batchIds) {
        return batchStatusService.updateBatchToPendingStatus(batchIds);
    }

    boolean isBatchStartingForTheFirstTime(long batchId) {
        return batchStartingService.isBatchStartingForTheFirstTime(batchId);
    }

    public void markBatchAsStarted(long batchId) {
        batchStartingService.markMatchAsStarted(batchId);
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
        long id = Cursors.getLong(cursor, DownloadContract.Batches._ID);
        String title = Cursors.getString(cursor, DownloadContract.Batches.COLUMN_TITLE);
        String description = Cursors.getString(cursor, DownloadContract.Batches.COLUMN_DESCRIPTION);
        String bigPictureUrl = Cursors.getString(cursor, DownloadContract.Batches.COLUMN_BIG_PICTURE);
        int status = Cursors.getInt(cursor, DownloadContract.Batches.COLUMN_STATUS);
        @NotificationVisibility.Value int visibility = Cursors.getInt(cursor, DownloadContract.Batches.COLUMN_VISIBILITY);
        String extraData = Cursors.getString(cursor, DownloadContract.Batches.COLUMN_EXTRA_DATA);
        long totalSizeBytes = Cursors.getLong(cursor, DownloadContract.BatchesWithSizes.COLUMN_TOTAL_BYTES);
        long currentSizeBytes = Cursors.getLong(cursor, DownloadContract.BatchesWithSizes.COLUMN_CURRENT_BYTES);
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

    public Cursor retrieveFor(BatchQuery query) {
        return resolver.query(downloadsUriProvider.getBatchesUri(), null, query.getSelection(), query.getSelectionArguments(), query.getSortOrder());
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

}
