package com.safelogj.dfly;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.PowerManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.OkHttpClient;

public class AppController extends Application implements CameraXConfig.Provider {
    public static final String LOG_TAG = "dfly";
    public static final String NOTIFICATION_CHANNEL_ID = "Notification_CHANNEL_ID";
    public static final String EMPTY_STRING = "";
    private static final String CLOUDS = "clouds";
    private static final String CLOUDS_JSON = "clouds.txt";
    private static final String TG_BOT_TOKEN = "tgBotToken";
    private static final String TG_CHAT_ID = "tgChatId";
    private static final String YA_ACC = "yaAcc";
    private static final String APP_PASS = "appPass";
    private static final String ENCRYPTED_DATA_KEY = "encryptedData";
    private static final String KEY_ALIAS = "SavedRouterKeyAlias";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 16;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final ScheduledExecutorService saveFileExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Clouds savedClouds = new Clouds();
    private WeakReference<Activity> currentActivityRef;
    private OkHttpClient okHttpClient;
    private Cipher mCipher;

    private PowerManager mPowerManager;
    private ColorStateList btnBackColorGreen;
    private ColorStateList btnBackColorBlack;
    private ColorStateList btnRipleColorGreen;
    private ColorStateList btnRipleColorBlack;


    public WeakReference<Activity> getCurrentActivityRef() {
        return currentActivityRef;
    }

    public PowerManager getPowerManager() {
        return mPowerManager;
    }

    public ColorStateList getBtnBackColorGreen() {
        return btnBackColorGreen;
    }

    public ColorStateList getBtnBackColorBlack() {
        return btnBackColorBlack;
    }

    public ColorStateList getBtnRipleColorGreen() {
        return btnRipleColorGreen;
    }

    public ColorStateList getBtnRipleColorBlack() {
        return btnRipleColorBlack;
    }

    public Clouds getSavedClouds() {
        return savedClouds;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        regActivityListener();
        createNotificationChannel();
        initBatteryIcons();
        readCloudsEncrypted();
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS) // Время на установку связи
                .writeTimeout(5, TimeUnit.MINUTES)   // Время на отправку данных (для POST)
                .readTimeout(30, TimeUnit.SECONDS)    // Время на ожидание ответа
                .callTimeout(9, TimeUnit.MINUTES) // Общее время на весь запрос с ответом, чтоб не переподключалось много раз
                .retryOnConnectionFailure(true)
                .build();
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    @Override
    public @NonNull CameraXConfig getCameraXConfig() {
         return Camera2Config.defaultConfig();
//        return new CameraXConfig.Builder()
//                .fromConfig(Camera2Config.defaultConfig())
//                .setMinimumLoggingLevel(Log.ERROR) // Меньше мусора в логах
//                .build();
    }

    public void writeCloudsEncrypted() {
        saveFileExecutor.schedule(()-> {
            File routersListDir = new File(getFilesDir(), CLOUDS);
            if (!routersListDir.exists() && !routersListDir.mkdirs()) {
                Log.d(LOG_TAG, "Failed to create directory.");
                return;
            }

            File routersListFile = new File(routersListDir, CLOUDS_JSON);

            try {
                JSONObject currentCloudsJson = new JSONObject();
                buildJsonFromClouds(currentCloudsJson, savedClouds); // Пароль здесь в открытом виде

                // 2. Шифрование всего JSON-контента
                String rawJsonString = currentCloudsJson.toString();
                byte[] rawJsonBytes = rawJsonString.getBytes(StandardCharsets.UTF_8);
                byte[] encryptedCombinedBytes = encrypt(rawJsonBytes);
                String encryptedBase64 = Base64.encodeToString(encryptedCombinedBytes, Base64.NO_WRAP);

                // 3. Создание JSON-оболочки для записи в файл
                JSONObject fileWrapper = new JSONObject();
                fileWrapper.put(ENCRYPTED_DATA_KEY, encryptedBase64);

                // 4. Запись JSON-оболочки в файл
                try (FileWriter file = new FileWriter(routersListFile)) {
                    file.write(fileWrapper.toString(4));
                }

            } catch (Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
                Log.d(LOG_TAG, "Error writing encrypted JSON file or key management failure: ", e);
            }
        }, 0, TimeUnit.SECONDS);

    }

    private void buildJsonFromClouds(JSONObject cloudsJson, Clouds clouds) throws JSONException {
        String tgBotToken = clouds.getTgBotToken();
        cloudsJson.put(TG_BOT_TOKEN, tgBotToken != null ? tgBotToken : EMPTY_STRING);
        String tgChatId = clouds.getTgChatId();
        cloudsJson.put(TG_CHAT_ID, tgChatId != null ? tgChatId : EMPTY_STRING);
        String yaAcc = clouds.getYaAcc();
        cloudsJson.put(YA_ACC, yaAcc != null ? yaAcc : EMPTY_STRING);
        String appPass = clouds.getAppPass();
        cloudsJson.put(APP_PASS, appPass != null ? appPass : EMPTY_STRING);
    }

    private void readCloudsFromJson(JSONObject cloudsJson, Clouds clouds) {
        String tgBotToken = cloudsJson.optString(TG_BOT_TOKEN, EMPTY_STRING);
        String tgChatId = cloudsJson.optString(TG_CHAT_ID, EMPTY_STRING);
        String yaAcc = cloudsJson.optString(YA_ACC, EMPTY_STRING);
        String appPass = cloudsJson.optString(APP_PASS, EMPTY_STRING);
        clouds.setTgBotToken(tgBotToken);
        clouds.setTgChatId(tgChatId);
        clouds.setYaAcc(yaAcc);
        clouds.setAppPass(appPass);
    }

