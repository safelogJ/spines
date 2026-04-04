package com.safelogj.dfly;

import okhttp3.Credentials;

public class Clouds {

    private String tgBotToken = AppController.EMPTY_STRING;
    private String tgChatId = AppController.EMPTY_STRING;
    private String yaAcc = AppController.EMPTY_STRING;
    private String appPass = AppController.EMPTY_STRING;
    private String credentials = AppController.EMPTY_STRING;


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

    public String getAppPass() {
        return appPass;
    }

    public void setAppPass(String appPass) {
        this.appPass = appPass;
    }

    public boolean isValidTg() {
        return !tgBotToken.isEmpty() && !tgChatId.isEmpty();
    }

    public boolean idValidYaDisk() {
        return !yaAcc.isEmpty() && !appPass.isEmpty();
    }

    public String getCredentials() {
        return credentials;
    }

    public void buildCredentials() {
        if(idValidYaDisk()) {
            credentials = Credentials.basic(yaAcc, appPass);
        }
    }
}
