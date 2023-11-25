package com.simahero.youtubedowndloader;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.text.Charsets;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class DownloadService extends Service {

    private BroadcastReceiver broadcastReceiver = null;
    private long downloadID = -1;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                System.out.println(intent.getAction());
                if (downloadID == intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)){
                    Toast.makeText(getApplicationContext(), "Download Finished!", Toast.LENGTH_SHORT).show();
                }
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);

        if (sharedText != null) {
            new ConvertAsyncTask().execute(getYouTubeId(sharedText));
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    public class ConvertAsyncTask extends AsyncTask<String, String, String> {

        private static final String TAG = "YoutubeDownloader";
        String filename = null;
        String downloadURL = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... f_url) {

            OkHttpClient client = new OkHttpClient.Builder()
                    .callTimeout(0, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS)
                    .build();

            String apiUrl = "https://youtube-mp36.p.rapidapi.com/dl?id=" + f_url[0];

            Log.i(TAG,apiUrl);

            Request linkRequest = new Request.Builder()
                    .url(apiUrl)
                    .get()
                    .addHeader("X-RapidAPI-Key", "262dcb28abmsh4bb8fc96037235ep12c308jsn996ab483f148")
                    .addHeader("X-RapidAPI-Host", "youtube-mp36.p.rapidapi.com")
                    .build();
            try {
                Log.i(TAG, "Getting URL.");

                Response response = client.newCall(linkRequest).execute();

                String responseBody = response.body().string();

                Log.i(TAG, responseBody);

                JSONObject json = new JSONObject(responseBody);

                //filename = json.getJSONObject("data").getString("title").replaceAll("\\W+", "") + ".mp3";
                //downloadURL = json.getJSONObject("files").getJSONObject("mp3").getString("link");
                String isFinished = json.getString("status");

                while (!isFinished.equals("ok")){
                    try {
                        TimeUnit.SECONDS.sleep(5);
                        response = client.newCall(linkRequest).execute();
                        responseBody = response.body().string();
                        Log.i(TAG, responseBody);
                        json = new JSONObject(responseBody);
                        isFinished = json.getString("status");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                filename = json.getString("title").replaceAll("\\W+", "") + ".mp3";
                downloadURL = json.getString("link");

            } catch (IOException | JSONException e) {
                //Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri downloadUri = Uri.parse(downloadURL);
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);
            request.allowScanningByMediaScanner();

            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                    .setAllowedOverRoaming(false)
                    .setMimeType("audio/MP3")
                    .setTitle(filename)
                    .setDescription("Downloading " + filename)
                    .setVisibleInDownloadsUi(true)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            downloadID = downloadManager.enqueue(request);

            return filename;
        }

        @Override
        protected void onPostExecute(String file_url) {
            super.onPostExecute(file_url);
            System.out.println(file_url);
        }

    }

    private String getYouTubeId(String youTubeUrl) {

        /*
        try {
            return URLEncoder.encode(youTubeUrl, Charsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
         */



        String pattern = "https?://(?:[0-9A-Z-]+\\.)?(?:youtu\\.be/|youtube\\.com\\S*[^\\w\\-\\s])([\\w\\-]{11})(?=[^\\w\\-]|$)(?![?=&+%\\w]*(?:['\"][^<>]*>|</a>))[?=&+%\\w]*";

        Pattern compiledPattern = Pattern.compile(pattern,
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = compiledPattern.matcher(youTubeUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;

    }

}