    private void readCloudsEncrypted() {
        saveFileExecutor.schedule(()-> {
            File routersListDir = new File(getFilesDir(), CLOUDS);
            File routersListFile = new File(routersListDir, CLOUDS_JSON);
            StringBuilder fileContent = new StringBuilder();

            if (!routersListFile.exists()) {
                Log.d(LOG_TAG, "Encrypted settings file not found.");
                return;
            }

            // 1. Чтение содержимого файла-оболочки
            try (FileReader reader = new FileReader(routersListFile)) {
                char[] buffer = new char[1024];
                int length;
                while ((length = reader.read(buffer)) != -1) {
                    fileContent.append(buffer, 0, length);
                }
            } catch (IOException e) {
                Log.d(LOG_TAG, "Error reading encrypted settings file: ", e);
                return;
            }

            // 2. Извлечение и дешифрование данных
            try {
                JSONObject fileWrapper = new JSONObject(fileContent.toString());
                String encryptedBase64 = fileWrapper.getString(ENCRYPTED_DATA_KEY);

                // Декодирование и дешифрование
                byte[] combinedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT); // Base64.DEFAULT безопасно для декодирования
                byte[] decryptedBytes = decrypt(combinedBytes);
                String rawJsonString = new String(decryptedBytes, StandardCharsets.UTF_8);

                // 3. Парсинг дешифрованного полного JSON
                JSONObject currentCloudsJson = new JSONObject(rawJsonString);
                readCloudsFromJson(currentCloudsJson, savedClouds); // Использует открытый пароль из JSON

            } catch (
                    Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
                Log.d(LOG_TAG, "Error reading or decrypting full JSON data: ", e);
            }

        }, 0, TimeUnit.SECONDS);

    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        // Попытка получить существующий ключ
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        // Если ключа нет, создаем новый (Требуется API 23+ для KeyGenParameterSpec)
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

        // Настройка параметров: AES/GCM/NoPadding
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE)
                .build());

        return keyGenerator.generateKey();
    }


    private byte[] encrypt(byte[] dataBytes) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        if (mCipher == null) {
            mCipher = Cipher.getInstance(TRANSFORMATION);
        }
        mCipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] iv = mCipher.getIV();
        byte[] encryptedData = mCipher.doFinal(dataBytes);
        byte[] combined = new byte[1 + iv.length + encryptedData.length];
        combined[0] = (byte) iv.length; // Сохраняем длину IV в первом байте
        System.arraycopy(iv, 0, combined, 1, iv.length); // Копируем IV начиная со второго байта
        System.arraycopy(encryptedData, 0, combined, 1 + iv.length, encryptedData.length); // Копируем данные
        return combined;
    }

    private byte[] decrypt(byte[] combinedBytes) throws Exception {
        // Минимальная длина: 1 байт (длина IV) + 1 байт (IV) + 16 байт (GCM Tag) = 18 байт
        if (combinedBytes.length < 1 + GCM_TAG_LENGTH) {
            throw new InvalidKeyException("Combined data too short to contain IV length and GCM Tag.");
        }

        int ivLength = combinedBytes[0] & 0xFF; // Получаем фактическую длину IV из первого байта
        // Проверяем, достаточно ли данных для IV и GCM Tag
        if (combinedBytes.length < 1 + ivLength + GCM_TAG_LENGTH) {
            throw new InvalidKeyException("IV length leads to combined data too short for GCM Tag.");
        }
        // Извлекаем IV
        byte[] iv = Arrays.copyOfRange(combinedBytes, 1, 1 + ivLength);
        // Извлекаем зашифрованные данные (начинаются после байта длины и IV)
        byte[] encryptedData = Arrays.copyOfRange(combinedBytes, 1 + ivLength, combinedBytes.length);

        SecretKey secretKey = getOrCreateSecretKey();
        mCipher = Cipher.getInstance(TRANSFORMATION);
        // GCM_TAG_LENGTH * 8, так как длина тега указывается в битах (16 байт * 8 = 128 бит)
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

        mCipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return mCipher.doFinal(encryptedData);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void regActivityListener() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@androidx.annotation.NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                //
            }

            @Override
            public void onActivityStarted(@androidx.annotation.NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityResumed(@androidx.annotation.NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@androidx.annotation.NonNull Activity activity) {
                //
            }

            @Override
            public void onActivityStopped(@androidx.annotation.NonNull Activity activity) {
                Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
                if (current == activity) {
                    currentActivityRef = null;
                }
            }

            @Override
            public void onActivitySaveInstanceState(@androidx.annotation.NonNull Activity activity, @androidx.annotation.NonNull Bundle outState) {
                //
            }

            @Override
            public void onActivityDestroyed(@androidx.annotation.NonNull Activity activity) {
                //
            }
        });
    }

    private void initBatteryIcons() {
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        btnBackColorGreen = ContextCompat.getColorStateList(getApplicationContext(), R.color.green_600);
        btnBackColorBlack = ContextCompat.getColorStateList(getApplicationContext(), R.color.black3);
        btnRipleColorGreen = ContextCompat.getColorStateList(getApplicationContext(), R.color.green_100);
        btnRipleColorBlack = ContextCompat.getColorStateList(getApplicationContext(), R.color.light_gray);
    }
}
