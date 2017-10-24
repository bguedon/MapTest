package com.test.maptest;

import android.app.Application;

import com.mapbox.mapboxsdk.Mapbox;

/**
 * Created by bguedon on 24/10/2017.
 */

public class BaseApplication extends Application {
        @Override
        public void onCreate() {
            super.onCreate();

            // Map Access token from build variant
            Mapbox.getInstance(getApplicationContext(), BuildConfig.MAP_API_KEY);
        }
}
