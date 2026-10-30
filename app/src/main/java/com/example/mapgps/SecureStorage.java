package com.example.mapgps;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Almacenamiento cifrado de la bitácora (EncryptedSharedPreferences) y de las fotos
 * (EncryptedFile). Todas las claves viven en Android Keystore.
 *
 * <p>Los métodos hacen E/S y criptografía: llamarlos siempre fuera del hilo principal.
 */
final class SecureStorage {
    private static final String TAG = "RumboSecurity";
    private static final String PREFS_NAME = "secure_map_prefs";
    private static final String KEY_DATA = "rumbo_data";
    private static final String LEGACY_DATA_FILE = "rumbo_places.json";
    private static final String LEGACY_PHOTO_DIR = "photos";
    private static final String PHOTO_DIR = "photos_enc";
    private static final int PHOTO_MAX_SIDE = 1600;
    private static final int PHOTO_QUALITY = 85;

    private final Context context;
    private MasterKey masterKey;
    private SharedPreferences prefs;

    SecureStorage(Context context) {
        this.context = context.getApplicationContext();
    }

    // ---------------------------------------------------------------- inicialización

    private synchronized void ensureReady() throws GeneralSecurityException, IOException {
        if (prefs != null) return;
        try {
            open();
        } catch (GeneralSecurityException | IOException | RuntimeException first) {
            // Clave de Keystore invalidada o preferencias corruptas (p. ej. tras restaurar el
            // dispositivo): los datos ya no se pueden descifrar. Se reinicia el almacén para
            // que la app siga funcionando en vez de fallar siempre.
            Log.e(TAG, "Almacén cifrado inutilizable; se reinicia", first);
            reset();
            open();
        }
    }

    private void open() throws GeneralSecurityException, IOException {
        masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    private void reset() {
        prefs = null;
        masterKey = null;
        context.deleteSharedPreferences(PREFS_NAME);
        deleteChildren(photoDir());
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo borrar la clave maestra", e);
        }
    }

    // ---------------------------------------------------------------- bitácora

    /**
     * Lee la bitácora. Si existe un archivo JSON de una versión anterior (y fotos sin cifrar)
     * los migra al almacenamiento cifrado y elimina los originales solo si el guardado tuvo éxito.
     */
    synchronized List<Place> load() {
        List<Place> places = new java.util.ArrayList<>();
        try {
            ensureReady();
            String json = prefs.getString(KEY_DATA, null);
            File legacyFile = new File(context.getFilesDir(), LEGACY_DATA_FILE);
            boolean fromLegacyFile = false;
            if (json == null && legacyFile.exists()) {
                json = readText(legacyFile);
                fromLegacyFile = true;
            }
            if (json == null) return places;

            PlaceStore.Parsed parsed = PlaceStore.parse(json);
            places = parsed.places;
            boolean migratedPhotos = migrateLegacyPhotos(places);

            if (parsed.legacy || fromLegacyFile || migratedPhotos) {
                if (saveJson(PlaceStore.toJson(places))) {
                    if (fromLegacyFile && !legacyFile.delete()) Log.w(TAG, "No se pudo borrar el JSON antiguo");
                    deleteLegacyPhotoDir();
                }
            }
            deleteOrphanPhotos(places);
        } catch (Exception e) {
            Log.e(TAG, "No fue posible leer la bitácora cifrada", e);
        }
        return places;
    }

    synchronized boolean saveJson(String json) {
        try {
            ensureReady();
            boolean ok = prefs.edit().putString(KEY_DATA, json).commit();
            if (!ok) Log.e(TAG, "No fue posible guardar la bitácora cifrada");
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar la bitácora cifrada", e);
            return false;
        }
    }

    // ---------------------------------------------------------------- fotos

    /**
     * Procesa la foto tomada con la cámara (reduce, corrige la rotación EXIF, comprime) y la
     * guarda cifrada. Devuelve el nombre del archivo cifrado. No borra {@code source}.
     */
    synchronized String savePhoto(String placeId, File source) throws GeneralSecurityException, IOException {
        ensureReady();
        byte[] jpeg = encodeJpeg(source);
        String fileName = placeId + "_" + System.currentTimeMillis() + ".enc";
        writeEncrypted(new File(photoDir(), fileName), jpeg);
        return fileName;
    }

