package com.eguzkitza.bingen.ble;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.eguzkitza.bingen.lib.crypto.CryptoUtils;
import com.eguzkitza.bingen.lib.crypto.CryptoUtils.EncryptionType;
import com.eguzkitza.bingen.lib.crypto.DhBigInteger;
import com.eguzkitza.bingen.lib.crypto.DhPrivateKey;
import com.eguzkitza.bingen.lib.crypto.DiffieHellmanExchangeKey;

/**
 * BleMonitorService
 * 
 * Class to hold Service for BLuE scanning. 
 * Here we have our logic, it uses BleMonitor for low level communication with BLE
 * 
 */
@SuppressLint({ "InlinedApi", "NewApi" })
public class BleMonitorService extends Service {

	private static final String TAG = BleMonitorService.class.getSimpleName();
	
//	private final static String NONCE_ALGORITHM = "AES";
//	private final static int NONCE_LENGTH = 160; // 160 bits = 20 bytes, maximum that we can send thru BLE
//	private final static String ENCRYPTION_TYPE = "AES/128/CBC/ZeroBytePadding/Rijndael";
//	private final static String SHARED_KEY_ALGORITHM = "HMAC_SHA256";
//	private final static int SHARED_KEY_LENGTH = 128;
	
	//private final static String ENCRYPTION_TYPE = "AES/CBC/PKCS5Padding/128";
	private final static String ENCRYPTION_TYPE = "AES/CBC/ZeroBytePadding/128";
	private final static int MAX_DATA_SIZE = 20; // Maximum MTU allowed in BLE
	
	private Toast toast;
	private static BluetoothAdapter mBluetoothAdapter;

	private BleMonitor bleMonitor;
//	private boolean bluetoothenabled = false;
	private BleMonitorServiceHandler monitorServiceHandler = new BleMonitorServiceHandler(this);
	private MediaPlayer mp;

	private boolean blueSecurityEnabled;
//	private String clientNonce;
//	private String serverNonce;
	private DiffieHellmanExchangeKey dhExchangeKey;
	private DhBigInteger dhClientKey;
	private DhPrivateKey dhServerKey; 
	private String sharedKey;
	
	private byte[] receivedData;
	private int[] packetInfo;
	boolean newData = true;
	static long startTimeReady;// = System.nanoTime();
	//long startTimeReceiving = System.nanoTime();
	long startTimeCombine;// = System.nanoTime();
	long timeTakenReady;// = System.nanoTime();
	//long timeTakenReceiving = System.nanoTime(); // ~same as Combine
	long timeTakenCombine;// = System.nanoTime();
	int totalPackets;

	final RemoteCallbackList<BleServiceCallback> mCallbacks = new RemoteCallbackList<BleServiceCallback>();

	/*------ Constants -------*/
	private static final int CHECK_APP = 3;
	private static final int CHECK_APP_TIMEOUT = 3000;
	private static final int START_ADVERTISE = 1;
	private static final int STOP_ADVERTISE = 2;
	private static final int BLUETOOTH_ACTION = 4;
	private static final int BLE_MONITOR_ACTION = 5;
	private static final int BLE_NOTIFY_CHANGE = 6;
	private static final int BLE_CONNECTION_STATE_CHANGED = 7;
	private static final int SET_ADVERTISE_PARAMETERS = 8;
	private static final int SET_SECURITY_ENABLED = 9;
//	private static final int SET_PRIVATE_KEYS = 10;

	private int advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY; // just in case setAdvertiseParameters is not called
	private int advertisePower = AdvertiseSettings.ADVERTISE_TX_POWER_LOW; // just in case setAdvertiseParameters is not called

	private final BleService.Stub mBinder = new BleService.Stub() {

		@Override
		public void startBleMonitor() throws RemoteException {
			// startBleMonitor();
			Log.i(TAG, "came from activity");
			if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
				monitorServiceHandler.sendEmptyMessage(START_ADVERTISE);
			}
		}

