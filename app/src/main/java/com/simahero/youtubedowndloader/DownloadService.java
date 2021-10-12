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
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        String filename = null;
        String downloadURL = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... f_url) {

            OkHttpClient client = new OkHttpClient();

            Request linkRequest = new Request.Builder()
                    .url("https://youtube-to-mp32.p.rapidapi.com/api/yt_to_mp3?video_id=" + f_url[0])
                    .addHeader("x-rapidapi-host", SECRETS.HOST)
                    .addHeader("x-rapidapi-key", SECRETS.KEY)
                    .get()
                    .build();

            try {
                Response response = client.newCall(linkRequest).execute();
                JSONObject json = new JSONObject(response.body().string());

                System.out.println(json.toString());

                filename = json.getString("Title").replaceAll("\\W+", "") + ".mp3";
                downloadURL = json.getString("Download_url");
            } catch (IOException | JSONException e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri downloadUri = Uri.parse(downloadURL);
            System.out.println(downloadUri);
            DownloadManager.Request request = new DownloadManager.Request(downloadUri);

            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE)
                    .setAllowedOverRoaming(false)
                    .setTitle(filename)
                    .setDescription("Downloading " + filename)
                    .setVisibleInDownloadsUi(true)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, filename)
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
