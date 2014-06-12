package com.cooliris.cache;

import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;

public class CacheConstants {

    // Wait 2 seconds to start the thumbnailer so that the application can load
    // without any overheads.
    public static final int THUMBNAILER_WAIT_IN_MS = 2000;
    public static final int DEFAULT_THUMBNAIL_WIDTH = 128;
    public static final int DEFAULT_THUMBNAIL_HEIGHT = 96;

    public static final String DEFAULT_IMAGE_SORT_ORDER = Images.ImageColumns.DATE_TAKEN
            + " ASC";
    public static final String DEFAULT_VIDEO_SORT_ORDER = Video.VideoColumns.DATE_TAKEN
            + " ASC";
    public static final String DEFAULT_BUCKET_SORT_ORDER = "upper("
            + Images.ImageColumns.BUCKET_DISPLAY_NAME + ") ASC";

    // Must preserve order between these indices and the order of the terms in
    // BUCKET_PROJECTION_IMAGES, BUCKET_PROJECTION_VIDEOS.
    // Not using SortedHashMap for efficieny reasons.
    public static final int BUCKET_ID_INDEX = 0;
    public static final int BUCKET_NAME_INDEX = 1;
    public static final String[] BUCKET_PROJECTION_IMAGES = new String[] {
            Images.ImageColumns.BUCKET_ID,
            Images.ImageColumns.BUCKET_DISPLAY_NAME };

    public static final String[] BUCKET_PROJECTION_VIDEOS = new String[] {
            Video.VideoColumns.BUCKET_ID,
            Video.VideoColumns.BUCKET_DISPLAY_NAME };

    // Must preserve order between these indices and the order of the terms in
    // THUMBNAIL_PROJECTION.
    public static final int THUMBNAIL_ID_INDEX = 0;
    public static final int THUMBNAIL_DATE_MODIFIED_INDEX = 1;
    public static final int THUMBNAIL_DATA_INDEX = 2;
    public static final int THUMBNAIL_ORIENTATION_INDEX = 3;
    public static final String[] THUMBNAIL_PROJECTION = new String[] {
            Images.ImageColumns._ID, Images.ImageColumns.DATE_ADDED,
            Images.ImageColumns.DATA, Images.ImageColumns.ORIENTATION };

    public static final String[] SENSE_PROJECTION = new String[] {
            Images.ImageColumns.BUCKET_ID,
            "MAX(" + Images.ImageColumns.DATE_ADDED + "), COUNT(*)" };

    // Must preserve order between these indices and the order of the terms in
    // INITIAL_PROJECTION_IMAGES and
    // INITIAL_PROJECTION_VIDEOS.
    public static final int MEDIA_ID_INDEX = 0;
    public static final int MEDIA_CAPTION_INDEX = 1;
    public static final int MEDIA_MIME_TYPE_INDEX = 2;
    public static final int MEDIA_LATITUDE_INDEX = 3;
    public static final int MEDIA_LONGITUDE_INDEX = 4;
    public static final int MEDIA_DATE_TAKEN_INDEX = 5;
    public static final int MEDIA_DATE_ADDED_INDEX = 6;
    public static final int MEDIA_DATE_MODIFIED_INDEX = 7;
    public static final int MEDIA_DATA_INDEX = 8;
    public static final int MEDIA_ORIENTATION_OR_DURATION_INDEX = 9;
    public static final int MEDIA_BUCKET_ID_INDEX = 10;
    public static final String[] PROJECTION_IMAGES = new String[] {
            Images.ImageColumns._ID, Images.ImageColumns.TITLE,
            Images.ImageColumns.MIME_TYPE, Images.ImageColumns.LATITUDE,
            Images.ImageColumns.LONGITUDE, Images.ImageColumns.DATE_TAKEN,
            Images.ImageColumns.DATE_ADDED, Images.ImageColumns.DATE_MODIFIED,
            Images.ImageColumns.DATA, Images.ImageColumns.ORIENTATION,
            Images.ImageColumns.BUCKET_ID };

    public static final String[] PROJECTION_VIDEOS = new String[] {
            Video.VideoColumns._ID, Video.VideoColumns.TITLE,
            Video.VideoColumns.MIME_TYPE, Video.VideoColumns.LATITUDE,
            Video.VideoColumns.LONGITUDE, Video.VideoColumns.DATE_TAKEN,
            Video.VideoColumns.DATE_ADDED, Video.VideoColumns.DATE_MODIFIED,
            Video.VideoColumns.DATA, Video.VideoColumns.DURATION,
            Video.VideoColumns.BUCKET_ID };

    public static final String BASE_CONTENT_STRING_IMAGES = (Images.Media.EXTERNAL_CONTENT_URI)
            .toString() + "/";
    public static final String BASE_CONTENT_STRING_VIDEOS = (Video.Media.EXTERNAL_CONTENT_URI)
            .toString() + "/";

    // Special indices in the Albumcache.
    public static final int ALBUM_CACHE_METADATA_INDEX = -1;
    public static final int ALBUM_CACHE_DIRTY_INDEX = -2;
    public static final int ALBUM_CACHE_DIRTY_BUCKET_INDEX = -4;
    public static final int ALBUM_CACHE_LOCALE_INDEX = -5;
}
