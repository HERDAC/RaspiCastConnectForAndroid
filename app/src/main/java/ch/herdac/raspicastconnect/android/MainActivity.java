package ch.herdac.raspicastconnect.android;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_RETURN_CODE = 1000;
    private static final String SSH_USER = "pi";
    private static final String SSH_HOST = "RaspiCast";
    private static final int SSH_PORT = 22;
    private static final String SSH_PWD = "hqum#RPI";
    private final String TAG = "DebugHER";
    private static WifiP2pManager manager;
    private static WifiManager wmgr;
    private static WifiP2pManager.Channel channel;
    private static BroadcastReceiver receiver;
    private static IntentFilter intentFilter;
    private static final String wifiSSID = "HERDAC";
    private static final String wifiPWD = "hqum#wifi";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button sendFileBtn = findViewById(R.id.sendFileBtn);
        sendFileBtn.setOnClickListener(this::sendFileClick);

        /*
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
        Button btnConnect = findViewById(R.id.sendFileBtn);
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

        });*/


    }

    private void sendFileClick(View v) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = wifiSSID;
            config.preSharedKey = "\"" + wifiPWD + "\"";

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                makeToast("Permission denied");
                //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACC});
                return;
            }

            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiManager.addNetwork(config);
            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
            for (WifiConfiguration i : list) {
                if (i.SSID != null && i.SSID.equals("\"" + wifiSSID + "\"")) {
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(i.networkId, true);
                    wifiManager.reconnect();
                    break;
                }
            }

        } else {
            NetworkSpecifier networkSpecifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(wifiSSID)
                    .setWpa2Passphrase(wifiPWD)
                    .build();

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(networkSpecifier)
                    .build();

            ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    promptFileSelect();
                }
            };
            connectivityManager.requestNetwork(request, callback);
        }
    }

    private void promptFileSelect() {
        //makeToast("Choose file");
        Intent chooseFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFileIntent.setType("video/mp4");
        chooseFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFileIntent = Intent.createChooser(chooseFileIntent, "Choose a file");
        startActivityForResult(chooseFileIntent, FILE_CHOOSER_RETURN_CODE);
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
        //registerReceiver(receiver, intentFilter);
    }
    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(receiver);
    }

    private void makeToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RETURN_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                Log.i("FileChooser", String.valueOf(uri));

                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    Log.i("FileChooser", String.valueOf(inputStream.available()));
                    sendSSH(inputStream);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                //String filePath = uri.getPath();
                //Log.i("FileChooser", filePath);
            }
        }
        super.onActivityReenter(resultCode, data);
    }

    private void sendSSH(InputStream inputStream) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = new JSch().getSession(SSH_USER, SSH_HOST, SSH_PORT);
            session.setPassword(SSH_PWD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            channel.put(inputStream, "/home/pi/video01.mp4");

            makeToast("File sent successfully");

        } catch (JSchException | SftpException e) {
            e.printStackTrace();
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }
}