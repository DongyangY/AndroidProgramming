package com.example.dyyao.assignment_2nd;

import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private double initLat, initLng;
    private final String[] BUILDING_NAMES = {"Busch Campus Center", "HighPoint Solutions Stadium",
            "Electrical Engineering Building", "Rutgers Student Center", "Old Queens"};
    private final double[] BUILDING_LATS = {40.523128, 40.513817, 40.521663, 40.502661, 40.498720};
    private final double[] BUILDING_LNGS = {-74.458797, -74.464844, -74.460665, -74.451771, -74.446229};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        Intent intent = getIntent();
        initLat = intent.getDoubleExtra("initLat", 0);
        initLng = intent.getDoubleExtra("initLng", 0);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                mMap.getUiSettings().setCompassEnabled(true);
                mMap.getUiSettings().setZoomControlsEnabled(true);
                mMap.getUiSettings().setAllGesturesEnabled(true);
                setUpMap(mMap);
            }
        }
    }

    private void setUpMap(GoogleMap mMap) {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(initLat, initLng), 15));
        addBuildings(mMap);
        addHeadMap(mMap);
    }

    private void addBuildings(GoogleMap mMap) {
        for (int i = 0; i < BUILDING_NAMES.length; i++) {
            mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(BUILDING_LATS[i], BUILDING_LNGS[i]))
                            .title(BUILDING_NAMES[i])
                            .snippet("Distance: " + getDistanceFromInit(BUILDING_LATS[i], BUILDING_LNGS[i]) + " km"));
        }
    }

    private String getDistanceFromInit(double lat, double lng) {
        Location l1 = new Location("");
        l1.setLatitude(initLat);
        l1.setLongitude(initLng);
        Location l2 = new Location("");
        l2.setLatitude(lat);
        l2.setLongitude(lng);

        return String.valueOf(l1.distanceTo(l2) / 1000);
    }

    private void addHeadMap(GoogleMap mMap) {
        List<LatLng> list = new ArrayList<LatLng>();

        Cursor results = MainActivity.database.rawQuery("SELECT * FROM " + MainActivity.TABLE_NAME + ";", null);
        while (results.moveToNext()) {
            double latitude = results.getDouble(MainActivity.TableElement.Latitude.ordinal());
            double longitude = results.getDouble(MainActivity.TableElement.Longitude.ordinal());
            list.add(new LatLng(latitude, longitude));
        }

        if (list.size() != 0) {
            HeatmapTileProvider mProvider = new HeatmapTileProvider.Builder()
                    .data(list)
                    .build();
            TileOverlay mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
        }

    }
}
