package com.safelogj.dfly.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;


import com.safelogj.dfly.AppController;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.enqueueUniquePeriodicWork(RecorderService.YA_QUEUE, ExistingPeriodicWorkPolicy.KEEP, getSendRequest(YaWorker.class));
            workManager.enqueueUniquePeriodicWork(RecorderService.TG_QUEUE, ExistingPeriodicWorkPolicy.KEEP, getSendRequest(TgWorker.class));
            workManager.enqueueUniquePeriodicWork(RecorderService.NX_QUEUE, ExistingPeriodicWorkPolicy.KEEP, getSendRequest(NxWorker.class));
            workManager.enqueueUniquePeriodicWork(RecorderService.REMOVE_QUEUE, ExistingPeriodicWorkPolicy.KEEP, getRemoveRequest());

            Log.d(AppController.LOG_TAG, "BootReceiver пнул все воркеры ");
        } else {
            Log.d(AppController.LOG_TAG, "BootReceiver " + intent.getAction());
        }
    }

    private PeriodicWorkRequest getSendRequest(Class<? extends ListenableWorker> workerClass) {
        return new PeriodicWorkRequest.Builder(workerClass, 15, TimeUnit.MINUTES)
                .setConstraints(RecorderService.constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }

    private PeriodicWorkRequest getRemoveRequest() {
        return new PeriodicWorkRequest.Builder(FileRemoveWorker.class, 1, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();
    }
}
