package com.leasual.rxbluetoothle;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.leasual.rxble.Action;
import com.leasual.rxble.GattEvents;
import com.leasual.rxble.RxBluetoothLE;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements GattEvents, AdapterView.OnItemClickListener {
    private static final String TAG = "MainActivity";

    private RxBluetoothLE rxBluetoothLE;
    private List<String> devices = new ArrayList<>();
    private Subscription findDevices;
    private Subscription startDiscovery;
    private Subscription stopDiscovery;

    @Bind(R.id.result)
    ListView result;
    @Bind(R.id.start)
    Button start;
    @Bind(R.id.stop)
    Button stop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        rxBluetoothLE = new RxBluetoothLE(this);
        rxBluetoothLE.setGattEvents(this);

        if (!rxBluetoothLE.isBluetoothEnable()){
            rxBluetoothLE.enableBluetooth(this);
        }

        findDevices = rxBluetoothLE.observeDevices()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::addDevice);
        startDiscovery = rxBluetoothLE.observeDiscovery()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.computation())
                .filter(Action.isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
                .subscribe(action -> {
                    start.setText("Searching");
                });
        stopDiscovery = rxBluetoothLE.observeDiscovery()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.computation())
                .filter(Action.isEqualTo(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
                .subscribe(action -> {
                    start.setText("Restart");
                });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unsubscribe(findDevices);
        unsubscribe(startDiscovery);
        unsubscribe(stopDiscovery);
        rxBluetoothLE.disconnect();
        rxBluetoothLE.close();
    }

    @OnClick(R.id.start) public void start(){
        devices.clear();
        setAdapter(devices);
        rxBluetoothLE.startDiscovery();
    }

    @OnClick(R.id.stop) public void stop(){
        rxBluetoothLE.cancelDiscovery();
    }


    @Override
    public void deviceConnectState(boolean isConnected) {
        Log.d(TAG, "deviceConnectState: "+isConnected);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, boolean discovered) {
        Log.d(TAG, "onServicesDiscovered: " + discovered);
        Log.d(TAG, "onServicesDiscovered: "+ rxBluetoothLE.getSupportedGattServices().size());
    }

    @Override
    public void characteristicReadState(BluetoothGattCharacteristic characteristic, boolean success) {
        Log.d(TAG, "characteristicReadState: "+ success);
    }

    @Override
    public void characteristicWriteState(BluetoothGattCharacteristic characteristic, boolean success) {
        Log.d(TAG, "characteristicWriteState: "+ success);
    }

    @Override
    public void characteristicDataChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Log.d(TAG, "characteristicDataChange: ");
    }

    private void addDevice(BluetoothDevice device) {
        String deviceName;
        deviceName = device.getAddress();
        if (!TextUtils.isEmpty(device.getName())) {
            deviceName += " " + device.getName();
        }
        devices.add(deviceName);

        setAdapter(devices);
    }

    private void setAdapter(List<String> list) {
        int itemLayoutId = android.R.layout.simple_list_item_1;
        result.setAdapter(new ArrayAdapter<>(this, itemLayoutId, list));
        result.setOnItemClickListener(this);
    }

    private static void unsubscribe(Subscription subscription) {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String address = devices.get(position).split(" ")[0];
        Log.d(TAG, "onItemClick: "+"address: "+ address);
        rxBluetoothLE.connect(address);
    }
}
