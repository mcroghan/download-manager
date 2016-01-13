package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.database.Cursor;
import android.support.annotation.NonNull;

import com.novoda.notils.string.QueryUtils;
import com.novoda.notils.string.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
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
    private final BatchRetrievalService batchRetrievalService;

    BatchRepository(ContentResolver resolver, DownloadDeleter downloadDeleter, DownloadsUriProvider downloadsUriProvider, SystemFacade systemFacade) {
        this.resolver = resolver;
        this.downloadDeleter = downloadDeleter;
        this.downloadsUriProvider = downloadsUriProvider;
        this.batchStatusService = new BatchStatusService(resolver, downloadsUriProvider, systemFacade);
        this.batchStartingService = new BatchStartingService(resolver, downloadsUriProvider);
        this.batchRetrievalService = new BatchRetrievalService(resolver, downloadsUriProvider);
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

    public List<DownloadBatch> retrieveBatchesFor(Collection<FileDownloadInfo> downloads) {
        return batchRetrievalService.retrieveBatchesFor(downloads);
    }

    public DownloadBatch retrieveBatchFor(FileDownloadInfo download) {
        return batchRetrievalService.retrieveBatchFor(download);
    }

    public Cursor retrieveFor(BatchQuery query) {
        return batchRetrievalService.retrieveFor(query);
    }

    private Cursor queryBatches(String[] projection, String selection, String[] selectionArgs) {
        return batchRetrievalService.queryBatches(projection, selection, selectionArgs);
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

}
