package com.cooliris.media.layer;

import javax.microedition.khronos.opengles.GL11;

import android.view.MotionEvent;

import com.cooliris.media.RenderView;

public interface Interface {

    public interface LayoutInterface {

        public void onSurfaceCreated(RenderView view, GL11 gl);

        public void onSizeChanged();

        public void onHiddenChanged();

        public boolean onTouchEvent(MotionEvent event);
    }

    public interface RenderInterface {

        // Returns true if something is animating.
        public boolean update(RenderView view, float frameInterval);

        public void renderOpaque(RenderView view, GL11 gl);

        public void renderBlended(RenderView view, GL11 gl);

        public void generate(RenderView view, RenderView.Lists lists);
    }
}
