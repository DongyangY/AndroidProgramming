package com.example.dyyao.assignment_4th;

import android.app.IntentService;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FetchAddressIntentService extends IntentService {

    private static final String TAG = "AddressFetcher";
    public static final int SUCCESS_RESULT = 0;
    public static final int FAILURE_RESULT = 1;
    public static final String PACKAGE_NAME =
            "com.example.dyyao.assignment_4th";
    public static final String RECEIVER = PACKAGE_NAME + ".RECEIVER";
    public static final String RESULT_DATA_KEY = PACKAGE_NAME + ".RESULT_DATA_KEY";
    public static final String LOCATION_DATA_EXTRA = PACKAGE_NAME + ".LOCATION_DATA_EXTRA";

    private ResultReceiver mReceiver;

    public FetchAddressIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "start geocoder");

        String error = "";

        mReceiver = intent.getParcelableExtra(RECEIVER);

        if (mReceiver == null) {
            Log.i(TAG, "no receiver");
            return;
        }

        Location location = intent.getParcelableExtra(LOCATION_DATA_EXTRA);

        if (location == null) {
            Log.i(TAG, "no location");
            deliverResultToReceiver(FAILURE_RESULT, "no location");
            return;
        }

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        List<Address> addresses = null;

        try {
            addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
        } catch (IOException e) {
            Log.i(TAG, e.getMessage());
            error = e.getMessage();
        }

        if (addresses == null || addresses.size() == 0) {
            if (error.isEmpty()) {
                error = "no address matched";
                Log.i(TAG, error);
            }
            deliverResultToReceiver(FAILURE_RESULT, error);
        } else {
            Address address = addresses.get(0);
            ArrayList<String> addressFragments = new ArrayList<String>();
            for(int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                addressFragments.add(address.getAddressLine(i));
            }
            Log.i(TAG, "address matched");
            deliverResultToReceiver(SUCCESS_RESULT,
                    TextUtils.join(System.getProperty("line.separator"), addressFragments));
        }
    }

    private void deliverResultToReceiver(int resultCode, String message) {
        Bundle bundle = new Bundle();
        bundle.putString(RESULT_DATA_KEY, message);
        mReceiver.send(resultCode, bundle);
    }
}
