package com.shutternote;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

/** Creation defaults only; camera-session choices never write this store. */
final class FilmRollDefaultsStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    FilmRollDefaultsStore(Context context) { this(context.getApplicationContext().getSharedPreferences("film_roll_defaults", Context.MODE_PRIVATE)); }
    FilmRollDefaultsStore(SharedPreferences preferences) { this.preferences=preferences; }
    void seed(List<FilmRollRecord> rolls) {
        synchronized(LOCK) {
            if(preferences.getBoolean("seeded",false))return;
            for(int i=rolls.size()-1;i>=0;i--) {
                FilmRollRecord r=rolls.get(i);recordFocal(r.focalLength);recordCount(r.plannedFrames,r.countMode);
            }
            preferences.edit().putBoolean("seeded",true).apply();
        }
    }
    void recordFocal(String value) {
        if(Arrays.asList("35","40","50").contains(value))preferences.edit().putString("focal",value).apply();
    }
    void recordCount(int count,int mode) {
        if(count<1||count>999)return;
        int normalized=mode==24&&count==24?24:mode==36&&count==36?36:0;
        preferences.edit().putInt("count",count).putInt("mode",normalized).apply();
    }
    void restore(FilmRollRecord draft) {
        String focal=preferences.getString("focal","35");
        draft.focalLength=Arrays.asList("35","40","50").contains(focal)?focal:"35";
        int count=preferences.getInt("count",36),mode=preferences.getInt("mode",36);
        draft.plannedFrames=count>=1&&count<=999?count:36;
        draft.countMode=mode==24&&draft.plannedFrames==24?24:mode==36&&draft.plannedFrames==36?36:0;
    }
}
