package com.cooliris.media;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.os.Process;
import android.provider.MediaStore.Images;
import android.util.Log;

import com.cooliris.app.App;
import com.cooliris.app.LogUtils;
import com.cooliris.media.MediaClustering.Cluster;

public final class MediaFeed implements Runnable {
    private final String TAG = "MediaFeed";
    public static final int OPERATION_DELETE = 0;
    public static final int OPERATION_ROTATE = 1;
    public static final int OPERATION_CROP = 2;

    private static final int NUM_ITEMS_LOOKAHEAD = 60;
    private IndexRange mVisibleRange = new IndexRange();
    private IndexRange mBufferedRange = new IndexRange();
    private ArrayList<MediaSet> mMediaSets = new ArrayList<MediaSet>();
    private Listener mListener;
    private DataSource mDataSource;
    private boolean mListenerNeedsUpdate = false;
    private boolean mMediaFeedNeedsToRun = false;
    private MediaSet mSingleWrapper = new MediaSet();
    private boolean mInClusteringMode = false;
    // 这个是干什么的，不太懂的..
    private HashMap<MediaSet, MediaClustering> mClusterSets = new HashMap<MediaSet, MediaClustering>(32);
    private int mExpandedMediaSetIndex = Shared.INVALID;
    private MediaFilter mMediaFilter;
    private MediaSet mMediaFilteredSet;
    private Context mContext;
    private Thread mDataSourceThread = null;
    private Thread mAlbumSourceThread = null;
    private boolean mListenerNeedsLayout;
    private boolean mSingleImageMode;
    private boolean mLoading;

    private String mUri = Images.Media.EXTERNAL_CONTENT_URI.toString();
    String[] mUriArray = new String[] { mUri };
    private volatile boolean mIsShutdown = false;

    /**
     * GridLayer实现了这个接口，这个接口看样子是很有用的，却没有注释..GridLayer里边的onFeedChanged实现了超级多的东西
     */
    public interface Listener {
        public abstract void onFeedAboutToChange(MediaFeed feed);

        public abstract void onFeedChanged(MediaFeed feed, boolean needsLayout);
    }

    public MediaFeed(Context context, DataSource dataSource, Listener listener) {
        feedLog();
        mContext = context;
        mListener = listener;
        mDataSource = dataSource;
        mSingleWrapper.setNumExpectedItems(1);
        mLoading = true;
    }

    public void setVisibleRange(int begin, int end) {
        feedLog();
        if (begin != mVisibleRange.begin || end != mVisibleRange.end) {
            mVisibleRange.begin = begin;
            mVisibleRange.end = end;
            int numItems = 96;
            int numItemsBy2 = numItems / 2;
            int numItemsBy4 = numItems / 4;
            mBufferedRange.begin = (begin / numItemsBy2) * numItemsBy2 - numItemsBy4;
            mBufferedRange.end = mBufferedRange.begin + numItems;
            mMediaFeedNeedsToRun = true;
        }
    }

    public void setFilter(MediaFilter filter) {
        feedLog();
        mMediaFilter = filter;
        mMediaFilteredSet = null;
        if (mListener != null) {
            mListener.onFeedAboutToChange(this);
        }
        mMediaFeedNeedsToRun = true;
    }

    public void removeFilter() {
        feedLog();
        mMediaFilter = null;
        mMediaFilteredSet = null;
        if (mListener != null) {
            mListener.onFeedAboutToChange(this);
            updateListener(true);
        }
        mMediaFeedNeedsToRun = true;
    }

    public ArrayList<MediaSet> getMediaSets() {
        feedLog();
        return mMediaSets;
    }

