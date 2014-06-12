package com.cooliris.media.component;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.cooliris.app.App;
import com.cooliris.app.Res;
import com.cooliris.cache.CacheService;
import com.cooliris.datasource.ConcatenatedDataSource;
import com.cooliris.datasource.DataSource;
import com.cooliris.datasource.LocalDataSource;
import com.cooliris.media.GridLayoutInterface;
import com.cooliris.media.RenderView;
import com.cooliris.media.layer.grid.GridLayer;

public final class Gallery extends Activity {
	public static final String REVIEW_ACTION = "com.cooliris.media.action.REVIEW";
	private static final String TAG = "Gallery";

	private App mApp = null;
	private RenderView mRenderView = null;
	private GridLayer mGridLayer;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mApp = new App(Gallery.this);

		mRenderView = new RenderView(this);


		//96,72是用来设定每个item的大小的，起码变成48和36会有明显区别
		mGridLayer = new GridLayer(this, (int) (96.0f * App.PIXEL_DENSITY),
				(int) (72.0f * App.PIXEL_DENSITY), new GridLayoutInterface(4),
				mRenderView);

//	      mGridLayer = new GridLayer(this, (int) (48.0f * App.PIXEL_DENSITY),
//	                (int) (36.0f * App.PIXEL_DENSITY), new GridLayoutInterface(4),
//	                mRenderView);


		mRenderView.setRootLayer(mGridLayer);
		setContentView(mRenderView);
		initializeDataSource();
		Log.i(TAG, "onCreate");

	}

	@Override
	public void onResume() {
		super.onResume();
		if (mRenderView != null) {
			mRenderView.onResume();
		}
		if (mApp.isPaused()) {
			mApp.onResume();
		}
	}


	@Override
	public void onPause() {
		super.onPause();
		if (mRenderView != null)
			mRenderView.onPause();

		LocalDataSource.sThumbnailCache.flush();
		LocalDataSource.sThumbnailCacheVideo.flush();

		mApp.onPause();
	}

	@Override
	public void onStop() {
		super.onStop();
		if (mGridLayer != null)
			mGridLayer.stop();

		// Start the thumbnailer.
		CacheService.startCache(this, true);
	}

	@Override
	public void onDestroy() {
		// Force GLThread to exit.
		setContentView(Res.layout.main);

		if (mGridLayer != null) {
			DataSource dataSource = mGridLayer.getDataSource();
			if (dataSource != null) {
				dataSource.shutdown();
			}
			mGridLayer.shutdown();
		}
		if (mRenderView != null) {
			mRenderView.shutdown();
			mRenderView = null;
		}
		mGridLayer = null;
		mApp.shutdown();
		super.onDestroy();
		Log.i(TAG, "onDestroy");
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (mGridLayer != null) {
			mGridLayer.markDirty(30);
		}
		if (mRenderView != null)
			mRenderView.requestRender();
		Log.i(TAG, "onConfigurationChanged");
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mRenderView != null) {
			return mRenderView.onKeyDown(keyCode, event)
					|| super.onKeyDown(keyCode, event);
		} else {
			return super.onKeyDown(keyCode, event);
		}
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case CropImage.CROP_MSG: {
			if (resultCode == RESULT_OK) {
				setResult(resultCode, data);
				finish();
			}
			break;
		}
		case CropImage.CROP_MSG_INTERNAL: {
			// We cropped an image, we must try to set the focus of the camera
			// to that image.
			if (resultCode == RESULT_OK) {
				String contentUri = data.getAction();
				if (mGridLayer != null && contentUri != null) {
					mGridLayer.focusItem(contentUri);
				}
			}
			break;
		}
		}
	}

	//这个是目前为止需要看的最重要的函数了
	private void initializeDataSource() {
		// Creating the DataSource objects.
		final LocalDataSource localDataSource = new LocalDataSource(
				Gallery.this, LocalDataSource.URI_ALL_MEDIA, false);
		final ConcatenatedDataSource combinedDataSource = new ConcatenatedDataSource(
				localDataSource);

			//是以android.intent.action.Main 走进来的，从以前的代码来看，不同的action还对应不同的source源呢，这一下可没少删除代码..
		localDataSource.setMimeFilter(true, true);
	    mGridLayer.setDataSource(combinedDataSource);

		// We record the set of enabled accounts for picasa.
	}
}
