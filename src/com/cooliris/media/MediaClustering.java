/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cooliris.media;

import java.util.ArrayList;

/**
 * Implementation of an agglomerative based clustering where all items within a
 * certain time cutoff are grouped into the same cluster. Small adjacent
 * clusters are merged and large individual clusters are considered for
 * splitting.
 *
 * TODO: Limitation: Can deal with items not being added incrementally to the
 * end of the current date range but effectively assumes this is the case for
 * efficient performance.
 */

public final class MediaClustering {
    // If 2 items are greater than 25 miles apart, they will be in different
    // clusters.
    private static final int GEOGRAPHIC_DISTANCE_CUTOFF_IN_MILES = 20;

    // Do not want to split based on anything under 1 min.
    private static final long MIN_CLUSTER_SPLIT_TIME_IN_MS = 60000L;

    // Disregard a cluster split time of anything over 2 hours.
    private static final long MAX_CLUSTER_SPLIT_TIME_IN_MS = 7200000L;

    // Try and get around 9 clusters (best-effort for the common case).
    private static final int NUM_CLUSTERS_TARGETED = 9;

    // Try and merge 2 clusters if they are both smaller than min cluster size.
    // The min cluster size can range from 8 to 15.
    private static final int MIN_MIN_CLUSTER_SIZE = 8;
    private static final int MAX_MIN_CLUSTER_SIZE = 15;

    // Try and split a cluster if it is bigger than max cluster size.
    // The max cluster size can range from 20 to 50.
    private static final int MIN_MAX_CLUSTER_SIZE = 20;
    private static final int MAX_MAX_CLUSTER_SIZE = 50;

    // Initially put 2 items in the same cluster as long as they are within
    // 3 cluster frequencies of each other.
    private static int CLUSTER_SPLIT_MULTIPLIER = 3;

    // The minimum change factor in the time between items to consider a
    // partition.
    // Example: (Item 3 - Item 2) / (Item 2 - Item 1).
    private static final int MIN_PARTITION_CHANGE_FACTOR = 2;

    // Make the cluster split time of a large cluster half that of a regular
    // cluster.
    private static final int PARTITION_CLUSTER_SPLIT_TIME_FACTOR = 2;

    private ArrayList<Cluster> mClusters;
    private Cluster mCurrCluster;
    private long mClusterSplitTime = (MIN_CLUSTER_SPLIT_TIME_IN_MS + MAX_CLUSTER_SPLIT_TIME_IN_MS) / 2;
    private long mLargeClusterSplitTime = mClusterSplitTime / PARTITION_CLUSTER_SPLIT_TIME_FACTOR;
    private int mMinClusterSize = (MIN_MIN_CLUSTER_SIZE + MAX_MIN_CLUSTER_SIZE) / 2;
    private int mMaxClusterSize = (MIN_MAX_CLUSTER_SIZE + MAX_MAX_CLUSTER_SIZE) / 2;

    MediaClustering() {
        mClusters = new ArrayList<Cluster>();
        mCurrCluster = new Cluster();
    }

    public void clear() {
        int numClusters = mClusters.size();
        for (int i = 0; i < numClusters; i++) {
            Cluster cluster = mClusters.get(i);
            cluster.clear();
        }
        if (mCurrCluster != null) {
            mCurrCluster.clear();
        }
    }

    public void setTimeRange(long timeRange, int numItems) {
        if (numItems != 0) {
            int meanItemsPerCluster = numItems / NUM_CLUSTERS_TARGETED;
            // Heuristic(启发式的) to get min and max cluster size - half and double the
            // desired items per cluster.
            mMinClusterSize = meanItemsPerCluster / 2;
            mMaxClusterSize = meanItemsPerCluster * 2;
            mClusterSplitTime = timeRange / numItems * CLUSTER_SPLIT_MULTIPLIER;
        }
        mClusterSplitTime = Shared.clamp(mClusterSplitTime, MIN_CLUSTER_SPLIT_TIME_IN_MS, MAX_CLUSTER_SPLIT_TIME_IN_MS);
        mLargeClusterSplitTime = mClusterSplitTime / PARTITION_CLUSTER_SPLIT_TIME_FACTOR;
        mMinClusterSize = Shared.clamp(mMinClusterSize, MIN_MIN_CLUSTER_SIZE, MAX_MIN_CLUSTER_SIZE);
        mMaxClusterSize = Shared.clamp(mMaxClusterSize, MIN_MAX_CLUSTER_SIZE, MAX_MAX_CLUSTER_SIZE);
    }