		@Override
		public void stopBleMonitor() throws RemoteException {
			if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
				monitorServiceHandler.sendEmptyMessage(STOP_ADVERTISE);
			}
		}

		@Override
		public void registerCallback(BleServiceCallback callback) throws RemoteException {
			mCallbacks.register(callback);
		}

		@Override
		public void setAdvertiseParameters(int advertiseMode, int advertisePower) throws RemoteException {
			Log.i(TAG, "BleMonitorService sending message advertise Mode: " + advertiseMode);
			Log.i(TAG, "BleMonitorService sending message advertise Power: " + advertisePower);
			Message message = Message.obtain();
			message.what = SET_ADVERTISE_PARAMETERS;
			message.arg1 = advertiseMode;
			message.arg2 = advertisePower;
			monitorServiceHandler.sendMessage(message);
		}
		
		@Override
		public void setSecurityEnabled(boolean value) throws RemoteException {
			Log.i(TAG, "BleMonitorService sending message setSecurityEnabled: " + value);
			Message message = Message.obtain();
			message.what = SET_SECURITY_ENABLED;
			message.obj = value;
			monitorServiceHandler.sendMessage(message);
		}
		
//		@Override
//		public void setPrivateKeys(String keysJson) throws RemoteException {
//			Log.i(TAG, "BleMonitorService sending message setPrivateKeys: " + keysJson);
//			Message message = Message.obtain();
//			message.what = SET_PRIVATE_KEYS;
//			message.obj = keysJson;
//			monitorServiceHandler.sendMessage(message);
//		}

	};

	@Override
	public void onCreate() {
		super.onCreate();
		BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		startBleMonitor();
		mp = MediaPlayer.create(this, R.raw.beep);
		monitorServiceHandler.sendEmptyMessage(CHECK_APP);
		
		dhExchangeKey = new DiffieHellmanExchangeKey(DiffieHellmanExchangeKey.P, DiffieHellmanExchangeKey.G, 
				DiffieHellmanExchangeKey.BIT_LENGTH, DiffieHellmanExchangeKey.ALGORITHM, 
				DiffieHellmanExchangeKey.KEY_BIT_LENGTH);
		
	}

	public void setAdvertiseParameters(int advertiseMode, int advertisePower) {
		Log.i(TAG, "BleMonitorService setAdvertiseParameters Mode: " + advertiseMode);
		Log.i(TAG, "BleMonitorService setAdvertiseParameters Power: " + advertisePower);
		this.advertiseMode = advertiseMode;
		this.advertisePower = advertisePower;
		
	}
	private void startBleMonitor() {
		displayToast("starting ble monitor service");
		bleMonitor = new BleMonitor(this, advertiseMode, advertisePower);

		registerReceiver(bleMonitorReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		registerReceiver(bleMonitorReceiver, new IntentFilter(BleMonitor.BLE_MONITOR_ACTION));
	}

	private void stopBleMonitor() {
		displayToast("stopping ble monitor service");

		if (bleMonitor != null) {
			bleMonitor.stopAdvertise();
			bleMonitor = null;
		}

		if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
			mBluetoothAdapter.disable();
		}

		unregisterReceiver(bleMonitorReceiver);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopBleMonitor();
		if (mp != null) {
			mp.release();
			mp = null;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}

	public class LocalBinder extends Binder {

		public BleMonitorService getService() {
			return BleMonitorService.this;
		}

	}

	private static final class BleMonitorServiceHandler extends Handler {

		WeakReference<BleMonitorService> mService;

		public BleMonitorServiceHandler(BleMonitorService service) {
			mService = new WeakReference<BleMonitorService>(service);
		}

		@Override
		public void handleMessage(Message msg) {

			BleMonitorService service = mService.get();
			if (service == null) {
				Log.e(TAG, "BleMonitorServiceHandler. BleMonitorService service is null!");
				return;
			}
			if (service.bleMonitor == null) {
				Log.e(TAG, "BleMonitorServiceHandler. BleMonitorService monitor is null!");
				return;
			}

			switch (msg.what) {
			case CHECK_APP:
				if (!service.isAppRunning()) {
					service.displayToast("App is not running");
					service.stopSelf();
				} else {
					sendEmptyMessageDelayed(CHECK_APP, CHECK_APP_TIMEOUT);
				}
				break;
			case START_ADVERTISE:
				service.bleMonitor.startAdvertise();
				service.sendProcessString("starting advertise");
				break;
			case STOP_ADVERTISE:
				// service.stopBleMonitor();
				service.bleMonitor.stopAdvertise();
				service.sendProcessString("stopping advertise");
				break;
			case BLE_NOTIFY_CHANGE:
				service.bleMonitor.notifyCharChange(msg.obj.toString());
//				service.sendProcessString("notify for char change.");
				break;
			case BLE_CONNECTION_STATE_CHANGED:
				if (msg.arg1 == BluetoothGatt.GATT_SUCCESS && msg.arg2 == BluetoothProfile.STATE_CONNECTED) {
					service.setServerNonce();
					service.resetData();
					service.sendProcessString("ready to write.");
				}
				break;
			case BLUETOOTH_ACTION:
				switch (msg.arg1) {
				case BluetoothAdapter.STATE_ON:
					sendEmptyMessageDelayed(START_ADVERTISE, 100);
					break;
				case BluetoothAdapter.STATE_TURNING_OFF:
					if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
						sendEmptyMessageDelayed(STOP_ADVERTISE, 100);
					}
					break;

				default:
					break;
				}
				break;
			case BLE_MONITOR_ACTION:
				switch (msg.arg1) {
				case BleMonitor.CHAR_READ_REQUEST:
					Log.i(TAG, "BleMonitorServiceHandler handling message Monitor Action -> Char Read Request: " + msg.arg1);
					break;
				case BleMonitor.CHAR_WRITE_REQUEST:
					byte[] value = (byte[]) msg.obj;
					Log.i(TAG, "BleMonitorServiceHandler handling message Monitor Action -> Char Write Request: " + msg.arg1 + " Data: " + value);
					service.processData(value);
					break;
				case BleMonitor.CHAR_WRITE_NONCE_REQUEST:
					byte[] clientNonce = (byte[]) msg.obj;
					Log.i(TAG, "BleMonitorServiceHandler handling message Monitor Action -> Char Write Nonce Request: " + msg.arg1);
					//Log.i(TAG, "Data: " + new String(clientNonce));
					//Log.i(TAG, "Data: " + CryptoUtils.bytesToHex(clientNonce));
					service.getClientNonce(clientNonce);
					break;
				default:
					break;
				}
				break;
			case SET_ADVERTISE_PARAMETERS:
				Log.i(TAG, "BleMonitorServiceHandler handling message advertise Mode: " + msg.arg1);
				Log.i(TAG, "BleMonitorServiceHandler handling message advertise Power: " + msg.arg2);
				service.bleMonitor.setAdvertiseMode(msg.arg1);
				service.bleMonitor.setAdvertisePower(msg.arg2);
				break;
			case SET_SECURITY_ENABLED:
				boolean value = (Boolean) msg.obj;
				Log.i(TAG, "BleMonitorServiceHandler handling message set security enabled: " + value);
				service.setSecurityEnabled(value);
				break;
//			case SET_PRIVATE_KEYS:
//				String value = msg.obj.toString();
//				Log.i(TAG, "BleMonitorServiceHandler handling message set Private Keys: " + value);
//				service.setPrivateKeys(value);
//				break;
			default:
				break;
			}
		}
	}

	BroadcastReceiver bleMonitorReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				switch (state) {
				case BluetoothAdapter.STATE_OFF:
					Log.i(TAG, "Bluetooth turned off");
					sendProcessString("bluetooth turned off");
					break;
				case BluetoothAdapter.STATE_TURNING_OFF:
					Log.i(TAG, "Bluetooth turning off");
					displayToast("turning off bluetooth");
					monitorServiceHandler.obtainMessage(BLUETOOTH_ACTION, state, 0).sendToTarget();
					sendProcessString("bluetooth turning off");
					break;
				case BluetoothAdapter.STATE_ON:
					Log.i(TAG, "Bluetooth turned on");
					monitorServiceHandler.obtainMessage(BLUETOOTH_ACTION, state, 0).sendToTarget();
					sendProcessString("bluetooth turned on");
					break;
				case BluetoothAdapter.STATE_TURNING_ON:
					Log.i(TAG, "Bluetooth turning on");
					displayToast("turning on bluetooth");
					sendProcessString("bluetooth turning on");
					break;
				}
			} else if (action.equals(BleMonitor.BLE_MONITOR_ACTION)) {
				final int state = intent.getIntExtra(BleMonitor.BLE_MONITOR_STATE, -1);
				switch (state) {
				case BleMonitor.ADVERTISE_FAILURE:
					Log.i(TAG, "advertising failure ---> error = " + getExtra(intent, BleMonitor.FAILURE_EXTRA));
					sendProcessString("advertising failure");
					break;
				case BleMonitor.ADVERTISE_SUCCESS:
					Log.i(TAG, "advertising success");
					sendProcessString("advertising success");
					break;
				case BleMonitor.BLE_SERVICE_ADDED:
					Log.i(TAG, "service added ---> gatt status = " + getExtra(intent, BleMonitor.GATT_STATUS_EXTRA));
					sendProcessString("ble sevice added.");
					break;
				case BleMonitor.CHAR_READ_REQUEST:
					Log.i(TAG, "characteristic read request ---> request id = " + getExtra(intent, BleMonitor.REQUEST_ID_EXTRA));
					sendProcessString("char read request.");
					monitorServiceHandler.obtainMessage(BLE_MONITOR_ACTION, BleMonitor.CHAR_READ_REQUEST, 0).sendToTarget();
					break;
				case BleMonitor.CHAR_WRITE_REQUEST:
					byte[] data = (byte[]) getExtra(intent, BleMonitor.VALUE_EXTRA);
					synchronized (data) {
						Log.i(TAG, "characteristic write request ---> request id = " + getExtra(intent, BleMonitor.REQUEST_ID_EXTRA) + ", data = " + new String(data));
						monitorServiceHandler.obtainMessage(BLE_MONITOR_ACTION, BleMonitor.CHAR_WRITE_REQUEST, 0, data).sendToTarget();
						sendProcessString("receiving data...");
					}
					break;
				case BleMonitor.CHAR_WRITE_NONCE_REQUEST:
					//String clientNonceEncrypted = (String) getExtra(intent, BleMonitor.VALUE_EXTRA);
					byte[] clientNonceEncrypted = (byte[]) getExtra(intent, BleMonitor.VALUE_EXTRA);
					synchronized (clientNonceEncrypted) {
						Log.i(TAG, "characteristic write nonce request ---> request id = " + getExtra(intent, BleMonitor.REQUEST_ID_EXTRA) + ", data = " + new String(clientNonceEncrypted));
						monitorServiceHandler.obtainMessage(BLE_MONITOR_ACTION, BleMonitor.CHAR_WRITE_NONCE_REQUEST, 0, clientNonceEncrypted).sendToTarget();
						sendProcessString("receiving client shared key...");
					}
					break;
				case BleMonitor.DESC_READ_REQUEST:
					Log.i(TAG, "descriptor read request ---> request id = " + getExtra(intent, BleMonitor.REQUEST_ID_EXTRA));
					sendProcessString("descriptor read request");
					break;
				case BleMonitor.DESC_WRITE_REQUEST:
					Log.i(TAG, "descriptor write request ---> request id = " + getExtra(intent, BleMonitor.REQUEST_ID_EXTRA) + ", data = " + getExtra(intent, BleMonitor.VALUE_EXTRA));
					sendProcessString("descriptor write request");
					break;
				case BleMonitor.BLE_CONNECTION_STATE:
					int gatt_status = (Integer) getExtra(intent, BleMonitor.GATT_STATUS_EXTRA);
					int gatt_state = (Integer) getExtra(intent, BleMonitor.GATT_STATE_EXTRA);
					Log.i(TAG, "connection state change ---> gatt_status = " + gatt_status + ", gatt_state = " + gatt_state);
					monitorServiceHandler.obtainMessage(BLE_CONNECTION_STATE_CHANGED, gatt_status, gatt_state).sendToTarget();
					break;
				}
			}
		}
	};

	@SuppressWarnings("deprecation")
	private boolean isAppRunning() {
		ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
		List<RunningTaskInfo> procInfos = activityManager.getRunningTasks(Integer.MAX_VALUE);
		for (int i = 0; i < procInfos.size(); i++) {
			if (procInfos.get(i).baseActivity.getPackageName().equals(getPackageName())) {
				return true;
			}
		}

		return false;
	}

	public void displayToast(String message) {
		if (toast == null) {
			toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
		}

		toast.setText(message);
		toast.show();
	}

	public void setSecurityEnabled (boolean value) {
		this.blueSecurityEnabled = value;
	}

	private void setServerNonce() {
		dhServerKey  = dhExchangeKey.generatePrivateKey();
		bleMonitor.setReadCharacteristic(dhServerKey.getSharedPreKey().getValueBytes());
	}
