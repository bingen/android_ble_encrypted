package com.eguzkitza.bingen.ble.test;

import com.eguzkitza.bingen.ble.BleMonitorService;
import com.eguzkitza.bingen.ble.BleService;
import com.eguzkitza.bingen.ble.BleServiceCallback;
import com.eguzkitza.bingen.ble.ui.DebugMonitorFragment;
import com.eguzkitza.bingen.ble.ui.DebugMonitorFragment.DebugMonitorFragmentCallback;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class BleTest extends Activity implements DebugMonitorFragmentCallback {
	private final static String TAG = "BleTest";

	private static final boolean BLuE_SECURITY_ENABLED = true;
//	private final static String KEY_TYPE = "AES/128/CBC/ZeroBytePadding/Rijndael";
//	private final static String PRIVATE_KEY = "vLenhWgQ9276J6SrWu4fWHeB38EZjT+8YYMEVS5dZn8=";
//	private final static String IV = "9dddPk9oELzLmPS5JA1vZg==";

	private static final int REQUEST_ENABLE_BT = 1;
	
	private boolean blueEnabled = true;
	
	private ListView timesListView;
	private ArrayAdapter<String> timesAdapter;
	private Button clearButton;
	
	protected DebugMonitorFragment debugMonitorFragment;
//	private FrameLayout debugMonitorFrame;
	
	private BluetoothAdapter mBluetoothAdapter;
	private BleService mBleMonitorService;
	private boolean serviceBound = false;
	
//	private long startTime;
//	private long stopTime;
//	private long timeTakenReady; // total BLuE since it's in ready state
//	private long timeTakenCombine; // total BLuE to combine strings received
//	private int packetsReceived; // number of BLuE packets received in a session
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "onCreate");
		setContentView(R.layout.activity_ble_test);
		
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
			finish();
		}

		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		
		createBLuETextBackgroundThread();
		
		initTimesList();
		
		initDebugComponents();
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
	}
	@Override
	public void onStart() {
		super.onStart();
		Log.i(TAG, "onStart");
		
		startBLuETextBackgroundThread();

		startBleService();
	}

	@Override
	public void onStop() {
		super.onStop();
		Log.i(TAG, "onStop");
		stopBleService();
		stopBLuETextBackgroundThread();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Log.i(TAG, "onResume");
		if (!mBluetoothAdapter.isEnabled()) {
			blueEnabled = false;
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else {
			blueEnabled = true;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.i(TAG, "onPause");

		stopBleService();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy");

		stopEverything();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {
			if (resultCode == Activity.RESULT_CANCELED) {
				blueEnabled = false;
				finish();
				return;
			} else {

			}
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	private void initTimesList() {
		timesAdapter = new ArrayAdapter<String>(this, R.layout.simple_list_item_1);
		timesListView = (ListView) findViewById(R.id.timesListView);
		timesListView.setAdapter(timesAdapter);
		clearList();
		clearButton = (Button) findViewById(R.id.clearButton);
		clearButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				clearList();
			}
		});
	}
	private void clearList() {
		timesAdapter.clear();
		timesAdapter.add("packetsReceived - timeTakenReady - timeTakenCombine");
	}
	private void initDebugComponents() {
//		debugMonitorFrame = (FrameLayout) findViewById(R.id.debug_monitor_fragment);

		debugMonitorFragment = new DebugMonitorFragment();
		Bundle bundle = new Bundle();
		bundle.putBoolean(DebugMonitorFragment.BLUETOOTH, true);
		bundle.putInt(DebugMonitorFragment.ADVERTISE_MODE, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
		bundle.putInt(DebugMonitorFragment.ADVERTISE_POWER, AdvertiseSettings.ADVERTISE_TX_POWER_LOW);
		debugMonitorFragment.setArguments(bundle);
		getFragmentManager().beginTransaction()
				.replace(R.id.debug_monitor_fragment, debugMonitorFragment)
				.commit();
	}
	private void startBleService() {
		if (blueEnabled) {
			Intent intent = new Intent(this, BleMonitorService.class);
			bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		}
	}
	private void stopBleService() {
		if (serviceBound) {
			try {
				mBleMonitorService.stopBleMonitor();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			unbindService(serviceConnection);
			serviceBound = false;
		}
	}
	private void stopEverything() {
		Log.i(TAG, "Stop Everything");
		stopBleService();
		destroyBLuETextBackgroundThread();
	}


    
	
	private BleServiceCallback mBleServiceCallback = new BleServiceCallback.Stub() {

		@Override
		public void getReceivedString(String data) throws RemoteException {
			Log.i(TAG, "BLuE Final String ---> " + data);
			displayBLuEResult(data);
		}

		@Override
		public void getProcessText(String data) throws RemoteException {
			updateBLuEText(data, 0);
		}

		@Override
		public void getTimes(long timeTakenReady, long timeTakenCombine, int packetsReceived) throws RemoteException {
			setBLuETimes(timeTakenReady, timeTakenCombine, packetsReceived);
		}
	};
	private ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "service disconnected");
			serviceBound = false;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "service connected");
//			startTime = System.nanoTime();
			mBleMonitorService = BleService.Stub.asInterface(service);
			serviceBound = true;
			try {
				mBleMonitorService.registerCallback(mBleServiceCallback);
				mBleMonitorService.setAdvertiseParameters(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, 
						AdvertiseSettings.ADVERTISE_TX_POWER_LOW);
//				mBleMonitorService.setPrivateKeys(getKeys());
				mBleMonitorService.setSecurityEnabled(BLuE_SECURITY_ENABLED);
				mBleMonitorService.startBleMonitor();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	};
	


	private void setBLuETimes(long timeTakenReady, long timeTakenCombine, int packetsReceived) {
//		this.timeTakenReady = timeTakenReady;
//		this.timeTakenCombine = timeTakenCombine;
//		this.packetsReceived = packetsReceived;
		Log.d(TAG, "setBLuETimes: " + packetsReceived + " - " + timeTakenReady + " - " + timeTakenCombine);
		timesAdapter.add(packetsReceived + " - " + timeTakenReady + " - " + timeTakenCombine);
	}
	private void displayBLuEResult(String data) {
		Log.d(TAG, "Inside BLuE display result. Data: " + data);
		//sessionJsonTextView.setText(data);
		if (debugMonitorFragment != null) {
			debugMonitorFragment.updateJsonTextView(data);
		}
//		// we stop BLuE monitoring while waiting for the server to avoid too many connections queued
//		try {
//			mBleMonitorService.stopBleMonitor();
//		} catch (RemoteException e) {
//			e.printStackTrace();
//		}
	}

	/*
	 *  to update lower BLuE text with a delay for important messages
	 *  we queue messages in a background thread to avoid blocking UI
	 */
	private void updateBLuEText(final String text, final int showTime){
		blueTextBackgroundHandler.post(new Runnable() {
			@Override
			public void run() {
		        Message message = Message.obtain();
		        message.obj = text;
				blueTextHandler.sendMessage(message);
				SystemClock.sleep(showTime);
			}
		});
	}
	private HandlerThread blueTextBackgroundThread;
	private Handler blueTextBackgroundHandler;
	
	private void createBLuETextBackgroundThread() {
		Log.d(TAG, "Creating BLuE text background thread");
		blueTextBackgroundThread = new HandlerThread("BLuE Text Background Handler Thread");
		blueTextBackgroundThread.start();
		blueTextBackgroundHandler = new Handler(blueTextBackgroundThread.getLooper());
	}
	private void startBLuETextBackgroundThread() {
		Log.d(TAG, "Starting BLuE text background thread");
		if (!blueTextBackgroundThread.isAlive()) {
			Log.d(TAG, "Background thread state: " + blueTextBackgroundThread.getState().name());
			// http://docs.oracle.com/javase/6/docs/api/java/lang/Thread.html#start()
			//blueTextBackgroundThread.start();
			createBLuETextBackgroundThread();
		}
	}
	private void stopBLuETextBackgroundThread() {
		Log.d(TAG, "Stopping BLuE text background thread");
		if (blueTextBackgroundThread != null) {
			blueTextBackgroundThread.quit();
		}
	}
	private void destroyBLuETextBackgroundThread() {
		Log.d(TAG, "Destroying BLuE text background thread");
    	try {
    		blueTextBackgroundThread.join();
    		blueTextBackgroundThread = null;
    		blueTextBackgroundHandler = null;
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    	}
	}
	// this one belongs to UI thread
	private Handler blueTextHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
            Activity activity = BleTest.this;
            if (activity != null && debugMonitorFragment != null) {
            	debugMonitorFragment.updateProcessText((String)msg.obj);
            }
		}
	};
	

	protected void setBLuEAdvertiseParameters(int advertiseMode, int advertisePower) {
		try {
			mBleMonitorService.stopBleMonitor();
			mBleMonitorService.setAdvertiseParameters(advertiseMode, advertisePower);
			mBleMonitorService.startBleMonitor();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void onChangeBLuEAdvertiseParameters(int advertiseMode,
			int advertisePower) {
		setBLuEAdvertiseParameters(advertiseMode, advertisePower);
	}
}
