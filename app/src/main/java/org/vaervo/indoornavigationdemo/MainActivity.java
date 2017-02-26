package org.vaervo.indoornavigationdemo;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseArray;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String RECORDS_FILENAME = "saved_info";
    private WifiManager mWifiManager;
    private TextView mInfoTextView;
    private List<Record> records;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION},
                0);

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
        records = readRecordsFromFile();
        SparseArray<Map<String, List<WifiNetworkInfo>>> mapSparseArray = new SparseArray<>();
        for (Record record : records) {
            if (mapSparseArray.get(record.getLocation()) == null) {
                mapSparseArray.put(record.getLocation(),
                        new HashMap<String, List<WifiNetworkInfo>>());
            }
            for (WifiNetworkInfo info : record.getDescription()) {
                if (mapSparseArray.get(record.getLocation()).get(info.getBSSID()) == null) {
                    mapSparseArray.get(record.getLocation()).put(info.getBSSID(),
                            new ArrayList<WifiNetworkInfo>());
                }
                mapSparseArray.get(record.getLocation()).get(info.getBSSID()).add(info);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mapSparseArray.size(); i++) {
            builder.append("Networks at ").append(mapSparseArray.keyAt(i));
            for (List<WifiNetworkInfo> infoList : mapSparseArray.valueAt(i).values()) {
                builder
                        .append("\n\t")
                        .append(infoList.get(0).getSSID())
                        .append(" (")
                        .append(infoList.get(0).getBSSID())
                        .append("): ");
                int sum = 0;
                int count = 0;
                for (WifiNetworkInfo info : infoList) {
                    builder.append(info.getSignalLevel()).append(" ");
                    sum += info.getSignalLevel();
                    count++;
                }
                builder.append("=> ").append(sum / count);
            }
        }

        TextView savedInfoTextView = (TextView) findViewById(R.id.saved_info_text_view);
        if (savedInfoTextView != null) {
            savedInfoTextView.setText(builder);
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
        int location = 0;
        EditText locationEditText = (EditText) findViewById(R.id.location_edit_text);
        if (locationEditText != null) {
            location = Integer.parseInt(locationEditText.getText().toString());
        }
        records.add(new Record(location, networkInfoList));
        writeRecordsToFile();
        updateSavedInfo();
    }

    private boolean writeRecordsToFile() {
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
    @NonNull
    private List<Record> readRecordsFromFile() {
        FileInputStream fin = null;
        ObjectInputStream ois = null;
        try {
            fin = getApplicationContext().openFileInput(RECORDS_FILENAME);
            ois = new ObjectInputStream(fin);
            return (List<Record>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ArrayList<>();
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
