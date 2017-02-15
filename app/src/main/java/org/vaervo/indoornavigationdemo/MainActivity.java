package org.vaervo.indoornavigationdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String RECORDS_FILENAME = "saved_info";
    private WifiManager mWifiManager;
    private TextView mInfoTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mInfoTextView = (TextView)findViewById(R.id.info_text_view);
        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScan();
            }
        };
        setOnClickListenerToView(R.id.update_button, onClickListener);
        setOnClickListenerToView(R.id.save_button, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
            }
        });
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Toast.makeText(MainActivity.this,
                        R.string.rssi_changed_toast_text,
                        Toast.LENGTH_SHORT)
                        .show();
                startScan();
            }
        }, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateScanResults();
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSavedInfo();
    }

    private void updateSavedInfo() {
        List<WifiNetworkInfo> records = readRecordsFromFile();
        if (records != null) {
            StringBuilder builder = new StringBuilder();
            for (WifiNetworkInfo wifiNetworkInfo : records) {
                builder.append(wifiNetworkInfo).append("\n");
            }
            TextView savedInfoTextView = (TextView) findViewById(R.id.saved_info_text_view);
            if (savedInfoTextView != null) {
                savedInfoTextView.setText(builder);
            }
        }
    }

    private void save() {
        final List<WifiNetworkInfo> networkInfoList = new ArrayList<>();
        consumeAllScanResults(new ScanResultConsumer() {
            @Override
            public void consume(ScanResult result) {
                networkInfoList.add(new WifiNetworkInfo(result));
            }
        });
        writeRecordsToFile(networkInfoList);
        updateSavedInfo();
    }

    private boolean writeRecordsToFile(List<WifiNetworkInfo> records) {
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = getApplicationContext().openFileOutput(RECORDS_FILENAME, Context.MODE_PRIVATE);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(records);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private List<WifiNetworkInfo> readRecordsFromFile() {
        FileInputStream fin = null;
        ObjectInputStream ois = null;
        try {
            fin = getApplicationContext().openFileInput(RECORDS_FILENAME);
            ois = new ObjectInputStream(fin);
            return (List<WifiNetworkInfo>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setOnClickListenerToView(int viewId, View.OnClickListener onClickListener) {
        View updateButton = findViewById(viewId);
        if (updateButton != null) {
            updateButton.setOnClickListener(onClickListener);
        }
    }

    private void updateScanResults() {
        final StringBuilder builder = new StringBuilder();
        ScanResultConsumer scanResultConsumer = new ScanResultConsumer() {
            @Override
            public void consume(ScanResult result) {
                appendScanResultInfoToBuilder(result, builder);
            }
        };
        consumeAllScanResults(scanResultConsumer);
        mInfoTextView.setText(builder.toString());
    }

    private void consumeAllScanResults(ScanResultConsumer scanResultConsumer) {
        List<ScanResult> scanResults = mWifiManager.getScanResults();
        for (ScanResult result : scanResults) {
            scanResultConsumer.consume(result);
        }
    }

    private void appendScanResultInfoToBuilder(ScanResult result, StringBuilder builder) {
        String ssid = result.SSID;
        String bssid = result.BSSID;
        int signalLevel = result.level;
        builder
                .append(MessageFormat.format("{0} ({1}): {2}", ssid, bssid, signalLevel))
                .append("\n");
    }

    private void startScan() {
        mWifiManager.startScan();
    }

    private interface ScanResultConsumer {
        void consume(ScanResult result);
    }
}
