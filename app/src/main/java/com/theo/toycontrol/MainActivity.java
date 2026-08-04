package com.theo.toycontrol;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.Random;
import java.util.UUID;
import android.os.ParcelUuid;

public class MainActivity extends AppCompatActivity {

    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback currentCallback;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();
    private TextView statusText;

    private static final int[] LEVELS = {10, 20, 30, 40, 60, 80, 100};
    private static final String[] LEVEL_NAMES = {"1档", "2档", "3档", "4档", "5档", "6档", "7档"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);

        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            statusText.setText("请先开启蓝牙");
            return;
        }
        advertiser = btAdapter.getBluetoothLeAdvertiser();
        requestPermissions();

        int[] btnIds = {R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7};
        for (int i = 0; i < btnIds.length; i++) {
            final int level = i;
            Button btn = findViewById(btnIds[i]);
            btn.setOnClickListener(v -> sendCommand(level));
        }
        findViewById(R.id.btnStop).setOnClickListener(v -> sendStop());
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            }, 1);
        }
    }

    private byte[] buildActiveBigEndian(int levelIndex) {
        int level = LEVELS[levelIndex];
        int V = 0x32 + (level / 2);
        int R = random.nextInt(256);
        byte[] b = new byte[16];
        b[0]  = 0x71;
        b[1]  = 0x00;
        b[2]  = 0x0A;
        b[3]  = (byte) R;
        b[4]  = (byte) 0x82;
        b[5]  = 0x00;
        b[6]  = 0x75;
        b[7]  = (byte) 0xEF;
        b[8]  = 0x01;
        b[9]  = 0x00;
        b[10] = 0x64;
        b[11] = 0x00;
        b[12] = 0x00;
        b[13] = (byte) V;
        b[14] = 0x02;
        int cs = 0;
        for (int i = 0; i < 15; i++) cs += (b[i] & 0xFF);
        b[15] = (byte)(cs & 0xFF);
        return b;
    }

    private byte[] buildStopBigEndian() {
        int R = random.nextInt(256);
        byte[] b = new byte[16];
        b[0]  = 0x71;
        b[1]  = 0x00;
        b[2]  = 0x0A;
        b[3]  = (byte) R;
        b[4]  = 0x0F;
        b[5]  = 0x00;
        b[6]  = 0x75;
        b[7]  = (byte) 0xEF;
        b[8]  = 0x01;
        b[9]  = 0x00;
        b[10] = 0x00;
        b[11] = 0x00;
        b[12] = 0x00;
        b[13] = 0x00;
        b[14] = 0x00;
        int cs = 0;
        for (int i = 0; i < 15; i++) cs += (b[i] & 0xFF);
        b[15] = (byte)(cs & 0xFF);
        return b;
    }

    private ParcelUuid bigEndianBytesToUuid(byte[] b) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (b[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (b[i] & 0xff);
        return new ParcelUuid(new UUID(msb, lsb));
    }

    private void sendCommand(int levelIndex) {
        stopAdvertising();
        startAdvertising(buildActiveBigEndian(levelIndex));
        statusText.setText("当前：" + LEVEL_NAMES[levelIndex]);
    }

    private void sendStop() {
        stopAdvertising();
        startAdvertisingOnce(buildStopBigEndian());
        statusText.setText("已停止");
    }

    private void startAdvertising(byte[] uuidBytes) {
        if (advertiser == null) return;
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build();
        AdvertiseData data = new AdvertiseData.Builder()
            .addServiceUuid(bigEndianBytesToUuid(uuidBytes))
            .setIncludeDeviceName(false)
            .build();
        currentCallback = new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                runOnUiThread(() -> statusText.setText("广播失败: " + errorCode));
            }
        };
        if (checkPerm()) advertiser.startAdvertising(settings, data, currentCallback);
    }

    private void startAdvertisingOnce(byte[] uuidBytes) {
        if (advertiser == null) return;
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build();
        AdvertiseData data = new AdvertiseData.Builder()
            .addServiceUuid(bigEndianBytesToUuid(uuidBytes))
            .setIncludeDeviceName(false)
            .build();
        AdvertiseCallback cb = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings s) {
                handler.postDelayed(() -> {
                    if (checkPerm()) advertiser.stopAdvertising(this);
                }, 800);
            }
        };
        if (checkPerm()) advertiser.startAdvertising(settings, data, cb);
    }

    private void stopAdvertising() {
        if (currentCallback != null && advertiser != null && checkPerm()) {
            advertiser.stopAdvertising(currentCallback);
            currentCallback = null;
        }
    }

    private boolean checkPerm() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendStop();
    }
}
