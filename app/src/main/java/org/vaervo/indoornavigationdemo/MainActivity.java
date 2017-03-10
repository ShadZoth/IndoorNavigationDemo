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
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String RECORDS_FILENAME = "saved_info";
    private WifiManager mWifiManager;
    private TextView mInfoTextView;
    private List<Record> mRecords;

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
        mRecords = readRecordsFromFile();
        SparseArray<Map<String, List<WifiNetworkInfo>>> mapSparseArray = transformRecords();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mapSparseArray.size(); i++) {
            if (i != 0) {
                builder.append("\n");
            }
            builder.append("Networks at ").append(mapSparseArray.keyAt(i));
            for (List<WifiNetworkInfo> infoList : mapSparseArray.valueAt(i).values()) {
                builder
                        .append("\n\t")
                        .append(infoList.get(0).getSSID())
                        .append(" (")
                        .append(infoList.get(0).getBSSID())
                        .append("): ");
                for (int j = 0, infoListSize = infoList.size(); j < infoListSize; j++) {
                    WifiNetworkInfo info = infoList.get(j);
                    if (j != infoListSize - 1) {
                        builder.append(info.getSignalLevel()).append(" ");
                    } else {
                        builder.append("=> ").append(getAverageSignalLevel(infoList));
                    }
                }
            }
        }

        TextView savedInfoTextView = (TextView) findViewById(R.id.saved_info_text_view);
        if (savedInfoTextView != null) {
            savedInfoTextView.setText(builder);
        }
    }

    private int getAverageSignalLevel(List<WifiNetworkInfo> infoList) {
        return infoList.get(infoList.size() - 1).getSignalLevel();
    }

    @NonNull
    private SparseArray<Map<String, List<WifiNetworkInfo>>> transformRecords() {
        SparseArray<Map<String, List<WifiNetworkInfo>>> mapSparseArray = new SparseArray<>();
        for (Record record : mRecords) {
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
        for (int i = 0; i < mapSparseArray.size(); i++) {
            for (List<WifiNetworkInfo> infoList : mapSparseArray.valueAt(i).values()) {
                int sum = 0;
                int count = 0;
                for (WifiNetworkInfo info : infoList) {
                    sum += info.getSignalLevel();
                    count++;
                }
                int average = sum / count;
                infoList.add(new WifiNetworkInfo(infoList.get(0).getSSID() + "_average",
                        infoList.get(0).getBSSID(),
                        average));
            }
        }
        return mapSparseArray;
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
        mRecords.add(new Record(location, networkInfoList));
        writeRecordsToFile();
        updateSavedInfo();
    }

    private boolean writeRecordsToFile() {
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = getApplicationContext().openFileOutput(RECORDS_FILENAME, Context.MODE_PRIVATE);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(mRecords);
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
        builder.append("Your current position is: ").append(calculateCurrentPosition());
        mInfoTextView.setText(builder.toString());
    }

    private double calculateCurrentPosition() {
        SparseArray<Map<String, List<WifiNetworkInfo>>> mapSparseArray = transformRecords();
        Map<String, WeightedObservedPoints> stringWeightedObservedPointsMap = new HashMap<>();
        for (int i = 0; i < mapSparseArray.size(); i++) {
            int location = mapSparseArray.keyAt(i);
            Map<String, List<WifiNetworkInfo>> stringListMap = mapSparseArray.valueAt(i);
            for (String bssid : stringListMap.keySet()) {
                WeightedObservedPoints points = stringWeightedObservedPointsMap.get(bssid);
                if (points == null) {
                    points = new WeightedObservedPoints();
                    stringWeightedObservedPointsMap.put(bssid, points);
                }
                points.add(getAverageSignalLevel(stringListMap.get(bssid)), location);
            }
        }
        double[] results = new double[stringWeightedObservedPointsMap.size()];
        int i = 0;
        for (String bssid : stringWeightedObservedPointsMap.keySet()) {
            PolynomialCurveFitter fitter = PolynomialCurveFitter.create(2);
            double[] coeffs = fitter.fit(stringWeightedObservedPointsMap.get(bssid).toList());
            results[i] = calculateCurrentPosition(bssid, coeffs);
            i++;
        }
        Log.d("REG", Arrays.toString(results));
        double min = min(results);
        double max = max(results);
        double sum = 0;
        for (double result : results) {
            if (result != max && result != min) {
                sum += result;
            }
        }
        return sum / (results.length - 2);
    }

    private double max(double[] doubles) {
        double res = doubles[0];
        for (double aDouble : doubles) {
            if (aDouble > res) {
                res = aDouble;
            }
        }
        return res;
    }

    private double min(double[] doubles) {
        double res = doubles[0];
        for (double aDouble : doubles) {
            if (aDouble < res) {
                res = aDouble;
            }
        }
        return res;
    }

    private double calculateCurrentPosition(String bssid, double[] coeffs) {
        ValueFinder valueFinder = new ValueFinder(bssid);
        consumeAllScanResults(valueFinder);
        return valueFinder.getValue() * valueFinder.getValue() * coeffs[2]
                + valueFinder.getValue() * coeffs[1]
                + coeffs[0];
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

    private class ValueFinder implements ScanResultConsumer {
        private final String mBSSID;
        private int mValue;

        ValueFinder(String bssid) {
            this.mBSSID = bssid;
        }

        @Override
        public void consume(ScanResult result) {
            if (result.BSSID.equals(mBSSID)) {
                mValue = result.level;
            }
        }

        int getValue() {
            return mValue;
        }
    }
}
