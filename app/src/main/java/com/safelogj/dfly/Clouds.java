package com.safelogj.dfly;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Credentials;

public class Clouds {

    private static final String DEFAULT_URL = "https://ivo.lv.tab.digital/remote.php/dav/files/";
    private static final String MARKER = "/remote.php/dav/files/";
    private static final String SLASH = "/";
    private final ReentrantLock yaLock = new ReentrantLock();
    private final ReentrantLock tgLock = new ReentrantLock();
    private final ReentrantLock nxLock = new ReentrantLock();
    private final ReentrantLock removeLock = new ReentrantLock();
    @NonNull
    private final List<VideoFileTicket> videoFileTicketList = new CopyOnWriteArrayList<>();
    private String tgBotToken = AppController.EMPTY_STRING;
    private String tgChatId = AppController.EMPTY_STRING;
    private String yaAcc = AppController.EMPTY_STRING;
    private String yaAppPass = AppController.EMPTY_STRING;
    private String credentialsYa = AppController.EMPTY_STRING;
    private String credentialsNext = AppController.EMPTY_STRING;
    private String nextCloudUrl = AppController.EMPTY_STRING;
    private String nextCloudLogin = AppController.EMPTY_STRING;
    private String nextCloudPass = AppController.EMPTY_STRING;
    private String nextUserField = AppController.EMPTY_STRING;

    public ReentrantLock getYaLock() { return yaLock; }
    public ReentrantLock getTgLock() { return tgLock; }
    public ReentrantLock getNxLock() { return nxLock; }
    public ReentrantLock getRemoveLock() { return removeLock; }


    public String getTgBotToken() {
        return tgBotToken;
    }

    public void setTgBotToken(String tgBotToken) {
        this.tgBotToken = tgBotToken;
    }

    public String getTgChatId() {
        return tgChatId;
    }

    public void setTgChatId(String tgChatId) {
        this.tgChatId = tgChatId;
    }

    public String getYaAcc() {
        return yaAcc;
    }

    public void setYaAcc(String yaAcc) {
        this.yaAcc = yaAcc;
    }

    public String getYaAppPass() {
        return yaAppPass;
    }

    public void setYaAppPass(String yaAppPass) {
        this.yaAppPass = yaAppPass;
    }

    public boolean isValidTg() {
        return !tgBotToken.isEmpty() && !tgChatId.isEmpty();
    }

    public boolean isValidYaDisk() {
        return !yaAcc.isEmpty() && !yaAppPass.isEmpty();
    }

    public boolean isValidNextCloud() {
        return !nextCloudLogin.isEmpty() && !nextCloudPass.isEmpty() && !nextCloudUrl.isEmpty();
    }

    public String getCredentialsYa() {
        return credentialsYa;
    }

    public String getCredentialsNext() {
        return credentialsNext;
    }

    public void buildYaCredentials() {
        if(isValidYaDisk()) {
            credentialsYa = Credentials.basic(yaAcc, yaAppPass);
        }
    }
    public void buildNextCredentials() {
        if(isValidNextCloud()) {
            credentialsNext = Credentials.basic(nextCloudLogin, nextCloudPass);
        }
    }

    public String getNextCloudUrl() {
        return nextCloudUrl;
    }

    public String getNextCloudPass() {
        return nextCloudPass;
    }

    public void setNextCloudPass(String nextCloudPass) {
        this.nextCloudPass = nextCloudPass;
    }

    public String getNextUserField() {
        return nextUserField;
    }

    public void setNextUserField(String nextUserField) {
        this.nextUserField = nextUserField;
        buildUrlLogin();
    }

    private void buildUrlLogin() {
        if (nextUserField.contains(MARKER)) {
            int markerIndex = nextUserField.indexOf(MARKER);
            String afterMarker = nextUserField.substring(markerIndex + MARKER.length());
            int folderIndex = afterMarker.indexOf(SLASH);
            nextCloudLogin = folderIndex == -1 ? afterMarker : afterMarker.substring(0, folderIndex);
            nextCloudUrl = nextUserField.endsWith(SLASH) ? nextUserField : nextUserField + SLASH;
        } else {
            nextCloudLogin = nextUserField;
            nextCloudUrl = DEFAULT_URL + nextUserField + SLASH;
        }
    }
    @NonNull
    public List<VideoFileTicket> getVideoFileTicketList() {
        return videoFileTicketList;
    }
}
