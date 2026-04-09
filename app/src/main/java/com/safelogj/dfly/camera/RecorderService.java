package com.safelogj.dfly.camera;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.google.common.util.concurrent.ListenableFuture;
import com.safelogj.dfly.AppController;

import com.safelogj.dfly.Clouds;
import com.safelogj.dfly.NotificationActivity;
import com.safelogj.dfly.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecorderService extends LifecycleService {

    public static final String VIDEO_FILE_PATH = "video_file_path";
    public static final String START_TIME = "start_time";
    private static final String WAKELOCKTAG = "Spines::WakelockTag";
    private static final AtomicBoolean isRecorderServiceRun = new AtomicBoolean(false);
    private static final int MAX_DURATION_MILLIS = 60_000;
    private static final int STEP_MILLIS = 10_000;
    private final IBinder mBinder = new LocalBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US);
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                getWakeLock();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                releaseWakeLock();
            }
        }
    };
    private final Runnable rebindCameraRunnable = () -> {
        if (isRecorderServiceRun.get()) {
            rebindCamera();
        }
    };
    private final Runnable recordNextChunkRunnable = () -> {
        if (isRecorderServiceRun.get()) {
            recordNextChunk();
        }
    };
    private final Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    private final Preview preview = new Preview.Builder().build();
    private int currentDurationSeconds;
    private File mVideoDir;
    private PowerManager powerManager;
    private PowerManager.WakeLock mWakeLock;
    private ProcessCameraProvider cameraProvider;
    private VideoCapture<Recorder> videoCapture;
    private Preview.SurfaceProvider surfaceProvider;
    private Recording activeRecording;
    private CameraSelector backCameraSelector;
    private CameraSelector frontCameraSelector;
    private CameraSelector defaultCameraSelector;
    private Clouds clouds;

    public static boolean isServiceRun() {
        return isRecorderServiceRun.get();
    }

    public void setSurfaceProvider(@Nullable Preview.SurfaceProvider provider) {
        // Log.d(AppController.LOG_TAG, "прилетел сурфасе провайдер ====== " + provider);
        mainHandler.removeCallbacks(recordNextChunkRunnable);
        stopRecordingFile();
        surfaceProvider = provider;
        preview.setSurfaceProvider(surfaceProvider);
        mainHandler.removeCallbacks(rebindCameraRunnable);
        mainHandler.postDelayed(rebindCameraRunnable, 1100);
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public RecorderService getRecorderService() {
            return RecorderService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        isRecorderServiceRun.set(true);
        showNotification();
        registerScreenListener();
        initResources();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterScreenListener();
        isRecorderServiceRun.set(false);
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);

        mainHandler.removeCallbacks(rebindCameraRunnable);
        mainHandler.removeCallbacks(recordNextChunkRunnable);

        stopRecordingFile();
        // 3. Освобождаем камеру
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        mWakeLock = null;
        surfaceProvider = null;
        preview.setSurfaceProvider(null);
        super.onDestroy();
    }


    private void rebindCamera() {

        try {
            mainHandler.removeCallbacks(recordNextChunkRunnable);
            stopRecordingFile();
            cameraProvider.unbindAll();
            preview.setSurfaceProvider(surfaceProvider);
            if (surfaceProvider != null) {
                cameraProvider.bindToLifecycle(this, getSelector(backCameraSelector), preview, videoCapture);
                Log.i(AppController.LOG_TAG, "Привязка задней камеры");
            } else {
                cameraProvider.bindToLifecycle(this, getSelector(frontCameraSelector), videoCapture);
                Log.i(AppController.LOG_TAG, "Привязка фронтальной камеры");
            }
            mainHandler.post(recordNextChunkRunnable);
        } catch (IllegalStateException | IllegalArgumentException | UnsupportedOperationException |
                 NullPointerException e) {
            Log.d(AppController.LOG_TAG, "Rebinding camera failed", e);
        }
    }

    private void recordNextChunk() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        stopRecordingFile();
        File videoFile = new File(mVideoDir, getFormattedDate());
        // 3. Настраиваем параметры вывода
        FileOutputOptions fileOutputOptions = new FileOutputOptions.Builder(videoFile).build();
        // 4. Запускаем запись
        activeRecording = videoCapture.getOutput()
                .prepareRecording(this, fileOutputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Finalize finalizeEvent) {
                        if (!finalizeEvent.hasError()) {
                            Log.d(AppController.LOG_TAG, "Файл записан: " + videoFile.getAbsolutePath());
                            if (clouds.isValidTg() || clouds.isValidYaDisk()) {
                                uploadWithWorkers(videoFile.getAbsolutePath());
                            }

                        } else {
                            Log.d(AppController.LOG_TAG, "Ошибка записи: " + finalizeEvent.getError());
                            if (videoFile.exists() && !videoFile.delete()) {
                                Log.d(AppController.LOG_TAG, "Ошибка удаления повреждённой записи: " + finalizeEvent.getError());
                            }
                        }
                    }
                });

        if (currentDurationSeconds < MAX_DURATION_MILLIS) {
            currentDurationSeconds += STEP_MILLIS;
        }

        mainHandler.postDelayed(recordNextChunkRunnable, currentDurationSeconds);
    }

    private void uploadWithWorkers(String path) {
        Data inputData = new Data.Builder()
                .putString(VIDEO_FILE_PATH, path)
                .putLong(START_TIME, System.currentTimeMillis())
                .build();

        WorkContinuation continuation = null;

        if (clouds.isValidYaDisk()) {
            OneTimeWorkRequest yaRequest = new OneTimeWorkRequest.Builder(YaWorker.class)
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .build();

            continuation = WorkManager.getInstance(this).beginUniqueWork(path, ExistingWorkPolicy.KEEP, yaRequest);
        }

        if (clouds.isValidTg()) {
            OneTimeWorkRequest tgRequest = new OneTimeWorkRequest.Builder(TgWorker.class)
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .build();

            if (continuation == null) {
                continuation = WorkManager.getInstance(this).beginUniqueWork(path, ExistingWorkPolicy.KEEP, tgRequest);
            } else {
                continuation = continuation.then(tgRequest);
            }
        }

        if (continuation != null) {
            continuation.enqueue();
        }

    }

    private void stopRecordingFile() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
    }

    private CameraSelector getSelector(CameraSelector selector) {
        return selector == null ? defaultCameraSelector : selector;
    }

    private void showNotification() {
        Intent notificationIntent = new Intent(this, NotificationActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, AppController.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        startForeground(1, notificationBuilder.build());
    }

    private void initResources() {
        if (powerManager == null || mWakeLock == null) {
            AppController controller = (AppController) getApplication();
            clouds = controller.getSavedClouds();
            clouds.buildCredentials();
            powerManager = controller.getPowerManager();
            if (powerManager != null) {
                if (mWakeLock == null) {
                    mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCKTAG);
                }
                if (!powerManager.isInteractive()) {
                    getWakeLock();
                }
            }
        }

        mVideoDir = new File(getExternalFilesDir(null), "video");

        if (!mVideoDir.exists() && !mVideoDir.mkdirs()) return;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                initCameraSelectors();
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                mainHandler.post(rebindCameraRunnable);
            } catch (ExecutionException | InterruptedException | NullPointerException |
                     CancellationException e) {
                Log.d(AppController.LOG_TAG, "Ошибка CameraProvider", e);
            }
        }, ContextCompat.getMainExecutor(this));

    }

    private void initCameraSelectors() {
        try {
            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                backCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                frontCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                return;
            }

            List<CameraInfo> cameraInfos = cameraProvider.getAvailableCameraInfos();
            if (!cameraInfos.isEmpty()) {
                defaultCameraSelector = cameraInfos.get(0).getCameraSelector();
            }
        } catch (CameraInfoUnavailableException e) {
            Log.i(AppController.LOG_TAG, "Ошибка доступа к списку камер", e);
        }
    }

    private String getFormattedDate() {
        String cameraSuffix = (surfaceProvider != null) ? "BACK" : "FRONT";
        return String.format("rec_%s_%s.mp4", simpleDateFormat.format(new Date(System.currentTimeMillis())), cameraSuffix);
    }

    private void getWakeLock() {
        try {
            if (mWakeLock != null && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
                Log.i(AppController.LOG_TAG, "mWakeLock.acquire() " + Thread.currentThread().getName());
            }
        } catch (Exception e) {
            Log.i(AppController.LOG_TAG, "mWakeLock.acquire() = Ошибка " + Thread.currentThread().getName());
        }

    }

    private void releaseWakeLock() {
        try {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
                Log.w(AppController.LOG_TAG, "mWakeLock.release() " + Thread.currentThread().getName());
            }
        } catch (Exception e) {
            Log.w(AppController.LOG_TAG, "mWakeLock.release() = Ошибка " + Thread.currentThread().getName());
        }
    }

    private void registerScreenListener() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            registerReceiver(screenReceiver, filter);
        } catch (Exception e) {
            Log.w(AppController.LOG_TAG, "registerScreenListener = Ошибка " + Thread.currentThread().getName());
            getWakeLock();
        }
    }

    private void unregisterScreenListener() {
        if (isRecorderServiceRun.get()) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception e) {
                Log.w(AppController.LOG_TAG, "unregisterScreenListener = Ошибка " + Thread.currentThread().getName());
            }
        }
    }
}
