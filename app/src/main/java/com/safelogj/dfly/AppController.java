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
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.CameraXConfig;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

public class AppController extends Application implements CameraXConfig.Provider {
    public static final String LOG_TAG = "dfly";
    public static final String NOTIFICATION_CHANNEL_ID = "Notification_CHANNEL_ID";
    public static final String EMPTY_STRING = "";
    private static final String CLOUDS = "clouds";
    private static final String CLOUDS_JSON = "clouds.txt";
    private static final String FILES = "files";
    private static final String FILES_JSON = "files.txt";
    private static final String TG_BOT_TOKEN = "tgBotToken";
    private static final String TG_CHAT_ID = "tgChatId";
    private static final String YA_ACC = "yaAcc";
    private static final String YA_APP_PASS = "appPass";
    private static final String NEXTCLOUD_USERF = "nextCloudUserF";
    private static final String NEXTCLOUD_PASS = "nextCloudPass";
    private static final String TICKET_LIST = "ticketList";
    private static final String TICKET_PATH = "ticketPath";
    private static final String TICKET_DATE = "ticketDate";
    private static final String TICKET_YA_SEND = "ticketYaSend";
    private static final String TICKET_TG_SEND = "ticketTgSend";
    private static final String TICKET_NX_SEND = "ticketNxSend";
    private static final String TICKET_REMOVE = "ticketRemove";
    private static final String ENCRYPTED_DATA_KEY = "encryptedData";
    private static final String KEY_ALIAS = "SavedRouterKeyAlias";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 16;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final ScheduledExecutorService saveFileExecutor = Executors.newSingleThreadScheduledExecutor();
    @NonNull
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

    @NonNull
    public Clouds getSavedClouds() {
        return savedClouds;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        regActivityListener();
        createNotificationChannel();
        initBatteryIconColors();
        readCloudsEncrypted();
        readTicketsFromFile();

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(3); // Всего не более 3-х активных задач в сети 1-3
        dispatcher.setMaxRequestsPerHost(1); // Не даем одному облаку забить весь канал несколькими потоками 3

        okHttpClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(30, TimeUnit.SECONDS) // Время на установку связи
                .writeTimeout(25, TimeUnit.MINUTES)   // Время на отправку данных (для POST)
                .readTimeout(30, TimeUnit.SECONDS)  // Время на ожидание подтверждения получения данных
                .callTimeout(30, TimeUnit.MINUTES)  // Общее время на весь запрос с ответом, чтоб не переподключалось много раз
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
        saveFileExecutor.execute(() -> {
            File routersListDir = new File(getFilesDir(), CLOUDS);
            if (!routersListDir.exists() && !routersListDir.mkdirs()) {
                Log.d(LOG_TAG, "Failed to create directory.");
                return;
            }

            File routersListFile = new File(routersListDir, CLOUDS_JSON);

            try {
                JSONObject currentCloudsJson = new JSONObject();
                buildJsonFromClouds(currentCloudsJson); // Пароль здесь в открытом виде

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

            } catch (
                    Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
                Log.d(LOG_TAG, "Error writing encrypted JSON file or key management failure: ", e);
            }
        });

    }

    private void buildJsonFromClouds(JSONObject cloudsJson) throws JSONException {
        String tgBotToken = savedClouds.getTgBotToken();
        cloudsJson.put(TG_BOT_TOKEN, tgBotToken != null ? tgBotToken : EMPTY_STRING);
        String tgChatId = savedClouds.getTgChatId();
        cloudsJson.put(TG_CHAT_ID, tgChatId != null ? tgChatId : EMPTY_STRING);
        String yaAcc = savedClouds.getYaAcc();
        cloudsJson.put(YA_ACC, yaAcc != null ? yaAcc : EMPTY_STRING);
        String appPass = savedClouds.getYaAppPass();
        cloudsJson.put(YA_APP_PASS, appPass != null ? appPass : EMPTY_STRING);
        String nextUserF = savedClouds.getNextUserField();
        cloudsJson.put(NEXTCLOUD_USERF, nextUserF != null ? nextUserF : EMPTY_STRING);
        String nextPass = savedClouds.getNextCloudPass();
        cloudsJson.put(NEXTCLOUD_PASS, nextPass != null ? nextPass : EMPTY_STRING);
    }

