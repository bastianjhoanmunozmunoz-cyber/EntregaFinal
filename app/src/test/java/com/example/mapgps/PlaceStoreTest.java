package com.example.mapgps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PlaceStoreTest {

    @Test
    public void roundTrip_keepsAllFields() throws JSONException {
        Place a = new Place("id-a", -33.45, -70.66, 123L);
        a.name = "Casa";
        a.note = "Primera nota\nsegunda línea";
        a.photoFile = "id-a_1.enc";
        Place b = new Place("id-b", 10.5, 20.25, 456L);
        b.name = "Lugar 2";
        List<Place> original = new ArrayList<>();
        original.add(a);
        original.add(b);

        PlaceStore.Parsed parsed = PlaceStore.parse(PlaceStore.toJson(original));

        assertFalse(parsed.legacy);
        assertEquals(2, parsed.places.size());
        Place first = parsed.places.get(0);
        assertEquals("id-a", first.id);
        assertEquals(-33.45, first.lat, 1e-9);
        assertEquals(-70.66, first.lng, 1e-9);
        assertEquals("Casa", first.name);
        assertEquals("Primera nota\nsegunda línea", first.note);
        assertEquals("id-a_1.enc", first.photoFile);
        assertEquals(123L, first.createdAt);
        assertNull(parsed.places.get(1).photoFile);
    }

    @Test
    public void legacyFormat_isMigratedWithPhotos() throws JSONException {
        String legacy = "{\"places\":[{\"lat\":-33.1,\"lng\":-70.2},{\"lat\":1.0,\"lng\":2.0}],"
                + "\"photos\":[{\"lat\":-33.1,\"lng\":-70.2,\"uri\":\"content://com.example.mapgps.fileprovider/photos/rumbo_99.jpg\"},"
                + "{\"lat\":50.0,\"lng\":60.0,\"uri\":\"content://x/photos/rumbo_100.jpg\"}]}";

        PlaceStore.Parsed parsed = PlaceStore.parse(legacy);

        assertTrue(parsed.legacy);
        assertEquals(3, parsed.places.size()); // 2 lugares + 1 creado para la foto huérfana
        assertEquals("rumbo_99.jpg", parsed.places.get(0).legacyPhoto);
        assertNull(parsed.places.get(1).legacyPhoto);
        assertEquals("rumbo_100.jpg", parsed.places.get(2).legacyPhoto);
        assertEquals("Lugar 1", parsed.places.get(0).name);
        assertEquals("Lugar 2", parsed.places.get(1).name);
        assertEquals("Lugar 3", parsed.places.get(2).name);
    }

    @Test
    public void legacyPhotosAtSameCoordinates_areNotLost() throws JSONException {
        String legacy = "{\"places\":[{\"lat\":1.0,\"lng\":2.0}],\"photos\":["
                + "{\"lat\":1.0,\"lng\":2.0,\"uri\":\"content://x/photos/a.jpg\"},"
                + "{\"lat\":1.0,\"lng\":2.0,\"uri\":\"content://x/photos/b.jpg\"}]}";

        PlaceStore.Parsed parsed = PlaceStore.parse(legacy);

        assertEquals(2, parsed.places.size());
        assertEquals("a.jpg", parsed.places.get(0).legacyPhoto);
        assertEquals("b.jpg", parsed.places.get(1).legacyPhoto);
    }

    @Test
    public void parse_skipsInvalidCoordinatesAndUnsafePhotoNames() throws JSONException {
        String json = "{\"version\":2,\"places\":["
                + "{\"id\":\"ok\",\"lat\":1,\"lng\":2,\"photo\":\"../secreto\"},"
                + "{\"id\":\"bad\",\"lat\":999,\"lng\":2},"
                + "{\"id\":\"nan\",\"lng\":2}]}";

        PlaceStore.Parsed parsed = PlaceStore.parse(json);

        assertEquals(1, parsed.places.size());
        assertEquals("ok", parsed.places.get(0).id);
        assertNull(parsed.places.get(0).photoFile);
    }

    @Test
    public void sanitizeName_trimsCollapsesAndLimitsLength() {
        assertEquals("Mi casa", PlaceStore.sanitizeName("  Mi \t\n casa  "));
        assertEquals("", PlaceStore.sanitizeName(null));
        String longName = new String(new char[100]).replace('\0', 'x');
        assertEquals(Place.MAX_NAME, PlaceStore.sanitizeName(longName).length());
    }

    @Test
    public void sanitizeNote_keepsNewlinesButLimitsLength() {
        assertEquals("a\nb", PlaceStore.sanitizeNote("a\u0000\nb"));
        String longNote = new String(new char[500]).replace('\0', 'y');
        assertEquals(Place.MAX_NOTE, PlaceStore.sanitizeNote(longNote).length());
    }

    @Test
    public void uniqueDefaultName_fillsFirstFreeNumber() {
        List<Place> places = new ArrayList<>();
        Place p1 = new Place("1", 0, 0, 0);
        p1.name = "Lugar 1";
        Place p3 = new Place("3", 0, 0, 0);
        p3.name = "Lugar 3";
        places.add(p1);
        places.add(p3);
        assertEquals("Lugar 2", PlaceStore.uniqueDefaultName(places));
    }

    @Test
    public void isSafeFileName_rejectsTraversal() {
        assertTrue(PlaceStore.isSafeFileName("abc_123.enc"));
        assertFalse(PlaceStore.isSafeFileName("../x"));
        assertFalse(PlaceStore.isSafeFileName("a/b"));
        assertFalse(PlaceStore.isSafeFileName(""));
        assertFalse(PlaceStore.isSafeFileName(null));
    }

    @Test
    public void isValidCoordinate_checksRanges() {
        assertTrue(PlaceStore.isValidCoordinate(-90, 180));
        assertFalse(PlaceStore.isValidCoordinate(90.1, 0));
        assertFalse(PlaceStore.isValidCoordinate(0, -180.1));
        assertFalse(PlaceStore.isValidCoordinate(Double.NaN, 0));
    }
}
