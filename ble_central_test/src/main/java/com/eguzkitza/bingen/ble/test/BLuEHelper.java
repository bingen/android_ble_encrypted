package com.eguzkitza.bingen.ble.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.eguzkitza.bingen.lib.crypto.DhBigInteger;
import com.eguzkitza.bingen.lib.crypto.DhPrivateKey;
import com.eguzkitza.bingen.lib.crypto.DiffieHellmanExchangeKey;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

@SuppressLint({ "InlinedApi", "NewApi" })
public class BLuEHelper { //extends Activity {

	public static final String TAG = "BLE_C";
	private static final boolean DEFAULT_BLuE_SECURITY_ENABLED = true;
	//private static final String ENCRYPTION_TYPE = "AES/CBC/PKCS5Padding";
	private static final String ENCRYPTION_TYPE = "AES/CBC/ZeroBytePadding"; // /128/Rijndael";
	
	public static final int REQUEST_ENABLE_BT = 1;
	
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_SERVICE_FOUND = 3;
    
    private int mConnectionState = STATE_DISCONNECTED;

	public static final String TARGET_SERVICE_UUID = "AAAA";
	public static final String BLE_SERVICE_UUID = "0000aaaa-0000-1000-8000-00805f9b34fb";
	public static final String WRITE_CHARACTERISTIC_UUID = "0000dddd-0000-1000-8000-00805f9b34fb";
	public static final String WRITE_NONCE_CHARACTERISTIC_UUID = "0000cccc-0000-1000-8000-00805f9b34fb";
	public static final String READ_CHARACTERISTIC_UUID = "0000bbbb-0000-1000-8000-00805f9b34fb";
	
	public static final int RSSI_THRESHOLD = -70; 
	private static final int TIMEOUT_IN_SECONDS = 10;
    private static final int MAX_RETRIES = 5;
	
	private static final int MAX_DATA_SIZE = 20;

	public static final int MAIN_HANDLER_PROCESS_TEXT = 1;
	public static final int MAIN_HANDLER_SET_TIMES = 2;
	
	private Context mContext;
	
	private BluetoothAdapter mBluetoothAdapter;
//	private BluetoothLeScanner mBluetoothLeScanner; 
	private BluetoothGattService gattServiceUsed;
//  private String mBluetoothDeviceAddress;  
	private BluetoothGatt mBluetoothGatt;

	private StringInterface stringTranslator;
	
	private boolean blueSecurityEnabled;
	//private String clientNonce;
	//private String clientNonceEncrypted;
	//private String serverNonce;
	private DiffieHellmanExchangeKey dhExchangeKey;
	private DhPrivateKey dhClientKey;
	private DhBigInteger dhServerKey;
	private String sharedKey;
	
	private Runnable scanTicketRunnable;
	private boolean scanTicketDemanded = false;
	private Handler mainHandler;
	private Handler backgroundHandler;
	private HandlerThread backgroundThread;
	
	private int scanAgainInterval; // if < 0, not restarting
    private int retries = 0;
    
	private String deviceAddress;

    private SessionBLuEData data;
    private String originalData;
    private int pieceIndexToSend = 0;
    
    private int rssiThreshold = RSSI_THRESHOLD;

    private long startScanTime;
    private long deviceFoundTime;
    private long connectedTime;
    private long servicesDiscoveredTime;
    private long startSendingTime;
    private long finishedSendingTime;
    
    public BLuEHelper(Context context, Handler handler, StringInterface stringTranslator, 
    		int scanAgainInterval) 
    {
    	this(context, handler, stringTranslator, scanAgainInterval, DEFAULT_BLuE_SECURITY_ENABLED);
    }
    public BLuEHelper(Context context, Handler handler, StringInterface stringTranslator, 
    		int scanAgainInterval, boolean blueSecurityEnabled) 
    {
    	this.mContext = context;
    	this.mainHandler = handler;
    	this.stringTranslator = stringTranslator;
    	this.scanAgainInterval = scanAgainInterval;
    	this.blueSecurityEnabled = blueSecurityEnabled;
    	
    	init();
    }
    
