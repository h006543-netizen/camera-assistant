package com.shutternote;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Personal camera history; counts successful roll uses rather than picker taps. */
final class CameraUsageStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    static final class Entry {
        final String id, name; final int count; final long recent;
        Entry(String id, String name, int count, long recent) { this.id=id; this.name=name; this.count=count; this.recent=recent; }
    }
    CameraUsageStore(Context context) { this(context.getApplicationContext().getSharedPreferences("camera_usage", Context.MODE_PRIVATE)); }
    CameraUsageStore(SharedPreferences preferences) { this.preferences=preferences; }
    void seed(List<FilmRollRecord> rolls) {
        synchronized (LOCK) {
            if (preferences.getBoolean("seeded",false)) return;
            for(int i=rolls.size()-1;i>=0;i--) record(rolls.get(i).cameraId,rolls.get(i).cameraName);
            preferences.edit().putBoolean("seeded",true).apply();
        }
    }
    void record(String id,String name) {
        if(name==null||name.trim().isEmpty())return;
        synchronized (LOCK) {
            String key=id.isEmpty()?"custom:"+name:id;
            long sequence=preferences.getLong("sequence",0)+1;
            preferences.edit().putInt("count/"+key,preferences.getInt("count/"+key,0)+1)
                    .putString("id/"+key,id).putString("name/"+key,name).putLong("recent/"+key,sequence)
                    .putLong("sequence",sequence).putString("lastId",id).putString("lastName",name).apply();
        }
    }
    boolean remove(String id, String name) {
        synchronized (LOCK) {
            String key = id.isEmpty() ? "custom:" + name : id;
            java.util.Map<String, ?> before = preferences.getAll();
            boolean saved = preferences.edit().remove("count/" + key).remove("id/" + key)
                    .remove("name/" + key).remove("recent/" + key).commit();
            // SharedPreferences changes memory even when its disk commit fails.
            if (!saved && before.containsKey("count/" + key)) {
                preferences.edit().putInt("count/" + key, (Integer) before.get("count/" + key))
                        .putString("id/" + key, (String) before.get("id/" + key))
                        .putString("name/" + key, (String) before.get("name/" + key))
                        .putLong("recent/" + key, (Long) before.get("recent/" + key)).apply();
            }
            return saved;
        }
    }
    Entry last(){return new Entry(preferences.getString("lastId",""),preferences.getString("lastName",""),0,0);}
    List<Entry> top(){
        List<Entry> entries=new ArrayList<>();
        for(String path:preferences.getAll().keySet())if(path.startsWith("count/")){
            String key=path.substring(6),name=preferences.getString("name/"+key,"");
            if(!name.isEmpty())entries.add(new Entry(preferences.getString("id/"+key,""),name,preferences.getInt(path,0),preferences.getLong("recent/"+key,0)));
        }
        entries.sort((a,b)->{int count=Integer.compare(b.count,a.count);return count!=0?count:Long.compare(b.recent,a.recent);});
        return new ArrayList<>(entries.subList(0,Math.min(3,entries.size())));
    }
}
