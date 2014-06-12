package com.cooliris.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.cooliris.app.App;
import com.cooliris.cache.obj.DiskCache;
import com.cooliris.datasource.LocalDataSource;
import com.cooliris.media.a_media.MediaItem;
import com.cooliris.media.utils.Utils;

public class CacheHelper {
    final static String TAG = CacheService.TAG;

    public static final String getCachePath(final String subFolderName) {
        return Environment.getExternalStorageDirectory() + "/Android/data/com.cooliris.media/cache/" + subFolderName;
    }

    public static final Locale getLocaleForAlbumCache() {
        final byte[] data = CacheService.sAlbumCache.get(CacheConstants.ALBUM_CACHE_LOCALE_INDEX, 0);
        if (data != null && data.length > 0) {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bis);
            try {
                String country = Utils.readUTF(dis);
                if (country == null)
                    country = "";
                String language = Utils.readUTF(dis);
                if (language == null)
                    language = "";
                String variant = Utils.readUTF(dis);
                if (variant == null)
                    variant = "";
                final Locale locale = new Locale(language, country, variant);
                dis.close();
                bis.close();
                return locale;
            } catch (IOException e) {
                // Could not read locale in cache.
                Log.i(TAG, "Error reading locale from cache.");
                return null;
            }
        }
        return null;
    }

    public static final void putLocaleForAlbumCache(final Locale locale) {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);
        try {
            Utils.writeUTF(dos, locale.getCountry());
            Utils.writeUTF(dos, locale.getLanguage());
            Utils.writeUTF(dos, locale.getVariant());
            dos.flush();
            bos.flush();
            final byte[] data = bos.toByteArray();
            CacheService.sAlbumCache.put(CacheConstants.ALBUM_CACHE_LOCALE_INDEX, data, 0);
            CacheService.sAlbumCache.flush();
            dos.close();
            bos.close();
        } catch (IOException e) {
            // Could not write locale to cache.
            Log.i(TAG, "Error writing locale to cache.");
        }
    }

    public static final void populateVideoItemFromCursor(final MediaItem item, final ContentResolver cr,
            final Cursor cursor, final String baseUri) {
        item.setMediaType(MediaItem.MEDIA_TYPE_VIDEO);
        populateMediaItemFromCursor(item, cr, cursor, baseUri);
    }

    public static final void populateMediaItemFromCursor(final MediaItem item, final ContentResolver cr,
            final Cursor cursor, final String baseUri) {
        item.mId = cursor.getLong(CacheConstants.MEDIA_ID_INDEX);
        item.mCaption = cursor.getString(CacheConstants.MEDIA_CAPTION_INDEX);
        item.mMimeType = cursor.getString(CacheConstants.MEDIA_MIME_TYPE_INDEX);
        item.mLatitude = cursor.getDouble(CacheConstants.MEDIA_LATITUDE_INDEX);
        item.mLongitude = cursor.getDouble(CacheConstants.MEDIA_LONGITUDE_INDEX);
        item.mDateTakenInMs = cursor.getLong(CacheConstants.MEDIA_DATE_TAKEN_INDEX);
        item.mDateAddedInSec = cursor.getLong(CacheConstants.MEDIA_DATE_ADDED_INDEX);
        item.mDateModifiedInSec = cursor.getLong(CacheConstants.MEDIA_DATE_MODIFIED_INDEX);
        if (item.mDateTakenInMs == item.mDateModifiedInSec) {
            item.mDateTakenInMs = item.mDateModifiedInSec * 1000;
        }
        item.mFilePath = cursor.getString(CacheConstants.MEDIA_DATA_INDEX);
        if (baseUri != null)
            item.mContentUri = baseUri + item.mId;
        final int itemMediaType = item.getMediaType();
        final int orientationDurationValue = cursor.getInt(CacheConstants.MEDIA_ORIENTATION_OR_DURATION_INDEX);
        if (itemMediaType == MediaItem.MEDIA_TYPE_IMAGE) {
            item.mRotation = orientationDurationValue;
        } else {
            item.mDurationInSec = orientationDurationValue;
        }
    }

    // Returns -1 if we failed to examine EXIF information or EXIF parsing
    // failed.
    public static final long fetchDateTaken(final MediaItem item) {
        if (!item.isDateTakenValid() && !item.mTriedRetrievingExifDateTaken
                && (item.mFilePath.endsWith(".jpg") || item.mFilePath.endsWith(".jpeg"))) {
            try {
                Log.i(TAG, "Parsing date taken from exif");
                final ExifInterface exif = new ExifInterface(item.mFilePath);
                final String dateTakenStr = exif.getAttribute(ExifInterface.TAG_DATETIME);
                if (dateTakenStr != null) {
                    try {
                        final Date dateTaken = CacheService.mDateFormat.parse(dateTakenStr);
                        return dateTaken.getTime();
                    } catch (ParseException pe) {
                        try {
                            final Date dateTaken = CacheService.mAltDateFormat.parse(dateTakenStr);
                            return dateTaken.getTime();
                        } catch (ParseException pe2) {
                            Log.i(TAG, "Unable to parse date out of string - " + dateTakenStr);
                        }
                    }
                }
            } catch (Exception e) {
                Log.i(TAG, "Error reading Exif information, probably not a jpeg.");
            }

            // Ensures that we only try retrieving EXIF date taken once.
            item.mTriedRetrievingExifDateTaken = true;
        }
        return -1L;
    }

    public static final byte[] queryThumbnail(final Context context, final long thumbId, final long origId,
            final boolean isVideo, final long timestamp) {
        final DiskCache thumbnailCache = (isVideo) ? LocalDataSource.sThumbnailCacheVideo
                : LocalDataSource.sThumbnailCache;
        return queryThumbnail(context, thumbId, origId, isVideo, thumbnailCache, timestamp);
    }

    private static final byte[] queryThumbnail(final Context context, final long thumbId, final long origId,
            final boolean isVideo, final DiskCache thumbnailCache, final long timestamp) {
        if (!App.get(context).isPaused()) {
            final Thread thumbnailThread = CacheService.THUMBNAIL_THREAD.getAndSet(null);
            if (thumbnailThread != null) {
                thumbnailThread.interrupt();
            }
        }
        byte[] bitmap = thumbnailCache.get(thumbId, timestamp);
        if (bitmap == null) {
            final long time = SystemClock.uptimeMillis();
            bitmap = CacheService.buildThumbnailForId(context, thumbnailCache, thumbId, origId, isVideo,
                    CacheConstants.DEFAULT_THUMBNAIL_WIDTH,
 CacheConstants.DEFAULT_THUMBNAIL_HEIGHT, timestamp);
            Log.i(TAG, "Built thumbnail and screennail for " + origId + " in " + (SystemClock.uptimeMillis() - time));
        }
        return bitmap;
    }

}