    public void setData( String data ) { //, String clientNonce, String clientNonceEncrypted ) {
    	if (blueSecurityEnabled) {
        	this.data = new SessionBLuEData(new byte[]{0}, false);
    	} else {
        	this.data = new SessionBLuEData(data.getBytes(), true);
    	}
    	originalData = data;
//    	this.clientNonce = clientNonce;
//    	this.clientNonceEncrypted = clientNonceEncrypted;
    }
    
//    @SuppressLint("NewApi")
	private boolean init() {
		
		// Use this check to determine whether BLE is supported on the device.
		// Then you can selectively disable BLE-related features.
		if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {  
			displayScanInfoUI(stringTranslator.getString(R.string.blue_not_supported));
			return false;
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			displayScanInfoUI(stringTranslator.getString(R.string.blue_not_supported));
			return false;
		}
		
//		mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
		
//	    if (android.os.Build.VERSION.SDK_INT < 18) {
//	    	Log.i(TAG, "requires Android 4.3");
//			onBleNotSupported();
//			return false;
//	    }

	    scanTicketRunnable = new Runnable() {
			@Override
			public void run() {
				scanTicketDemanded = true;
	        	data.resetForScan();
	        	scanTicket();
			}
		};
		
		dhExchangeKey = new DiffieHellmanExchangeKey(DiffieHellmanExchangeKey.P, DiffieHellmanExchangeKey.G, 
				DiffieHellmanExchangeKey.BIT_LENGTH, DiffieHellmanExchangeKey.ALGORITHM, 
				DiffieHellmanExchangeKey.KEY_BIT_LENGTH);
		
		stopEverything();
		
		startBackgroundHandler();

		startLeScan();
		
		return true;
	}
    
    public void startBackgroundHandler() {
    	backgroundThread = new HandlerThread("BLuEHelperBackground");
    	backgroundThread.start();
    	backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    private void stopBackgroundHandler() {
    	if (backgroundThread == null) {
    		Log.i(TAG, "not stopping backgroundThread because it's null");
    		return;
    	}
    	backgroundThread.quit();
    	try {
    		backgroundThread.join();
    		backgroundThread = null;
    		backgroundHandler = null;
    	} catch (InterruptedException e) {
    		Log.e(TAG, e.getMessage());
    	}
    }

	public void stopEverything() {
		Log.i(TAG, "Stop everything in BLuEHelper");
		closeConnection();
		stopBackgroundHandler();
		mConnectionState = STATE_DISCONNECTED;
	}
	
    public void closeConnection() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        
		stopScanning();
    }
	
//	@SuppressLint("NewApi")
	private void stopScanning(){
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
//		mBluetoothLeScanner.stopScan(mScanCallback);
	}
	
//	@SuppressLint("NewApi")
	private void startLeScan() {
		resetTimes();
//		Log.e(TAG, "start Le Scan. Time: " + startScanTime);
		backgroundHandler.post(new Runnable() {
			@Override
			public void run() {
				mBluetoothAdapter.startLeScan(mLeScanCallback);
				//mBluetoothLeScanner.startScan(mScanCallback);
			}
		});
	}
	
