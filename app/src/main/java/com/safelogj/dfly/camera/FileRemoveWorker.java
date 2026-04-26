package com.safelogj.dfly.camera;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.safelogj.dfly.AppController;
import com.safelogj.dfly.Clouds;
import com.safelogj.dfly.VideoFileTicket;

import java.io.File;
import java.nio.file.Files;

public class FileRemoveWorker extends Worker {

    public FileRemoveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppController controller = (AppController) getApplicationContext();
        Clouds clouds = controller.getSavedClouds();
        Log.d(AppController.LOG_TAG, Thread.currentThread().getName() + " Воркер удалятор  doWork.");

        for (VideoFileTicket ticket : clouds.getVideoFileTicketList()) {
            if (System.currentTimeMillis() - ticket.getDateMillis() > 172_800_000L) {
                ticket.setNeedRemove(true);
                try {
                    Files.deleteIfExists(new File(ticket.getPath()).toPath());
                    Log.d(AppController.LOG_TAG, "Удалён файл " + ticket.getPath());
                } catch (Exception e) {
                    Log.d(AppController.LOG_TAG, "Не удалился файл в  = FileRemoveWorker" + ticket.getPath());
                }
            }
        }

        if (clouds.getVideoFileTicketList().stream().noneMatch(t -> System.currentTimeMillis() - t.getDateMillis() < 172_800_000L)) {
            WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(RecorderService.REMOVE_QUEUE);
            if (!RecorderService.isServiceRun()) {
               controller.writeTicketsToFile();
            }
        }
        return Result.success();
    }
}
