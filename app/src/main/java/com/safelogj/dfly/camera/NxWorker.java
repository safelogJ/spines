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
        AppController controller = (AppController) getApplicationContext();
        Clouds clouds = controller.getSavedClouds();
        if (!clouds.getNxLock().tryLock()) {
            Log.d(AppController.LOG_TAG, Thread.currentThread().getName() +" NX Занято другим воркером, выхожу.");
            return Result.success();
        }
        Log.d(AppController.LOG_TAG, Thread.currentThread().getName() + " NX Захват лока.");
        for (VideoFileTicket ticket : clouds.getVideoFileTicketList()) {
            if (ticket.isNeedSendNx()) {
                File file = new File(ticket.getPath());
                if (file.exists() && System.currentTimeMillis() - ticket.getDateMillis() < 172_800_000L) {
                    Log.d(AppController.LOG_TAG, "doWork Nx = ");
                    try {
                        if (uploadToNextCloud(file, clouds)) {
                            ticket.setNeedSendNx(false);
                        }
                    } catch (Exception e) {
                        Log.d(AppController.LOG_TAG, "ошибка в Nx воркере при отправке");
                    }
                } else {
                    ticket.setNeedSendNx(false);
                    ticket.setNeedRemove(true);
                }
            }
        }
        if (clouds.getVideoFileTicketList().stream().anyMatch(VideoFileTicket::isNeedSendNx)) {
            clouds.getNxLock().unlock();
            Log.d(AppController.LOG_TAG, "NxLock отпущен.");
            return Result.retry();
        } else {
            if (!RecorderService.isServiceRun()) {
                controller.writeTicketsToFile();
            }
            clouds.getNxLock().unlock();
            Log.d(AppController.LOG_TAG, "NxLock отпущен.");
            return Result.success();
        }
    }

    private boolean uploadToNextCloud(File file, Clouds clouds) throws IllegalArgumentException {
        Request request = new Request.Builder()
                .url(clouds.getNextCloudUrl() + file.getName())
                .put(RequestBody.create(file, MediaType.parse("video/mp4"))) // WebDAV использует PUT для загрузки
                .addHeader("Authorization", clouds.getCredentialsNext())
                .build();

        try (Response response = ((AppController) getApplicationContext()).getOkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(AppController.LOG_TAG, "Файл успешно загружен Next " + " " + file.getName());
                return true;
            } else if (response.code() == HttpURLConnection.HTTP_FORBIDDEN
                    || response.code() == HttpURLConnection.HTTP_BAD_GATEWAY
                    || response.code() == HttpURLConnection.HTTP_UNAUTHORIZED
                    || response.code() == HttpURLConnection.HTTP_NOT_FOUND
                    || response.code() == 507) { // Недостаточно места в памяти
                Log.d(AppController.LOG_TAG, "Ошибка отправки Next неудача : код = " + response.code());
                return true;
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка отправки Next: код = " + response.code());
                return false;
            }
        } catch (IOException e) {
            Log.d(AppController.LOG_TAG, "Ошибка Next  " + e);
            return false;
        }
    }
}
