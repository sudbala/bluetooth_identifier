/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private static final String TAG = "Evening";
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private boolean mScanning;
    private Handler mHandler;

    // Intent filter for broadcast receiver
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        //accessed but does not provide the data I think we need because it does not update when switching between
        Log.d("location", "in here");
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        Log.d("location", intentFilter.toString());
        return intentFilter;
    }



    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d("RECEIVER IN SCAN", action);
            if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                Log.d("from update reciever", action);
                Log.d("RECEIVER IN SCAN", intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
//                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
//            // Automatically connects to the device upon successful start-up initialization.
//            boolean connection = mBluetoothLeService.connect(mDeviceAddress);
//            Log.d("Maria", String.valueOf(connection));
//
//            if (!connection)
//                Log.d("Maria", "connection failed mrp");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, mServiceConnection.toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, DeviceControlActivity.class);

        //to fix when scanning problem is solved
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
//                    List<BluetoothGattService> listGatt = mBluetoothLeService.getSupportedGattServices();

//                    int i = 0;
//                    Log.d("Device List", String.valueOf(mLeDeviceListAdapter.mLeDevices.size()));
//                    for (BluetoothDevice device: mLeDeviceListAdapter.mLeDevices) {
//                        Log.d("NAME", device.getName());
//                    }
//                    mLeDeviceListAdapter.notifyDataSetChanged();
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.getBluetoothLeScanner().startScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
        }

        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public void removeDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.remove(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            final Set<String> connected = new HashSet<>();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BluetoothDevice device = result.getDevice();
//                    Log.d("ENTERING SCAN", "SCANNED " + device.toString());

                    int rssi = result.getRssi();


                    String device_name = device.getName();

                    String device_address = device.getAddress();
                    if (rssi > -70 && result.isConnectable()) {
                        Log.d("RSSI", String.valueOf(rssi));
                        mLeDeviceListAdapter.addDevice(device);
//                        String device_name_2 = result.getScanRecord().getDeviceName();
//                        try{
//                            Log.d("DEVICE NAME", device_name_2);
//                        }catch (Exception e){
//
//                        }
                        if (!connected.contains(device_address)) {
                            mBluetoothLeService.connect(device_address);
                        } else {
                            connected.add(device_address);
                        }
                        List<BluetoothGattService> listGatt = mBluetoothLeService.getSupportedGattServices();
                        if (listGatt.size() > 0) {
                            String uuid = null;
                            ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
                            ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                                    = new ArrayList<ArrayList<HashMap<String, String>>>();
                            mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

                            // Loops through available GATT Services.
                            for (BluetoothGattService gattService : listGatt) {
                                HashMap<String, String> currentServiceData = new HashMap<String, String>();
                                uuid = gattService.getUuid().toString();
                                if (!uuid.equals("0000180a-0000-1000-8000-00805f9b34fb")) {
                                    continue;
                                }

                                List<BluetoothGattCharacteristic> gattCharacteristics =
                                        gattService.getCharacteristics();

                                BluetoothGattCharacteristic marias_thing = gattCharacteristics.get(1);
                                try {
                                    Log.d("CHARAC", marias_thing.toString());
                                    Log.d("CHARAC", "HELLO");
                                    final byte[] data = marias_thing.getValue();
                                    if (data == null) {
                                        Log.d("CHARAC", "womp");
                                    }
                                    Log.d("CHARAC", data.length + "");
                                    if (data != null && data.length > 0) {
//                                        Log.d("listener", "in hope");
                                        final StringBuilder stringBuilder = new StringBuilder(data.length);
                                        for (byte byteChar : data) {
                                            stringBuilder.append(String.format("%02X ", byteChar));
                                        }
                                        String stringversion = stringBuilder.toString();
                                        Log.d("sud", stringversion);
                                        String nospace = stringversion.replaceAll("\\s", "");
                                        String result = "";
                                        char[] charArray = nospace.toCharArray();
                                        for(int i = 0; i < charArray.length; i=i+2) {
                                            String st = ""+charArray[i]+""+charArray[i+1];
                                            char ch = (char)Integer.parseInt(st, 16);
                                            result += ch;
                                        }
                                         Log.d("NAME", result);
                                    }
                                } catch (Exception e) {

                                }
                            }
                        }



                    }
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });



        }
    };

//    // Device scan callback.
//    private BluetoothAdapter.LeScanCallback mLeScanCallback =
//            new BluetoothAdapter.LeScanCallback() {
//
//
//        @Override
//        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
////                    boolean connection = mBluetoothLeService.connect(device.getAddress());
////                    if (connection)
//                    Log.d("RSSI", String.valueOf(rssi));
//                    if (rssi > -70) {
//                        mLeDeviceListAdapter.addDevice(device);
//                    }
//                    mLeDeviceListAdapter.notifyDataSetChanged();
//                }
//            });
//        }
//    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
