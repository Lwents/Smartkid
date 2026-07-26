package com.example.smartkid.common.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Theo dõi trạng thái mạng để màn hình biết lúc nào mất/có kết nối.
 */
public class NetworkStateReceiver extends BroadcastReceiver {
    public interface Listener {
        void onNetworkChanged(boolean connected);
    }

    private final Listener listener;

    public NetworkStateReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (listener != null) {
                listener.onNetworkChanged(isConnected(context));
            }
        } catch (Exception exception) {
            AppLogger.error(context, "NetworkStateReceiver",
                    "Không thể đọc trạng thái kết nối", exception);
        }
    }

    /** Kiểm tra nhanh máy có đang kết nối mạng không. */
    public static boolean isConnected(Context context) {
        if (context == null) {
            return false;
        }
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return false;
            }
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception exception) {
            AppLogger.error(context, "NetworkStateReceiver",
                    "Không thể kiểm tra kết nối", exception);
            return false;
        }
    }
}