    public void writeTicketsToFile() {
        saveFileExecutor.execute(() -> {
            File ticketsListDir = new File(getFilesDir(), FILES);
            if (!ticketsListDir.exists() && !ticketsListDir.mkdirs()) {
                Log.d(LOG_TAG, "Failed to create directory.");
                return;
            }

            File tempFile = new File(ticketsListDir, FILES_JSON + ".tmp");
            File finalFile = new File(ticketsListDir, FILES_JSON);
            boolean isSuccess = false;
            try (JsonWriter writer = new JsonWriter(new BufferedWriter(new FileWriter(tempFile)))) {
                writer.beginObject();
                writer.name(TICKET_LIST);
                writer.beginArray();
                for (VideoFileTicket ticket : savedClouds.getVideoFileTicketList()) {
                    writeTicket(writer, ticket);
                }
                writer.endArray();
                writer.endObject();
                isSuccess = true;
                Log.d(LOG_TAG, "Записано в файл потоком. Тикетов: " + savedClouds.getVideoFileTicketList().size());
            } catch (Exception e) {
                Log.d(LOG_TAG, "Ошибка при записи тикетов: ", e);
            }

            try {
                if (isSuccess && tempFile.exists() && tempFile.renameTo(finalFile)) {
                    Log.d(LOG_TAG, "Запись прошла успешно, файл заменен.");
                    return;
                }
                if (tempFile.exists() && tempFile.delete()) {
                    Log.d(LOG_TAG, "Запись tempFile неудача, файл tempFile удалён.");
                } else {
                    Log.d(LOG_TAG, "Запись tempFile неудача, файл tempFile неудалён.");
                }
            } catch (Exception e) {
                Log.d(LOG_TAG, "Ошибка переименования тикетов: ", e);
            }

        });
    }

    private void writeTicket(JsonWriter writer, VideoFileTicket ticket) throws IOException {
            writer.beginObject();
            writer.name(TICKET_PATH).value(ticket.getPath());
            writer.name(TICKET_DATE).value(ticket.getDateMillis());
            writer.name(TICKET_YA_SEND).value(ticket.isNeedSendYa());
            writer.name(TICKET_TG_SEND).value(ticket.isNeedSendTg());
            writer.name(TICKET_NX_SEND).value(ticket.isNeedSendNx());
            writer.name(TICKET_REMOVE).value(ticket.isNeedRemove());
            writer.endObject();
    }

    private void readCloudsFromJson(JSONObject cloudsJson) {
        String tgBotToken = cloudsJson.optString(TG_BOT_TOKEN, EMPTY_STRING);
        String tgChatId = cloudsJson.optString(TG_CHAT_ID, EMPTY_STRING);
        String yaAcc = cloudsJson.optString(YA_ACC, EMPTY_STRING);
        String appPass = cloudsJson.optString(YA_APP_PASS, EMPTY_STRING);
        String nextUserF = cloudsJson.optString(NEXTCLOUD_USERF, EMPTY_STRING);
        String nextPass = cloudsJson.optString(NEXTCLOUD_PASS, EMPTY_STRING);
        savedClouds.setTgBotToken(tgBotToken);
        savedClouds.setTgChatId(tgChatId);
        savedClouds.setYaAcc(yaAcc);
        savedClouds.setYaAppPass(appPass);
        savedClouds.setNextUserField(nextUserF);
        savedClouds.setNextCloudPass(nextPass);
    }


