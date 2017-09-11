package com.eguzkitza.bingen.ble;

import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;

/**
 * BleMonitor
 * 
 * Class used by service BleMonitorService (where the logic is) for low level communication with BLE
 */
@SuppressLint({ "InlinedApi", "NewApi" })
public class BleMonitor {
	private static final String TAG = BleMonitor.class.getSimpleName();
	
	private Context context;
	private BluetoothDevice connectedDevice;
	private BluetoothManager bluetoothManager;

	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothLeAdvertiser bluetoothLeAdvertiser;
	private BluetoothGattServer bluetoothGattServer;
	private BluetoothGattCharacteristic writeCharacteristic;
	private BluetoothGattCharacteristic writeNonceCharacteristic;
	private BluetoothGattCharacteristic readCharacteristic;

	/* Constants */
	private static final UUID BLE_SERVICE_UUID = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb");
	private static final UUID READ_CHARACTERISTIC_UUID = UUID.fromString("0000bbbb-0000-1000-8000-00805f9b34fb");
	private static final UUID WRITE_NONCE_CHARACTERISTIC_UUID = UUID.fromString("0000cccc-0000-1000-8000-00805f9b34fb");
	private static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("0000dddd-0000-1000-8000-00805f9b34fb");

	public static final String BLE_MONITOR_ACTION = "BLE_MONITOR_ACTION";
	public static final String BLE_MONITOR_EXTRA = "BLE_MONITOR_EXTRA";
	public static final String BLE_MONITOR_STATE = "BLE_MONITOR_STATE";
	public static final int ADVERTISE_SUCCESS = 200;
	public static final int ADVERTISE_FAILURE = 500;
	public static final String FAILURE_EXTRA = "FAILURE_EXTRA";
	public static final int BLE_SERVICE_ADDED = 20;
	public static final String GATT_STATUS_EXTRA = "GATT_STATUS_EXTRA";
	public static final String GATT_STATE_EXTRA = "GATT_STATE_EXTRA";
	public static final int BLE_CONNECTION_STATE = 21;
	public static final int CHAR_WRITE_REQUEST = 22;
	public static final int CHAR_WRITE_NONCE_REQUEST = 23;
	public static final int CHAR_READ_REQUEST = 24;
	public static final int DESC_WRITE_REQUEST = 25;
	public static final int DESC_READ_REQUEST = 26;
	public static final String REQUEST_ID_EXTRA = "REQUEST_ID_EXTRA";
	public static final String VALUE_EXTRA = "VALUE_EXTRA";
	
	private int advertiseMode; // = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
	private int advertisePower;// = AdvertiseSettings.ADVERTISE_TX_POWER_LOW;

	public BleMonitor(Context context, int advertiseMode, int advertisePower) {
		this.context = context;
		this.advertiseMode = advertiseMode;
		this.advertisePower = advertisePower;
		init();
	}

	private void init() {
		bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
	}

	// private static final class BleMonitorHandler extends Handler {
	// WeakReference<BleMonitor> mBleMonitor;
	//
	// public BleMonitorHandler(BleMonitor bleMonitor) {
	// mBleMonitor = new WeakReference<BleMonitor>(bleMonitor);
	// }
	//
	// @Override
	// public void handleMessage(Message msg) {
	// BleMonitor bleMonitor = mBleMonitor.get();
	//
	// }
	// }

	public void startAdvertise() {
		openGattServer();
		advertise();
	}

	public void stopAdvertise() {
		Log.i(TAG, "Stop Advertise");

		if (bluetoothGattServer != null) {
			bluetoothGattServer.close();
			bluetoothGattServer = null;
		}

		if (bluetoothLeAdvertiser != null) {
			bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
			bluetoothLeAdvertiser = null;
		}
	}

	private void openGattServer() {
		bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback);

		Log.i("setupGattServer", "Gatt server setup complete ");// + bluetoothGattServer.toString());

		// Read Service
		BluetoothGattService bleService = new BluetoothGattService(BLE_SERVICE_UUID,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);

		readCharacteristic = new BluetoothGattCharacteristic(READ_CHARACTERISTIC_UUID, 
				BluetoothGattCharacteristic.PROPERTY_READ, 
				BluetoothGattCharacteristic.PERMISSION_READ);
		readCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		bleService.addCharacteristic(readCharacteristic);

		writeCharacteristic = new BluetoothGattCharacteristic(WRITE_CHARACTERISTIC_UUID, 
				BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 
				BluetoothGattCharacteristic.PERMISSION_WRITE);
		writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		bleService.addCharacteristic(writeCharacteristic);

		writeNonceCharacteristic = new BluetoothGattCharacteristic(WRITE_NONCE_CHARACTERISTIC_UUID, 
				BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, 
				BluetoothGattCharacteristic.PERMISSION_WRITE);
		writeNonceCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		bleService.addCharacteristic(writeNonceCharacteristic);
		
