package com.example.dyyao.assignment_4th;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private String TAG = "Assignment 4";

    private static final float THRESHOLD_ACCE_MAGNITUDE = 10.6f;

    private Size mPreviewSize;
    private Size mVideoSize;
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private File mDirectory;
    private File file;
    private File logFile;
    private AddressResultReceiver mResultReceiver;
    private String mAddress;
    private int cameraIndex;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private SensorManager sensorManager;
    private Sensor sensor;
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;
    private Handler backgroundHandler;
    private SensorEventListener sensorEventListener;
    private boolean isCaptureAfterRecord;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "google play service connected");
        createLocationRequest();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "google play service suspended");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "google play service update location");
        Log.i(TAG, "Lat: " + location.getLatitude() + "Lng: " + location.getLongitude());
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        if (!Geocoder.isPresent()) {
            Log.i(TAG, "geo-coder not available");
            return;
        }

        startIntentService(location);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "google play service connection failed");
    }

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            Log.i(TAG, "address received");
            showLastCapture();
            mAddress = resultData.getString(FetchAddressIntentService.RESULT_DATA_KEY);
            ((TextView)findViewById(R.id.address)).setText(mAddress);
        }
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.i(TAG, "texture width: " + width + " height: " + height);
            openCamera(cameraIndex);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }

    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, "onOpened");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.i(TAG, "onDisconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.i(TAG, "onError");
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView)findViewById(R.id.preview);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        mResultReceiver = new AddressResultReceiver(new Handler());

        buildGoogleApiClient();

        //setUpLocationUpdate();

        setUpSensorUpdate();

        mDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), getString(R.string.app_name));
        if (!mDirectory.exists()) mDirectory.mkdirs();
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "started");

        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        try {
            mPreviewSession.stopRepeating();
            mPreviewSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        sensorManager.unregisterListener(sensorEventListener);

        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "stopped");

        super.onStop();
        if (mGoogleApiClient.isConnected()) mGoogleApiClient.disconnect();
    }

    private void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(500);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    public void onLog(View view) {
        Switch sw = (Switch) findViewById(R.id.log);
        if (sw.isChecked()) {
            Calendar calendar = Calendar.getInstance();
            String date = new SimpleDateFormat("MM_dd_yyyy_HH_mm_ss").format(calendar.getTime());
            logFile = new File(mDirectory, date + ".txt");
        }
    }

    private void setUpSensorUpdate() {
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor sensor = event.sensor;

                if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                    float acceleration_x = event.values[0];
                    float acceleration_y = event.values[1];
                    float acceleration_z = event.values[2];
                    ((TextView) findViewById(R.id.acceleration)).setText(
                            "acceleration:\nx: " + acceleration_x + "\ny: " +
                                    acceleration_y + "\nz: " + acceleration_z
                    );

                    Switch sw = (Switch) findViewById(R.id.log);
                    if (sw.isChecked()) {
                        try {
                            FileOutputStream writer = new FileOutputStream(logFile, true);
                            writer.write((acceleration_x + "," + acceleration_y + "," + acceleration_z + "\n").getBytes());
                        } catch (FileNotFoundException e) {
                            Toast.makeText(MainActivity.this, "File Output Error", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    float magnitude = (float)Math.sqrt(acceleration_x * acceleration_x +
                            acceleration_y * acceleration_y + acceleration_z * acceleration_z);

                    if (!mIsRecordingVideo && magnitude >= THRESHOLD_ACCE_MAGNITUDE) {
                        Toast.makeText(MainActivity.this, "Video recording started", Toast.LENGTH_SHORT).show();
                        startRecordingVideo();

                        // in practice, 10000 only get 9s video
                        new CountDownTimer(11000, 1000) {
                            private TextView textView = (TextView)findViewById(R.id.recording_status);

                            public void onTick(long millisUntilFinished) {
                                textView.setVisibility(View.VISIBLE);
                                textView.setText(String.valueOf(millisUntilFinished / 1000));
                            }

                            public void onFinish() {
                                textView.setVisibility(View.INVISIBLE);
                                stopRecordingVideo();
                                isCaptureAfterRecord = true;
                            }
                        }.start();
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
    }

    private void setUpLocationUpdate() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.i(TAG, "onLocationChanged");

                if (!Geocoder.isPresent()) {
                    Log.i(TAG, "geo-coder not available");
                    return;
                }

                startIntentService(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.i(TAG, "onStatusChanged");
            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.i(TAG, "onProviderEnabled");
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.i(TAG, "onProviderDisabled");
            }
        };
    }

    private Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.i(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private void openCamera(int camera) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[camera];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // could change to choose optimal preview size later
            mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }

    }

    private void setUpMediaRecorder() throws IOException {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(getVideoFile(this).getAbsolutePath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        Log.i(TAG, "video size width: " + mVideoSize.getWidth() + " height: " + mVideoSize.getHeight());
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
    }

    private void startRecordingVideo() {
        try {
            mIsRecordingVideo = true;
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingVideo() {
        try {
            mPreviewSession.stopRepeating();
            mPreviewSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mIsRecordingVideo = false;

        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        Toast.makeText(this, "Saved: " + getVideoFile(this),
                Toast.LENGTH_SHORT).show();

        startPreview();
    }

    private File getVideoFile(Context context) {
        return new File(mDirectory, "video.mp4");
    }

    protected void startPreview() {

        if(null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            Log.i(TAG, "startPreview fail, return");
            return;
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if(null == texture) {
            Log.i(TAG,"texture is null, return");
            return;
        }

        try {
            setUpMediaRecorder();
        } catch (IOException e) {
            e.printStackTrace();
        }

        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mPreviewBuilder.addTarget(surface);

        Surface recorderSurface = mMediaRecorder.getSurface();
        mPreviewBuilder.addTarget(recorderSurface);

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface, recorderSurface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mPreviewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.i(TAG, "session failed");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {

        if(null == mCameraDevice) {
            Log.i(TAG, "updatePreview error, return");
        }

        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        backgroundHandler = new Handler(thread.getLooper());

        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if (isCaptureAfterRecord) {
            takePicture();
            isCaptureAfterRecord = false;
        }
    }

    public void capture(View view) {
        takePicture();
    }

    public void showLastCapture() {
        ImageView imageView = ((ImageView) findViewById(R.id.last_capture));
        imageView.setVisibility(View.VISIBLE);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8;
        Bitmap bitmap = BitmapFactory.decodeFile(String.valueOf(file), options);
        imageView.setImageBitmap(bitmap);
    }

    private void startIntentService(Location location) {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(FetchAddressIntentService.RECEIVER, mResultReceiver);
        intent.putExtra(FetchAddressIntentService.LOCATION_DATA_EXTRA, location);
        startService(intent);
    }

    public void switchCamera(View view) {
        cameraIndex = cameraIndex == 0 ? 1 : 0;

        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        openCamera(cameraIndex);
    }

    public void takePicture() {
        if(null == mCameraDevice) {
            Log.i(TAG, "mCameraDevice is null, return");
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());

            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            }

            int width = 640;
            int height = 480;

            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            Log.i(TAG, "JPEG width: " + width + " height: " + height);

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            Calendar calendar = Calendar.getInstance();
            String date = new SimpleDateFormat("MM_dd_yyyy_HH_mm_ss").format(calendar.getTime());
            //Log.i(TAG, date);
            file = new File(mDirectory, date + ".jpg");

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.i(TAG, "onImageAvailable");
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                    Log.i(TAG, "save finished");

                    try {
                        // use google play service instead
                        //locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null);
                        startLocationUpdates();
                    } catch (SecurityException e) {
                        Log.i(TAG, "location exception");
                    }

                }

            };

            HandlerThread thread = new HandlerThread("CameraPicture");
            thread.start();
            backgroundHandler = new Handler(thread.getLooper());
            reader.setOnImageAvailableListener(readerListener, backgroundHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request, TotalCaptureResult result) {

                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "onCaptureCompleted");
                    mMediaRecorder.reset();
                    startPreview();
                }

            };

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {

                    try {
                        session.capture(captureBuilder.build(), captureListener, backgroundHandler);
                    } catch (CameraAccessException e) {

                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }


}
