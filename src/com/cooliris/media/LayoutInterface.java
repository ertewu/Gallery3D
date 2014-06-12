package com.cooliris.media;

import com.cooliris.math.Vector3f;

public abstract class LayoutInterface {
    public abstract void getPositionForSlotIndex(int displayIndex, int itemWidth, int itemHeight, Vector3f outPosition); // the
    // positions
    // of the
    // individual
    // slots
}