	private void resetTimes() {
	    startScanTime = System.nanoTime();
	    deviceFoundTime = 0;
	    connectedTime = 0;
	    servicesDiscoveredTime = 0;
	    startSendingTime = 0;
	    finishedSendingTime = 0;
		
	}
		
	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
			if (backgroundHandler == null) {
//				Log.e(TAG, "backgroundHandler is null in BluetoothAdapter.LeScanCallback onLeScan!");
				return;
			}
			deviceFoundTime = Math.round((System.nanoTime() - startScanTime)/1E6);
//			Log.e(TAG, "onLeScan. Time: " + deviceFoundTime);
			backgroundHandler.post(new Runnable() {
				@Override
				public void run() {
					if (isCorrectDevice(scanRecord)) {
						connectOnGoodRSSI(device.getAddress(), rssi);
					}
				}
			});
		}
	};

	private boolean isCorrectDevice(byte[] scanRecord) {
		byte[] n = { scanRecord[6], scanRecord[5] };
		if (BLuE_Utils.bytesToHex(n).equals(TARGET_SERVICE_UUID)) {
			return true;
		}

		return false;
	}
	
	private void connectOnGoodRSSI(String address, int rssi){
    	Log.i(TAG, "connectOnGoodRSSI: " + " Checking RSSI of: " + rssi + " against threshold of: " + rssiThreshold);
//		Log.e(TAG, "connectOnGoodRSSI. Time: " + ((System.nanoTime() - startScanTime)/1E6));
    	
		if(rssi >= rssiThreshold){
			stopScanning();
			deviceAddress = address;
			boolean connectResult = connect(deviceAddress);
			Log.i(TAG, "connectOnGoodRSSI. connectResult: " + connectResult + ". data is provided: " + (data == null ? "null" : data.isProvided()));
//			if (connectResult && data != null && data.isProvided()) {
//				startScanTicket();
//			}
		}
	}
	
	private boolean connect(final String address) {
//		Log.e(TAG, "connect. Time: " + ((System.nanoTime() - startScanTime)/1E6));
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        
        if(mBluetoothGatt != null){
        	mBluetoothGatt.close();
        }

        // Previously connected device. Try to reconnect.
        /*
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }
		*/
        
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        // http://stackoverflow.com/questions/22214254/android-ble-connect-slowly
        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
        
        Log.i(TAG, "Trying to create a new connection.");
//        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }
       
	// Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        	Log.i(TAG, "onConnectionStateChange: " + " status: " + status + ", newState: " + newState);
			connectedTime = Math.round((System.nanoTime() - startScanTime)/1E6);
//    		Log.e(TAG, "onConnectionStateChange. Time: " + connectedTime);
        	
            if (newState == BluetoothProfile.STATE_CONNECTED) {
            	mConnectionState = STATE_CONNECTED;
                Log.i(TAG, "BluetoothGattCallback. Connected to GATT server.");              
                // Attempts to discover services after successful connection.
                if(mBluetoothGatt == null) {
                	Log.i(TAG, "---> BluetoothGattCallback. mBluetoothGatt is null");
                } else {
                	discoverServices();
//                	createService();
                }
  
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothGattCallback. Disconnected from GATT server.");
                mConnectionState = STATE_DISCONNECTED;
                if(data.isConfirmed() == false){
                	Log.i(TAG, "BluetoothGattCallback. Retry connection, iteration: " + retries);
                	if(retries < MAX_RETRIES){
            			connect(deviceAddress);
                		retries++;
                	} else {
                    	Log.i(TAG, "BluetoothGattCallback. MAX_RETRIES reached");
            			displayScanInfoUI(stringTranslator.getString(R.string.blue_scan_failure));
                		// TODO: restart?!
                	}
                }
            } 
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			servicesDiscoveredTime = Math.round((System.nanoTime() - startScanTime)/1E6);
//    		Log.e(TAG, "onServicesDiscovered. Time: " + servicesDiscoveredTime);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> servicesList = gatt.getServices();
                Log.i(TAG, "BluetoothGattCallback. Number of services found: " + servicesList.size());
                //findServiceForConnectionParams(servicesList);
                findServiceForSend(servicesList);
            } else {
                Log.w(TAG, "BluetoothGattCallback. onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        	Log.i(TAG, "----> BluetoothGattCallback. onCharacteristicRead. Status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
//                String value = BLuE_Utils.stringFromByteArray(characteristic.getValue());
            	byte[] value = characteristic.getValue();
                Log.i(TAG, "-----> BluetoothGattCallback. Value: " + value);
                if(value != null && value.length > 0) {
//                    serverNonce = value;
                    dhServerKey = new DhBigInteger(value);
                    encryptDataAndSend();
                }
            }
        }   
 
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        	Log.i(TAG, "----> BluetoothGattCallback. onCharacteristicWrite");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = characteristic.getValue();
                if(value != null && value.length > 0) {
                    Log.i(TAG, "-----> BluetoothGattCallback. Value: " + value);
	            	// if it's response from Nonce sending
	            	if (characteristic.getUuid().toString().equals(WRITE_NONCE_CHARACTERISTIC_UUID)) {
	                    Log.i(TAG, "-----> BluetoothGattCallback onCharacteristicWrite. Is Nonce. " + characteristic.getUuid().toString());
	            		receiveServerNonce();
	            	} else { // it's response from session sending
	                    Log.i(TAG, "-----> BluetoothGattCallback onCharacteristicWrite. Is Regular. " + characteristic.getUuid().toString());
	                	checkAndSend(value);
	            	}
                } 
            }
        }   
 
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        	Log.i(TAG, "----> BluetoothGattCallback. onCharacteristicChanged");
            String value = BLuE_Utils.stringFromByteArray(characteristic.getValue());
            Log.i(TAG, "-----> BluetoothGattCallback. Value: " + value);