//	private void decryptClientNonce(String clientNonceEncrypted) {
//		EncryptionType encryptionType;
//		if (keyList != null && keyList.size() > 0) {
//			String clientNonce = null;
//    		int i = 0;
//    		while(clientNonce == null && i < keyList.size()) {
//    			encryptionType = new EncryptionType(keyList.get(i).getEncryptionType());
//    			clientNonce = CryptoUtils.decrypt(encryptionType, 
//    	    			keyList.get(i), clientNonceEncrypted);
//    	    	Log.i(TAG, "Nonce after decrypt: " + clientNonce); // Ensure to comment this out!
//    	    	i++;
//    		}
//		} else {
//			Log.e(TAG, "No keys to decrypt!");
//		}
//
//		sharedKey = CryptoUtils.getSharedKey(clientNonce, serverNonce, 
//				SHARED_KEY_ALGORITHM, SHARED_KEY_LENGTH);
//	}
	private void getClientNonce(byte[] value) {
		dhClientKey = new DhBigInteger(value);

//		sharedKey = CryptoUtils.getSharedKey(clientNonce, serverNonce, 
//				SHARED_KEY_ALGORITHM, SHARED_KEY_LENGTH);
    	// get shared key from DH protocol
    	sharedKey = dhExchangeKey.calculateSharedKey(dhClientKey.getValue(), 
    			dhServerKey.getExponent().getValue(), dhClientKey.getValueBase64(), 
    			dhServerKey.getSharedPreKey().getValueBase64());
    	
	}
	
	private void processData(byte[] data) {
		Log.i(TAG, "Inside process data");
		String s = combineReceivedData(data);
		Log.i(TAG, "Inside process data. Combine Received Data: " + s);

		// we are using onCharacteristicWrite instead of onCharacteristicChanged in Client, 
		// so no need to change characteristic
		//monitorServiceHandler.obtainMessage(BLE_NOTIFY_CHANGE, s).sendToTarget();
	}

	private String combineReceivedData(byte[] data) {
		//Log.d(TAG, "data String received in combineReceivedData: " + data);
		int idx = (int)data[0];

		Log.d(TAG, "packetInfo: " + (packetInfo == null ? "empty" : Arrays.toString(packetInfo)));
		if (newData) {
			startTimeCombine = System.nanoTime();
			Log.d(TAG, "startTimeCombine set to: " + startTimeCombine); 
			//totalPackets = (int)data[1];
			//receivedData = new byte[totalPackets * (MAX_DATA_SIZE - 2)];
			byte[] total = new byte[2];
			System.arraycopy(data, 1, total, 0, 2);
			String dataSize = CryptoUtils.bytesToHex(total);
			Log.i(TAG, "Data Size Hex: " + dataSize);
			int receivedDataSize = Integer.parseInt(dataSize, 16);
			Log.i(TAG, "Data Size: " + receivedDataSize);
			receivedData = new byte[receivedDataSize];
			totalPackets = (int) Math.ceil((double)(receivedDataSize + 2) / (MAX_DATA_SIZE - 1));
			Log.i(TAG, "Total number of packets: " + totalPackets);
			packetInfo = new int[totalPackets];
			Log.d(TAG, "packetInfo: " + (packetInfo == null ? "empty" : Arrays.toString(packetInfo)));

			for (int i = 0; i < totalPackets; i++) {
				packetInfo[i] = 0;
			}
			newData = false;
			
			//int 
			System.arraycopy(data, 3, receivedData, 0, 
					Math.min(data.length - 3, receivedData.length));
		} else {
			int dstPos = idx * (MAX_DATA_SIZE - 1) - 2;
			System.arraycopy(data, 1, receivedData, dstPos, 
					Math.min(data.length - 1, receivedData.length - dstPos));
			
		}

		packetInfo[idx] = 1;

		Log.d(TAG, "Received string " + idx + "/" + totalPackets + " --->" + new String(data));
		Log.d(TAG, "Received string " + idx + "/" + totalPackets + " --->" + CryptoUtils.bytesToHex(data));
		if (isAllDataReceived()) {
			displayFinalString();
			//beep();
			resetData();
		}
		return "" + idx;
	}
	
	private void resetData() {
		startTimeReady = System.nanoTime();
		Log.d(TAG, "startTimeReady set to: " + startTimeReady); 
		newData = true;
	}

	private boolean isAllDataReceived() {
		for (int i = 0; i < packetInfo.length; i++) {
			if (packetInfo[i] == 0) {
				return false;
			}
		}
		return true;
	}

