package com.leasual.rxble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by james.li on 2016/1/6.
 */
public interface GattEvents {
    void deviceConnectState(boolean isConnected);
    void onServicesDiscovered(BluetoothGatt gatt,boolean discovered);
    void characteristicReadState( BluetoothGattCharacteristic characteristic,boolean success);
    void characteristicWriteState( BluetoothGattCharacteristic characteristic,boolean success);
    void characteristicDataChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
}
