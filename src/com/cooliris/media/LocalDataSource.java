package com.cooliris.media;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;

import com.cooliris.cache.CacheService;

/**只看到122行就可以了*/
public class LocalDataSource implements DataSource {

    public static final String URI_ALL_MEDIA = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString();
    public static final DiskCache sThumbnailCache = new DiskCache("local-image-thumbs");

    private final DiskCache mDiskCache;
    private Context mContext;

    public LocalDataSource(final Context context) {
        mContext = context;
        // 在nexus4以及我的手机上,是符合MediaStore.Images.Media.EXTERNAL_CONTENT_URI这个要求的，则是sThumbnailCache
        mDiskCache = sThumbnailCache;
    }

    @Override
    public DiskCache getThumbnailCache() {
        return mDiskCache;
    }

    @Override
    public void loadItemsForSet(MediaFeed feed, MediaSet parentSet, int rangeStart, int rangeEnd) {
        // 这里删除了大把代码啊..没想到那些东西干啥的
        CacheService.loadMediaItemsIntoMediaFeed(mContext, feed, parentSet, rangeStart, rangeEnd);
    }

    @Override
    public void loadMediaSets(final MediaFeed feed) {
        //这么一大段代码，在开机就只走了这句话..
        CacheService.loadMediaSets(mContext, feed, this, true);
    }

    /**
     * 这是固定的两个URI,说明LocalDataSource的数据来源是固定的，写死的
     */
    @Override
    public String[] getDatabaseUris() {
        return new String[] { Images.Media.EXTERNAL_CONTENT_URI.toString() };
    }

    /** 这里是真正沿着数据刷新内容的地方?用uri,但我想知道他妈的databaseUris 在哪里用到了？ */
    @Override
    public void refresh(final MediaFeed feed, final String[] databaseUris) {
        // We check to see what has changed.
        long[] ids = CacheService.computeDirtySets(mContext);
        int numDirtySets = ids.length;
        // 第一次这里是19,这是个什么东西呢？
        Log.i("ertewu", "LocalDataSource numDirtySet is:" + numDirtySets);
        for (int i = 0; i < numDirtySets; ++i) {
            long setId = ids[i];
            if (feed.getMediaSet(setId) != null) {
                MediaSet newSet = feed.replaceMediaSet(setId, this);
                newSet.generateTitle(true);
            } else {
                MediaSet mediaSet = feed.addMediaSet(setId, this);
                mediaSet.generateTitle(true);
            }
        }
    }

    /**
     * Matches code in MediaProvider.computeBucketValues. Should be a common
     * function.
     */
    public static int getBucketId(String path) {
        return (path.toLowerCase().hashCode());
    }


    /****************没用的函数放在这...*****************************/

    @Override
    public boolean performOperation(int operation, ArrayList<MediaBucket> mediaBuckets, Object data) {
        return true;
    }

    private void rotateItem(final MediaItem item, float angleToRotate) {

    }

    @Override
    public void shutdown() {

    }

    /**这个目前开机用不着的，就先得了吧*********/
    public static MediaItem createMediaItemFromUri(Context context, Uri target) {
        MediaItem item = null;
        long id = ContentUris.parseId(target);
        ContentResolver cr = context.getContentResolver();
        String whereClause = Images.ImageColumns._ID + "=" + Long.toString(id);
        try {

            final Uri uri = Images.Media.EXTERNAL_CONTENT_URI;
            final String[] projection = CacheService.PROJECTION_IMAGES;
            Cursor cursor = cr.query(uri, projection, whereClause, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    item = new MediaItem();
                    CacheService.populateMediaItemFromCursor(item, cr, cursor, uri.toString() + "/");
                    item.mId = id;
                }
                cursor.close();
                cursor = null;
            }
        } catch (Exception e) {
            // If the database operation failed for any reason.
        }
        return item;
    }


    /****************这两个东西无太关紧要，却放在前边挡的视线..***********/
    public static final String CAMERA_STRING = "Camera";
    public static final String DOWNLOAD_STRING = "download";
    // 这个是我的相机里的照片的文件夹
    public static final String CAMERA_BUCKET_NAME = Environment.getExternalStorageDirectory().toString() + "/DCIM/"
            + CAMERA_STRING;
    // 这是我sdcard/download文件夹
    public static final String DOWNLOAD_BUCKET_NAME = Environment.getExternalStorageDirectory().toString() + "/"
            + DOWNLOAD_STRING;
    public static final int CAMERA_BUCKET_ID = getBucketId(CAMERA_BUCKET_NAME);
    public static final int DOWNLOAD_BUCKET_ID = getBucketId(DOWNLOAD_BUCKET_NAME);

}
