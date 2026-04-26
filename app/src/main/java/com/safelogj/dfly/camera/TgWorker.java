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

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TgWorker extends Worker {

    public TgWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppController controller = (AppController) getApplicationContext();
        Clouds clouds = controller.getSavedClouds();
        if (!clouds.getTgLock().tryLock()) {
            Log.d(AppController.LOG_TAG, Thread.currentThread().getName() +" TG Занято другим воркером, выхожу.");
            return Result.success();
        }
        Log.d(AppController.LOG_TAG, Thread.currentThread().getName() + " TG Захват лока.");
        for (VideoFileTicket ticket : clouds.getVideoFileTicketList()) {
            if (ticket.isNeedSendTg()) {
                File file = new File(ticket.getPath());
                if (file.exists() && System.currentTimeMillis() - ticket.getDateMillis() < 172_800_000L) {
                    Log.d(AppController.LOG_TAG, "doWork Tg = попытка отправки файла");
                    try {
                        if (uploadToTelegram(file, clouds)) {
                            ticket.setNeedSendTg(false);
                        }
                    } catch (Exception e) {
                        Log.d(AppController.LOG_TAG, "ошибка в Tg воркере при отправке");
                    }
                } else {
                    ticket.setNeedSendTg(false);
                    ticket.setNeedRemove(true);
                }
            }
        }
        if (clouds.getVideoFileTicketList().stream().anyMatch(VideoFileTicket::isNeedSendTg)) {
            clouds.getTgLock().unlock();
            Log.d(AppController.LOG_TAG, "TgLock отпущен.");
            return Result.retry();
        } else {
            if (!RecorderService.isServiceRun()) {
                controller.writeTicketsToFile();
            }
            clouds.getTgLock().unlock();
            Log.d(AppController.LOG_TAG, "TgLock отпущен.");
            return Result.success();
        }
    }

    private boolean uploadToTelegram(File file, Clouds clouds) throws IllegalArgumentException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", clouds.getTgChatId())
                .addFormDataPart("video", file.getName(), RequestBody.create(file, MediaType.parse("video/mp4")))
                .addFormDataPart("caption", file.getName())
                .build();

        Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + clouds.getTgBotToken() + "/sendVideo")
                .post(requestBody)
                .build();

        try (Response response = ((AppController) getApplicationContext()).getOkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(AppController.LOG_TAG, "Видео успешно отправлено в TG = " + response.code() + " " + file.getName());
                return true;
            } else if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED
                    || response.code() == HttpURLConnection.HTTP_BAD_REQUEST
                    || response.code() == HttpURLConnection.HTTP_ENTITY_TOO_LARGE) {
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
