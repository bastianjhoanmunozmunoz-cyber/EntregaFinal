package com.example.mapgps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serialización JSON de la bitácora y validación de datos. No depende de Android
 * (salvo org.json), así que se puede probar con pruebas unitarias locales.
 */
final class PlaceStore {
    static final int VERSION = 2;
    static final String DEFAULT_NAME_PREFIX = "Lugar ";

    private PlaceStore() { }

    /** Resultado de leer el JSON; {@code legacy} indica que venía del formato antiguo (v1). */
    static final class Parsed {
        final List<Place> places;
        final boolean legacy;

        Parsed(List<Place> places, boolean legacy) {
            this.places = places;
            this.legacy = legacy;
        }
    }

    static String toJson(List<Place> places) {
        try {
            JSONArray items = new JSONArray();
            for (Place p : places) {
                JSONObject item = new JSONObject();
                item.put("id", p.id);
                item.put("lat", p.lat);
                item.put("lng", p.lng);
                item.put("name", p.name);
                item.put("note", p.note);
                item.put("createdAt", p.createdAt);
                if (p.photoFile != null) item.put("photo", p.photoFile);
                items.put(item);
            }
            JSONObject root = new JSONObject();
            root.put("version", VERSION);
            root.put("places", items);
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException("No se pudo serializar la bitácora", e);
        }
    }

    static Parsed parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        boolean legacy = root.optInt("version", 1) < 2;
        List<Place> result = new ArrayList<>();
        JSONArray saved = root.optJSONArray("places");
        if (saved != null) {
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                double lat = item.optDouble("lat", Double.NaN);
                double lng = item.optDouble("lng", Double.NaN);
                if (!isValidCoordinate(lat, lng)) continue;
                String id = item.optString("id", "");
                if (id.isEmpty()) id = UUID.randomUUID().toString();
                Place p = new Place(id, lat, lng, item.optLong("createdAt", 0L));
                String name = sanitizeName(item.optString("name", ""));
                p.name = name.isEmpty() ? uniqueDefaultName(result) : name;
                p.note = sanitizeNote(item.optString("note", ""));
                String photo = item.optString("photo", "");
                p.photoFile = isSafeFileName(photo) ? photo : null;
                result.add(p);
            }
        }
        if (legacy) attachLegacyPhotos(root.optJSONArray("photos"), result);
        return new Parsed(result, legacy);
    }

    /** Cada foto antigua se asocia a un lugar con las mismas coordenadas; si no hay, crea uno. */
    private static void attachLegacyPhotos(JSONArray photos, List<Place> result) {
        if (photos == null) return;
        for (int i = 0; i < photos.length(); i++) {
            JSONObject entry = photos.optJSONObject(i);
            if (entry == null) continue;
            double lat = entry.optDouble("lat", Double.NaN);
            double lng = entry.optDouble("lng", Double.NaN);
            String uri = entry.optString("uri", "");
            String file = uri.substring(uri.lastIndexOf('/') + 1);
            if (!isValidCoordinate(lat, lng) || !isSafeFileName(file)) continue;
            Place target = null;
            for (Place p : result) {
                if (p.legacyPhoto == null && Math.abs(p.lat - lat) < 1e-9 && Math.abs(p.lng - lng) < 1e-9) {
                    target = p;
                    break;
                }
            }
            if (target == null) {
                target = Place.create(lat, lng);
                target.name = uniqueDefaultName(result);
                result.add(target);
            }
            target.legacyPhoto = file;
        }
    }

    static boolean isValidCoordinate(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng)
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /** Evita rutas con separadores o saltos de directorio al construir archivos. */
    static boolean isSafeFileName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 120
                && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }

    static String sanitizeName(String raw) {
        if (raw == null) return "";
        String clean = raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        return truncate(clean, Place.MAX_NAME);
    }

    static String sanitizeNote(String raw) {
        if (raw == null) return "";
        String clean = raw.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        return truncate(clean, Place.MAX_NOTE);
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) return text;
        int end = max;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end).trim();
    }

    /** "Lugar 1", "Lugar 2"… el primero que no esté usado. */
    static String uniqueDefaultName(List<Place> places) {
        for (int n = 1; ; n++) {
            String candidate = DEFAULT_NAME_PREFIX + n;
            boolean used = false;
            for (Place p : places) {
                if (candidate.equals(p.name)) {
                    used = true;
                    break;
                }
            }
            if (!used) return candidate;
        }
    }
}
