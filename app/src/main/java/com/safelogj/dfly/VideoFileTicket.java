package com.safelogj.dfly;

import androidx.annotation.NonNull;

import com.safelogj.dfly.camera.RecorderService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoFileTicket {
    private final Pattern pattern = Pattern.compile("rec_(\\d{2}-\\d{2}-\\d{4}_\\d{2}-\\d{2}-\\d{2})_");
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US);
    @NonNull
    private String path = AppController.EMPTY_STRING;
    private final AtomicLong dateMillis = new AtomicLong();
    private final AtomicBoolean needRemove = new AtomicBoolean(false);
    private final AtomicBoolean needSendYa = new AtomicBoolean(false);
    private final AtomicBoolean needSendTg = new AtomicBoolean(false);
    private final AtomicBoolean needSendNx = new AtomicBoolean(false);

    @NonNull
    public String getPath() {
        return path;
    }

    public void setPath(@NonNull String path) {
        this.path = path;
    }

    public long getDateMillis() {
        return dateMillis.get();
    }

    public void buildDateMillis(String path) {
        dateMillis.set(parseDateMillis(path));
    }

    public void setDateMillis(long date) {
        dateMillis.set(date);
    }

    public boolean isNeedRemove() {
        return needRemove.get();
    }

    public void setNeedRemove(boolean needRemove) {
        this.needRemove.set(needRemove);
    }

    public boolean isNeedSendYa() {
        return needSendYa.get();
    }

    public void setNeedSendYa(boolean needSendYa) {
        this.needSendYa.set(needSendYa);
    }

    public boolean isNeedSendTg() {
        return needSendTg.get();
    }

    public void setNeedSendTg(boolean needSendTg) {
        this.needSendTg.set(needSendTg);
    }

    public boolean isNeedSendNx() {
        return needSendNx.get();
    }

    public void setNeedSendNx(boolean needSendNx) {
        this.needSendNx.set(needSendNx);
    }

    public void setWorkerFlag(@NonNull String worker) {
        switch (worker) {
            case RecorderService.YA_QUEUE -> needSendYa.set(true);
            case RecorderService.TG_QUEUE -> needSendTg.set(true);
            case RecorderService.NX_QUEUE -> needSendNx.set(true);
        }
    }

    private long parseDateMillis(String path) {
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            try {
                String date = matcher.group(1);
                if (date != null) {
                    Date d = simpleDateFormat.parse(date);
                    return d == null ? 0 : d.getTime();
                }
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
}
