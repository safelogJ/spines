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

public class NxWorker extends Worker {

    public NxWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String filePath = getInputData().getString(RecorderService.VIDEO_FILE_PATH);
        if (filePath == null) return Result.success();

        File file = new File(filePath);
        if (!file.exists()) return Result.success();

        try {
            Clouds clouds = ((AppController) getApplicationContext()).getSavedClouds();
            Log.d(AppController.LOG_TAG, "doWork Next =   " + filePath);
            if (uploadToNextCloud(file, clouds)) return Result.success();

        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "ошибка в NEXT воркере при отправке" + filePath);
        }

        long startTime = getInputData().getLong(RecorderService.START_TIME, 0);
        if (System.currentTimeMillis() - startTime > (2 * 24 * 60 * 60 * 1000L)) {
            Log.d(AppController.LOG_TAG, "2 Суток прошло, файл так и не ушел в Next. Отмена.");
            return Result.success();
        } else {
            return Result.retry();
        }
    }

    private boolean uploadToNextCloud(File file, Clouds clouds) {
        String uploadUrl = clouds.getNextCloudUrl() + file.getName();
        RequestBody requestBody = RequestBody.create(file, MediaType.parse("video/mp4"));
        String credential = clouds.getCredentialsNext();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .put(requestBody) // WebDAV использует PUT для загрузки
                .addHeader("Authorization", credential)
                .build();
        OkHttpClient httpClient = ((AppController) getApplicationContext()).getOkHttpClient();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(AppController.LOG_TAG, "Файл успешно загружен Next ");
                return true;
            } else if (response.code() == HttpURLConnection.HTTP_FORBIDDEN
                    || response.code() == HttpURLConnection.HTTP_BAD_GATEWAY
                    || response.code() == HttpURLConnection.HTTP_UNAUTHORIZED
                    || response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.e(AppController.LOG_TAG, "Ошибка отправки Next неудача : код = " + response.code());
                return true;
            } else {
                Log.e(AppController.LOG_TAG, "Ошибка отправки Next: код = " + response.code());
                return false;
            }
        } catch (IOException e) {
            Log.d(AppController.LOG_TAG, "Ошибка Next  " + e);
            return false;
        }

    }

}
