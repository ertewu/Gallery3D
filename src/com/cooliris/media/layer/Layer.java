package com.cooliris.media.layer;

import javax.microedition.khronos.opengles.GL11;

import android.view.MotionEvent;

import com.cooliris.media.RenderView;

public abstract class Layer implements Interface.RenderInterface, Interface.LayoutInterface {
    public float mX = 0f;
    public float mY = 0f;
    public float mWidth = 0;
    public float mHeight = 0;
    public boolean mHidden = false;

    public final float getX() {
        return mX;
    }

    public final float getY() {
        return mY;
    }

    public final void setPosition(float x, float y) {
        mX = x;
        mY = y;
    }

    public final float getWidth() {
        return mWidth;
    }

    public final float getHeight() {
        return mHeight;
    }

    public final void setSize(float width, float height) {
        if (mWidth != width || mHeight != height) {
            mWidth = width;
            mHeight = height;
            onSizeChanged();
        }
    }

    public boolean isHidden() {
        return mHidden;
    }

    public void setHidden(boolean hidden) {
        if (mHidden != hidden) {
            mHidden = hidden;
            onHiddenChanged();
        }
    }

    // Allows subclasses to further constrain the hit test defined by layer
    // bounds.
    public boolean containsPoint(float x, float y) {
        return true;
    }

    @Override
    public boolean update(RenderView view, float frameInterval) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public void onHiddenChanged() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSizeChanged() {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSurfaceCreated(RenderView view, GL11 gl) {
        // TODO Auto-generated method stub

    }

    @Override
    public void renderOpaque(RenderView view, GL11 gl) {
        // TODO Auto-generated method stub

    }
}
