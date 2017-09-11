package com.eguzkitza.bingen.ble.ui;

/**
 */
public interface ScanResultInterface {
    void onSuccess();
    void onSuccess(String startTimeText);
    void onFailure(String message);
}
