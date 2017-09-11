package com.eguzkitza.bingen.ble.ui;

import java.util.LinkedHashMap;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

@SuppressLint("NewApi")
public class DebugMonitorFragment extends Fragment {
	private final static String TAG = "DebugMonitorFragment";

    // bundle tags (initial params)
    public final static String BLUETOOTH = "bluetooth";
    public final static String ADVERTISE_MODE = "advertise_mode";
    public final static String ADVERTISE_POWER = "advertise_power";

	private View mContentView = null;
	private DebugMonitorFragmentCallback mCallback;

    // modes and parameters
    private boolean bluetooth = true;
    private int advertiseMode;// = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
    private int advertisePower;// = AdvertiseSettings.ADVERTISE_TX_POWER_LOW;

	private TextView sessionJsonLabel;
	private TextView sessionJsonTextView;
    // bluetooth elements
    private TextView processText;
	private Spinner advertiseModeSpinner;
	private Spinner advertisePowerSpinner;

    public  DebugMonitorFragment() {
    }
//	public DebugMonitorFragment(int advertiseMode, int advertisePower) {
//		this.advertiseMode = advertiseMode;
//		this.advertisePower = advertisePower;
//
//	}
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Bundle bundle = getArguments();
        this.bluetooth = bundle.getBoolean(BLUETOOTH);
        this.advertiseMode = bundle.getInt(ADVERTISE_MODE);
        this.advertisePower = bundle.getInt(ADVERTISE_POWER);

        mContentView = inflater.inflate(R.layout.fragment_debug_monitor, null);

        initComponents();