    public MediaSet getMediaSet(final long setId) {
        feedLog();
        if (setId != Shared.INVALID) {
            try {
                int mMediaSetsSize = mMediaSets.size();
                for (int i = 0; i < mMediaSetsSize; i++) {
                    final MediaSet set = mMediaSets.get(i);
                    if (set.mId == setId) {
                        set.mFlagForDelete = false;
                        return set;
                    }
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public MediaSet getFilteredSet() {
        feedLog();
        return mMediaFilteredSet;
    }

    public MediaSet addMediaSet(final long setId, DataSource dataSource) {
        feedLog();
        MediaSet mediaSet = new MediaSet(dataSource);
        mediaSet.mId = setId;
        mMediaSets.add(mediaSet);
        if (mDataSourceThread != null && !mDataSourceThread.isAlive()) {
            LogUtils.printStackTrace("addMediaSet");
            mDataSourceThread.start();
        }
        mMediaFeedNeedsToRun = true;
        return mediaSet;
    }

    public DataSource getDataSource() {
        feedLog();
        return mDataSource;
    }

    public MediaClustering getClustering() {
        feedLog();
        if (mExpandedMediaSetIndex != Shared.INVALID && mExpandedMediaSetIndex < mMediaSets.size()) {
            return mClusterSets.get(mMediaSets.get(mExpandedMediaSetIndex));
        }
        return null;
    }

    public ArrayList<Cluster> getClustersForSet(final MediaSet set) {
        feedLog();
        ArrayList<Cluster> clusters = null;
        if (mClusterSets != null && mClusterSets.containsKey(set)) {
            MediaClustering mediaClustering = mClusterSets.get(set);
            if (mediaClustering != null) {
                clusters = mediaClustering.getClusters();
            }
        }
        return clusters;
    }

    public void addItemToMediaSet(MediaItem item, MediaSet mediaSet) {
        feedLog();
        item.mParentMediaSet = mediaSet;
        mediaSet.addItem(item);
        synchronized (mClusterSets) {
            if (item.mClusteringState == MediaItem.NOT_CLUSTERED) {
                MediaClustering clustering = mClusterSets.get(mediaSet);
                if (clustering == null) {
                    clustering = new MediaClustering();
                    mClusterSets.put(mediaSet, clustering);
                }
                clustering.setTimeRange(mediaSet.mMaxTimestamp - mediaSet.mMinTimestamp, mediaSet.getNumExpectedItems());
                clustering.addItemForClustering(item);
                item.mClusteringState = MediaItem.CLUSTERED;
            }
        }
        mMediaFeedNeedsToRun = true;
    }

    public void performOperation(final int operation, final ArrayList<MediaBucket> mediaBuckets, final Object data) {
        feedLog();
        int numBuckets = mediaBuckets.size();
        final ArrayList<MediaBucket> copyMediaBuckets = new ArrayList<MediaBucket>(numBuckets);
        for (int i = 0; i < numBuckets; ++i) {
            copyMediaBuckets.add(mediaBuckets.get(i));
        }
        if (operation == OPERATION_DELETE && mListener != null) {
            mListener.onFeedAboutToChange(this);
        }
        Thread operationThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<MediaBucket> mediaBuckets = copyMediaBuckets;
                if (operation == OPERATION_DELETE) {
                    int numBuckets = mediaBuckets.size();
                    for (int i = 0; i < numBuckets; ++i) {
                        MediaBucket bucket = mediaBuckets.get(i);
                        MediaSet set = bucket.mediaSet;
                        ArrayList<MediaItem> items = bucket.mediaItems;
                        if (set != null && items == null) {
                            // Remove the entire bucket.
                            removeMediaSet(set);
                        } else if (set != null && items != null) {
                            // We need to remove these items from the set.
                            int numItems = items.size();
                            // We also need to delete the items from the
                            // cluster.
                            MediaClustering clustering = mClusterSets.get(set);
                            for (int j = 0; j < numItems; ++j) {
                                MediaItem item = items.get(j);
                                removeItemFromMediaSet(item, set);
                                if (clustering != null) {
                                    clustering.removeItemFromClustering(item);
                                }
                            }
                            set.updateNumExpectedItems();
                            set.generateTitle(true);
                        }
                    }
                    updateListener(true);
                    mMediaFeedNeedsToRun = true;
                    if (mDataSource != null) {
                        mDataSource.performOperation(OPERATION_DELETE, mediaBuckets, null);
                    }
                } else {
                    mDataSource.performOperation(operation, mediaBuckets, data);
                }
            }
        });
        operationThread.setName("Operation " + operation);
        operationThread.start();
    }

    public void removeMediaSet(MediaSet set) {
        feedLog();
        synchronized (mMediaSets) {
            mMediaSets.remove(set);
        }
        mMediaFeedNeedsToRun = true;
    }

    private void removeItemFromMediaSet(MediaItem item, MediaSet mediaSet) {
        feedLog();
        mediaSet.removeItem(item);
        synchronized (mClusterSets) {
            MediaClustering clustering = mClusterSets.get(mediaSet);
            if (clustering != null) {
                clustering.removeItemFromClustering(item);
            }
        }
        mMediaFeedNeedsToRun = true;
    }

    public void updateListener(boolean needsLayout) {
        feedLog();
        mListenerNeedsUpdate = true;
        mListenerNeedsLayout = needsLayout;
    }

    public int getNumSlots() {
        feedLog();
        int currentMediaSetIndex = mExpandedMediaSetIndex;
        ArrayList<MediaSet> mediaSets = mMediaSets;
        int mediaSetsSize = mediaSets.size();

        if (mInClusteringMode == false) {
            if (currentMediaSetIndex == Shared.INVALID || currentMediaSetIndex >= mediaSetsSize) {
                return mediaSetsSize;
            } else {
                MediaSet setToUse = (mMediaFilteredSet == null) ? mediaSets.get(currentMediaSetIndex) : mMediaFilteredSet;
                return setToUse.getNumExpectedItems();
            }
        } else if (currentMediaSetIndex != Shared.INVALID && currentMediaSetIndex < mediaSetsSize) {
            MediaSet set = mediaSets.get(currentMediaSetIndex);
            MediaClustering clustering = mClusterSets.get(set);
            if (clustering != null) {
                return clustering.getClustersForDisplay().size();
            }
        }
        return 0;
    }

    public void copySlotStateFrom(MediaFeed another) {
        feedLog();
        mExpandedMediaSetIndex = another.mExpandedMediaSetIndex;
        mInClusteringMode = another.mInClusteringMode;
    }

    public MediaSet getSetForSlot(int slotIndex) {
        feedLog();
        if (slotIndex < 0) {
            return null;
        }

        ArrayList<MediaSet> mediaSets = mMediaSets;
        int mediaSetsSize = mediaSets.size();
        int currentMediaSetIndex = mExpandedMediaSetIndex;

        if (mInClusteringMode == false) {
            if (currentMediaSetIndex == Shared.INVALID || currentMediaSetIndex >= mediaSetsSize) {
                if (slotIndex >= mediaSetsSize) {
                    return null;
                }
                return mMediaSets.get(slotIndex);
            }
            if (mSingleWrapper.getNumItems() == 0) {
                mSingleWrapper.addItem(null);
            }
            MediaSet setToUse = (mMediaFilteredSet == null) ? mMediaSets.get(currentMediaSetIndex) : mMediaFilteredSet;
            ArrayList<MediaItem> items = setToUse.getItems();
            if (slotIndex >= setToUse.getNumItems()) {
                return null;
            }
            mSingleWrapper.getItems().set(0, items.get(slotIndex));
            return mSingleWrapper;
        } else if (currentMediaSetIndex != Shared.INVALID && currentMediaSetIndex < mediaSetsSize) {
            MediaSet set = mediaSets.get(currentMediaSetIndex);
            MediaClustering clustering = mClusterSets.get(set);
            if (clustering != null) {
                ArrayList<MediaClustering.Cluster> clusters = clustering.getClustersForDisplay();
                if (clusters.size() > slotIndex) {
                    MediaClustering.Cluster cluster = clusters.get(slotIndex);
                    cluster.generateCaption(mContext);
                    return cluster;
                }
            }
        }
        return null;
    }

    public boolean isLoading() {
        feedLog();
        return mLoading;
    }

    public void start() {
        feedLog();
        mLoading = true;
        // this是指runnable
        mDataSourceThread = new Thread(this);
        mDataSourceThread.setName("MediaFeed");
        mIsShutdown = false;
        // 整个mAlbumSourceThread就只是loadMediaSets 这个函数
        mAlbumSourceThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (mContext == null)
                    return;
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                if (mDataSource != null) {
                    loadMediaSets();
                }
                // 以前这里关于sd卡扫描的部分，大部分都没有用，于是我决定删除它们..
                mLoading = false;
            }
        });
        mAlbumSourceThread.setName("MediaSets");
        mAlbumSourceThread.start();
    }

    // 这个地方是在一开始去load数据的地方，是有一定时间的,但也不是很多，在s2上，只有600ms
    private void loadMediaSets() {
        feedLog();
        if (mDataSource == null)
            return;
        final ArrayList<MediaSet> sets = mMediaSets;
        synchronized (sets) {
            mDataSource.refresh(MediaFeed.this, mDataSource.getDatabaseUris());
        }
        mMediaFeedNeedsToRun = true;
        updateListener(false);
    }

    @Override
    public void run() {
        feedLog();
        DataSource dataSource = mDataSource;
        int sleepMs = 10;
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        if (dataSource != null) {
            //没想到这个线程是一直开着的？ 为什么要这么一直刷..
            while (!Thread.interrupted() && !mIsShutdown) {
                String[] databaseUris = mUriArray;
                boolean performRefresh = false;
                boolean settingFeedAboutToChange = false;
                if (performRefresh) {
                    if (dataSource != null) {
                        if (mListener != null) {
                            settingFeedAboutToChange = true;
                            mListener.onFeedAboutToChange(this);
                        }
                        dataSource.refresh(this, databaseUris);
                        mMediaFeedNeedsToRun = true;
                    }
                }
                if (mListenerNeedsUpdate && !mMediaFeedNeedsToRun) {
                    mListenerNeedsUpdate = false;
                    if (mListener != null)
                        synchronized (mMediaSets) {
                            mListener.onFeedChanged(this, mListenerNeedsLayout);
                        }
                }

                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    return;
                }

                sleepMs = 300;
                if (!mMediaFeedNeedsToRun)
                    continue;
                App app = App.get(mContext);
                if (app == null || app.isPaused())
                    continue;
                if (settingFeedAboutToChange) {
                    updateListener(true);
                }
                mMediaFeedNeedsToRun = false;
                ArrayList<MediaSet> mediaSets = mMediaSets;
                synchronized (mediaSets) {
                    int expandedSetIndex = mExpandedMediaSetIndex;
                    if (expandedSetIndex >= mMediaSets.size()) {
                        expandedSetIndex = Shared.INVALID;
                    }
                    if (expandedSetIndex == Shared.INVALID) {
                        // We purge the sets outside this visibleRange.
                        int numSets = mediaSets.size();
                        IndexRange visibleRange = mVisibleRange;
                        IndexRange bufferedRange = mBufferedRange;
                        boolean scanMediaSets = true;
                        for (int i = 0; i < numSets; ++i) {
                            if (i >= visibleRange.begin && i <= visibleRange.end && scanMediaSets) {
                                MediaSet set = mediaSets.get(i);
                                int numItemsLoaded = set.mNumItemsLoaded;
                                if (numItemsLoaded < set.getNumExpectedItems() && numItemsLoaded < 8) {
                                    synchronized (set) {
                                        dataSource.loadItemsForSet(this, set, numItemsLoaded, 8);
                                        set.checkForDeletedItems();
                                    }
                                    if (set.getNumExpectedItems() == 0) {
                                        mediaSets.remove(set);
                                        break;
                                    }
                                    if (mListener != null) {
                                        mListenerNeedsUpdate = false;
                                        mListener.onFeedChanged(this, mListenerNeedsLayout);
                                        mListenerNeedsLayout = false;
                                    }
                                    sleepMs = 100;
                                    scanMediaSets = false;
                                }
                                if (!set.setContainsValidItems()) {
                                    mediaSets.remove(set);
                                    if (mListener != null) {
                                        mListenerNeedsUpdate = false;
                                        mListener.onFeedChanged(this, mListenerNeedsLayout);
                                        mListenerNeedsLayout = false;
                                    }
                                    break;
                                }
                            }
                        }
                        numSets = mediaSets.size();
                        for (int i = 0; i < numSets; ++i) {
                            MediaSet set = mediaSets.get(i);
                            if (i >= bufferedRange.begin && i <= bufferedRange.end) {
                                if (scanMediaSets) {
                                    int numItemsLoaded = set.mNumItemsLoaded;
                                    if (numItemsLoaded < set.getNumExpectedItems() && numItemsLoaded < 8) {
                                        synchronized (set) {
                                            dataSource.loadItemsForSet(this, set, numItemsLoaded, 8);
                                            set.checkForDeletedItems();
                                        }
                                        if (set.getNumExpectedItems() == 0) {
                                            mediaSets.remove(set);
                                            break;
                                        }
                                        if (mListener != null) {
                                            mListenerNeedsUpdate = false;
                                            mListener.onFeedChanged(this, mListenerNeedsLayout);
                                            mListenerNeedsLayout = false;
                                        }
                                        sleepMs = 100;
                                        scanMediaSets = false;
                                    }
                                }
                            } else if (!mListenerNeedsUpdate && (i < bufferedRange.begin || i > bufferedRange.end)) {
                                // Purge this set to its initial status.
                                MediaClustering clustering = mClusterSets.get(set);
                                if (clustering != null) {
                                    clustering.clear();
                                    mClusterSets.remove(set);
                                }
                                if (set.getNumItems() != 0)
                                    set.clear();
                            }
                        }
                    }
                    if (expandedSetIndex != Shared.INVALID) {
                        int numSets = mMediaSets.size();
                        for (int i = 0; i < numSets; ++i) {
                            // Purge other sets.
                            if (i != expandedSetIndex) {
                                MediaSet set = mediaSets.get(i);
                                MediaClustering clustering = mClusterSets.get(set);
                                if (clustering != null) {
                                    clustering.clear();
                                    mClusterSets.remove(set);
                                }
                                if (set.mNumItemsLoaded != 0)
                                    set.clear();
                            }
                        }
                        // Make sure all the items are loaded for the album.
                        int numItemsLoaded = mediaSets.get(expandedSetIndex).mNumItemsLoaded;
                        int requestedItems = mVisibleRange.end;
                        // requestedItems count changes in clustering mode.
                        if (mInClusteringMode && mClusterSets != null) {
                            requestedItems = 0;
                            MediaClustering clustering = mClusterSets.get(mediaSets.get(expandedSetIndex));
                            if (clustering != null) {
                                ArrayList<Cluster> clusters = clustering.getClustersForDisplay();
                                int numClusters = clusters.size();
                                for (int i = 0; i < numClusters; i++) {
                                    requestedItems += clusters.get(i).getNumExpectedItems();
                                }
                            }
                        }
                        MediaSet set = mediaSets.get(expandedSetIndex);
                        if (numItemsLoaded < set.getNumExpectedItems()) {
                            // We perform calculations for a window that gets
                            // anchored to a multiple of NUM_ITEMS_LOOKAHEAD.
                            // The start of the window is 0, x, 2x, 3x ... etc
                            // where x = NUM_ITEMS_LOOKAHEAD.
                            synchronized (set) {
                                dataSource.loadItemsForSet(this, set, numItemsLoaded, (requestedItems / NUM_ITEMS_LOOKAHEAD)
                                        * NUM_ITEMS_LOOKAHEAD + NUM_ITEMS_LOOKAHEAD);
                                set.checkForDeletedItems();
                            }
                            if (set.getNumExpectedItems() == 0) {
                                mediaSets.remove(set);
                                mListenerNeedsUpdate = false;
                                mListener.onFeedChanged(this, mListenerNeedsLayout);
                                mListenerNeedsLayout = false;
                            }
                            if (numItemsLoaded != set.mNumItemsLoaded && mListener != null) {
                                mListenerNeedsUpdate = false;
                                mListener.onFeedChanged(this, mListenerNeedsLayout);
                                mListenerNeedsLayout = false;
                            }
                        }
                    }
                    MediaFilter filter = mMediaFilter;
                    if (filter != null && mMediaFilteredSet == null) {
                        if (expandedSetIndex != Shared.INVALID) {
                            MediaSet set = mediaSets.get(expandedSetIndex);
                            ArrayList<MediaItem> items = set.getItems();
                            int numItems = set.getNumItems();
                            MediaSet filteredSet = new MediaSet();
                            filteredSet.setNumExpectedItems(numItems);
                            mMediaFilteredSet = filteredSet;
                            for (int i = 0; i < numItems; ++i) {
                                MediaItem item = items.get(i);
                                if (filter.pass(item)) {
                                    filteredSet.addItem(item);
                                }
                            }
                            filteredSet.updateNumExpectedItems();
                            filteredSet.generateTitle(true);
                        }
                        updateListener(true);
                    }
                }
            }
        }
    }

    public void expandMediaSet(int mediaSetIndex) {
        feedLog();
        // We need to check if this slot can be focused or not.
        if (mListener != null) {
            mListener.onFeedAboutToChange(this);
        }
        if (mExpandedMediaSetIndex > 0 && mediaSetIndex == Shared.INVALID) {
            // We are collapsing a previously expanded media set
            if (mediaSetIndex < mMediaSets.size() && mExpandedMediaSetIndex >= 0 && mExpandedMediaSetIndex < mMediaSets.size()) {
                MediaSet set = mMediaSets.get(mExpandedMediaSetIndex);
                if (set.getNumItems() == 0) {
                    set.clear();
                }
            }
        }
        mExpandedMediaSetIndex = mediaSetIndex;
        updateListener(true);
        mMediaFeedNeedsToRun = true;
    }

    public boolean canExpandSet(int slotIndex) {
        feedLog();
        int mediaSetIndex = slotIndex;
        if (mediaSetIndex < mMediaSets.size() && mediaSetIndex >= 0) {
            MediaSet set = mMediaSets.get(mediaSetIndex);
            if (set.getNumItems() > 0) {
                MediaItem item = set.getItems().get(0);
                if (item.mId == Shared.INVALID) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    public boolean hasExpandedMediaSet() {
        feedLog();
        return (mExpandedMediaSetIndex != Shared.INVALID);
    }

    public boolean restorePreviousClusteringState() {
        feedLog();
        boolean retVal = disableClusteringIfNecessary();
        if (retVal) {
            if (mListener != null) {
                mListener.onFeedAboutToChange(this);
            }
            updateListener(true);
            mMediaFeedNeedsToRun = true;
        }
        return retVal;
    }

    private boolean disableClusteringIfNecessary() {
        feedLog();
        if (mInClusteringMode) {
            // Disable clustering.
            mInClusteringMode = false;
            mMediaFeedNeedsToRun = true;
            return true;
        }
        return false;
    }

    public boolean isClustered() {
        feedLog();
        return mInClusteringMode;
    }

    public MediaSet getCurrentSet() {
        feedLog();
        if (mExpandedMediaSetIndex != Shared.INVALID && mExpandedMediaSetIndex < mMediaSets.size()) {
            return mMediaSets.get(mExpandedMediaSetIndex);
        }
        return null;
    }

    public void performClustering() {
        feedLog();
        if (mListener != null) {
            mListener.onFeedAboutToChange(this);
        }
        MediaSet setToUse = null;
        if (mExpandedMediaSetIndex != Shared.INVALID && mExpandedMediaSetIndex < mMediaSets.size()) {
            setToUse = mMediaSets.get(mExpandedMediaSetIndex);
        }
        if (setToUse != null) {
            MediaClustering clustering = null;
            synchronized (mClusterSets) {
                // Make sure the computation is completed to the end.
                clustering = mClusterSets.get(setToUse);
                if (clustering != null) {
                    clustering.compute(null, true);
                } else {
                    return;
                }
            }
            mInClusteringMode = true;
            updateListener(true);
        }
    }

    public void moveSetToFront(MediaSet mediaSet) {
        feedLog();
        ArrayList<MediaSet> mediaSets = mMediaSets;
        int numSets = mediaSets.size();
        if (numSets == 0) {
            mediaSets.add(mediaSet);
            return;
        }
        MediaSet setToFind = mediaSets.get(0);
        if (setToFind == mediaSet) {
            return;
        }
        mediaSets.set(0, mediaSet);
        int indexToSwapTill = -1;
        for (int i = 1; i < numSets; ++i) {
            MediaSet set = mediaSets.get(i);
            if (set == mediaSet) {
                mediaSets.set(i, setToFind);
                indexToSwapTill = i;
                break;
            }
        }
        if (indexToSwapTill != Shared.INVALID) {
            for (int i = indexToSwapTill; i > 1; --i) {
                MediaSet setEnd = mediaSets.get(i);
                MediaSet setPrev = mediaSets.get(i - 1);
                mediaSets.set(i, setPrev);
                mediaSets.set(i - 1, setEnd);
            }
        }
        mMediaFeedNeedsToRun = true;
    }

    public MediaSet replaceMediaSet(long setId, DataSource dataSource) {
        feedLog();
        Log.i(TAG, "Replacing media set " + setId);
        final MediaSet set = getMediaSet(setId);
        if (set != null)
            set.refresh();
        return set;
    }

    public void setSingleImageMode(boolean singleImageMode) {
        feedLog();
        mSingleImageMode = singleImageMode;
    }

    public boolean isSingleImageMode() {
        feedLog();
        return mSingleImageMode;
    }

    public MediaSet getExpandedMediaSet() {
        feedLog();
        if (mExpandedMediaSetIndex == Shared.INVALID)
            return null;
        if (mExpandedMediaSetIndex >= mMediaSets.size())
            return null;
        return mMediaSets.get(mExpandedMediaSetIndex);
    }

    private static void feedLog() {
        // LogUtils.footPrint();
    }
}
