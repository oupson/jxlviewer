package fr.oupson.jxlviewer;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

// Apply Android 12+ dynamic colors.
public class JxlApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
