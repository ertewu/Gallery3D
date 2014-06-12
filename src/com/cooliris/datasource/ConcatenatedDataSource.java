package com.cooliris.datasource;

import java.util.ArrayList;

import android.util.Log;

import com.cooliris.cache.obj.DiskCache;
import com.cooliris.media.a_media.MediaBucket;
import com.cooliris.media.a_media.MediaFeed;
import com.cooliris.media.a_media.MediaSet;
import com.cooliris.media.utils.ArrayUtils;


public final class ConcatenatedDataSource implements DataSource {
    private static final String TAG = "ConcatenatedDataSource";
    private final DataSource mFirst;

    public ConcatenatedDataSource(DataSource first) {
        mFirst = first;
    }

    @Override
    public void loadMediaSets(final MediaFeed feed) {
        mFirst.loadMediaSets(feed);
    }

    @Override
    public void loadItemsForSet(final MediaFeed feed, final MediaSet parentSet, int rangeStart, int rangeEnd) {
        if (parentSet != null) {
            DataSource dataSource = parentSet.mDataSource;
            if (dataSource != null) {
                dataSource.loadItemsForSet(feed, parentSet, rangeStart, rangeEnd);
            } else {
                Log.e(TAG, "MediaSet was not added to the feed");
            }
        }
    }

    @Override
    public boolean performOperation(int operation, final ArrayList<MediaBucket> mediaBuckets, Object data) {
        ArrayList<MediaBucket> singleBucket = new ArrayList<MediaBucket>(1);
        singleBucket.add(null);
        int numBuckets = mediaBuckets.size();
        boolean retVal = true;
        for (int i = 0; i < numBuckets; ++i) { // CR: iterator for
            MediaBucket bucket = mediaBuckets.get(i);
            MediaSet set = bucket.mediaSet;
            if (set != null) {
                DataSource dataSource = set.mDataSource;
                if (dataSource != null) {
                    singleBucket.set(0, bucket);
                    retVal &= dataSource.performOperation(operation, singleBucket, data);
                } else {
                    Log.e(TAG, "MediaSet was not added to the feed");
                }
            }
        }
        return retVal;
    }

    @Override
    public DiskCache getThumbnailCache() {
        throw new UnsupportedOperationException("ConcatenatedDataSource should not create MediaItems");
    }

    @Override
    public void shutdown() {
        mFirst.shutdown();
    }

    /**
     * 第一次load应用时,databaseUris 里有两个值
     * [content://media/external/images/media, content://media/external/video/media]
     */
    @Override
    public void refresh(final MediaFeed feed, final String[] databaseUris) {
        mFirst.refresh(feed, databaseUris);
    }

    @Override
    public String[] getDatabaseUris() {
        String[] first = mFirst.getDatabaseUris();
        // We concatenate
        return ArrayUtils.addAll(first, null);
    }
}