    public void addItemForClustering(MediaItem mediaItem) {
        compute(mediaItem, false);
    }

    //看样子，这个函数是这个类的核心操作函数,我只是觉得它很长..
    public void compute(MediaItem currentItem, boolean processAllItems) {
        if (currentItem != null) {
            int numClusters = mClusters.size();
            int numCurrClusterItems = mCurrCluster.mNumItemsLoaded;
            boolean geographicallySeparateItem = false;
            boolean itemAddedToCurrentCluster = false;

            // Determine if this item should go in the current cluster or be the
            // start of a new cluster.
            if (numCurrClusterItems == 0) {
                mCurrCluster.addItem(currentItem);
            } else {
                MediaItem prevItem = mCurrCluster.getLastItem();
                if (isGeographicallySeparated(prevItem, currentItem)) {
                    mClusters.add(mCurrCluster);
                    geographicallySeparateItem = true;
                } else if (numCurrClusterItems > mMaxClusterSize) {
                    splitAndAddCurrentCluster();
                } else if (timeDistance(prevItem, currentItem) < mClusterSplitTime) {
                    mCurrCluster.addItem(currentItem);
                    itemAddedToCurrentCluster = true;
                } else if (numClusters > 0 && numCurrClusterItems < mMinClusterSize
                        && !mCurrCluster.mGeographicallySeparatedFromPrevCluster) {
                    mergeAndAddCurrentCluster();
                } else {
                    mClusters.add(mCurrCluster);
                }

                // Creating a new cluster and adding the current item to it.
                if (!itemAddedToCurrentCluster) {
                    mCurrCluster = new Cluster();
                    if (geographicallySeparateItem) {
                        mCurrCluster.mGeographicallySeparatedFromPrevCluster = true;
                    }
                    mCurrCluster.addItem(currentItem);
                }
            }
        }

        if (processAllItems && mCurrCluster.mNumItemsLoaded > 0) {
            int numClusters = mClusters.size();
            int numCurrClusterItems = mCurrCluster.mNumItemsLoaded;

            // The last cluster may potentially be too big or too small.
            if (numCurrClusterItems > mMaxClusterSize) {
                splitAndAddCurrentCluster();
            } else if (numClusters > 0 && numCurrClusterItems < mMinClusterSize
                    && !mCurrCluster.mGeographicallySeparatedFromPrevCluster) {
                mergeAndAddCurrentCluster();
            } else {
                mClusters.add(mCurrCluster);
            }
            mCurrCluster = new Cluster();
        }
    }

    private void splitAndAddCurrentCluster() {
        ArrayList<MediaItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.mNumItemsLoaded;
        int secondPartitionStartIndex = getPartitionIndexForCurrentCluster();
        if (secondPartitionStartIndex != -1) {
            Cluster partitionedCluster = new Cluster();
            for (int j = 0; j < secondPartitionStartIndex; j++) {
                partitionedCluster.addItem(currClusterItems.get(j));
            }
            mClusters.add(partitionedCluster);
            partitionedCluster = new Cluster();
            for (int j = secondPartitionStartIndex; j < numCurrClusterItems; j++) {
                partitionedCluster.addItem(currClusterItems.get(j));
            }
            mClusters.add(partitionedCluster);
        } else {
            mClusters.add(mCurrCluster);
        }
    }