//	private void beep() {
//		if (mp != null) {
//			mp.start();
//		}
//	}

	private void displayFinalString() {
		long endTime = System.nanoTime();
		Log.d(TAG, "endTime set to: " + endTime); 
		timeTakenReady = Math.round((double)(endTime - startTimeReady) / 1E6);
//		timeTakenReceiving = Math.round((double)(endTime - startTimeReceiving) / 1E6);
		timeTakenCombine = Math.round((double)(endTime - startTimeCombine) / 1E6);

		Log.d(TAG, "All data received. Combined string ---> " + new String(receivedData));
		String finalString = decryptReceivedData(receivedData);
		Log.d(TAG, "All data received. Decrypted string ---> " + finalString);
		sendTimes(timeTakenReady, timeTakenCombine, totalPackets);
		sendFinalString(finalString);
	}
	private String decryptReceivedData(byte[] crypt) {
		if (!blueSecurityEnabled) { // if BLE security is not enabled it should come plain
			Log.i(TAG, "BLuE security disabled");
			return new String(crypt);
		}
		EncryptionType encryptionType = new EncryptionType(ENCRYPTION_TYPE);
    	// we use 16 first bytes of client shared key as IV
    	byte[] iv = new byte[16];
    	System.arraycopy(dhClientKey.getValueBytes(), 0, iv, 0, 16);
    	String stringIv = Base64.encodeToString(iv, Base64.DEFAULT);
		String clear = CryptoUtils.decrypt(encryptionType, crypt, sharedKey, stringIv);
		
		return clear;
	}

	private void sendProcessString(String data) {
		try {
			mCallbacks.beginBroadcast();
			mCallbacks.getBroadcastItem(0).getProcessText(data);
			mCallbacks.finishBroadcast();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	private void sendFinalString(String data) {
		try {
			mCallbacks.beginBroadcast();
			mCallbacks.getBroadcastItem(0).getReceivedString(data);
			mCallbacks.finishBroadcast();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	private void sendTimes(long timeTakenReady, long timeTakenCombine, int packetsReceived) {
		try {
			mCallbacks.beginBroadcast();
			mCallbacks.getBroadcastItem(0).getTimes(timeTakenReady, timeTakenCombine, packetsReceived);
			mCallbacks.finishBroadcast();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public static Object getExtra(Intent intent, String key) {
		return intent.getBundleExtra(BleMonitor.BLE_MONITOR_EXTRA) == null ? -1 : intent.getBundleExtra(BleMonitor.BLE_MONITOR_EXTRA).get(key);
	}

}