        return mContentView;
    }

    private void initComponents() {
        // General section
        sessionJsonLabel = (TextView) mContentView.findViewById(R.id.sessionJsonLabel);
        sessionJsonLabel.setText("Json Session:");
        sessionJsonTextView = (TextView) mContentView.findViewById(R.id.sessionJsonTextView);

        // Bluetooth section
        initBluetoothComponents();

    }

    private void initBluetoothComponents() {
        LinearLayout bluetoothContainer = (LinearLayout) mContentView.findViewById(R.id.bluetoothContainer);
        processText = (TextView) mContentView.findViewById(R.id.processText);
        processText.setText("Hello");
        advertiseModeSpinner = (Spinner) mContentView.findViewById(R.id.advertiseModeSpinner);
        advertisePowerSpinner = (Spinner) mContentView.findViewById(R.id.advertisePowerSpinner);
        populateAdvertiseSpinners();
        if(!bluetooth) {
            bluetoothContainer.setVisibility(View.GONE);
        }
    }
    @Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        
        // This makes sure that the container activity has implemented
        // the callback interface. Otherwise, it throws an exception
        try {
            mCallback = (DebugMonitorFragmentCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement DebugMonitorFragmentCallback");
        }
        
    }
    
	public interface DebugMonitorFragmentCallback {
		void onChangeBLuEAdvertiseParameters(int advertiseMode, int advertisePower);
	}
	
    public void updateJsonTextView(String data) {
    	if (sessionJsonTextView != null) {
    		sessionJsonTextView.setText(data);
    	}
    }
    public void updateProcessText(String data) {
    	Log.d(TAG, "Process text: " + data);
    	if (processText != null) {
    		processText.setText(data);
    	}
    }

    // BLE Advertise Spinners spinners
	private void populateAdvertiseSpinners() {

		populateAdvertiseMaps();
		
		ArrayAdapter<String> advertiseModeAdapter = new ArrayAdapter<String>(getActivity(), 
				R.layout.spinner_row, advertiseModeList);
		advertiseModeSpinner.setAdapter(advertiseModeAdapter);
		advertiseModeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View arg1, int position, long id) {
				if( position > 0 && advertiseModeMap.get(advertiseModeList[position]) != advertiseMode ) {
					advertiseMode = advertiseModeMap.get(advertiseModeList[position]);
					Log.i(TAG, "Selected advertise Mode: " + advertiseMode + " - " + advertiseModeList[position]);
					//setBLuEAdvertiseParameters();
					mCallback.onChangeBLuEAdvertiseParameters(advertiseMode, advertisePower);
				}
			}
			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
				// do nothing
			}
		});
		//advertiseModeSpinner.setSelection(2);
		setAdvertiseModeSpinner(advertiseMode);

		ArrayAdapter<String> advertisePowerAdapter = new ArrayAdapter<String>(getActivity(), 
				R.layout.spinner_row, advertisePowerList);
		advertisePowerSpinner.setAdapter(advertisePowerAdapter);
		advertisePowerSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View arg1, int position, long id) {
				if( position > 0 && advertisePowerMap.get(advertisePowerList[position]) != advertisePower ) {
					advertisePower = advertisePowerMap.get(advertisePowerList[position]);
					Log.i(TAG, "Selected advertise Power: " + advertisePower + " - "  + advertisePowerList[position]);
					//setBLuEAdvertiseParameters();
					mCallback.onChangeBLuEAdvertiseParameters(advertiseMode, advertisePower);
				}
			}
			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
				// do nothing
			}
		});
		//advertisePowerSpinner.setSelection(1);
		setAdvertisePowerSpinner(advertisePower);
	}
	
	String[] advertiseModeList;
	LinkedHashMap<String,Integer> advertiseModeMap = new LinkedHashMap<String, Integer>();
	String[] advertisePowerList;
	LinkedHashMap<String,Integer> advertisePowerMap = new LinkedHashMap<String, Integer>();

	private void populateAdvertiseMaps() {
		advertiseModeMap.put("Low Power", AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
		advertiseModeMap.put("Balanced", AdvertiseSettings.ADVERTISE_MODE_BALANCED);
		advertiseModeMap.put("Low Latency", AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
		advertiseModeList = (String[]) advertiseModeMap.keySet().toArray(new String[0]);
		
		advertisePowerMap.put("Ultra Low Power", AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW);
		advertisePowerMap.put("Low Power", AdvertiseSettings.ADVERTISE_TX_POWER_LOW);
		advertisePowerMap.put("Medium Power", AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
		advertisePowerMap.put("High Power", AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
		advertisePowerList = (String[]) advertisePowerMap.keySet().toArray(new String[0]);
	}
	private void setAdvertiseModeSpinner(int modeValue) {
		int i = 0;
		for (String modeName : advertiseModeList) {
			if (advertiseModeMap.get(modeName) == modeValue) {
				advertiseModeSpinner.setSelection(i);
				return;
			}
			i++;
		}
	}
	private void setAdvertisePowerSpinner(int powerValue) {
		int i = 0;
		for (String powerName : advertisePowerList) {
			if (advertisePowerMap.get(powerName) == powerValue) {
				advertisePowerSpinner.setSelection(i);
				return;
			}
			i++;
		}
	}
	
	
	public class SpinnerAdapter extends ArrayAdapter<String> {

		private Context context;
		private String[] values;
		//private int resourceId;

		public SpinnerAdapter(Context context, int resourceId, String[] values) {
			super(context, resourceId, values);
			this.context = context;
			this.values = values;
			//this.resourceId = resourceId;

		}

		public int getCount() {
			return values.length;
		}

		public String getItem(int position) {
			return values[position];
		}

		public long getItemId(int position) {
			return position;
		}

//		@Override
//		public View getView(int position, View convertView, ViewGroup parent) {
//
//			View row = convertView;
//
//			LayoutInflater inflater = ((Activity) context).getLayoutInflater();
//			row = inflater.inflate(resourceId, parent, false);
//
//			TextView label = (TextView) row.findViewById(R.id.spinnerRow);
//			label.setText(values[position]);
//
//			return label;
//		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {

			TextView textView = (TextView) View.inflate(context, android.R.layout.simple_spinner_dropdown_item, null);
			textView.setText(values[position]);
			return textView;
		}
	}
}
