package com.eguzkitza.bingen.ble.test;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BleCentralTest extends Activity {
	private final static String TAG = "BleCentralTest";

	private static final boolean BLuE_SECURITY_ENABLED = true;
	private final static int SCAN_AGAIN_INTERVAL = -1; // TODO: move to operator setting?
	
	private final static String SESSION = "Yet Another BLuE Test to Encrypt and Send";
//	private final static String CLIENT_NONCE = "aDmkSb+hNDyIBZeN0nf58w==";
//	private final static String CLIENT_NONCE_ENCRYPTED = "g1HVUZqvNSW6OkvqGneMaA==";

    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 1;

	private EditText sessionText;
	private Button sendButton;
	private Button stopButton;
	private Button restartButton;
	
	private ListView timesListView;
	private ArrayAdapter<String> timesAdapter;
	private Button clearButton;
	
	private boolean blueEnabled = true;
	private BLuEHelper blueHelper;
	private TextView processText;

	// BLuE sensitivity bar
	private final static int BLUE_SENSITIVITY_DIFF = 90;
	private SeekBar blueSensitivity;
	//private LinearLayout blueSensitivityContanier;
	private TextView blueSensitivityValue;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_ble_central_test);
		
		initComponents();

        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            checkPermissions();
        }

		initBlue();
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
	}
	// Permissions ofr Marshmallow+
	// https://stackoverflow.com/a/44021987/1937418
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<String, Integer>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);


                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                // Check for ACCESS_FINE_LOCATION
                if (perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

                        ) {
                    // All Permissions Granted
                    Toast.makeText(this, "All Permission GRANTED !! Thank You :)", Toast.LENGTH_SHORT)
                            .show();


                } else {
                    // Permission Denied
                    Toast.makeText(this, "One or More Permissions are DENIED Exiting App :(", Toast.LENGTH_SHORT)
                            .show();

                    finish();
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<String>();

        final List<String> permissionsList = new ArrayList<String>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("Show Location");

        if (permissionsList.size() > 0) {
            if (permissionsNeeded.size() > 0) {

                // Need Rationale
                String message = "App need access to " + permissionsNeeded.get(0);

                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);

                showMessageOKCancel(message,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                            }
                        });
                return;
            }
            requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            return;
        }

        Toast.makeText(this, "No new Permission Required- Launching App .You are Awesome!!", Toast.LENGTH_SHORT)
                .show();
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean addPermission(List<String> permissionsList, String permission) {

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission))
                return false;
        }
        return true;
    }

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.i(TAG, "session activity started");
	}

	@Override
	public void onStop() {
		super.onStop();
		Log.i(TAG, "session activity stopped");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.i(TAG, "****session activity resumed updating UI***");
		blueHelperResume();
	}

	@Override
	public void onPause() {
		super.onPause();
		blueHelperPause();
		Log.i(TAG, "session activity paused");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "->session activity destroyed");
		// LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
	}

	private void initComponents() {
		sessionText = (EditText) findViewById(R.id.sessionText);
		sessionText.setText(SESSION);
		sendButton = (Button) findViewById(R.id.sendButton);
		sendButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				startBLuEScanning();
			}
		});
		sendButton.setVisibility(View.GONE);
		stopButton = (Button) findViewById(R.id.stopButton);
		stopButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				stopBLuEScanning();
			}
		});
		stopButton.setVisibility(View.GONE);
		restartButton = (Button) findViewById(R.id.restartButton);
		restartButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				restartBLuEScanning();
			}
		});
		
		initTimesList();
		
		// BLuE
		processText = (TextView) findViewById(R.id.processText);
		
		initBlueSensitivity();
		
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
		timesAdapter.add("deviceFoundTime - connectedTime - servicesDiscoveredTime - startSendingTime - finishedSendingTime");
	}
	
	private void initBlue() {
		// we pass it a Handler attached to the UI thread
		Handler mainHandlerForBLuEHelper = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				String message = (String)msg.obj;
				Log.i(TAG, message);
				switch(msg.what) {
				case BLuEHelper.MAIN_HANDLER_PROCESS_TEXT:
					processText.setText(message);
					break;
				case BLuEHelper.MAIN_HANDLER_SET_TIMES:
					timesAdapter.add(message);
				}
			}
		};
		blueHelper = new BLuEHelper(this, mainHandlerForBLuEHelper, 
				new StringTranslator(this), SCAN_AGAIN_INTERVAL, BLuE_SECURITY_ENABLED);
		
	}
	
	private void blueHelperResume() {
		Log.i(TAG, "onResume");
		if (!blueEnabled || blueHelper == null) {
			Log.i(TAG, "BLuE not enabled, so nothing to do");
			return;
		}

		// Ensures Bluetooth is enabled on the device. If Bluetooth is not currently enabled,
		// fire an intent to display a dialog asking the user to grant permission to enable it.
		if (!blueHelper.isBluetoothEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, BLuEHelper.REQUEST_ENABLE_BT);
		} else {
			blueHelper.startBackgroundHandler();
		}
		
		startBLuEScanning();
	}
	private void blueHelperPause() {
		if (blueEnabled && blueHelper != null) {
			blueHelper.stopEverything();
		}
	}
	private void startBLuEScanning() {
		if (blueEnabled && blueHelper != null) {
			blueHelper.setData(sessionText.getText().toString()); //, CLIENT_NONCE, CLIENT_NONCE_ENCRYPTED);
			//blueHelper.setData(Base64.encodeToString(sessionText.getText().toString().getBytes(), Base64.DEFAULT)); //, CLIENT_NONCE, CLIENT_NONCE_ENCRYPTED);
			Log.i(TAG, "   ---> session set for BLuE broadcast!");
			Toast.makeText(BleCentralTest.this, "session set for BLuE broadcast", Toast.LENGTH_SHORT).show();
			blueHelper.startScanTicket();
			Log.i(TAG, "   ---> BLuE scanning!");
		}
	}
	private void stopBLuEScanning() {
		Log.i(TAG, "   ---> Stopping BLuE scanning!");
		if (blueEnabled && blueHelper != null) {
			blueHelper.stopScanTicket();
		}
	}
	private void restartBLuEScanning() {
		blueHelperPause();
		blueHelperResume();
	}
	
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == BLuEHelper.REQUEST_ENABLE_BT) {
			if (resultCode == Activity.RESULT_CANCELED) {
				blueEnabled = false;
				return;
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private OnSeekBarChangeListener blueSensitivityListener = new OnSeekBarChangeListener() {

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (fromUser) {
				int rssiThreshold = progress - BLUE_SENSITIVITY_DIFF;
				blueSensitivityValue.setText(""+rssiThreshold);
				blueHelper.setRssiThreshold(rssiThreshold);
				blueHelperPause();
				blueHelperResume();
			}
		}
		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}
		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
		
	};

	private void initBlueSensitivity() {
		blueSensitivity = (SeekBar) findViewById(R.id.blueSensitivity);
		blueSensitivity.setProgress(BLuEHelper.RSSI_THRESHOLD + BLUE_SENSITIVITY_DIFF);
		blueSensitivity.setOnSeekBarChangeListener(blueSensitivityListener);
		//blueSensitivityContanier = (LinearLayout) findViewById(R.id.blueSensitivityContanier);
		blueSensitivityValue = (TextView) findViewById(R.id.blueSensitivityValue);
		blueSensitivityValue.setText(""+BLuEHelper.RSSI_THRESHOLD);
	}
	
}
