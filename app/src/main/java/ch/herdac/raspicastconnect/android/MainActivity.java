package ch.herdac.raspicastconnect.android;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String TAG= "DebugHER";
    private static WifiP2pManager manager;
    private static WifiManager wmgr;
    private static WifiP2pManager.Channel channel;
    private static BroadcastReceiver receiver;
    private static IntentFilter intentFilter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        boolean initP2pBool = initP2p();
        Log.i(TAG, "Init P2p: "+ initP2pBool);
        if (!initP2pBool) {
            finish();
        }

        wmgr = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        Button btntOn = findViewById(R.id.btnOn);
        Button btntOff = findViewById(R.id.btnOFF);
        Button btnConnect = findViewById(R.id.connect);
        btntOn.setOnClickListener(v -> {
            Log.i(TAG, "ON");
            wmgr.setWifiEnabled(true);
            Log.i(TAG, "State: " + wmgr.isWifiEnabled());
        });
        btntOff.setOnClickListener(v -> {
            Log.i(TAG, "OFF");
            wmgr.setWifiEnabled(false);
            wmgr.disconnect();
            Log.i(TAG, "State: " + wmgr.isWifiEnabled());
        });

        btnConnect.setOnClickListener(v -> {
            Log.i(TAG, "Connect");
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "HERDAC";
            wmgr.addNetwork(conf);

        });



    }

    private boolean initP2p() {
        // Device capability definition check
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            Log.e(TAG, "Wi-Fi Direct is not supported by this device.");
            return false;
        }

        // Hardware capability check
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "Cannot get Wi-Fi system service.");
            return false;
        }

        if (!wifiManager.isP2pSupported()) {
            Log.e(TAG, "Wi-Fi Direct is not supported by the hardware or Wi-Fi is off.");
            return false;
        }

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager == null) {
            Log.e(TAG, "Cannot get Wi-Fi Direct system service.");
            return false;
        }

        channel = manager.initialize(this, getMainLooper(), null);
        if (channel == null) {
            Log.e(TAG, "Cannot initialize Wi-Fi Direct.");
            return false;
        }

        return true;
    }

    /* register the broadcast receiver with the intent values to be matched */
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }
    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

}