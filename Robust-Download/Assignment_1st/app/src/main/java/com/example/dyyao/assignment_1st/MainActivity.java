package com.example.dyyao.assignment_1st;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends Activity {

    private String TAG = this.getClass().getSimpleName();

    private DownloadManager downloadManager;
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private NetworkChangedReceiver receiver;
    private SQLiteDatabase database;
    private File directory, file;

    private enum Network {All, WifiOnly, RUOnly}
    private final String[] RUNETWORKS = {"RUWireless_Secure", "RUWireless", "LAWN", "ECE", "RU_WIRELESS"};
    //private final String[] RUNETWORKS = {"RUWireless_Secure"};
    private String currentNetworkName = "nothing";
    private Queue<String> RUQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        downloadManager = (DownloadManager) MainActivity.this.getSystemService(Context.DOWNLOAD_SERVICE);
        wifiManager = (WifiManager) MainActivity.this.getSystemService(WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) MainActivity.this.getSystemService(CONNECTIVITY_SERVICE);
        receiver = new NetworkChangedReceiver();
        registerReceiver(receiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        database = openOrCreateDatabase("RobustDownload", MODE_PRIVATE, null);
        //database.execSQL("DROP TABLE Log");
        database.execSQL("CREATE TABLE IF NOT EXISTS Log(ID INTEGER PRIMARY KEY AUTOINCREMENT, Connection_Time VARCHAR, Time_Seconds LONG, Network_Name VARCHAR, Duration_Seconds VARCHAR);");
        directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), getString(R.string.app_name));
        if (!directory.exists()) directory.mkdirs();
        file = new File(directory, "MeasurementLog.txt");
        if (!file.exists()) try {
            file.createNewFile();
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, "Log File Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    public class NetworkChangedReceiver extends BroadcastReceiver {

        public void logNetwork(String networkName) {
            Calendar calendar = Calendar.getInstance();
            String date = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a").format(calendar.getTime());
            long second = calendar.getTimeInMillis() / 1000;

            Cursor results = database.rawQuery("SELECT * FROM Log;", null);
            if (results.moveToLast() && results.getString(4).equals("Ongoing")) {
                long presecond = Long.valueOf(results.getString(2));
                database.execSQL("UPDATE Log SET Duration_Seconds = " + String.valueOf(second - presecond) + " WHERE ID = " + results.getString(0));
            }
            database.execSQL("INSERT INTO Log(Connection_Time, Time_Seconds, Network_Name, Duration_Seconds) VALUES('" + date + "'," + second + ",'" + networkName + "','Ongoing');");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo networkWifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                NetworkInfo networkMobileInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                String newName = "";
                if (networkWifiInfo.isConnected()) {
                    newName = wifiInfo.getSSID();
                    newName = newName.substring(1, newName.length() - 1);
                    if (Arrays.asList(RUNETWORKS).contains(newName)) {
                        if (RUQueue == null) RUQueue = new LinkedList<String>();
                        while (!RUQueue.isEmpty()) {
                            //Log.i(TAG, "RU dequeue");
                            new DownloadFileTask(false, true).execute(RUQueue.remove());
                        }
                    }
                } else if (networkMobileInfo.isConnected()) {
                    newName = "Cellular";
                } else {
                    newName = "Network Disconnected";
                }

                if (!currentNetworkName.equals(newName)) {
                    if (newName.equals("Network Disconnected")) Toast.makeText(MainActivity.this, newName, Toast.LENGTH_SHORT).show();
                    else {
                        Toast.makeText(MainActivity.this, newName + " Connected", Toast.LENGTH_SHORT).show();
                        logNetwork(newName);
                    }
                    //Log.i(TAG, newName);
                    currentNetworkName = newName;
                }
            }
        }
    }

    public class DownloadFileTask extends AsyncTask<String, Double, DownloadFileTask.Result> {

        public class Result {
            private boolean IsDownloaded;
            private long Duration; //ns
            private long Latency; //ns
            private int Throughput; //B/s
            private long TotalBytes;

            public Result(boolean isDownloaded) {IsDownloaded = isDownloaded;}
            public Result(boolean isDownloaded , long duration, long latency, int throughput, long totalBytes) {
                IsDownloaded = isDownloaded;
                Duration = duration;
                Latency = latency;
                Throughput = throughput;
                TotalBytes = totalBytes;
            }

            public boolean isDownloaded() {return IsDownloaded;}
            public long getDuration() {return Duration;}
            public long getLatency() {return Latency;}
            public int getThroughput() {return Throughput;}
            public long getTotalBytes() {return TotalBytes;}
        }

        private Network network;

        public DownloadFileTask(boolean isWifiOnly, boolean isRUOnly) {
            if (!isWifiOnly && !isRUOnly) network = Network.All;
            if (isWifiOnly && !isRUOnly) network = Network.WifiOnly;
            if (!isWifiOnly && isRUOnly) network = Network.RUOnly;
            if (isWifiOnly && isRUOnly) network = Network.RUOnly;
        }

        public Result enqueueDownload(String fileName, String url, int allowedNetworkTypes) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription(url);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setAllowedNetworkTypes(allowedNetworkTypes);

            downloadManager = (DownloadManager) MainActivity.this.getSystemService(Context.DOWNLOAD_SERVICE);

            long fileId = downloadManager.enqueue(request);

            long current = 0, total = -1;
            Cursor cursor;
            long startTime = System.nanoTime(), firstByteTime = 0;
            long tenSecondsBytes = 0;
            while (current != total) {
                cursor = downloadManager.query(new DownloadManager.Query().setFilterById(fileId));

                if (!cursor.moveToFirst()) {
                    Toast.makeText(MainActivity.this, "File not found", Toast.LENGTH_SHORT).show();
                    break;
                }

                if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_PENDING) {
                    //Log.i(TAG, "download pending");
                }

                if (!(cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_PENDING) &&
                        !(cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_PAUSED)) {

                    current = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    //Log.i(TAG, "current " + String.valueOf(current) + " total " + String.valueOf(total));
                    if (total != -1 && firstByteTime == 0) firstByteTime = System.nanoTime();

                    publishProgress(Double.valueOf(current) / Double.valueOf(total));

                    if ((System.nanoTime() - startTime) / 1000000000 >= 10) tenSecondsBytes = current;
                }

                cursor.close();
            }

            long duration = System.nanoTime() - startTime;
            long latency = firstByteTime - startTime; // nanosecond
            //Log.i(TAG, String.valueOf(latency));
            int throughput = (int)((double)total / (double)duration * 1000000000); // Bytes per second

            if (duration / 1000000000 >= 10) throughput = (int)((double)tenSecondsBytes / 10.0);

            return new Result(true, duration, latency,throughput, total);

            //Log.i(TAG, "latency: " + String.valueOf(latency / 1000000) + "ms" + " duration: " + String.valueOf(duration / 1000000) + "ms" + " throughput: " + String.valueOf(throughput) + "B/s");
        }

        public void enequeueRU (String url) {
            if (RUQueue == null) RUQueue = new LinkedList<String>();
            RUQueue.add(url);
        }

        @Override
        protected Result doInBackground(String... urls) {
            Result result;
            String fileName = URLUtil.guessFileName(urls[0], null, MimeTypeMap.getFileExtensionFromUrl(urls[0]));

            if (network == Network.All) {
                result = enqueueDownload(fileName, urls[0], DownloadManager.Request.NETWORK_WIFI |
                        DownloadManager.Request.NETWORK_MOBILE);
                //Log.i(TAG, "Mobile & Wifi enqueued");
            }
            else if (network == Network.WifiOnly) {
                result = enqueueDownload(fileName, urls[0], DownloadManager.Request.NETWORK_WIFI);
                //Log.i(TAG, "Wifi enqueued");
            }
            // RUOnly case
            else {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String wifiName = wifiInfo.getSSID();
                wifiName = wifiName.substring(1, wifiName.length() - 1);
                if (Arrays.asList(RUNETWORKS).contains(wifiName)) {
                    result = enqueueDownload(fileName, urls[0], DownloadManager.Request.NETWORK_WIFI);
                }
                else {
                    enequeueRU(urls[0]);
                    //Log.i(TAG, "RU enqueued");
                    return new Result(false);
                }
            }
            
            return result;
        }

        @Override
        protected void onProgressUpdate(Double... progress) {
            ProgressBar bar = (ProgressBar) findViewById(R.id.progress);
            int percentage = (int) (bar.getMax() * progress[0]);
            bar.setProgress(percentage);
            ((TextView)findViewById(R.id.label_progress)).setText(String.valueOf(percentage) + "%");
        }

        @Override
        protected void onPostExecute(Result result) {
            if (result.isDownloaded()) {
                Toast.makeText(MainActivity.this, "Downloaded", Toast.LENGTH_SHORT).show();
                StringBuffer sb = new StringBuffer();
                sb.append("Latency: ");
                sb.append(result.getLatency() / 1000000);
                sb.append("ms\n");
                sb.append("Throughput: ");
                sb.append(result.getThroughput());
                sb.append("B/s\n");
                sb.append("Duration: ");
                sb.append(result.getDuration() / 1000000);
                sb.append("ms\n");
                sb.append("Total Bytes: ");
                sb.append(result.getTotalBytes());
                ((TextView)findViewById(R.id.measurement)).setText(sb.toString());

                try {
                    FileOutputStream writer = new FileOutputStream(file, true);
                    Calendar calendar = Calendar.getInstance();
                    String date = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a").format(calendar.getTime());
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    String wifiName = wifiInfo.getSSID();
                    writer.write((date + " " + " " + wifiName.substring(1, wifiName.length() - 1) + "\n" + sb.toString() + "\n").getBytes());
                } catch (FileNotFoundException e) {
                    Toast.makeText(MainActivity.this, "File Output Error", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                ProgressBar bar = (ProgressBar) findViewById(R.id.progress);
                bar.setProgress(0);
                ((TextView)findViewById(R.id.label_progress)).setText("0%");
            }
        }
    }

    public void switchURL(View view) {
        TextView url = (TextView)findViewById(R.id.url);
        if (((CheckBox)findViewById(R.id.checkbox_url)).isChecked()) url.setText(R.string.graduate_url);
        else url.setText(R.string.default_url);
    }

    public void disconnectWifi(View view) {
        if (wifiManager.isWifiEnabled()) wifiManager.disconnect();
    }

    public void downloadFile(View view) {
        String url = ((EditText) findViewById(R.id.url)).getText().toString();
        boolean isWifiOnly = ((CheckBox) findViewById(R.id.wifi)).isChecked();
        boolean isRUOnly = ((CheckBox) findViewById(R.id.ruwifi)).isChecked();
        new DownloadFileTask(isWifiOnly, isRUOnly).execute(url);
    }

    public void switchLog(View view) {
        if (((Switch)findViewById(R.id.switch_log)).isChecked()) {
            refreshLog(findViewById(R.id.button_log));
            ((TextView)findViewById(R.id.log)).setVisibility(TextView.VISIBLE);
        }
        else ((TextView)findViewById(R.id.log)).setVisibility(TextView.INVISIBLE);
    }

    public void refreshLog(View view) {
        Calendar calendar = Calendar.getInstance();
        long second = calendar.getTimeInMillis() / 1000;

        StringBuffer logs = new StringBuffer();
        Cursor results = database.rawQuery("SELECT * FROM Log;", null);
        while (results.moveToNext()) {
            String duration = results.getString(4).equals("Ongoing") ? String.valueOf(second - Long.valueOf(results.getString(2))) : results.getString(4);
            //Log.i(TAG, results.getString(1) + " " + results.getString(2) + " " + results.getString(3) + " " + duration);
            logs.append(results.getString(1));
            logs.append(" ");
            logs.append(results.getString(3));
            logs.append(" ");
            logs.append(duration);
            logs.append("s\n");
        }

        ((TextView)findViewById(R.id.log)).setText(logs.toString());
    }

}
