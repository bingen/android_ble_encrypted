package com.eguzkitza.bingen.ble;

interface BleServiceCallback {
    void getReceivedString(String data);
    void getProcessText(String data);
    void getTimes(long timeTakenReady, long timeTakenCombine, int packets);
}