package com.shutternote;

import static org.junit.Assert.*;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class FilmRollCatalogTest {
    @Test public void e100dIsASeparateHundredSpeedFilm() throws Exception {
        NodeList films = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/xml/film_roll_catalog.xml")).getElementsByTagName("film");
        boolean found = false;
        for (int i=0;i<films.getLength();i++) {
            Element film=(Element)films.item(i);
            if ("kodak-ektachrome-e100d".equals(film.getAttribute("id"))) {
                found=true; assertEquals("100",film.getAttribute("speed")); assertEquals("true",film.getAttribute("cine"));
                assertEquals("EKTACHROME E100D",film.getAttribute("name"));
            }
        }
        assertTrue(found);
    }
    @Test public void selectionKeysAreUniqueAndFilmRowsNeverCombineProducts() throws Exception {
        Element catalog = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/xml/film_roll_catalog.xml")).getDocumentElement();
        NodeList films = catalog.getElementsByTagName("film");
        Set<String> filmIds = new HashSet<>();
        String previousBrand = "";
        Set<String> seenBrands = new HashSet<>();
        int previousSpeed = 0;
        for (int i = 0; i < films.getLength(); i++) {
            Element film = (Element) films.item(i);
            assertTrue(filmIds.add(film.getAttribute("id")));
            assertFalse(film.getAttribute("name").contains("/"));
            String brand = film.getAttribute("brand");
            if (!brand.equals(previousBrand)) {
                assertTrue(seenBrands.add(brand)); previousSpeed = 0; previousBrand = brand;
                if ("CINESTILL".equals(brand)) assertEquals("KODAK", ((Element) films.item(i - 1)).getAttribute("brand"));
                if ("FUJIFILM".equals(brand)) assertEquals("CINESTILL", ((Element) films.item(i - 1)).getAttribute("brand"));
            }
            int speed = Integer.parseInt(film.getAttribute("speed"));
            assertTrue(speed >= previousSpeed); previousSpeed = speed;
        }
        assertTrue(filmIds.contains("kodak-vision3-200t"));
        assertTrue(filmIds.contains("kodak-verita-200d"));
        assertFalse(filmIds.contains("user-vision3-800t"));
        assertTrue(filmIds.contains("cinestill-50d"));
        assertTrue(filmIds.contains("cinestill-bwxx"));
        assertTrue(filmIds.contains("cinestill-400d"));
        assertTrue(filmIds.contains("cinestill-800t"));
        NodeList cameras = catalog.getElementsByTagName("camera");
        Set<String> keys = new HashSet<>(), brands = new HashSet<>();
        for (int i = 0; i < cameras.getLength(); i++) {
            Element camera = (Element) cameras.item(i);
            assertTrue(keys.add(camera.getAttribute("id")));
            brands.add(camera.getAttribute("brand"));
            assertFalse(camera.getAttribute("name").isEmpty());
            assertFalse(camera.getAttribute("format").isEmpty());
        }
        assertEquals(20, brands.size());
        assertTrue(keys.contains("leica-m6"));
        assertTrue(keys.contains("nikon-fm2"));
        assertTrue(keys.contains("canon-ae-1"));
        assertTrue(keys.contains("contax-t2"));
        assertTrue(keys.contains("mamiya-mamiya-7ii"));
        assertTrue(keys.contains("pentax-17"));
    }
}
