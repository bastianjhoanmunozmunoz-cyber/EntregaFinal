package com.example.mapgps;

import java.util.UUID;

/** Un lugar guardado en la bitácora. Solo se usa desde el hilo principal. */
final class Place {
    static final int MAX_NAME = 40;
    static final int MAX_NOTE = 200;

    final String id;
    final double lat;
    final double lng;
    final long createdAt;
    String name = "";
    String note = "";
    /** Nombre del archivo cifrado de la foto dentro de {@code files/photos_enc/}, o null. */
    String photoFile;
    /** Solo para migrar versiones antiguas: nombre del JPG sin cifrar en {@code files/photos/}. */
    String legacyPhoto;

    Place(String id, double lat, double lng, long createdAt) {
        this.id = id;
        this.lat = lat;
        this.lng = lng;
        this.createdAt = createdAt;
    }

    static Place create(double lat, double lng) {
        return new Place(UUID.randomUUID().toString(), lat, lng, System.currentTimeMillis());
    }
}
