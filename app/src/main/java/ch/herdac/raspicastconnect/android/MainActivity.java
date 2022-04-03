package ch.herdac.raspicastconnect.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
import android.provider.OpenableColumns;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private static final int FILE_CHOOSER_RETURN_CODE = 1000;
    private static final String SSH_USER = "pi";
    private static final int SSH_PORT = 22;
    private static final String SSH_PWD = "hqum#RPI";
    private static final String wifiSSID = "HERDAC";
    private static final String wifiPWD = "hqum#wifi";

    private boolean wifiWasEnabled = true;
    private Integer networkId = null;

    private WifiBroadcastReceiver wifiBroadcastReceiver;
    private List<DiscoveredDevice> reachableDevices = null;

    private static boolean waitingForWifi = false;

    protected Button sendFileBtn;
    protected ProgressBar progressBar;
    protected TextView progressHint;

    public void onWifiConnected(Context context, String ssid) {
        Log.i("WIFI", "Connected to "+ssid);
        Log.i("WIFI", ssid+" | "+wifiSSID+" | "+ssid.equals(wifiSSID));
        if (waitingForWifi && !ssid.equals(wifiSSID)) {
            runOnUiThread(() -> {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.wrong_wifi_title)
                        .setMessage(getString(R.string.wrong_wifi_msg, wifiSSID))
                        .create();
                dialog.show();
            });
        }
        waitingForWifi = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sendFileBtn = findViewById(R.id.sendFileBtn);
        sendFileBtn.setOnClickListener(this::sendFileClick);
        progressBar = findViewById(R.id.progressBar);
        progressHint = findViewById(R.id.progressHint);

        IntentFilter wifiBroadcastReceiverIntentFilter = new IntentFilter();
        wifiBroadcastReceiverIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiBroadcastReceiverIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        wifiBroadcastReceiver = new WifiBroadcastReceiver(this);
        registerReceiver(wifiBroadcastReceiver, wifiBroadcastReceiverIntentFilter);
    }

    private void discoverDevices() throws Exception {
        byte[] localHostIp = null;

        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();

        interfaceLoop:
        for (NetworkInterface netInt : Collections.list(nets)) {
            Enumeration<InetAddress> inetAddresses = netInt.getInetAddresses();
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                byte[] ipAddress = inetAddress.getAddress();
                if (ipAddress[0] == (byte) 192 && ipAddress[1] == (byte) 168) {
                    localHostIp = ipAddress;
                    break interfaceLoop;
                }
            }
        }

        if (localHostIp == null) { throw new Exception("Local host ip not found"); }

        List<DiscoveredDevice> devices = new ArrayList<>();
        byte[] ip = localHostIp.clone();
        for (int i = 100; i <= 105; i++) {
            if (i == localHostIp[3]) { continue; }
            ip[3] = (byte) i;
            devices.add(new DiscoveredDevice(InetAddress.getByAddress(ip).getHostAddress()));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            reachableDevices = discoverParallel(devices);
        } else {
            reachableDevices = discoverSequential(devices);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private List<DiscoveredDevice> discoverParallel(List<DiscoveredDevice> devices) {
        System.out.println("Parallel discovery");
        return devices.parallelStream().filter(DiscoveredDevice::discover).collect(Collectors.toList());
    }
    private List<DiscoveredDevice> discoverSequential(List<DiscoveredDevice> devices) {
        System.out.println("Sequential discovery");
        List<DiscoveredDevice> discoveredDevices = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            DiscoveredDevice device = devices.get(i);
            if (device.discover()) {
                discoveredDevices.add(device);
            }
        }
        return discoveredDevices;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //registerReceiver(wifiBroadcastReceiver, wifiBroadcastReceiverIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(wifiBroadcastReceiver);
    }

    private void sendFileClick(View v) {
        sendFileBtn.setEnabled(false);
        progressHint.setText(R.string.hint_connecting_wifi);

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiWasEnabled = wifiManager.isWifiEnabled();
        wifiManager.setWifiEnabled(true);

        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            waitingForWifi = true;
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
        runOnUiThread(() -> progressHint.setText(R.string.hint_select_file));
        Intent chooseFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFileIntent.setType("video/mp4");
        chooseFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFileIntent = Intent.createChooser(chooseFileIntent, "Choose a file");
        startActivityForResult(chooseFileIntent, FILE_CHOOSER_RETURN_CODE);
    }

    private void makeToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("Range")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RETURN_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getData();
                Log.i("FileChooser", String.valueOf(uri));

                new Thread(() -> {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                        cursor.moveToFirst();
                        long size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                        cursor.close();
                        Log.i("FileChooser", String.valueOf(inputStream.available()));
                        Log.i("FileChooser", String.valueOf(size));
                        sendSSH(inputStream, size);

                    } catch (Exception e) {
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

    private void sendSSH(InputStream inputStream, long size) throws Exception {
        runOnUiThread(() ->  progressHint.setText(R.string.hint_search_device));
        if (reachableDevices == null) {
            discoverDevices();
        }

        Log.i("SSH", "Devices: "+reachableDevices.size());
        Log.i("SSH", "Devices: "+ Arrays.toString(reachableDevices.toArray()));
        if (reachableDevices.size() > 1) {
            runOnUiThread(() -> {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.too_many_devices_title)
                        .setMessage(R.string.too_many_devices_msg)
                        .create();
                dialog.show();
            });
            return;
        }
        if (reachableDevices.size() == 0) {
            runOnUiThread(() ->  {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.no_device_title)
                        .setMessage(R.string.no_device_msg)
                        .create();
                dialog.show();
            });
            return;
        }

        String ssh_host = reachableDevices.get(0).hostIp;

        runOnUiThread(() -> progressHint.setText(R.string.hint_sftp_connect));
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = new JSch().getSession(SSH_USER, ssh_host, SSH_PORT);
            session.setPassword(SSH_PWD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            runOnUiThread(() -> progressHint.setText(R.string.hint_sftp_upload));
            channel.put(inputStream, "/home/pi/video01.mp4", new SftpUploadProgress(this, inputStream, size));
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
        runOnUiThread(() -> {
            progressHint.setText("");
            sendFileBtn.setEnabled(true);
        });
    }

    private static class DiscoveredDevice {
        private static final int TIMEOUT = 100;
        private String hostIp;
        private String hostName;

        public DiscoveredDevice(String hostIp) { this.hostIp = hostIp; }

        public boolean discover() {
            try {
                InetAddress host = InetAddress.getByName(hostIp);
                if (host.isReachable(TIMEOUT)) {
                    hostName = host.getHostName();
                    return true;
                }
            } catch (IOException ignored) {}
            return false;
        }

        @Override
        public String toString() {
            return String.format("IP: %s \t Name: %s", hostIp, hostName);
        }
    }
}