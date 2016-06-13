# AndroidProgramming

Android Assignments: 1) robust download 2) localization and the daily path 3) touchscreen gestures 4) capture, video and sensors.

## Robust download

Use [DownloadManager](http://developer.android.com/reference/android/app/DownloadManager.html) to download files robustly.

> Feature

1. Download file only in certain networks, i.e., given SSIDs. 
2. Progress bar indicating the bytes received in total bytes.
3. Automatically continue downloading when the network is resumed.
4. Measure the latency and throughput.
5. Log the connection time for different networks.

> Screen shot

<img src="https://github.com/DongyangY/AndroidProgramming/blob/master/Robust-Download/Screenshot.png" width="250" height="400" />

## Localization and the daily path

Use [Google play service](http://developer.android.com/training/location/index.html) to get the geo-location.

> Feature

1. Periodically update the phone location.
2. Fetch the address of the location.
3. Check in to record the location of a spot.
4. Export and import the check-ins.
5. Mark buildings on Google Map.
6. Draw heatmap based on check-ins on Google Map.
7. Calculate the distance between phone and buildings.

> Screen shot

<img src="https://github.com/DongyangY/AndroidProgramming/blob/master/Localization-and-the-Daily-Path/Screenshot1.png" width="250" height="400" />

<img src="https://github.com/DongyangY/AndroidProgramming/blob/master/Localization-and-the-Daily-Path/Screenshot2.png" width="250" height="400" />

## Touchscreen gestures

Use [MotionEvent](http://developer.android.com/reference/android/view/MotionEvent.html) to track multi-finger and draw.

> Feature

1. Track multiple fingers and show ids and coordinates.
2. Draw lines.

> Screen shot

<img src="https://github.com/DongyangY/AndroidProgramming/blob/master/Touchscreen-Gestures/Screenshot1.png" width="250" height="400" />

<img src="https://github.com/DongyangY/AndroidProgramming/blob/master/Touchscreen-Gestures/Screenshot2.png" width="250" height="400" />

## Capture, video and sensors

Use [camera2](http://developer.android.com/reference/android/hardware/camera2/package-summary.html) to take a photo and record a video, [SensorManager](http://developer.android.com/reference/android/hardware/SensorManager.html) to obtain 3-axis accelerometer data. 

> Feature 

1. Preview the camera.
2. Capture a picture to save locally and display on the screen.
3. Fetch the address of the picture.
4. Switch the front and back camera.
5. Display the accelerations in three axis. 
6. Record a 10s video after detecting the magnitude of acceleration vector > threshold.

> Screen shot

<img src="https://github.com/DongyangY/AndroidProgramming/blob/master/Capture-Video-Sensors/screenshot.jpg" width="250" height="400" />
