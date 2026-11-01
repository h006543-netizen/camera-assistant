package com.shutternote;

import android.content.Context;
import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;
import java.util.ArrayList;
import java.util.List;

final class FilmRollCatalog {
    static final class Item {
        final String id, brand, name, format, ratio;
        final int speed;
        final boolean ei, cine, half;
        Item(XmlResourceParser xml) {
            id = attr(xml, "id"); brand = attr(xml, "brand"); name = attr(xml, "name");
            format = attr(xml, "format"); ratio = attr(xml, "ratio");
            speed = xml.getAttributeIntValue(null, "speed", 0);
            ei = xml.getAttributeBooleanValue(null, "ei", false);
            cine = xml.getAttributeBooleanValue(null, "cine", false);
            half = xml.getAttributeBooleanValue(null, "half", false);
        }
        String fullName() { return brand + " " + name; }
        String cameraDetail(Context context) {
            return half ? context.getString(R.string.roll_half)
                    : ("135".equals(format) ? "35mm" : format) + " · " + ratio;
        }
        private static String attr(XmlResourceParser xml, String key) {
            String value = xml.getAttributeValue(null, key);
            return value == null ? "" : value;
        }
    }
    final List<Item> films = new ArrayList<>(), cameras = new ArrayList<>();

    FilmRollCatalog(Context context) {
        try (XmlResourceParser xml = context.getResources().getXml(R.xml.film_roll_catalog)) {
            int event;
            while ((event = xml.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                if ("film".equals(xml.getName())) films.add(new Item(xml));
                if ("camera".equals(xml.getName())) cameras.add(new Item(xml));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read the bundled roll catalog", exception);
        }
    }

    Item findCamera(String id) {
        for (Item item : cameras) if (item.id.equals(id)) return item;
        return null;
    }

    Item findFilm(String id) {
        for (Item item : films) if (item.id.equals(id)) return item;
        return null;
    }
}
