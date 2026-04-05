package com.safelogj.dfly.camera;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.safelogj.dfly.AppController;
import com.safelogj.dfly.Clouds;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class YaWorker extends Worker  {

    public YaWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String filePath = getInputData().getString(RecorderService.VIDEO_FILE_PATH);
        Clouds clouds = ((AppController) getApplicationContext()).getSavedClouds();

        if (filePath == null) return Result.success();

        File file = new File(filePath);
        if (!file.exists()) return Result.success();

        try {
            Log.d(AppController.LOG_TAG, "doWork Ya");
            uploadToYandexDisk(file, clouds);
            return Result.success();

        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "ошибка в воркере при отправке");
        }

        long startTime = getInputData().getLong(RecorderService.START_TIME, 0);
        if (System.currentTimeMillis() - startTime > 120_000L) {
            Log.d(AppController.LOG_TAG, "2 минуты прошли, файл так и не ушел. Отмена.");
            return Result.success();
        } else {
            return Result.retry();
        }
    }

    private void uploadToYandexDisk(File file, Clouds clouds) {

        RequestBody body = RequestBody.create(file, MediaType.parse("video/mp4"));

        Request request = new Request.Builder()
                .url("https://webdav.yandex.ru/" + file.getName())
                .addHeader("Authorization", clouds.getCredentialsYa())
                .addHeader("If-None-Match", "*")
                .put(body)
                .build();

        OkHttpClient httpClient = ((AppController) getApplicationContext()).getOkHttpClient();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() || response.code() == HttpURLConnection.HTTP_PRECON_FAILED) {
                Log.d(AppController.LOG_TAG, "Файл успешно загружен! = Ya" + response.code());
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка: в ответе Ya " + response.code() + " " + response.message());
            }
        } catch (IOException e) {
            Log.d(AppController.LOG_TAG, "Ошибка: при отправке Ya = " + e.getMessage());
           // return "timeout".equals(e.getMessage()); // true
        }
    }
}
