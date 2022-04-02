package ch.herdac.raspicastconnect.android;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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

public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_RETURN_CODE = 1000;
    private static final String SSH_USER = "pi";
    private static final String SSH_HOST = "192.168.42.160";
    private static final int SSH_PORT = 22;
    private static final String SSH_PWD = "hqum#RPI";
    private static final String wifiSSID = "HERDAC";
    private static final String wifiPWD = "hqum#wifi";

    private boolean wifiWasEnabled = true;
    private Integer networkId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button sendFileBtn = findViewById(R.id.sendFileBtn);
        sendFileBtn.setOnClickListener(this::sendFileClick);
    }

    private void sendFileClick(View v) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiWasEnabled = wifiManager.isWifiEnabled();
        wifiManager.setWifiEnabled(true);

        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = wifiSSID;
            config.preSharedKey = "\"" + wifiPWD + "\"";

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                makeToast("Permission denied");
                return;
            }

            Log.i("HERDAC-Wifi", "Network status (1): "+config.status);
            networkId = wifiManager.addNetwork(config);
            Log.i("HERDAC-Wifi", "Network ID (a): "+networkId);
            Log.i("HERDAC-Wifi", "Network ID (b): "+config.networkId);

            wifiManager.disconnect();
            boolean activated = wifiManager.enableNetwork(networkId, true);
            boolean connected = wifiManager.reconnect();
            Log.i("HERDAC-Wifi", "Activated: "+activated);
            Log.i("HERDAC-Wifi", "Connected: "+connected);

            Network network = getNetwork(connectivityManager);
            Log.i("HERDAC-Wifi", "Network status (2): "+config.status);

            if (network == null) {
                makeToast("Unable to connect to the wifi");
                forgetWifi();
                return;
            }

            ConnectivityManager.setProcessDefaultNetwork(network);

            promptFileSelect();

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

            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    connectivityManager.bindProcessToNetwork(network);
                    promptFileSelect();
                }
            };
            connectivityManager.requestNetwork(request, callback);
        }
    }

    private void promptFileSelect() {
        Intent chooseFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFileIntent.setType("video/mp4");
        chooseFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFileIntent = Intent.createChooser(chooseFileIntent, "Choose a file");
        startActivityForResult(chooseFileIntent, FILE_CHOOSER_RETURN_CODE);
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

                new Thread(() -> {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        Log.i("FileChooser", String.valueOf(inputStream.available()));
                        sendSSH(inputStream);

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        forgetWifi();
                    }
                }).start();
                return;
            } else {
                forgetWifi();
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
            channel.exit();

            runOnUiThread(() -> makeToast("File sent successfully"));

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

    private Network getNetwork(ConnectivityManager connectivityManager) {
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return network;
            }
        }

        return null;
    }

    private void forgetWifi() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (networkId != null) {
            wifiManager.removeNetwork(networkId);
            wifiManager.disconnect();
            wifiManager.reconnect();
            wifiManager.saveConfiguration();
        }
        wifiManager.setWifiEnabled(wifiWasEnabled);
    }
}