package com.cooliris.media.texture;

import android.graphics.Bitmap;

import com.cooliris.media.RenderView;

public class BitmapTexture extends Texture {
    // A simple flexible texture class that enables a Texture from a bitmap.
    final Bitmap mBitmap;

    public BitmapTexture(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    @Override
    public Bitmap load(RenderView view) {
        return mBitmap;
    }

}
