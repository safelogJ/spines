package com.safelogj.dfly.camera;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.safelogj.dfly.AppController;
import com.safelogj.dfly.Clouds;
import com.safelogj.dfly.VideoFileTicket;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class YaWorker extends Worker {

    public YaWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {

        AppController controller = (AppController) getApplicationContext();
        Clouds clouds = controller.getSavedClouds();
        List<VideoFileTicket> videoFileTicketList = clouds.getVideoFileTicketList();

        for (VideoFileTicket ticket : videoFileTicketList) {
            if (ticket.isNeedSendYa()) {
                File file = new File(ticket.getPath());
                if (file.exists()) {
                    Log.d(AppController.LOG_TAG, "doWork Ya = ");
                    try {
                        uploadToYandexDisk(file, clouds);
                        ticket.setNeedSendYa(false);
                    } catch (Exception e) {
                        if (System.currentTimeMillis() - ticket.getDateMillis() > 120_000L) {
                            ticket.setNeedSendYa(false);
                        }
                        Log.d(AppController.LOG_TAG, "ошибка в Ya воркере при отправке");
                    }
                } else {
                    ticket.setNeedSendYa(false);
                    ticket.setNeedRemove(true);
                }
            }
        }
        return (videoFileTicketList.stream().anyMatch(VideoFileTicket::isNeedSendYa)) ? Result.retry() : Result.success();
    }

    private void uploadToYandexDisk(File file, Clouds clouds) throws IllegalArgumentException {
        Request request = new Request.Builder()
                .url("https://webdav.yandex.ru/" + file.getName())
                .put(RequestBody.create(file, MediaType.parse("video/mp4")))
                .addHeader("Authorization", clouds.getCredentialsYa())
                .addHeader("If-None-Match", "*")
                .build();

        try (Response response = ((AppController) getApplicationContext()).getOkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()
                    || response.code() == HttpURLConnection.HTTP_PRECON_FAILED
                    || response.code() == 507) { // Недостаточно места в памяти
                Log.d(AppController.LOG_TAG, "Файл успешно загружен или нет места в облаке! = Ya" + response.code());
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка: в ответе Ya " + response.code() + " " + response.message());
            }
        } catch (IOException e) {
            Log.d(AppController.LOG_TAG, "Ошибка: при отправке Ya = " + e.getMessage());
        }
    }
}