    private int getPartitionIndexForCurrentCluster() {
        int partitionIndex = -1;
        float largestChange = MIN_PARTITION_CHANGE_FACTOR;
        ArrayList<MediaItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.mNumItemsLoaded;
        int minClusterSize = mMinClusterSize;

        // Could be slightly more efficient here but this code seems cleaner.
        if (numCurrClusterItems > minClusterSize + 1) {
            for (int i = minClusterSize; i < numCurrClusterItems - minClusterSize; i++) {
                MediaItem prevItem = currClusterItems.get(i - 1);
                MediaItem currItem = currClusterItems.get(i);
                MediaItem nextItem = currClusterItems.get(i + 1);

                if (prevItem.isDateTakenValid() && currItem.isDateModifiedValid() && nextItem.isDateModifiedValid()) {
                    long diff1 = Math.abs(nextItem.mDateTakenInMs - currItem.mDateTakenInMs);
                    long diff2 = Math.abs(currItem.mDateTakenInMs - prevItem.mDateTakenInMs);
                    float change = Math.max(diff1 / (diff2 + 0.01f), diff2 / (diff1 + 0.01f));
                    if (change > largestChange) {
                        if (timeDistance(currItem, prevItem) > mLargeClusterSplitTime) {
                            partitionIndex = i;
                            largestChange = change;
                        } else if (timeDistance(nextItem, currItem) > mLargeClusterSplitTime) {
                            partitionIndex = i + 1;
                            largestChange = change;
                        }
                    }
                }
            }
        }
        return partitionIndex;
    }

    private void mergeAndAddCurrentCluster() {
        int numClusters = mClusters.size();
        Cluster prevCluster = mClusters.get(numClusters - 1);
        ArrayList<MediaItem> currClusterItems = mCurrCluster.getItems();
        int numCurrClusterItems = mCurrCluster.mNumItemsLoaded;
        if (prevCluster.mNumItemsLoaded < mMinClusterSize) {
            for (int i = 0; i < numCurrClusterItems; i++) {
                prevCluster.addItem(currClusterItems.get(i));
            }
            mClusters.set(numClusters - 1, prevCluster);
        } else {
            mClusters.add(mCurrCluster);
        }
    }

    public synchronized ArrayList<Cluster> getClusters() {
        int numCurrClusterItems = mCurrCluster.mNumItemsLoaded;
        if (numCurrClusterItems == 0) {
            return mClusters;
        }
        ArrayList<Cluster> mergedClusters = new ArrayList<Cluster>();
        mergedClusters.addAll(mClusters);
        if (numCurrClusterItems > 0) {
            mergedClusters.add(mCurrCluster);
        }
        return mergedClusters;
    }

    public ArrayList<Cluster> getClustersForDisplay() {
        return mClusters;
    }

    //这个类被我给删成这么点了..
    public static final class Cluster extends MediaSet {
        private boolean mGeographicallySeparatedFromPrevCluster = false;

        public  MediaItem getLastItem() {
            final ArrayList<MediaItem> items = super.getItems();
            if (items == null || mNumItemsLoaded == 0) {
                return null;
            } else {
                return items.get(mNumItemsLoaded - 1);
            }
        }
    }

    // Returns the time interval between the two items in milliseconds.
    private static long timeDistance(MediaItem a, MediaItem b) {
        if (a == null || b == null) {
            return 0;
        }
        return Math.abs(a.mDateTakenInMs - b.mDateTakenInMs);
    }

    // Returns true if a, b are sufficiently geographically separated.
    private static boolean isGeographicallySeparated(MediaItem a, MediaItem b) {
        // If a or b are null, a or b have the default latitude, longitude
        // values or are close enough, return false.
        if (a != null && b != null && a.isLatLongValid() && b.isLatLongValid()) {
            int distance = (int) (LocationMediaFilter.toMile(LocationMediaFilter.distanceBetween(a.mLatitude, a.mLongitude,
                    b.mLatitude, b.mLongitude)) + 0.5);
            if (distance > GEOGRAPHIC_DISTANCE_CUTOFF_IN_MILES) {
                return true;
            }
        }
        return false;
    }
}
