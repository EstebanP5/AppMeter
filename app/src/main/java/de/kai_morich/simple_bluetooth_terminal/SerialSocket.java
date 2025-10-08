package de.kai_morich.simple_bluetooth_terminal;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;

public class SerialSocket implements Runnable {

    private static final UUID BLUETOOTH_SPP =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothDevice device;
    private BluetoothSocket socket;
    private SerialListener listener;
    private final BroadcastReceiver disconnectReceiver;

    private volatile boolean connected = false;

    public SerialSocket(Context context, BluetoothDevice device) {
        if (context instanceof Activity)
            throw new InvalidParameterException("Se esperaba un contexto de aplicación, no de actividad");

        this.context = context;
        this.device = device;

        disconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (listener != null)
                    listener.onSerialIoError(new IOException("Desconexión en segundo plano"));
                disconnect();
            }
        };
    }

    public String getName() {
        return device.getName() != null ? device.getName() : device.getAddress();
    }

    public InputStream getInputStream() throws IOException {
        if (socket == null)
            throw new IOException("No conectado");
        return socket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        if (socket == null)
            throw new IOException("No conectado");
        return socket.getOutputStream();
    }

    /**
     * Conecta en segundo plano y notifica a través del listener
     */
    public void connect(SerialListener listener) throws IOException {
        this.listener = listener;

        ContextCompat.registerReceiver(
                context,
                disconnectReceiver,
                new IntentFilter(Constants.INTENT_ACTION_DISCONNECT),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        Executors.newSingleThreadExecutor().submit(this);
    }

    public void disconnect() {
        listener = null;
        connected = false;

        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {}
            socket = null;
        }

        try {
            context.unregisterReceiver(disconnectReceiver);
        } catch (Exception ignored) {}
    }

    public void write(byte[] data) throws IOException {
        if (!connected || socket == null)
            throw new IOException("No conectado");
        socket.getOutputStream().write(data);
    }

    @Override
    public void run() {
        try {
            socket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP);
            socket.connect();

            connected = true;
            if (listener != null) {
                listener.onSerialConnect();
            }
        } catch (Exception e) {
            closeSocketQuietly();
            if (listener != null) {
                listener.onSerialConnectError(e);
            }
            return;
        }

        try {
            byte[] buffer = new byte[1024];
            int len;

            while (connected) {
                len = socket.getInputStream().read(buffer);
                if (len > 0) {
                    byte[] data = Arrays.copyOf(buffer, len);
                    if (listener != null) {
                        listener.onSerialRead(data);
                    }
                }
            }
        } catch (Exception e) {
            if (connected && listener != null) {
                listener.onSerialIoError(e);
            }
        } finally {
            connected = false;
            closeSocketQuietly();
        }
    }

    private void closeSocketQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        } finally {
            socket = null;
        }
    }
}
