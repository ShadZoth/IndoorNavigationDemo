package org.vaervo.indoornavigationdemo;

import android.net.wifi.ScanResult;

import java.io.Serializable;

class WifiNetworkInfo implements Serializable {
    private final String mSSID;
    private final String mBSSID;
    private final int mSignalLevel;

    WifiNetworkInfo(ScanResult result) {
        mSSID = result.SSID;
        mBSSID = result.BSSID;
        mSignalLevel = result.level;
    }

    WifiNetworkInfo(String SSID, String BSSID, int signalLevel) {
        mSSID = SSID;
        mBSSID = BSSID;
        mSignalLevel = signalLevel;
    }

    String getSSID() {
        return mSSID;
    }

    String getBSSID() {
        return mBSSID;
    }

    int getSignalLevel() {
        return mSignalLevel;
    }
}
