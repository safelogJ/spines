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
import java.nio.file.Files;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TgWorker extends Worker  {

    public TgWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String filePath = getInputData().getString(RecorderService.VIDEO_FILE_PATH);
        Clouds clouds = ((AppController) getApplicationContext()).getSavedClouds();

        if (filePath == null || !clouds.isValidTg()) return Result.success();

        File file = new File(filePath);
        if (!file.exists()) return Result.success();

        try {
            Log.d(AppController.LOG_TAG, "doWork TG");
            if (uploadToTelegram(file, clouds)) return Result.success();

        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "ошибка в воркере при отправке");
        }

        long startTime = getInputData().getLong(RecorderService.START_TIME, 0);

        if (System.currentTimeMillis() - startTime > (2 * 24 * 60 * 60 * 1000L)) {
            Log.d(AppController.LOG_TAG, "2 Суток прошло, файл так и не ушел. Отмена.");
            return Result.failure();
        } else {
            return Result.retry();
        }
    }

    private boolean uploadToTelegram(File file, Clouds clouds) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", clouds.getTgChatId())
                .addFormDataPart("video", file.getName(),
                        RequestBody.create(file, MediaType.parse("video/mp4")))
                .addFormDataPart("caption", "Запись от " + file.getName())
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + clouds.getTgBotToken() + "/sendVideo")
                .post(requestBody)
                .build();

        OkHttpClient httpClient = ((AppController) getApplicationContext()).getOkHttpClient();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Files.deleteIfExists(file.toPath());
                Log.d(AppController.LOG_TAG, "Видео успешно отправлено в TG = " + response.code());
                return true;
            } else if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED || response.code() == HttpURLConnection.HTTP_BAD_REQUEST) {
                Log.d(AppController.LOG_TAG, "Не верный токен или ID в телеграме: " + response.code());
                return true;
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка сервера TG: " + response.code());
                return false; // Попробуем позже
            }
        } catch (IOException e) {
            Log.d(AppController.LOG_TAG, "Ошибка сети при отправке в TG", e);
            return false;
        }
    }
}
