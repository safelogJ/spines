package com.safelogj.dfly.camera;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.safelogj.dfly.AppController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class FileRemoveWorker extends Worker {

    public FileRemoveWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String filePath = getInputData().getString(RecorderService.VIDEO_FILE_PATH);
        if (filePath == null) return Result.success();

        File file = new File(filePath);
        if (!file.exists()) return Result.success();

        long startTime = getInputData().getLong(RecorderService.START_TIME, 0);
        if (System.currentTimeMillis() - startTime > (3 * 24 * 60 * 60 * 1000L)) {
            Log.d(AppController.LOG_TAG, "3 Суток прошло, файл так и не удалён. Отмена.");
            return  Result.success();
        } else if (System.currentTimeMillis() - startTime > (2 * 24 * 60 * 60 * 1000L)) {
            Log.d(AppController.LOG_TAG, "2 Суток прошло, пробуем удалить файл.");
            try {
               return Files.deleteIfExists(file.toPath()) ? Result.success() : Result.retry();
            } catch (IOException e) {
                return Result.retry();
            }
        }
        return Result.retry();
    }
}