    /** Devuelve la foto descifrada y reducida a {@code maxSide} px, o null si falla. */
    synchronized Bitmap loadPhoto(String fileName, int maxSide) {
        if (!PlaceStore.isSafeFileName(fileName)) return null;
        try {
            ensureReady();
            File file = new File(photoDir(), fileName);
            if (!file.exists()) return null;
            EncryptedFile encrypted = encryptedFile(file);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = encrypted.openFileInput()) {
                byte[] chunk = new byte[16 * 1024];
                int read;
                while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
            }
            byte[] bytes = buffer.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo descifrar la foto", e);
            return null;
        }
    }

    synchronized void deletePhoto(String fileName) {
        if (!PlaceStore.isSafeFileName(fileName)) return;
        File file = new File(photoDir(), fileName);
        if (file.exists() && !file.delete()) Log.w(TAG, "No se pudo borrar " + fileName);
    }

    /** Borra las fotos cifradas que ningún lugar referencia. */
    private void deleteOrphanPhotos(List<Place> places) {
        Set<String> used = new HashSet<>();
        for (Place p : places) if (p.photoFile != null) used.add(p.photoFile);
        File[] files = photoDir().listFiles();
        if (files == null) return;
        for (File f : files) if (!used.contains(f.getName()) && !f.delete()) Log.w(TAG, "Huérfano sin borrar");
    }

    /** Limpia capturas temporales que hayan quedado de ejecuciones interrumpidas. */
    void clearCaptureCache(File keep) {
        File[] files = new File(context.getCacheDir(), "capture").listFiles();
        if (files == null) return;
        for (File f : files) {
            if (keep != null && f.getAbsolutePath().equals(keep.getAbsolutePath())) continue;
            if (!f.delete()) Log.w(TAG, "No se pudo borrar la captura temporal");
        }
    }

    // ---------------------------------------------------------------- migración

    private boolean migrateLegacyPhotos(List<Place> places) {
        boolean changed = false;
        for (Place p : places) {
            if (p.legacyPhoto == null) continue;
            File plain = new File(new File(context.getFilesDir(), LEGACY_PHOTO_DIR), p.legacyPhoto);
            if (plain.exists()) {
                try {
                    p.photoFile = savePhoto(p.id, plain);
                    changed = true;
                } catch (Exception e) {
                    Log.e(TAG, "No se pudo migrar una foto antigua", e);
                }
            }
            p.legacyPhoto = null;
        }
        return changed;
    }

    private void deleteLegacyPhotoDir() {
        File dir = new File(context.getFilesDir(), LEGACY_PHOTO_DIR);
        if (!dir.exists()) return;
        deleteChildren(dir);
        if (!dir.delete()) Log.w(TAG, "No se pudo borrar la carpeta de fotos antigua");
    }

    // ---------------------------------------------------------------- utilidades

    private File photoDir() {
        File dir = new File(context.getFilesDir(), PHOTO_DIR);
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "No se pudo crear " + dir);
        return dir;
    }

    private EncryptedFile encryptedFile(File file) throws GeneralSecurityException, IOException {
        return new EncryptedFile.Builder(context, file, masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
    }

    private void writeEncrypted(File target, byte[] data) throws GeneralSecurityException, IOException {
        if (target.exists() && !target.delete()) throw new IOException("No se pudo reemplazar " + target);
        EncryptedFile encrypted = encryptedFile(target);
        try (OutputStream out = encrypted.openFileOutput()) {
            out.write(data);
        }
    }

    private static String readText(File file) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] chunk = new byte[8 * 1024];
            int read;
            while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void deleteChildren(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) deleteChildren(f);
            if (!f.delete()) Log.w(TAG, "No se pudo borrar " + f.getName());
        }
    }

    private static byte[] encodeJpeg(File source) throws IOException {
        String path = source.getAbsolutePath();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, PHOTO_MAX_SIDE);
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IOException("La foto no se pudo leer");

        int rotation = exifRotation(path);
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            bitmap = rotated;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    private static int exifRotation(String path) {
        try {
            ExifInterface exif = new ExifInterface(path);
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    /** Mayor potencia de 2 que mantiene el lado mayor por encima de {@code maxSide}. */
    static int sampleSize(int width, int height, int maxSide) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / (sample * 2) >= maxSide) sample *= 2;
        return sample;
    }
}
