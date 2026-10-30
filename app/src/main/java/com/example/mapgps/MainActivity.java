package com.example.mapgps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Rumbo: una bitácora personal de lugares, coordenadas y recuerdos. */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "RumboMain";
    private static final String STATE_SELECTED = "selected_place";
    private static final String STATE_PENDING_FILE = "pending_file";
    private static final String STATE_PENDING_PLACE = "pending_place";
    private static final String STATE_PENDING_NEW = "pending_new";
    private static final String STATE_PENDING_LAT = "pending_lat";
    private static final String STATE_PENDING_LNG = "pending_lng";
    private static final float HUE_DEFAULT = 148f;
    private static final float HUE_SELECTED = BitmapDescriptorFactory.HUE_ORANGE;

    /** Todo el acceso a disco y criptografía va por este hilo, en orden. */
    private final ExecutorService diskWorker = Executors.newSingleThreadExecutor();
    private final List<Place> places = new ArrayList<>();
    private final Map<String, Marker> markers = new HashMap<>();
    private SecureStorage storage;
    private boolean placesLoaded;
    private boolean persistPending;

    private GoogleMap map;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private boolean locationUpdatesStarted;
    private LatLng currentLocation;

    private View root, topPanel, bottomPanel, emptyPhotoBadge;
    private TextView locationText, placesCount;
    private FloatingActionButton mapExpandButton;
    private ImageView photoPreview;
    private boolean mapExpanded;

    private String selectedPlaceId;
    private String lastPhotoPlaceId;
    private String previewPlaceId;
    private int previewToken;

    // Estado de una captura en curso (se guarda para sobrevivir a rotación o cierre del proceso).
    private File pendingFile;
    private String pendingPlaceId;
    private boolean pendingNewPlace;
    private double pendingLat, pendingLng;

    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    locateUser();
                } else {
                    locationText.setText(R.string.location_off);
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        showSettingsSnackbar(R.string.location_permission_blocked);
                    }
                }
            });
    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    clearPending(true);
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                        showSettingsSnackbar(R.string.camera_permission_blocked);
                    } else {
                        Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), this::onPictureTaken);

    // ================================================================== ciclo de vida

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        locationText = findViewById(R.id.locationText);
        placesCount = findViewById(R.id.placesCount);
        topPanel = findViewById(R.id.topPanel);
        bottomPanel = findViewById(R.id.bottomPanel);
        mapExpandButton = findViewById(R.id.mapExpandButton);
        photoPreview = findViewById(R.id.photoPreview);
        emptyPhotoBadge = findViewById(R.id.emptyPhotoBadge);

        storage = new SecureStorage(this);
        restoreState(savedInstanceState);

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) showLiveLocation(location);
            }
        };

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
        findViewById(R.id.myLocationButton).setOnClickListener(v -> requestLocation());
        mapExpandButton.setOnClickListener(v -> toggleMapExpanded());
        findViewById(R.id.deletePlacesButton).setOnClickListener(v -> showClearPointsConfirmation());
        ((MaterialButton) findViewById(R.id.cameraButton)).setOnClickListener(v -> requestCamera());
        photoPreview.setOnClickListener(v -> openPreviewPlace());

        refreshPreview();
        if (!hasMapsKey()) {
            Snackbar.make(root, R.string.maps_key_missing, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, v -> { }).show();
        }

        loadPlaces();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED, selectedPlaceId);
        if (pendingFile != null) {
            outState.putString(STATE_PENDING_FILE, pendingFile.getAbsolutePath());
            outState.putString(STATE_PENDING_PLACE, pendingPlaceId);
            outState.putBoolean(STATE_PENDING_NEW, pendingNewPlace);
            outState.putDouble(STATE_PENDING_LAT, pendingLat);
            outState.putDouble(STATE_PENDING_LNG, pendingLng);
        }
    }

    private void restoreState(@Nullable Bundle state) {
        if (state == null) return;
        selectedPlaceId = state.getString(STATE_SELECTED);
        String path = state.getString(STATE_PENDING_FILE);
        if (path != null) {
            pendingFile = new File(path);
            pendingPlaceId = state.getString(STATE_PENDING_PLACE);
            pendingNewPlace = state.getBoolean(STATE_PENDING_NEW);
            pendingLat = state.getDouble(STATE_PENDING_LAT);
            pendingLng = state.getDouble(STATE_PENDING_LNG);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (map != null && hasLocationPermission()) startLocationUpdates(hasFineLocation());
    }

    @Override protected void onPause() {
        stopLocationUpdates();
        super.onPause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        diskWorker.shutdown(); // las tareas ya encoladas (guardados) terminan antes de cerrar
    }

    private boolean isUiGone() { return isFinishing() || isDestroyed(); }

    // ================================================================== mapa

    @Override public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        applyMapPadding();
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.rumbo_map_style));
        } catch (Exception e) {
            android.util.Log.w(TAG, "No se pudo aplicar el estilo del mapa", e);
        }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(-35.6751, -71.5430), 4.8f));
        map.setOnMapClickListener(this::onMapTapped);
        map.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof String) selectPlace((String) tag);
            return false; // comportamiento por defecto: centra y muestra la ventana de información
        });
        map.setOnInfoWindowClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof String) showPlaceDialog((String) tag);
        });
        renderAllIfReady();
        requestLocation();
    }

    private void applyMapPadding() {
        if (map == null) return;
        map.setPadding(dp(mapExpanded ? 8 : 12), dp(mapExpanded ? 8 : 126),
                dp(mapExpanded ? 8 : 72), dp(mapExpanded ? 8 : 166));
    }

    private void toggleMapExpanded() {
        mapExpanded = !mapExpanded;
        topPanel.setVisibility(mapExpanded ? View.GONE : View.VISIBLE);
        bottomPanel.setVisibility(mapExpanded ? View.GONE : View.VISIBLE);
        mapExpandButton.setImageResource(mapExpanded ? R.drawable.ic_collapse_map : R.drawable.ic_expand_map);
        mapExpandButton.setContentDescription(getString(mapExpanded ? R.string.collapse_map : R.string.expand_map));
        applyMapPadding();
    }

    private void renderAllIfReady() {
        if (map == null || !placesLoaded) return;
        map.clear();
        markers.clear();
        for (Place p : places) addMarker(p);
        updateCount();
    }

    private void addMarker(Place p) {
        if (map == null) return;
        Marker marker = map.addMarker(new MarkerOptions()
                .position(new LatLng(p.lat, p.lng))
                .title(p.name)
                .snippet(snippet(p))
                .icon(BitmapDescriptorFactory.defaultMarker(p.id.equals(selectedPlaceId) ? HUE_SELECTED : HUE_DEFAULT)));
        if (marker != null) {
            marker.setTag(p.id);
            markers.put(p.id, marker);
        }
    }

    private void refreshMarker(Place p) {
        Marker marker = markers.get(p.id);
        if (marker == null) return;
        marker.setTitle(p.name);
        marker.setSnippet(snippet(p));
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(p.id.equals(selectedPlaceId) ? HUE_SELECTED : HUE_DEFAULT));
        if (marker.isInfoWindowShown()) marker.showInfoWindow();
    }

    private String snippet(Place p) {
        String coords = coordinates(p.lat, p.lng);
        return p.photoFile != null ? getString(R.string.snippet_with_photo, coords) : coords;
    }

    private String coordinates(double lat, double lng) {
        return String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng);
    }

    private void selectPlace(@Nullable String id) {
        Place previous = findPlace(selectedPlaceId);
        selectedPlaceId = id;
        if (previous != null) refreshMarker(previous);
        Place now = findPlace(id);
        if (now != null) refreshMarker(now);
        refreshPreview();
    }

    private void updateCount() {
        placesCount.setText(getResources().getQuantityString(R.plurals.points_count, places.size(), places.size()));
    }

    // ================================================================== lugares

    @Nullable private Place findPlace(@Nullable String id) {
        if (id == null) return null;
        for (Place p : places) if (p.id.equals(id)) return p;
        return null;
    }

    private void onMapTapped(LatLng point) {
        Place place = Place.create(point.latitude, point.longitude);
        place.name = PlaceStore.uniqueDefaultName(places);
        places.add(place);
        addMarker(place);
        selectPlace(place.id);
        updateCount();
        persist();
        Snackbar bar = Snackbar.make(root, getString(R.string.place_saved, coordinates(place.lat, place.lng)), Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> removePlace(place.id));
        if (bottomPanel.getVisibility() == View.VISIBLE) bar.setAnchorView(bottomPanel);
        bar.show();
    }

    private void removePlace(String id) {
        Place place = findPlace(id);
        if (place == null) return;
        places.remove(place);
        Marker marker = markers.remove(id);
        if (marker != null) marker.remove();
        if (id.equals(selectedPlaceId)) selectedPlaceId = null;
        if (id.equals(lastPhotoPlaceId)) lastPhotoPlaceId = null;
        updateCount();
        persist();
        deletePhotoFile(place.photoFile);
        refreshPreview();
    }

    private void deletePhotoFile(@Nullable String fileName) {
        if (fileName == null) return;
        diskWorker.execute(() -> storage.deletePhoto(fileName)); // encolado después del guardado
    }

    private void showClearPointsConfirmation() {
        if (places.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_delete, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_confirm, (dialog, which) -> clearAllPoints())
                .show();
    }

    private void clearAllPoints() {
        List<String> photoFiles = new ArrayList<>();
        for (Place p : places) if (p.photoFile != null) photoFiles.add(p.photoFile);
        places.clear();
        markers.clear();
        selectedPlaceId = null;
        lastPhotoPlaceId = null;
        if (map != null) map.clear();
        updateCount();
        persist();
        for (String file : photoFiles) deletePhotoFile(file);
        refreshPreview();
        Toast.makeText(this, R.string.all_deleted, Toast.LENGTH_SHORT).show();
    }

    // ================================================================== detalle de lugar

    private void showPlaceDialog(String id) {
        Place place = findPlace(id);
        if (place == null || isUiGone()) return;
        View view = getLayoutInflater().inflate(R.layout.dialog_place, null);
        ImageView photo = view.findViewById(R.id.dialogPhoto);
        TextView coords = view.findViewById(R.id.dialogCoordinates);
        TextInputEditText nameInput = view.findViewById(R.id.nameInput);
        TextInputEditText noteInput = view.findViewById(R.id.noteInput);
        MaterialButton photoButton = view.findViewById(R.id.dialogPhotoButton);
        MaterialButton removePhotoButton = view.findViewById(R.id.dialogRemovePhotoButton);

        coords.setText(coordinates(place.lat, place.lng));
        nameInput.setText(place.name);
        noteInput.setText(place.note);
        photoButton.setText(place.photoFile == null ? R.string.take_photo : R.string.change_photo);
        removePhotoButton.setVisibility(place.photoFile == null ? View.GONE : View.VISIBLE);
        if (place.photoFile != null) {
            photo.setVisibility(View.VISIBLE);
            loadPhotoAsync(place.photoFile, 900, bitmap -> {
                if (bitmap != null) photo.setImageBitmap(bitmap);
                else photo.setVisibility(View.GONE);
            });
        } else {
            photo.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.place_details)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> applyEdits(place, nameInput, noteInput))
                .setNeutralButton(R.string.delete, (d, w) -> confirmDeletePlace(place))
                .setNegativeButton(R.string.close, null)
                .create();
        photoButton.setOnClickListener(v -> {
            applyEdits(place, nameInput, noteInput);
            dialog.dismiss();
            startPhotoFor(place);
        });
        removePhotoButton.setOnClickListener(v -> {
            applyEdits(place, nameInput, noteInput);
            dialog.dismiss();
            removePhoto(place);
        });
        dialog.show();
    }

    /** Valida y guarda nombre y nota (longitud máxima, sin caracteres de control). */
    private void applyEdits(Place place, TextInputEditText nameInput, TextInputEditText noteInput) {
        String name = PlaceStore.sanitizeName(nameInput.getText() == null ? "" : nameInput.getText().toString());
        if (!name.isEmpty()) place.name = name;
        place.note = PlaceStore.sanitizeNote(noteInput.getText() == null ? "" : noteInput.getText().toString());
        refreshMarker(place);
        persist();
    }

    private void confirmDeletePlace(Place place) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_place_title)
                .setMessage(getString(R.string.delete_place_message, place.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> removePlace(place.id))
                .show();
    }

    private void removePhoto(Place place) {
        String old = place.photoFile;
        if (old == null) return;
        place.photoFile = null;
        refreshMarker(place);
        persist();
        deletePhotoFile(old);
        refreshPreview();
        Toast.makeText(this, R.string.photo_removed, Toast.LENGTH_SHORT).show();
    }

    // ================================================================== fotos

    /** Botón "Foto": usa el lugar seleccionado o, si no hay, crea uno en la ubicación actual. */
    private void requestCamera() {
        Place selected = findPlace(selectedPlaceId);
        if (selected != null) {
            startPhotoFor(selected);
        } else if (currentLocation != null) {
            beginCapture(UUID.randomUUID().toString(), true, currentLocation.latitude, currentLocation.longitude);
        } else {
            Toast.makeText(this, R.string.need_location_or_place, Toast.LENGTH_LONG).show();
        }
    }

    private void startPhotoFor(Place place) {
        beginCapture(place.id, false, place.lat, place.lng);
    }

    private void beginCapture(String placeId, boolean isNew, double lat, double lng) {
        pendingPlaceId = placeId;
        pendingNewPlace = isNew;
        pendingLat = lat;
        pendingLng = lng;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File folder = new File(getCacheDir(), "capture");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("No se pudo crear la carpeta temporal");
            File file = new File(folder, "capture_" + System.currentTimeMillis() + ".jpg");
            if (!file.createNewFile()) throw new IOException("No se pudo crear el archivo temporal");
            pendingFile = file;
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            takePicture.launch(uri);
        } catch (Exception e) {
            android.util.Log.e(TAG, "No se pudo abrir la cámara", e);
            clearPending(true);
            Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void onPictureTaken(boolean success) {
        File source = pendingFile;
        String placeId = pendingPlaceId;
        boolean isNew = pendingNewPlace;
        double lat = pendingLat, lng = pendingLng;
        clearPending(false);
        if (source == null || placeId == null) return;
        if (!success) {
            //noinspection ResultOfMethodCallIgnored
            source.delete();
            return;
        }
        Toast.makeText(this, R.string.saving_photo, Toast.LENGTH_SHORT).show();
        diskWorker.execute(() -> {
            String stored = null;
            try {
                stored = storage.savePhoto(placeId, source);
            } catch (Exception e) {
                android.util.Log.e(TAG, "No se pudo guardar la foto cifrada", e);
            }
            //noinspection ResultOfMethodCallIgnored
            source.delete();
            final String fileName = stored;
            runOnUiThread(() -> onPhotoStored(placeId, isNew, lat, lng, fileName));
        });
    }

    private void onPhotoStored(String placeId, boolean isNew, double lat, double lng, @Nullable String fileName) {
        if (fileName == null) {
            if (!isUiGone()) Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Place place = findPlace(placeId);
        if (place == null && isNew) {
            place = new Place(placeId, lat, lng, System.currentTimeMillis());
            place.name = PlaceStore.uniqueDefaultName(places);
            places.add(place);
            addMarker(place);
            updateCount();
        }
        if (place == null) { // el lugar se borró mientras se procesaba la foto
            deletePhotoFile(fileName);
            return;
        }
        String old = place.photoFile;
        place.photoFile = fileName;
        lastPhotoPlaceId = place.id;
        selectPlace(place.id);
        refreshMarker(place);
        persist();
        deletePhotoFile(old);
        if (isUiGone()) return;
        Toast.makeText(this, R.string.photo_saved, Toast.LENGTH_SHORT).show();
        if (map != null) map.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(place.lat, place.lng)));
        showPlaceDialog(place.id);
    }

    private void clearPending(boolean deleteFile) {
        if (deleteFile && pendingFile != null) {
            //noinspection ResultOfMethodCallIgnored
            pendingFile.delete();
        }
        pendingFile = null;
        pendingPlaceId = null;
        pendingNewPlace = false;
    }

    private void loadPhotoAsync(String fileName, int maxSide, Consumer<Bitmap> callback) {
        diskWorker.execute(() -> {
            Bitmap bitmap = storage.loadPhoto(fileName, maxSide);
            runOnUiThread(() -> { if (!isUiGone()) callback.accept(bitmap); });
        });
    }

    /** Miniatura del panel inferior: foto del lugar seleccionado o la más reciente. */
    private void refreshPreview() {
        Place target = findPlace(selectedPlaceId);
        if (target == null || target.photoFile == null) target = findPlace(lastPhotoPlaceId);
        if (target == null || target.photoFile == null) {
            target = null;
            for (int i = places.size() - 1; i >= 0; i--) {
                if (places.get(i).photoFile != null) { target = places.get(i); break; }
            }
        }
        final int token = ++previewToken;
        if (target == null) {
            previewPlaceId = null;
            photoPreview.setVisibility(View.GONE);
            emptyPhotoBadge.setVisibility(View.VISIBLE);
            return;
        }
        previewPlaceId = target.id;
        loadPhotoAsync(target.photoFile, 256, bitmap -> {
            if (token != previewToken) return; // llegó una actualización más nueva
            if (bitmap != null) {
                photoPreview.setImageBitmap(bitmap);
                photoPreview.setVisibility(View.VISIBLE);
                emptyPhotoBadge.setVisibility(View.GONE);
            } else {
                photoPreview.setVisibility(View.GONE);
                emptyPhotoBadge.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openPreviewPlace() {
        Place place = findPlace(previewPlaceId);
        if (place == null) return;
        selectPlace(place.id);
        if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(place.lat, place.lng), 16f));
        showPlaceDialog(place.id);
    }

    // ================================================================== persistencia

    private void loadPlaces() {
        final File captureInProgress = pendingFile;
        diskWorker.execute(() -> {
            storage.clearCaptureCache(captureInProgress);
            List<Place> loaded = storage.load();
            runOnUiThread(() -> {
                if (isUiGone()) return;
                places.addAll(0, loaded); // por si el usuario ya agregó algún punto durante la carga
                placesLoaded = true;
                if (persistPending) persist();
                renderAllIfReady();
                updateCount();
                refreshPreview();
            });
        });
    }

    /** Guarda una copia de la bitácora completa; la instantánea se toma aquí, en el hilo principal. */
    private void persist() {
        if (!placesLoaded) { // no sobrescribir lo guardado antes de haberlo leído
            persistPending = true;
            return;
        }
        persistPending = false;
        final String json = PlaceStore.toJson(places);
        diskWorker.execute(() -> storage.saveJson(json));
    }

    // ================================================================== ubicación

    private boolean hasFineLocation() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return hasFineLocation()
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocation() {
        if (!hasLocationPermission()) {
            locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        locationText.setText(R.string.getting_location);
        locateUser();
    }

    private void locateUser() {
        if (map == null || !hasLocationPermission()) return;
        boolean fine = hasFineLocation();
        try {
            map.setMyLocationEnabled(true);
            startLocationUpdates(fine);
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(fine ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(10_000).setDurationMillis(20_000).build();
            locationClient.getCurrentLocation(request, new CancellationTokenSource().getToken())
                    .addOnSuccessListener(this, location -> {
                        if (location == null) {
                            locationText.setText(R.string.no_gps_signal);
                            return;
                        }
                        showLiveLocation(location);
                        if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f));
                    })
                    .addOnFailureListener(this, error -> locationText.setText(R.string.location_failed));
        } catch (SecurityException e) {
            locationText.setText(R.string.location_off);
        }
    }

    private void showLiveLocation(Location location) {
        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        locationText.setText(getString(R.string.live_location, location.getLatitude(), location.getLongitude(), location.getAccuracy()));
    }

    private void startLocationUpdates(boolean fineLocation) {
        if (locationUpdatesStarted || locationCallback == null) return;
        LocationRequest request = new LocationRequest.Builder(
                fineLocation ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5_000L)
                .setMinUpdateIntervalMillis(2_000L)
                .setMaxUpdateDelayMillis(10_000L)
                .build();
        locationUpdatesStarted = true;
        try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                    .addOnFailureListener(this, error -> locationUpdatesStarted = false);
        } catch (SecurityException e) {
            locationUpdatesStarted = false;
        }
    }

    private void stopLocationUpdates() {
        if (!locationUpdatesStarted || locationCallback == null) return;
        locationClient.removeLocationUpdates(locationCallback);
        locationUpdatesStarted = false;
    }

    // ================================================================== utilidades

    private void showSettingsSnackbar(int messageRes) {
        Snackbar.make(root, messageRes, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings, v -> startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))))
                .show();
    }

    /** Detecta si la app se compiló sin clave real de Google Maps (mapa gris). */
    @SuppressWarnings("deprecation")
    private boolean hasMapsKey() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String key = info.metaData == null ? null : info.metaData.getString("com.google.android.geo.API_KEY");
            return key != null && !key.trim().isEmpty() && !"YOUR_API_KEY".equals(key.trim());
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
