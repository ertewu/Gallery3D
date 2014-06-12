package com.cooliris.cache;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.cooliris.datasource.LocalDataSource;
import com.cooliris.media.utils.Utils;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        Log.i(TAG, "Got intent with action " + action);
        if (action.equals(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)) {
            final Uri fileUri = intent.getData();
            final long bucketId = Utils.getBucketIdFromUri(
                    context.getContentResolver(), fileUri);
            if (!CacheService.isPresentInCache(bucketId)) {
                CacheService.markDirty();
            }
        } else if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
            LocalDataSource.sThumbnailCache.close();
            LocalDataSource.sThumbnailCacheVideo.close();
            CacheService.sAlbumCache.close();
            CacheService.sMetaAlbumCache.close();
            CacheService.sSkipThumbnailIds.flush();
        } else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
            ;
        } else if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            ;
        }
    }
}
