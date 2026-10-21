package de.kai_morich.simple_bluetooth_terminal.serial;

import java.util.ArrayDeque;

public interface SerialListener {
    public void onSerialConnect      ();
    public void onSerialConnectError (Exception e);
    public void onSerialRead         (byte[] data);                // socket -> service
    public void onSerialRead         (ArrayDeque<byte[]> datas);   // service -> UI thread
    public void onSerialIoError      (Exception e);
}
