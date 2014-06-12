package com.cooliris.media.texture;

import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.cooliris.media.RenderView;

public final class ResourceTexture extends Texture {
    private final int mResourceId;
    // ?这个scaled有什么用？在load里，看不到一点什么用途了..,难道说不是我理解的scale的意思了？
    private final boolean mScaled;

    @Override
    public boolean isCached() {
        return true;
    }

    public ResourceTexture(int resourceId, boolean scaled) {
        mResourceId = resourceId;
        mScaled = scaled;
    }

    @Override
    public Bitmap load(RenderView view) {
        // Load a bitmap from the resource.
        Bitmap bitmap = null;
        if (mScaled) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bitmap = BitmapFactory.decodeResource(view.getResources(), mResourceId, options);
        } else {
            InputStream inputStream = view.getResources().openRawResource(mResourceId);
            if (inputStream != null) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                } catch (Exception e) {
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) { /* ignore */
                    }
                }
            }
        }
        return bitmap;
    }
}
