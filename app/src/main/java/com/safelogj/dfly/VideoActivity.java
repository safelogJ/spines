package com.safelogj.dfly;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.safelogj.dfly.camera.RecorderService;
import com.safelogj.dfly.databinding.ActivityVideoBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VideoActivity extends AppCompatActivity {

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private RecorderService recorderService;
    private boolean isAllPermissionsGranted;

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                isAllPermissionsGranted = true;
                for (boolean isGranted : result.values()) {
                    if (!isGranted) {
                        isAllPermissionsGranted = false;
                        break;
                    }
                }

                if (isAllPermissionsGranted) {
                    Log.d(AppController.LOG_TAG, "Есть все разрешения");
                } else {
                    Log.d(AppController.LOG_TAG, "Не все разрешения получены");
                }
            });

    private final ServiceConnection recorderServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RecorderService.LocalBinder binder = (RecorderService.LocalBinder) service;
            recorderService = binder.getRecorderService();
            recorderService.setSurfaceProvider(mBinding.viewFinder.getSurfaceProvider());
            // Log.d(AppController.LOG_TAG, "Забиндилось ====== ");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // recorderService.setSurfaceProvider(null);
        }
    };

    private AppController controller;
    private PowerManager powerManager;
    private ActivityVideoBinding mBinding;
    private ColorStateList btnBackColorGreen;
    private ColorStateList btnBackColorBlack;
    private ColorStateList btnRipleColorGreen;
    private ColorStateList btnRipleColorBlack;
    private Clouds clouds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.inner, (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets gestureInsets = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures());
            int leftPadding = Math.max(gestureInsets.left, systemInsets.left);
            int rightPadding = Math.max(gestureInsets.right, systemInsets.right);
            int bottomPadding = Math.max(gestureInsets.bottom, systemInsets.bottom);
            int leftPaddingLand = Math.max(leftPadding, systemInsets.top);
            int rightPaddingLand = Math.max(rightPadding, systemInsets.top);

            if (v.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                v.setPadding(leftPaddingLand, systemInsets.top, rightPaddingLand, bottomPadding);
            } else {
                v.setPadding(leftPadding, systemInsets.top, rightPadding, bottomPadding);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        controller = (AppController) getApplication();
        clouds = controller.getSavedClouds();

        mBinding.stopServiceBtn.setOnClickListener(v -> {
            if (RecorderService.isServiceRun()) {
                if (recorderService != null) {
                    unbindService(recorderServiceConnection);
                    recorderService = null;
                }
                stopService(new Intent(this, RecorderService.class));
                readFromCloudsToFields();
                setFielderVisibility(View.VISIBLE);
                mBinding.stopServiceBtn.getDrawable().setTint(getColor(R.color.white));
            } else {
                if (isAllPermissionsGranted) {
                    writeFromFieldsToClouds();
                    Intent intent = new Intent(this, RecorderService.class);
                    ContextCompat.startForegroundService(this, intent);
                    bindService(intent, recorderServiceConnection, BIND_AUTO_CREATE);
                    setFielderVisibility(View.GONE);
                    mBinding.stopServiceBtn.getDrawable().setTint(getColor(R.color.redA400));
                }
            }
        });

        mBinding.batteryButton.setOnClickListener(view -> fixBattery());
        mBinding.youtubeButton.setOnClickListener(view -> openYoutubeLink());
        powerManager = controller.getPowerManager();
        btnBackColorGreen = controller.getBtnBackColorGreen();
        btnBackColorBlack = controller.getBtnBackColorBlack();
        btnRipleColorGreen = controller.getBtnRipleColorGreen();
        btnRipleColorBlack = controller.getBtnRipleColorBlack();

        setDarkStatusBar();
        checkAndRequestPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, RecorderService.class);
        bindService(intent, recorderServiceConnection, BIND_AUTO_CREATE);
        mHandler.postDelayed(this::checkButtonsColor, 700);
        if (RecorderService.isServiceRun()) {
            setFielderVisibility(View.GONE);
            mBinding.stopServiceBtn.getDrawable().setTint(getColor(R.color.redA400));
        } else {
            readFromCloudsToFields();
            setFielderVisibility(View.VISIBLE);
            mBinding.stopServiceBtn.getDrawable().setTint(getColor(R.color.white));
        }
    }

    private void readFromCloudsToFields() {
        mBinding.yandexMailEditText.setText(clouds.getYaAcc());
        mBinding.yandexAppPassEditText.setText(clouds.getAppPass());
        mBinding.tgTokenEditText.setText(clouds.getTgBotToken());
        mBinding.tgIdEditText.setText(clouds.getTgChatId());
    }

    private void writeFromFieldsToClouds() {
        Editable yaAcc = mBinding.yandexMailEditText.getText();
        clouds.setYaAcc(yaAcc == null ? AppController.EMPTY_STRING : yaAcc.toString());
        Editable appPass = mBinding.yandexAppPassEditText.getText();
        clouds.setAppPass(appPass == null ? AppController.EMPTY_STRING : appPass.toString());
        Editable tgToken = mBinding.tgTokenEditText.getText();
        clouds.setTgBotToken(tgToken == null ? AppController.EMPTY_STRING : tgToken.toString());
        Editable tgId = mBinding.tgIdEditText.getText();
        clouds.setTgChatId(tgId == null ? AppController.EMPTY_STRING : tgId.toString());
        controller.writeCloudsEncrypted();
    }

    @Override
    protected void onStop() {
        if (recorderService != null) {
            recorderService.setSurfaceProvider(null);
            unbindService(recorderServiceConnection);
            //  Log.d(AppController.LOG_TAG, "Анбиндинг ====== ");
            recorderService = null;
        }
        if (!RecorderService.isServiceRun()) {
            writeFromFieldsToClouds();
        }
        mHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        isAllPermissionsGranted = permissions.isEmpty();
        if (!isAllPermissionsGranted) {
            Log.d(AppController.LOG_TAG, "Отправка запроса на разрешения = " + permissions.size());
            permissionsLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    private void setFielderVisibility(int visible) {
        mBinding.innerFielder.setVisibility(visible);
    }

    private void checkButtonsColor() {
        if ((powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName()))
                || !isBatterySettingsAvailable()) {
            mBinding.batteryButton.setBackgroundTintList(btnBackColorGreen);
            mBinding.batteryButton.setRippleColor(btnRipleColorGreen);
        } else {
            mBinding.batteryButton.setBackgroundTintList(btnBackColorBlack);
            mBinding.batteryButton.setRippleColor(btnRipleColorBlack);
        }
    }

    private void fixBattery() {
        if (isBatterySettingsAvailable()) {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        }
    }

    private boolean isBatterySettingsAvailable() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfos.isEmpty()) {
            return false;
        }
        for (ResolveInfo info : resolveInfos) {
            String packageName = info.activityInfo != null ? info.activityInfo.packageName : AppController.EMPTY_STRING;
            String className = info.activityInfo != null ? info.activityInfo.name : AppController.EMPTY_STRING;
            String combined = (packageName + className).toLowerCase(Locale.ROOT);
            if (!combined.contains("stub")) {
                return true;
            }
        }
        return false;
    }

    private void setDarkStatusBar() {
        WindowInsetsControllerCompat controllerCompat = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controllerCompat.setAppearanceLightStatusBars(false);
        controllerCompat.setAppearanceLightNavigationBars(false);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.black));
    }

    private void openYoutubeLink() {
        try {
            Uri webpage = Uri.parse("https://www.youtube.com/watch?v=XX7zs7j_qgE&list=PL5Ch75WcmOXRW00PyHu-HGdLtBTKdgbeV");
            Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
            startActivity(intent);
        } catch (Exception e) {
            //
        }
    }
}