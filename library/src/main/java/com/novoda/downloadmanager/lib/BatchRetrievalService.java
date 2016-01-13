package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import com.novoda.downloadmanager.notifications.NotificationVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BatchRetrievalService {
    private final ContentResolver resolver;
    private final Uri batchesUri;

    public BatchRetrievalService(ContentResolver resolver, DownloadsUriProvider downloadsUriProvider) {
        this.resolver = resolver;
        this.batchesUri = downloadsUriProvider.getBatchesUri();
    }

    public List<DownloadBatch> retrieveBatchesFor(Collection<FileDownloadInfo> downloads) {
        Cursor cursor = resolver.query(batchesUri, null, null, null, null);

        if (cursor == null) {
            throw new RuntimeException("Failed to query for batches");
        }

        try {
            return marshallDownloadBatches(downloads, cursor);
        } finally {
            cursor.close();
        }
    }

    private List<DownloadBatch> marshallDownloadBatches(Collection<FileDownloadInfo> downloads, Cursor batchesCursor) {
        List<DownloadBatch> batches = new ArrayList<>(batchesCursor.getCount());
        while (batchesCursor.moveToNext()) {
            batches.add(marshallDownloadBatch(downloads, batchesCursor));
        }
        return batches;
    }

    public DownloadBatch retrieveBatchFor(FileDownloadInfo download) {
        String selection = DownloadContract.Batches._ID + " = ?";
        long batchId = download.getBatchId();
        String[] selectionArgs = {String.valueOf(batchId)};

        Cursor cursor = resolver.query(batchesUri, null, selection, selectionArgs, null);

        if (cursor == null) {
            throw new RuntimeException("Failed to query for batch with batchId = " + batchId);
        }

        try {
            if (cursor.moveToFirst()) {
                return marshallDownloadBatch(Collections.singletonList(download), cursor);
            } else {
                return DownloadBatch.DELETED;
            }
        } finally {
            cursor.close();
        }
    }

    private DownloadBatch marshallDownloadBatch(Collection<FileDownloadInfo> downloads, Cursor cursor) {
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

    public Cursor retrieveFor(BatchQuery query) {
        return resolver.query(batchesUri, null, query.getSelection(), query.getSelectionArguments(), query.getSortOrder());
    }

    public Cursor queryBatches(String[] projection, String selection, String[] selectionArgs) {
        return resolver.query(batchesUri, projection, selection, selectionArgs, null);
    }
}
