package com.eguzkitza.bingen.ble;

import com.eguzkitza.bingen.ble.BleServiceCallback;

interface BleService {
    void startBleMonitor();
    void stopBleMonitor();
    void registerCallback(BleServiceCallback callback);
    void setAdvertiseParameters(int advertiseMode, int advertisePower);
    void setSecurityEnabled(boolean value);
    //void setPrivateKeys(String keysJson);
}