package com.cooliris.media.component;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.cooliris.app.App;
import com.cooliris.app.Res;
import com.cooliris.media.MovieViewControl;

/**
 * This activity plays a video from a specified URI.
 */
public class MovieView extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "MovieView";

    private App mApp = null; 
    private MovieViewControl mControl;
    private boolean mFinishOnCompletion;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mApp = new App(MovieView.this);
        setContentView(Res.layout.movie_view);
        View rootView = findViewById(Res.id.root);
        Intent intent = getIntent();
        mControl = new MovieViewControl(rootView, this, intent.getData()) {
            @Override
            public void onCompletion() {
                if (mFinishOnCompletion) {
                    finish();
                }
            }
        };
        if (intent.hasExtra(MediaStore.EXTRA_SCREEN_ORIENTATION)) {
            int orientation = intent.getIntExtra(MediaStore.EXTRA_SCREEN_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (orientation != getRequestedOrientation()) {
                setRequestedOrientation(orientation);
            }
        }
        mFinishOnCompletion = intent.getBooleanExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
        win.setAttributes(winParams);
    }

    @Override
    public void onPause() {
        mControl.onPause();
        super.onPause();
    	mApp.onPause();
    }

    @Override
    public void onResume() {
        mControl.onResume();
        super.onResume();
    	mApp.onResume();
    }
    
    @Override
    public void onDestroy() {
        mControl.onDestroy();
    	mApp.shutdown();
    	super.onDestroy();
    }
}
