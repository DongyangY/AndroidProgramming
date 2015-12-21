package com.example.dyyao.assignment_2nd;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private final String TAG = this.getClass().getSimpleName();

    public static final long AUTO_CHECK_IN_INTERVAL_IN_MILLISEC = 300000;  // 5 min
    public static final long LOCATION_UPDATE_INTERVAL_IN_MILLISEC = 2000;  // 2 s
    public static final long LOCATION_FASTEST_UPDATE_INTERVAL_IN_MILLISEC = LOCATION_UPDATE_INTERVAL_IN_MILLISEC / 2;
    public static final String DATABASE_NAME = "Localization";
    public static final String TABLE_NAME = "CheckIn";
    public static SQLiteDatabase database;

    public enum TableElement {ID, Time, Latitude, Longitude, Address}

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;
    private String mLastUpdateTime;
    private String mAddress;
    private AddressResultReceiver mResultReceiver;
    private long lastElapseRealTime;

    private long autoCheckInCounter;
    private File directory, file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "created");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mResultReceiver = new AddressResultReceiver(new Handler());
        database = openOrCreateDatabase(DATABASE_NAME, MODE_PRIVATE, null);
        //database.execSQL("DROP TABLE " + TABLE_NAME);
        database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + "(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "Time VARCHAR, Latitude DOUBLE, " +
                "Longitude DOUBLE, " +
                "Address VARCHAR);");

        buildGoogleApiClient();
        displayCheckIns();

        directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), getString(R.string.app_name));
        if (!directory.exists()) directory.mkdirs();
        file = new File(directory, "log_file.txt");
        if (!file.exists()) try {
            file.createNewFile();
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, "Log File Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "started");

        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "stopped");

        super.onStop();
        if (mGoogleApiClient.isConnected()) mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "connected");

        createLocationRequest();

        lastElapseRealTime = SystemClock.elapsedRealtimeNanos();
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "connection failed");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "location changed");

        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        updateLocation();

        if (!Geocoder.isPresent()) {
            Log.i(TAG, "geo-coder not available");
            return;
        }
        startIntentService();
    }

    public void autoCheckInSwitched(View view) {
        //Log.i(TAG, "switched");

        if(!((Switch)findViewById(R.id.check_in_auto)).isChecked()) {
            //Log.i(TAG, "turn off");
            autoCheckInCounter = 0;
        }
    }

    public void export(View view) {
        String databasePath = this.getApplicationInfo().dataDir + "/databases/";
        File currentDB = new File(databasePath, DATABASE_NAME);
        File sd = Environment.getExternalStorageDirectory();
        Log.i(TAG, sd.toString());
        File exportDB = new File(sd, DATABASE_NAME);
        FileChannel source = null, destination = null;
        try {
            source = new FileInputStream(currentDB).getChannel();
            destination = new FileOutputStream(exportDB).getChannel();
            destination.transferFrom(source, 0, source.size());
            source.close();
            destination.close();
            ((TextView)findViewById(R.id.info)).setText("exported to: " + sd.toString() + "/" + DATABASE_NAME);
        } catch (FileNotFoundException e) {
            Log.i(TAG, "export: file not found");
            Toast.makeText(MainActivity.this, "Wrong export path", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.i(TAG, "export: source size exception ");
            Toast.makeText(MainActivity.this, "Source file size error", Toast.LENGTH_SHORT).show();
        }
    }

    public void load(View view) {
        String databasePath = Environment.getExternalStorageDirectory().toString() + "/" + DATABASE_NAME;
        Log.i(TAG, databasePath);
        if (new File(databasePath).exists()) {
            database = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.CREATE_IF_NECESSARY);
            displayCheckIns();
            ((TextView)findViewById(R.id.info)).setText("imported from: " + databasePath);
        } else {
            Toast.makeText(MainActivity.this, "Database to be imported is not found", Toast.LENGTH_SHORT).show();
        }
    }

    public void checkIn(View view) {
        database = openOrCreateDatabase(DATABASE_NAME, MODE_PRIVATE, null);

        database.execSQL("INSERT INTO CheckIn(Time, Latitude, Longitude, Address) " +
                "VALUES('" + mLastUpdateTime + "'," +
                mCurrentLocation.getLatitude() + "," +
                mCurrentLocation.getLongitude() + ",'" + mAddress + "');");
        displayCheckIns();
    }

    public void showMap(View view) {

        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
            Toast.makeText(this, "client is not connected yet", Toast.LENGTH_SHORT);
            return;
        }

        Location initLoca = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        Intent intent = new Intent(this, MapsActivity.class);
        intent.putExtra("initLat", initLoca.getLatitude());
        intent.putExtra("initLng", initLoca.getLongitude());
        startActivity(intent);
    }

    private void displayCheckIns() {
        Cursor cursor = database.rawQuery("SELECT * FROM CheckIn", null);
        String[] from = new String[] {"Latitude", "Longitude"};
        int[] to = new int[] {R.id.check_in_latitude, R.id.check_in_longitude};
        SimpleCursorAdapter cursorAdapter = new SimpleCursorAdapter(this,
                R.layout.list_check_in, cursor, from, to);
        ((ListView)findViewById(R.id.list_check_in)).setAdapter(cursorAdapter);
    }

    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(LOCATION_UPDATE_INTERVAL_IN_MILLISEC);
        mLocationRequest.setFastestInterval(LOCATION_FASTEST_UPDATE_INTERVAL_IN_MILLISEC);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    private void log(long delay) {
        try {
            FileOutputStream writer = new FileOutputStream(file, true);
            writer.write((mLastUpdateTime + "," + (mCurrentLocation.hasAccuracy() ? mCurrentLocation.getAccuracy() : 0) + "," + delay + "\n").getBytes());
        } catch (FileNotFoundException e) {
            Toast.makeText(MainActivity.this, "File Output Error", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateLocation() {
        if (mCurrentLocation != null && mLastUpdateTime != null) {
            ((TextView)findViewById(R.id.updateTime)).setText("Update Time: " + mLastUpdateTime);
            ((TextView)findViewById(R.id.latitude)).setText("Latitude: " + String.valueOf(mCurrentLocation.getLatitude()));
            ((TextView)findViewById(R.id.longitude)).setText("Longitude: " + String.valueOf(mCurrentLocation.getLongitude()));
            ((TextView)findViewById(R.id.accuracy)).setText("Accuracy: " + (mCurrentLocation.hasAccuracy() ? mCurrentLocation.getAccuracy() : 0) + "m");
            long delay = (mCurrentLocation.getElapsedRealtimeNanos() - lastElapseRealTime) / 1000000; // millisecond
            lastElapseRealTime = mCurrentLocation.getElapsedRealtimeNanos();
            ((TextView)findViewById(R.id.delay)).setText("Delay: " + String.valueOf(delay) + "ms");

            if (((Switch)findViewById(R.id.record)).isChecked()) {
                log(delay);
            }
        }
    }

    private void startIntentService() {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(FetchAddressIntentService.RECEIVER, mResultReceiver);
        intent.putExtra(FetchAddressIntentService.LOCATION_DATA_EXTRA, mCurrentLocation);
        startService(intent);
    }

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mAddress = resultData.getString(FetchAddressIntentService.RESULT_DATA_KEY);
            ((TextView)findViewById(R.id.address)).setText("Address:\n" + mAddress);

            if (((Switch)findViewById(R.id.check_in_auto)).isChecked()) {
                if (autoCheckInCounter >= AUTO_CHECK_IN_INTERVAL_IN_MILLISEC / LOCATION_UPDATE_INTERVAL_IN_MILLISEC) {
                    checkIn(findViewById(R.id.check_in));
                    autoCheckInCounter = 0;
                } else {
                    autoCheckInCounter++;
                }
            }
        }
    }


}
