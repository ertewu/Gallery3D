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

    private ArrayList<Cluster> mClusters;
    private Cluster mCurrCluster;
    private long mClusterSplitTime = (MIN_CLUSTER_SPLIT_TIME_IN_MS + MAX_CLUSTER_SPLIT_TIME_IN_MS) / 2;
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
        mMinClusterSize = Shared.clamp(mMinClusterSize, MIN_MIN_CLUSTER_SIZE, MAX_MIN_CLUSTER_SIZE);
        mMaxClusterSize = Shared.clamp(mMaxClusterSize, MIN_MAX_CLUSTER_SIZE, MAX_MAX_CLUSTER_SIZE);
    }

    public void addItemForClustering(MediaItem mediaItem) {
        compute(mediaItem, false);
    }

    //看样子，这个函数是这个类的核心操作函数,我只是觉得它很长..
    public void compute(MediaItem currentItem, boolean processAllItems) {
        if (currentItem != null) {
            //这个是0
            int numCurrClusterItems = mCurrCluster.mNumItemsLoaded;

            // Determine if this item should go in the current cluster or be the
            // start of a new cluster.
            if (numCurrClusterItems == 0) {
                //就只走了这一句话..
                mCurrCluster.addItem(currentItem);
            }
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


    //这个类被我给删成这么点了..
    public static final class Cluster extends MediaSet {

        public  MediaItem getLastItem() {
            final ArrayList<MediaItem> items = super.getItems();
            if (items == null || mNumItemsLoaded == 0) {
                return null;
            } else {
                return items.get(mNumItemsLoaded - 1);
            }
        }
    }
}
