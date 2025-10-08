package de.kai_morich.simple_bluetooth_terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Arrays;

public class SerialService extends Service implements SerialListener {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    class SerialBinder extends Binder {
        SerialService getService() { return SerialService.this; }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}
    private static class QueueItem {
        QueueType type;
        ArrayDeque<byte[]> datas;
        Exception e;
        QueueItem(QueueType type) { this.type=type; if(type==QueueType.Read) init(); }
        QueueItem(QueueType type, Exception e) { this.type=type; this.e=e; }
        QueueItem(QueueType type, ArrayDeque<byte[]> datas) { this.type=type; this.datas=datas; }
        void init() { datas = new ArrayDeque<>(); }
        void add(byte[] data) { datas.add(data); }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final ArrayDeque<QueueItem> queue1, queue2;
    private final QueueItem lastRead;

    private SerialSocket socket;
    private SerialListener listener;
    private boolean connected;

    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new ArrayDeque<>();
        queue2 = new ArrayDeque<>();
        lastRead = new QueueItem(QueueType.Read);
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        connected = false;
        cancelNotification();
        if(socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if(!connected) throw new IOException("not connected");
        socket.write(data);
    }

    public void attach(SerialListener listener) {
        if(Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        initNotification();
        cancelNotification();
        synchronized (this) { this.listener = listener; }
        for(QueueItem item : queue1) dispatch(item);
        for(QueueItem item : queue2) dispatch(item);
        queue1.clear(); queue2.clear();
    }

    public void detach() {
        if(connected) createNotification();
        listener = null;
    }

    private void dispatch(QueueItem item) {
        switch(item.type) {
            case Connect:       listener.onSerialConnect(); break;
            case ConnectError:  listener.onSerialConnectError(item.e); break;
            case Read:          listener.onSerialRead(item.datas); break;
            case IoError:       listener.onSerialIoError(item.e); break;
        }
    }

    private void initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL,
                    "Background service",
                    NotificationManager.IMPORTANCE_LOW
            );
            nc.setShowBadge(false);
            ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(nc);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public boolean areNotificationsEnabled() {
        NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel nc = nm.getNotificationChannel(Constants.NOTIFICATION_CHANNEL);
        return nm.areNotificationsEnabled() && nc != null && nc.getImportance() > NotificationManager.IMPORTANCE_NONE;
    }

    private void createNotification() {
        Intent disconnectIntent = new Intent()
                .setPackage(getPackageName())
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent disconnectPending = PendingIntent.getBroadcast(this, 1, disconnectIntent, flags);
        PendingIntent restartPending = PendingIntent.getActivity(this, 1, restartIntent, flags);

        Notification notification = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(socket!=null ? "Connected to "+socket.getName() : "Background Service")
                .setContentIntent(restartPending)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPending))
                .build();

        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }

    private void cancelNotification() {
        stopForeground(true);
    }

    // === IMPLEMENTACIÓN DE SerialListener ===

    @Override
    public void onSerialConnect() {
        if(!connected) return;
        postOrQueue(new QueueItem(QueueType.Connect));
    }

    @Override
    public void onSerialConnectError(Exception e) {
        if(!connected) return;
        postOrQueue(new QueueItem(QueueType.ConnectError, e));
        disconnect();
    }

    @Override
    public void onSerialIoError(Exception e) {
        if(!connected) return;
        postOrQueue(new QueueItem(QueueType.IoError, e));
        disconnect();
    }

    // Este método faltaba: encapsula un solo chunk en una cola
    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> single = new ArrayDeque<>();
        single.add(data);
        onSerialRead(single);
    }

    // Esta es la que filtra los tres parámetros
    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        if(!connected) return;
        // 1) Combina todos los chunks
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (byte[] chunk : datas) {
            baos.write(chunk, 0, chunk.length);
        }
        String raw = new String(baos.toByteArray(), UTF8);

        // 2) Extrae valores
        String serial    = extrae(raw, "SERIAL:");
        String hwVersion = extrae(raw, "HW_VERSION:");
        String fmVersion = extrae(raw, "FM_VERSION:");

        // 3) Prepara payload filtrado
        String filtered = "SERIAL:" + serial + "\n"
                + "HW_VERSION:" + hwVersion + "\n"
                + "FM_VERSION:" + fmVersion + "\n";

        byte[] outBytes = filtered.getBytes(UTF8);
        ArrayDeque<byte[]> outQueue = new ArrayDeque<>();
        outQueue.add(outBytes);
        postOrQueue(new QueueItem(QueueType.Read, outQueue));
    }

    private String extrae(String data, String key) {
        int i = data.indexOf(key);
        if (i == -1) return "N/A";
        int end = data.indexOf('\n', i);
        if (end == -1) end = data.length();
        return data.substring(i + key.length(), end).trim();
    }

    private void postOrQueue(QueueItem item) {
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(() -> dispatch(item));
            } else {
                queue2.add(item);
            }
        }
    }
}