    private void readCloudsEncrypted() {
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
            readCloudsFromJson(currentCloudsJson); // Использует открытый пароль из JSON

        } catch (
                Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
            Log.d(LOG_TAG, "Error reading or decrypting full JSON data: ", e);
        }

    }

    private void readTicketsFromFile() {
        saveFileExecutor.execute(() -> {
            File ticketsListDir = new File(getFilesDir(), FILES);
            File ticketsListFile = new File(ticketsListDir, FILES_JSON);

            if (!ticketsListFile.exists()) {
                Log.d(LOG_TAG, "Tickets file not found.");
                return;
            }

            List<VideoFileTicket> tempList = new ArrayList<>();

            try (JsonReader reader = new JsonReader(new FileReader(ticketsListFile))) {
                reader.beginObject(); // Входим в главный объект {
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.equals(TICKET_LIST)) {
                        reader.beginArray(); // Входим в [
                        while (reader.hasNext()) {
                            tempList.add(readSingleTicket(reader));
                        }
                        reader.endArray(); // Выходим из ]
                    } else {
                        reader.skipValue(); // Пропускаем неизвестные поля
                    }
                }
                reader.endObject(); // Выходим из }

                // Добавляем всё в основной список
                savedClouds.getVideoFileTicketList().addAll(tempList);
                Log.d(LOG_TAG, "Потоковое чтение завершено. Прочитано: " + tempList.size());

            } catch (Exception e) {
                Log.d(LOG_TAG, "Ошибка потокового чтения JSON: ", e);
            }
        });
    }

    private VideoFileTicket readSingleTicket(JsonReader reader) throws IOException {
        VideoFileTicket ticket = new VideoFileTicket();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case TICKET_PATH: ticket.setPath(reader.nextString()); break;
                case TICKET_DATE: ticket.setDateMillis(reader.nextLong()); break;
                case TICKET_YA_SEND: ticket.setNeedSendYa(reader.nextBoolean()); break;
                case TICKET_TG_SEND: ticket.setNeedSendTg(reader.nextBoolean()); break;
                case TICKET_NX_SEND: ticket.setNeedSendNx(reader.nextBoolean()); break;
                case TICKET_REMOVE: ticket.setNeedRemove(reader.nextBoolean()); break;
                default: reader.skipValue(); break;
            }
        }
        reader.endObject();
        return ticket;
    }

    private SecretKey getOrCreateSecretKey() throws KeyStoreException, IllegalArgumentException, IOException, NoSuchAlgorithmException,
            CertificateException, NullPointerException, UnrecoverableEntryException, NoSuchProviderException, InvalidAlgorithmParameterException {

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


    private byte[] encrypt(byte[] dataBytes) throws KeyStoreException, IllegalArgumentException, IOException, NoSuchAlgorithmException,
            CertificateException, NullPointerException, UnrecoverableEntryException, NoSuchProviderException, InvalidAlgorithmParameterException,
            NoSuchPaddingException, UnsupportedOperationException, InvalidKeyException, IllegalBlockSizeException, IllegalStateException,
            BadPaddingException {

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

    private byte[] decrypt(byte[] combinedBytes) throws KeyStoreException, IllegalArgumentException, IOException, NoSuchAlgorithmException,
            CertificateException, NullPointerException, UnrecoverableEntryException, NoSuchProviderException, InvalidAlgorithmParameterException,
            NoSuchPaddingException, UnsupportedOperationException, InvalidKeyException, IllegalBlockSizeException, IllegalStateException,
            BadPaddingException {

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

    private void initBatteryIconColors() {
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        btnBackColorGreen = ContextCompat.getColorStateList(getApplicationContext(), R.color.green_600);
        btnBackColorBlack = ContextCompat.getColorStateList(getApplicationContext(), R.color.black3);
        btnRipleColorGreen = ContextCompat.getColorStateList(getApplicationContext(), R.color.green_100);
        btnRipleColorBlack = ContextCompat.getColorStateList(getApplicationContext(), R.color.light_gray);
    }

}
