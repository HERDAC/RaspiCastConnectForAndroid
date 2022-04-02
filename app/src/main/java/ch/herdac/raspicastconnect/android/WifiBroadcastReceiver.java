package ch.herdac.raspicastconnect.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("WFBR", "Received: "+intent.getAction());
        if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION) || intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
            if (networkInfo == null || networkInfo.getState() == null) {

            } else {
                Log.i("WFBR", "Network info: "+ networkInfo.getState());
            }

            if (wifiInfo == null || wifiInfo.getSSID() == null) {
                Log.i("WFBR", "Uninteresting");
            } else {
                String ssid = wifiInfo.getSSID();
                Log.i("WFBR", "SSID: "+ssid);
            }
        }
    }
}
