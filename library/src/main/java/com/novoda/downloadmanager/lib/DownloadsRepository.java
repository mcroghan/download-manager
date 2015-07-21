package com.novoda.downloadmanager.lib;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import com.novoda.notils.string.StringUtils;

import java.util.ArrayList;
import java.util.List;

class DownloadsRepository {

    private final ContentResolver contentResolver;
    private final DownloadInfoCreator downloadInfoCreator;
    private final DownloadsUriProvider downloadsUriProvider;

    public DownloadsRepository(ContentResolver contentResolver, DownloadInfoCreator downloadInfoCreator, DownloadsUriProvider downloadsUriProvider) {
        this.contentResolver = contentResolver;
        this.downloadInfoCreator = downloadInfoCreator;
        this.downloadsUriProvider = downloadsUriProvider;
    }

    public List<FileDownloadInfo> getAllDownloads() {
        Cursor downloadsCursor = contentResolver.query(
                downloadsUriProvider.getAllDownloadsUri(),
                null,
                null,
                null,
                DownloadContract.Batches._ID + " ASC");

        try {
            List<FileDownloadInfo> downloads = new ArrayList<>();
            FileDownloadInfo.Reader reader = new FileDownloadInfo.Reader(contentResolver, downloadsCursor);

            while (downloadsCursor.moveToNext()) {
                downloads.add(downloadInfoCreator.create(reader));
            }

            return downloads;
        } finally {
            downloadsCursor.close();
        }
    }

    public FileDownloadInfo getDownloadFor(long id) {
        Uri uri = ContentUris.withAppendedId(downloadsUriProvider.getAllDownloadsUri(), id);
        Cursor downloadsCursor = contentResolver.query(uri, null, null, null, null);
        try {
            downloadsCursor.moveToFirst();
            FileDownloadInfo.Reader reader = new FileDownloadInfo.Reader(contentResolver, downloadsCursor);
            return downloadInfoCreator.create(reader);
        } finally {
            downloadsCursor.close();
        }
    }

    public FileDownloadInfo.ControlStatus getDownloadInfoControlStatusFor(long id) {
        FileDownloadInfo.ControlStatus.Reader reader = new FileDownloadInfo.ControlStatus.Reader(contentResolver, downloadsUriProvider);
        return downloadInfoCreator.create(reader, id);
    }

    public List<Long> getAllSubmittedIds() {
        String[] projection = {DownloadContract.Downloads._ID};
        String selection = DownloadContract.Downloads.COLUMN_STATUS + " = ?";
        String[] selectionArgs = {String.valueOf(DownloadStatus.SUBMITTED)};

        Cursor downloadsCursor = contentResolver.query(
                downloadsUriProvider.getAllDownloadsUri(),
                projection,
                selection,
                selectionArgs,
                DownloadContract.Batches._ID + " ASC");

        try {
            List<Long> submittedIds = new ArrayList<>(downloadsCursor.getCount());

            while (downloadsCursor.moveToNext()) {
                submittedIds.add(downloadsCursor.getLong(0));
            }

            return submittedIds;
        } finally {
            downloadsCursor.close();
        }
    }

    public void update(ContentResolver contentResolver, List<Long> downloadIds, int status) {
        ContentValues values = new ContentValues(1);
        values.put(DownloadContract.Downloads.COLUMN_STATUS, status);

        String where = DownloadContract.Downloads._ID + " IN (?)";
        String selection = StringUtils.join(downloadIds, ", ");
        String[] selectionArgs = {selection};
        contentResolver.update(downloadsUriProvider.getAllDownloadsUri(), values, where, selectionArgs);
    }

    interface DownloadInfoCreator {
        FileDownloadInfo create(FileDownloadInfo.Reader reader);

        FileDownloadInfo.ControlStatus create(FileDownloadInfo.ControlStatus.Reader reader, long id);
    }

}
