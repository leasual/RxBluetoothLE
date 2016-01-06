package com.leasual.rxble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.text.TextUtils;

import java.util.List;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

/**
 * Created by james.li on 2016/1/6.
 */
public class RxBluetoothLE {

    private static final String TAG = "RxBluetoothLE";
    private static final int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private Context mContext;
    private String mBluetoothDeviceAddress;
    private GattEvents mGattEvents;

    public void setGattEvents(GattEvents events){
        this.mGattEvents = events;
    }

    public RxBluetoothLE(Context context){
        this.mContext = context;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     *  Return true if Bluetooth is available.
     */
    public boolean isBluetoothAvailable(){
        return !(mBluetoothAdapter == null || TextUtils.isEmpty(mBluetoothAdapter.getAddress()));
    }


    /**
     * Return true if Bluetooth is currently enabled and ready for use.
     */
    public boolean isBluetoothEnable(){
        return mBluetoothAdapter.isEnabled();
    }

    /**
     *This will issue a request to enable Bluetooth through the system settings (without stopping
     * your application) via ACTION_REQUEST_ENABLE action Intent.
     */
    public void enableBluetooth(Activity activity){
        if (!mBluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }
    }

    /**
     * Start the remote device discovery process.
     */
    public boolean startDiscovery(){
        return mBluetoothAdapter.startDiscovery();
    }

    /**
     * Return true if the local Bluetooth adapter is currently in the device
     * discovery process.
     */
    public boolean isDiscovering(){
        return mBluetoothAdapter.isDiscovering();
    }

    /**
     * Cancel the current device discovery process.
     */
    public boolean cancelDiscovery(){
        return mBluetoothAdapter.cancelDiscovery();
    }

    /**
     * Observes Bluetooth devices found while discovering.
     */
    public Observable<BluetoothDevice> observeDevices(){

        final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);

        return Observable.defer(() -> Observable.create(new Observable.OnSubscribe<BluetoothDevice>() {
            @Override public void call(Subscriber<? super BluetoothDevice> subscriber) {
                final BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (action.equals(BluetoothDevice.ACTION_FOUND)){
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            subscriber.onNext(device);
                        }
                    }
                };
                mContext.registerReceiver(receiver,filter);
                subscriber.add(unsubscribeInUiThread(() -> mContext.unregisterReceiver(receiver)));
            }
        }));
    }

    /**
     * Observes DiscoveryState, which can be ACTION_DISCOVERY_STARTED or ACTION_DISCOVERY_FINISHED
     * from {@link BluetoothAdapter}.
     */
    public Observable<String> observeDiscovery(){
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        return Observable.defer(() -> Observable.create(new Observable.OnSubscribe<String>() {
            @Override public void call(Subscriber<? super String> subscriber) {
                final BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        subscriber.onNext(intent.getAction());
                    }
                };
                mContext.registerReceiver(receiver,filter);
                subscriber.add(unsubscribeInUiThread(() -> mContext.unregisterReceiver(receiver)));
            }
        }));
    }

    /**
     * Observes BluetoothState. Possible values are:
     * {@link BluetoothAdapter#STATE_OFF},
     * {@link BluetoothAdapter#STATE_TURNING_ON},
     * {@link BluetoothAdapter#STATE_ON},
     * {@link BluetoothAdapter#STATE_TURNING_OFF},
     *
     * @return RxJava Observable with BluetoothState
     */
    public Observable<Integer> observeBluetoothState() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        return Observable.defer(() -> Observable.create(new Observable.OnSubscribe<Integer>() {

            @Override public void call(final Subscriber<? super Integer> subscriber) {
                final BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        subscriber.onNext(mBluetoothAdapter.getState());
                    }
                };
                mContext.registerReceiver(receiver, filter);
                subscriber.add(unsubscribeInUiThread(() -> mContext.unregisterReceiver(receiver)));
            }
        }));
    }


    /**
     * ble device connect
     */
    public boolean connect(String address){

        if (mBluetoothAdapter == null || address == null) {
            return false;
        }
        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        return true;

    }

    /**
     * ble  disconnect
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * ble  close
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * read data from characteristic
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    /**
     * write int data from characteristic
     */
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic,int data){
        if (mBluetoothAdapter == null || mBluetoothGatt == null){
            return false;
        }
        characteristic.setValue(data,BluetoothGattCharacteristic.FORMAT_UINT8,0);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * write bytes data from characteristic
     */
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic,byte[] data){
        if (mBluetoothAdapter == null || mBluetoothGatt == null){
            return false;
        }
        characteristic.setValue(data);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // when bluetoothle use advertisement mode should set this
        /*if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }*/
    }

    /**
     * get bluetoothGattServices
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    /**
     * when ble device connected will callback this
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mGattEvents.onServicesDiscovered(gatt,true);
            }else{
                mGattEvents.onServicesDiscovered(gatt,false);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mGattEvents.characteristicReadState(characteristic,true);
            }else{
                mGattEvents.characteristicReadState(characteristic,false);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mGattEvents.characteristicWriteState(characteristic,true);
            }else{
                mGattEvents.characteristicWriteState(characteristic,false);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            mGattEvents.characteristicDataChange(gatt,characteristic);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED){
                mGattEvents.deviceConnectState(true);
              /*  Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());*/
                mBluetoothGatt.discoverServices();
            }else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                mGattEvents.deviceConnectState(false);
            }
        }
    };

    private Subscription unsubscribeInUiThread(final Action0 unsubscribe) {
        return Subscriptions.create(() -> {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                unsubscribe.call();
            } else {
                final Scheduler.Worker inner = AndroidSchedulers.mainThread().createWorker();
                inner.schedule(() -> {
                    unsubscribe.call();
                    inner.unsubscribe();
                });
            }
        });
    }
}
