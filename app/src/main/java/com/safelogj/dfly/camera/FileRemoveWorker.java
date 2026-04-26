package com.safelogj.dfly.camera;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.safelogj.dfly.AppController;
import com.safelogj.dfly.Clouds;
import com.safelogj.dfly.VideoFileTicket;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FileRemoveWorker extends Worker {

    public FileRemoveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppController controller = (AppController) getApplicationContext();
        Clouds clouds = controller.getSavedClouds();
        List<VideoFileTicket> videoFileTicketList = clouds.getVideoFileTicketList();

        for (VideoFileTicket ticket : videoFileTicketList) {
            if (System.currentTimeMillis() - ticket.getDateMillis() > 172_800_000L) {
                ticket.setNeedRemove(true);
                try {
                    Files.deleteIfExists(new File(ticket.getPath()).toPath());
                } catch (Exception e) {
                    Log.d(AppController.LOG_TAG, "Не удалился файл в  = FileRemoveWorker" + ticket.getPath());
                }
            }
        }

        if (videoFileTicketList.stream().noneMatch(t -> System.currentTimeMillis() - t.getDateMillis() < 172_800_000L)) {
            WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(RecorderService.REMOVE_QUEUE);
        }
        return Result.success();
    }
}
