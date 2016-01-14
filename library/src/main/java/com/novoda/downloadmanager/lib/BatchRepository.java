package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.database.Cursor;
import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.List;

class BatchRepository {

    private final BatchStatusService batchStatusService;
    private final BatchStartingService batchStartingService;
    private final BatchRetrievalService batchRetrievalService;
    private final BatchDeletionService batchDeletionService;

    BatchRepository(ContentResolver resolver, DownloadDeleter downloadDeleter, DownloadsUriProvider downloadsUriProvider, SystemFacade systemFacade) {
        this.batchStatusService = new BatchStatusService(resolver, downloadsUriProvider, systemFacade);
        this.batchStartingService = new BatchStartingService(resolver, downloadsUriProvider);
        this.batchRetrievalService = new BatchRetrievalService(resolver, downloadsUriProvider);
        this.batchDeletionService = new BatchDeletionService(downloadDeleter, resolver, downloadsUriProvider);
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

    public void deleteMarkedBatchesFor(Collection<FileDownloadInfo> downloads) {
        batchDeletionService.deleteMarkedBatchesFor(downloads);
    }

}