		bluetoothGattServer.addService(bleService);
	}
	
	public void setReadCharacteristic(byte[] value) {
		readCharacteristic.setValue(value);
	}

	private BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

		@Override
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
			connectedDevice = device;
			updateGattCallback(status, newState);
		}

		@Override
		public void onServiceAdded(int status, BluetoothGattService service) {
			Bundle extras = new Bundle();
			extras.putInt(GATT_STATUS_EXTRA, status);
			sendBroadCast(BLE_SERVICE_ADDED, extras);
		}

		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, 
				int offset, BluetoothGattCharacteristic characteristic) 
		{
			super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
			
			Log.i(TAG, "onCharacteristicReadRequest " + characteristic.getUuid().toString());
			
			Bundle extras = new Bundle();
			extras.putInt(REQUEST_ID_EXTRA, requestId);
			sendBroadCast(CHAR_READ_REQUEST, extras);

			bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 
					offset, characteristic.getValue());
		}

		@Override
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, 
				BluetoothGattCharacteristic characteristic, boolean preparedWrite, 
				boolean responseNeeded, int offset, byte[] value) 
		{
			super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, 
					responseNeeded, offset, value);

			int state;
			if (characteristic.getUuid().equals(WRITE_NONCE_CHARACTERISTIC_UUID)) {
				state = CHAR_WRITE_NONCE_REQUEST;
			} else {
				state = CHAR_WRITE_REQUEST;
			}
			Bundle extras = new Bundle();
			extras.putInt(REQUEST_ID_EXTRA, requestId);
			extras.putByteArray(VALUE_EXTRA, value);
			sendBroadCast(state, extras);
			
			bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 
					offset, characteristic.getValue());
		}

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
			Bundle extras = new Bundle();
			extras.putInt(REQUEST_ID_EXTRA, requestId);
			sendBroadCast(DESC_READ_REQUEST, extras);
		}

		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			Bundle extras = new Bundle();
			extras.putInt(REQUEST_ID_EXTRA, requestId);
			extras.putString(VALUE_EXTRA, value.toString());
			sendBroadCast(DESC_WRITE_REQUEST, extras);
		}

		@Override
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
			Log.i(TAG, "onExecuteWrite");
		}
	};

	public void notifyCharChange(String s) {
		writeCharacteristic.setValue(s);
		try {
			bluetoothGattServer.notifyCharacteristicChanged(connectedDevice, writeCharacteristic, true);
		} catch(Exception e) {
			// Weird and random exception sometimes when switching off client's app
			// Throws an NPE due to invalid java.lang.Integer.intValue
			Log.e(TAG, e.getMessage());
		}
	}

	private void advertise() {
		AdvertiseSettings.Builder asBuilder = new AdvertiseSettings.Builder();
		try {
			Log.i(TAG, "Setting advertise Mode: " + advertiseMode);
			asBuilder.setAdvertiseMode(advertiseMode);
			Log.i(TAG, "Setting advertise Power: " + advertisePower);
			asBuilder.setTxPowerLevel(advertisePower);
			asBuilder.setConnectable(true);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}

		AdvertiseData.Builder adBuilder = new AdvertiseData.Builder();

		adBuilder.addServiceUuid(new ParcelUuid(BLE_SERVICE_UUID));

		bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

		Log.i(TAG, "Start advertising");
		bluetoothLeAdvertiser.startAdvertising(asBuilder.build(), adBuilder.build(), advertiseCallback);
	}

	private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {

		@Override
		public void onStartSuccess(AdvertiseSettings advertiseSettings) {
			sendBroadCast(ADVERTISE_SUCCESS);
		}

		@Override
		public void onStartFailure(int i) {
			Bundle extras = new Bundle();
			extras.putInt(FAILURE_EXTRA, i);
			sendBroadCast(ADVERTISE_FAILURE, extras);
		}
	};

	private void sendBroadCast(int state, Bundle extras) {
		Intent intent = new Intent();
		intent.setAction(BLE_MONITOR_ACTION);
		intent.putExtra(BLE_MONITOR_STATE, state);
		if (extras != null) {
			intent.putExtra(BleMonitor.BLE_MONITOR_EXTRA, extras);
		}

		context.sendBroadcast(intent);
	}

	private void sendBroadCast(int state) {
		sendBroadCast(state, null);
	}

	private void updateGattCallback(int gatt_status, int gatt_state) {
		Bundle extras = new Bundle();
		extras.putInt(GATT_STATUS_EXTRA, gatt_status);
		extras.putInt(GATT_STATE_EXTRA, gatt_state);
		sendBroadCast(BLE_CONNECTION_STATE, extras);
	}

	public int getAdvertiseMode() {
		return advertiseMode;
	}

	public void setAdvertiseMode(int advertiseMode) {
		this.advertiseMode = advertiseMode;
	}

	public int getAdvertisePower() {
		return advertisePower;
	}

	public void setAdvertisePower(int advertisePower) {
		this.advertisePower = advertisePower;
	}

}