//            if(value != null && value.length() > 0) {
//            	checkAndSend(value);
//            } 
        }
    };
    
    private void discoverServices() {
    	boolean discover = mBluetoothGatt.discoverServices();
    	Log.i(TAG, "BluetoothGattCallback. Attempting to start service discovery:" + discover);
    }
    // this doesn't work, it was an attempt to avoid service discovering to gain some time
//    private void createService() {
//    	gattServiceUsed = new BluetoothGattService(UUID.fromString(SERVICE_UUID), 
//    			BluetoothGattService.SERVICE_TYPE_PRIMARY);
//    	BluetoothGattCharacteristic readCharacteristic = 
//    			new BluetoothGattCharacteristic(UUID.fromString(READ_CHARACTERISTIC_UUID), 
//    					BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
//		readCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//		gattServiceUsed.addCharacteristic(readCharacteristic);
//    	BluetoothGattCharacteristic writeCharacteristic = 
//    			new BluetoothGattCharacteristic(UUID.fromString(WRITE_CHARACTERISTIC_UUID), 
//    					BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
//		writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//		gattServiceUsed.addCharacteristic(writeCharacteristic);
//    	Log.i(TAG, "createService. gattServiceUsed characteristic List: " + gattServiceUsed.getCharacteristics());
//    	mConnectionState = STATE_SERVICE_FOUND;
//    	checkAndSend(new byte[]{(byte)(-1 & 0xFF)});
//    }
    private void updateUIAfterConfirm(){
    	if (backgroundHandler != null) {
    		backgroundHandler.removeCallbacks(stopScanningTimeoutRunnable);
    	} else {
    		Log.e(TAG, "updateUIAfterConfirm. backgroundHandler is null!");
    	}
		displayScanInfoUI(stringTranslator.getString(R.string.blue_scan_success));
    }
    
    /* set connection interval 
     * We are disabling it, as Reader is not offering characteristic 0x2a04:
     * 
     * https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.peripheral_preferred_connection_parameters.xml
     * 
     * under service 0x1800:
     * 
     * https://developer.bluetooth.org/gatt/services/Pages/ServiceViewer.aspx?u=org.bluetooth.service.generic_access.xml
     * 
     * It only offers characteristics 0x2a00 and 0x2a01:
     * 
     * https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.device_name.xml
     * https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.gap.appearance.xml
     * 
     * Anyway, it seems Android's default connection time interval is already the minimum, 7.5ms
     * 
    private String CONN_SERVICE_UUID = "00001800-0000-1000-8000-00805f9b34fb";
    private static final UUID CONN_CHARACTERISTIC_UUID = UUID.fromString("00002a04-0000-1000-8000-00805F9B34FB");
    private static final int CONN_INTERVAL = 0x0006; // or should it be 0x0600? according to 5th comment here: http://vsnmobil.challengepost.com/forum_topics/3045-android-how-to-decrease-slave-latency
    private static final int SUPERVISION_TIMEOUT = 0x000A; // or 0x0A00?
    private void findServiceForConnectionParams(List<BluetoothGattService> gattServices){
    	BluetoothGattService connGattService = filterServices(gattServices, CONN_SERVICE_UUID);
    	if (connGattService != null) {
    		setConnectionInterval(connGattService);
    	}
    }
    private void setConnectionInterval(BluetoothGattService gattService) {
    	if (gattService == null) {
    		Log.e(TAG, "setConnectionInterval. Gatt service is null!");
    		return;
    	}
    	BluetoothGattCharacteristic connCharacteristic = 
    			gattService.getCharacteristic(CONN_CHARACTERISTIC_UUID);
        if (connCharacteristic != null) {
        	byte[] value = { (byte) (CONN_INTERVAL & 0x00FF), // gets LSB of 2 byte value
        			(byte) ((CONN_INTERVAL & 0xFF00) >> 8), // gets MSB of 2 byte value
					(byte) (CONN_INTERVAL & 0x00FF),
					(byte) ((CONN_INTERVAL & 0xFF00) >> 8),
					0, 0,
					(byte) (SUPERVISION_TIMEOUT & 0x00FF),
					(byte) ((SUPERVISION_TIMEOUT & 0xFF00) >> 8)
	        };
	        connCharacteristic.setValue(value);
	        boolean status = mBluetoothGatt.writeCharacteristic(connCharacteristic);
	        Log.d(TAG, "setConnectionInterval. Change connection interval result: " + status);
        } else {
    		Log.e(TAG, "setConnectionInterval. Connection characteristic is null!");
        }
    	
    }
     */
    
    private void findServiceForSend(List<BluetoothGattService> gattServices){
    	gattServiceUsed = filterServices(gattServices, BLE_SERVICE_UUID);
    	if (gattServiceUsed != null) {
			mConnectionState = STATE_SERVICE_FOUND;
    		if (scanTicketDemanded) {
    			if (blueSecurityEnabled) {
    				sendNonce();
    			} else {
    				checkAndSend(new byte[]{(byte)(-1 & 0xFF)});
    			}
    		}
    	}
    	
//    	updateConnectionStatus("correct service not found - retrying");
//    	connectAndSendData();
//    	retries++;
    }
    
    private BluetoothGattService filterServices(List<BluetoothGattService> gattServices, String targetUuid) {
    	for(BluetoothGattService gattService : gattServices){
    		String serviceUUID = gattService.getUuid().toString();
    		Log.i(TAG, "serviceUUID: " + serviceUUID);
    		
    		if(serviceUUID.equals(targetUuid)){
        		Log.i(TAG, "serviceUUID matches! UUID: " + serviceUUID + " Type: " + gattService.getType());
        		for(BluetoothGattCharacteristic characteristic : gattService.getCharacteristics()) {
        			Log.i(TAG, "serviceUUID characteristics: " + characteristic.getUuid().toString());
        		}
    			return gattService;
    		}
    	}
    	return null;
    }
    
    private void sendNonce() {
    	BluetoothGattCharacteristic writeGattCharacteristic = 
    			gattServiceUsed.getCharacteristic(UUID.fromString(WRITE_NONCE_CHARACTERISTIC_UUID));
    	//byte[] messageBytes = clientNonceEncrypted.getBytes();
    	dhClientKey = dhExchangeKey.generatePrivateKey();
    	Log.i(TAG, "writing to characteristic");   	 
    	Log.i(TAG, "Data: " + dhClientKey.getSharedPreKey().getValue());
    	Log.i(TAG, "Data: " + new String(dhClientKey.getSharedPreKey().getValueBytes()));
    	Log.i(TAG, "Data: " + BLuE_Utils.bytesToHex(dhClientKey.getSharedPreKey().getValueBytes()));
    	//Log.i(TAG,"is characteristic null: " + (writeGattCharacteristic == null) + "");
    	   
    	writeGattCharacteristic.setValue(dhClientKey.getSharedPreKey().getValueBytes());
    	mBluetoothGatt.writeCharacteristic(writeGattCharacteristic);	
    }
    
    private void receiveServerNonce() {
    	Log.i(TAG, "reading characteristic");   	 
    	BluetoothGattCharacteristic readGattCharacteristic = 
    			gattServiceUsed.getCharacteristic(UUID.fromString(READ_CHARACTERISTIC_UUID));
//		Log.i(TAG, "Read Characteristic descriptors count: " + readGattCharacteristic.getDescriptors().size());
//    	for (BluetoothGattDescriptor descriptor : readGattCharacteristic.getDescriptors()) {
//    		Log.i(TAG, "Read Characteristic descriptor value: " + descriptor.getValue());
//    		Log.i(TAG, "Read Characteristic descriptor permissions: " + descriptor.getPermissions());
//    	}
//    	mBluetoothGatt.setCharacteristicNotification(readGattCharacteristic, true);
    	boolean readResult = mBluetoothGatt.readCharacteristic(readGattCharacteristic);
    	Log.i(TAG, "reading characteristic result: " + readResult);   	 
    	
    }
    private void encryptDataAndSend() {
    	
    	if (blueSecurityEnabled) {
	    	// get shared key from DH protocol
	    	sharedKey = dhExchangeKey.calculateSharedKey(dhServerKey.getValue(), 
	    			dhClientKey.getExponent().getValue(), dhClientKey.getSharedPreKey().getValueBase64(), 
	    			dhServerKey.getValueBase64());
	    	
	    	// encrypt data to send using obtained shared key
	    	// we use 16 first bytes of client shared key as IV
	    	byte[] iv = new byte[16];
	    	System.arraycopy(dhClientKey.getSharedPreKey().getValueBytes(), 0, iv, 0, 16);
	    	String stringIv = Base64.encodeToString(iv, Base64.DEFAULT);
	    	byte[] encryptedData = BLuE_Utils.encrypt(originalData, ENCRYPTION_TYPE, sharedKey,
	    			stringIv);
	    	if (encryptedData == null) {
	    		Log.e(TAG, "Encryption failed!!!!!!!!!!!");
	    		return;
	    	}
	    	Log.i(TAG, "encryptDataAndSend, original length: " + originalData.length());
	    	Log.i(TAG, "encryptDataAndSend, encrypted length: " + encryptedData.length);
	    	
	    	data = new SessionBLuEData(encryptedData, true);

    	} else {
    		data = new SessionBLuEData(originalData.getBytes(), true);
    	}
    	// send encrypted data to server
		checkAndSend(new byte[]{(byte)(-1 & 0xFF)});
    }
    
    private void writeToCharacteristic(){
    	//ArrayList<BluetoothGattCharacteristic> characteristics = (ArrayList<BluetoothGattCharacteristic>) gattService.getCharacteristics();
    	
    	BluetoothGattCharacteristic writeGattCharacteristic = 
    			gattServiceUsed.getCharacteristic(UUID.fromString(WRITE_CHARACTERISTIC_UUID));
    	byte[] messageBytes = data.getSessionBLuEPieces().get(pieceIndexToSend).getValue();
    	//Log.i(TAG, "Number of characteristics: " + characteristics.size()); 
    	Log.i(TAG, "writing to characteristic");   	 
    	Log.i(TAG, "Piece index to Send: " + pieceIndexToSend);
    	Log.i(TAG, "Over a total of " + data.getPiecesNum());
    	Log.i(TAG, "Data: " + BLuE_Utils.stringFromByteArray(messageBytes));
    	Log.i(TAG, "Data: " + BLuE_Utils.bytesToHex(messageBytes));
    	//Log.i(TAG,"is characteristic null: " + (writeGattCharacteristic == null) + "");
    	   
    	writeGattCharacteristic.setValue(messageBytes);
    	mBluetoothGatt.writeCharacteristic(writeGattCharacteristic);	
    }
    
    public void startScanTicket() {
        if (backgroundHandler == null)
            return;
    	backgroundHandler.post(scanTicketRunnable);
    }
    private void scanTicket(){
    	Log.i(TAG, "scanTicket. Entering.");
//		Log.e(TAG, "scanTicket. Time: " + ((System.nanoTime() - startScanTime)/1E6));
    	if(data.isProvided()){
    		if (mConnectionState != STATE_SERVICE_FOUND) {
    	    	Log.e(TAG, "scanTicket. Service not found yet. State: " + mConnectionState);
    	    	if (mConnectionState == STATE_DISCONNECTED) {
    	    		stopScanning();
    	    		startLeScan();
    	    	} else {
    	    		discoverServices();
    	    	}
    		} else {
	    		if(!data.isConfirmed()){
	    	    	Log.i(TAG, "scanTicket. Starting to send data.");
	    			retries = 0;
    				checkAndSend(new byte[]{(byte)(-1 & 0xFF)});
	    			startScanningTimeout(true);
	    			displayScanInfoUI(stringTranslator.getString(R.string.blue_scanning));
	    		} else {
	    			/*
	    			 *  we shouldn't reach here. Callback on characteristic changed calls checkAndSend
	    			 *  and if all data has been sent calls finishedSending which restarts the process
	    			 */
	    	    	Log.i(TAG, "scanTicket. Data is already transmitted.");
	    		}
    		}
    	}
    }
    public void stopScanTicket() {
    	backgroundHandler.removeCallbacks(scanTicketRunnable);
    }
    
    private void checkAndSend(byte[] value) {
//		Log.e(TAG, "checkAndSend. Time: " + ((System.nanoTime() - startScanTime)/1E6));
    	// set piece of data just sent as confirmed
    	//int pieceIndexSent = Integer.parseInt(value);
    	if (value.length > 1) {
    		Log.i(TAG, "Last piece sent: " + (int)(value[0]));
    	}
		//int pieceIndexSent = value.charAt(0) - 48;
		int pieceIndexSent = value[0];
		Log.i(TAG, "checkAndSend. Last piece sent index: " + pieceIndexSent);
    	if (pieceIndexSent >= 0 && pieceIndexSent < data.getPiecesNum()) {
    		data.getSessionBLuEPieces().get(pieceIndexSent).setConfirmed(true);
    	} else if (pieceIndexSent < 0) {
			startSendingTime = Math.round((System.nanoTime() - startScanTime)/1E6);
    	}
    	if (pieceIndexSent == (data.getPiecesNum()-1)) {
    		Log.i(TAG, "Last piece sent!");
    	}
    	// get next piece to send
    	pieceIndexToSend = data.getNexUnsentPiece();
    	// if all them are already sent, stop
    	if (pieceIndexToSend < 0) {
    		finishedSending();
	    	return;
    	}
    	// otherwise continue sending
    	writeToCharacteristic();
    	
    }
    
    private void finishedSending() {
		finishedSendingTime = Math.round((System.nanoTime() - startScanTime)/1E6);
//		Log.e(TAG, "finishedSending. Time: " + finishedSendingTime);
		sendTimes();
		updateUIAfterConfirm();
		stopScanning();
    	//data.setConfirmed(true);
		if (backgroundHandler == null) {
			Log.e(TAG, "backgroundHandler is null in finishedSending!");
			return;
		}
		if (scanAgainInterval >= 0) {
			backgroundHandler.postDelayed(scanTicketRunnable, scanAgainInterval);
		}
    }
    
    private void sendTimes() {
        Message message = Message.obtain();
        message.what = MAIN_HANDLER_SET_TIMES;
        message.obj = deviceFoundTime + " - " + connectedTime  + " - " + servicesDiscoveredTime 
        		+ " - " + startSendingTime + " - " + finishedSendingTime;
		mainHandler.sendMessage(message);
    }
    
    public boolean isBluetoothEnabled() {
        if (mBluetoothAdapter == null)
            return false;
    	return mBluetoothAdapter.isEnabled();
    }
    
    private int t;
    
    public void startScanningTimeout(boolean show){
    	if(show){
    		t = TIMEOUT_IN_SECONDS;
			backgroundHandler.post(stopScanningTimeoutRunnable);
    	}else{
    		displayScanInfoUI(stringTranslator.getString(R.string.blue_stop_scanning));
    		timeOutReached();
    	}    	
    }
    
    private Runnable stopScanningTimeoutRunnable = new Runnable(){
    	public void run(){
    		t--;
    		if(t < -1){
    			stopScanning();
    			startScanningTimeout(false);
    		}else{
    			// to show 0 only for some time before turning off
    			if(t == -1){
    				backgroundHandler.postDelayed(stopScanningTimeoutRunnable, 200);
    			}else{
    				backgroundHandler.postDelayed(stopScanningTimeoutRunnable, 1000);
    			}
    		}
    	}
    };
    
    private void timeOutReached() {
		Log.e(TAG, "timeOutReached!");
		stopScanning(); // callback on state disconnected will check MAX_RETRIES and connect again
    	if (backgroundHandler != null) {
    		backgroundHandler.removeCallbacks(stopScanningTimeoutRunnable);
    	} else {
    		Log.e(TAG, "timeOutReached. backgroundHandler is null!");
    	}
    }
    
    // run the BLuE callbacks from UI thread 
    private void displayScanInfoUI(String text){
        Message message = Message.obtain();
        message.what = MAIN_HANDLER_PROCESS_TEXT;
        message.obj = text;
		mainHandler.sendMessage(message);
    }
    public int getRssiThreshold() {
		return rssiThreshold;
	}

	public void setRssiThreshold(int rssiThreshold) {
		this.rssiThreshold = rssiThreshold;
	}
	private class SessionBLuEData {
    	private byte[] value; // value of piece or of total session if it's parent
    	private boolean confirmed;
    	private ArrayList<SessionBLuEData> sessionBLuEPieces = new ArrayList<SessionBLuEData>(); 
    	
    	public SessionBLuEData(byte[] value, boolean parent) {
    		this.setValue(value);
    		this.confirmed = false;
    		sessionBLuEPieces = new ArrayList<SessionBLuEData>();
    		if (parent) {
    			truncDataForSend();
    		}
    	}
    	
    	//public SessionBLuEData(int index, int total, byte[] value) {
    	public SessionBLuEData(int index, byte[] value) {
    		this.setBytes(index, value);
    		this.confirmed = false;
    		sessionBLuEPieces = new ArrayList<SessionBLuEData>();
    	}
    	
    	private void truncDataForSend() {
        	int pieceSize = MAX_DATA_SIZE - 1; // 1 position for index
        	if (value != null) {
        		int pieces = (int)Math.ceil((double)(value.length + 2) / pieceSize);
        		byte[] truncatedData = getFirstPiece(value.length);
    			SessionBLuEData piece = new SessionBLuEData(0, truncatedData);
    			sessionBLuEPieces.add(piece);
        		for (int i = 1; i < pieces; i++) {
        			//String truncatedString = value.substring(i * pieceSize, Math.min((i+1) * pieceSize, value.length()));
        			truncatedData = new byte[pieceSize];
        			System.arraycopy(value, i * pieceSize - 2, truncatedData, 0, 
        					Math.min(pieceSize, (value.length - i * pieceSize + 2)));
        			piece = new SessionBLuEData(i, truncatedData);
        			sessionBLuEPieces.add(piece);
        		}
        	}
    		
    	}
    	
    	private byte[] getFirstPiece(int valueLength) {
    		// in first piece we use 2 bytes to store data size
			byte[] truncatedData = new byte[MAX_DATA_SIZE - 1];
			// let's copy the size
    		byte[] size = new byte[2];
            size[0] = (byte) (valueLength >> 8);
            size[1] = (byte) (valueLength);
			System.arraycopy(size, 0, truncatedData, 0, 2);
			// let's use remaining space for data
			System.arraycopy(value, 0, truncatedData, 2, 
					Math.min(MAX_DATA_SIZE - 3, value.length));

			return truncatedData;
    	}
    	
    	public int getNexUnsentPiece() {
    		for (int i = 0; i < sessionBLuEPieces.size(); i++) {
    			if (!sessionBLuEPieces.get(i).isConfirmed()) {
    				return i;
    			}
    		}
    		confirmed = true;
    		return -1;
    	}

		public int getPiecesNum() {
			return sessionBLuEPieces.size();
		}

		// set confirmed to false to restart sending again
		public void resetForScan() {
			for (SessionBLuEData piece : sessionBLuEPieces) {
				piece.setConfirmed(false);
			}
			this.setConfirmed(false);
		}
		
		public boolean isProvided() {
			if (data != null && !"".equals(data.getValue())) {
				return true;
			}
			return false;
		}
		
		public byte[] getValue() {
			return value;
		}

		public void setValue(byte[] value) {
			this.value = value;
		}

		//public void setBytes(int index, int total, byte[] data) {
		public void setBytes(int index, byte[] data) {
    		this.value = new byte[MAX_DATA_SIZE];
    		Arrays.fill(value, (byte)0);
			this.value[0] = (byte) (index & 0xff);
			//System.arraycopy(data, 0, this.value, 2, Math.min(data.length, MAX_DATA_SIZE - 2));
			System.arraycopy(data, 0, this.value, 1, Math.min(data.length, MAX_DATA_SIZE - 1));
		}

		public boolean isConfirmed() {
			return confirmed;
		}

		public void setConfirmed(boolean confirmed) {
			this.confirmed = confirmed;
		}

		public ArrayList<SessionBLuEData> getSessionBLuEPieces() {
			return sessionBLuEPieces;
		}

//		public void setSessionBLuEPieces(ArrayList<SessionBLuEData> sessionBLuEPieces) {
//			this.sessionBLuEPieces = sessionBLuEPieces;
//		}
		
    }
	
	public interface StringInterface {
		public String getString(int id);
	}
}
