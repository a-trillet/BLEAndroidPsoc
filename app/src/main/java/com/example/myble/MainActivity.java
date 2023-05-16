package com.example.myble;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {


    // TAG is used for informational messages
    private final static String TAG = MainActivity.class.getSimpleName();

    // Variables to access objects from the layout such as buttons, switches, values
    private static TextView mCapsenseValue;
    private static Button start_button;
    private static Button search_button;
    private static Button connect_button;
    private static Button discover_button;
    private static Button disconnect_button;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private static Switch led_switch;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private static Switch cap_switch;

    // Variables to manage BLE connection
    private static boolean mConnectState;
    private static boolean mServiceConnected;
    private static PSoCconnection mPSoCconnection;

    private static final int REQUEST_ENABLE_BLE = 1;
    private static final int RUNTIME_PERMISSION_REQUEST_CODE = 2;

    //This is required for Android 6.0 (Marshmallow)
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 1;
    private static final int PERMISSION_REQUEST_BLUETOOTH_SCAN = 2;


    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final int REQUEST_APP_SETTINGS = 2;

    // Keep track of whether CapSense Notifications are on or off
    private static boolean CapSenseNotifyState = false;

    // Find BLE service and adapter
    private BluetoothAdapter mBluetoothAdapter;


    /**
     * This manages the lifecycle of the BLE service.
     * When the service starts we get the service object and initialize the service.
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        /**
         * This is called when the PSoCconnection is connected
         *
         * @param componentName the component name of the service that has been connected
         * @param service service being bound
         */
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mPSoCconnection = ((PSoCconnection.LocalBinder) service).getService();
            mServiceConnected = true;
            mPSoCconnection.initialize();
        }

        /**
         * This is called when the PSoCCapSenseService is disconnected.
         *
         * @param componentName the component name of the service that has been connected
         */
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected");
            mPSoCconnection = null;
        }
    };

    /**
     * This is called when the main activity is first created
     *
     * @param savedInstanceState is any state saved from prior creations of this activity
     */
    //@TargetApi(Build.VERSION_CODES.M) // This is required for Android 6.0 (Marshmallow) to work
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up a variable to point to the CapSense value on the display
        mCapsenseValue = (TextView) findViewById(R.id.capsense_value);

        // Set up variables for accessing buttons and slide switches
        start_button = (Button) findViewById(R.id.start_button);
        search_button = (Button) findViewById(R.id.search_button);
        connect_button = (Button) findViewById(R.id.connect_button);
        discover_button = (Button) findViewById(R.id.discoverSvc_button);
        disconnect_button = (Button) findViewById(R.id.disconnect_button);
        led_switch = (Switch) findViewById(R.id.led_switch);
        cap_switch = (Switch) findViewById(R.id.capsense_switch);

        // Initialize service and connection state variable
        mServiceConnected = false;
        mConnectState = false;


        // inititialize bluetooth adapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();





        /* This will be called when the LED On/Off switch is touched */
        led_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Turn the LED on or OFF based on the state of the switch
                mPSoCconnection.writeLedCharacteristic(isChecked);
            }
        });

        /* This will be called when the CapSense Notify On/Off switch is touched */
        cap_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Turn CapSense Notifications on/off based on the state of the switch
                mPSoCconnection.writeCapSenseNotification(isChecked);
                CapSenseNotifyState = isChecked;  // Keep track of CapSense notification state
                if (isChecked) { // Notifications are now on so text has to say "No Touch"
                    mCapsenseValue.setText(R.string.NoTouch);
                } else { // Notifications are now off so text has to say "Notify Off"
                    mCapsenseValue.setText(R.string.NotifyOff);
                }
            }
        });
    }



    @Override
    protected void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }
        // Register the broadcast receiver. This specified the messages the main activity looks for from the PSoCconnection
        final IntentFilter filter = new IntentFilter();
        filter.addAction(PSoCconnection.ACTION_BLESCAN_CALLBACK);
        filter.addAction(PSoCconnection.ACTION_CONNECTED);
        filter.addAction(PSoCconnection.ACTION_DISCONNECTED);
        filter.addAction(PSoCconnection.ACTION_SERVICES_DISCOVERED);
        filter.addAction(PSoCconnection.ACTION_DATA_RECEIVED);
        registerReceiver(mBleUpdateReceiver, filter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BLE && resultCode == Activity.RESULT_CANCELED) {
            promptEnableBluetooth();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBleUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close and unbind the service when the activity goes away
        mPSoCconnection.close();
        unbindService(mServiceConnection);
        mPSoCconnection = null;
        mServiceConnected = false;
    }


    private void promptEnableBluetooth() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.d(TAG, "BLUETOOTH_CONNECT permission not granted");
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLE);
        }
    }

    public boolean hasPermission(String permissionType){
        return (ContextCompat.checkSelfPermission(MainActivity.this, permissionType)
                    == PackageManager.PERMISSION_GRANTED);
    }

    public boolean hasRequiredRuntimePermissions(){
        requestLocationPermission();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, " scan permission " + hasPermission(Manifest.permission.BLUETOOTH_SCAN) );
            Log.i(TAG, "connect permission " + hasPermission(Manifest.permission.BLUETOOTH_CONNECT) );
            Log.i(TAG, "location permission " + hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) );
            Log.i(TAG, "location coarse permission " + hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) );
            Log.i(TAG, "bluetooth permission " + hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) );
            Log.i(TAG, "bluetooth admin permission " + hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) );
        return hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
        }
        else {
            return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }
    public void requestRelevantRuntimePermissions(){
        if (!hasRequiredRuntimePermissions()) {
            //if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S){
                requestLocationPermission();
            //}
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBluetoothPermissions();
            }
        }
    }

    public void requestLocationPermission() {
        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)){
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);}
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)){
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);}
    }




    @RequiresApi(api = Build.VERSION_CODES.S)
    private static final String[] ANDROID_12_BLE_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT };
    @RequiresApi(api = Build.VERSION_CODES.S)
    public void requestBluetoothPermissions() {

        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN)){
            Log.w(TAG, "requesting BLUETOOTH permission scan");

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs bluetooth scan ");
            builder.setMessage("Please grant bluetooth scan so this app can detect devices.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @RequiresApi(api = Build.VERSION_CODES.S)
                public void onDismiss(DialogInterface dialog) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_REQUEST_BLUETOOTH_SCAN);
                }
            });
        builder.show();

        }
        if(!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)){
            Log.w(TAG, "requesting BLUETOOTH permission connect");

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs bluetooth connect ");
            builder.setMessage("Please grant bluetooth connect so this app can detect devices.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @RequiresApi(api = Build.VERSION_CODES.S)
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_BLUETOOTH_CONNECT);
                }
            });
            builder.show();

        }
        if(!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)){
            Log.w(TAG, "requesting location permission");

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location for bluetooth connect ");
            builder.setMessage("Please grant bluetooth connect so this app can detect devices.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @RequiresApi(api = Build.VERSION_CODES.S)
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }
            });
            builder.show();

        }

    }




    /**
     * This method handles the start bluetooth button
     *
     * @param view the view object
     */
    public void startBluetooth(View view) {

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            promptEnableBluetooth();
        }

        if (mBluetoothAdapter.isEnabled()){
            // Start the BLE Service
            Log.d(TAG, "Starting BLE Service");
            Intent gattServiceIntent = new Intent(this, PSoCconnection.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

            // Disable the start button and turn on the search  button
            start_button.setEnabled(false);
            search_button.setEnabled(true);
            Log.d(TAG, "Bluetooth is Enabled");
        }

    }

    /**
     * This method handles the Search for Device button
     *
     * @param view the view object
     */
    public void searchBluetooth(View view) {
        if(mServiceConnected) {
            Log.d(TAG, "mService is connected ");
            /* Scan for devices and look for the one with the service that we want */

            if (!hasRequiredRuntimePermissions()) {
                Log.d(TAG, "add permissions");
                //requestBluetoothPermissions();
                requestRelevantRuntimePermissions();
            } else {
            mPSoCconnection.scan();}
        }

        /* After this we wait for the scan callback to detect that a device has been found */
        /* The callback broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * This method handles the Connect to Device button
     *
     * @param view the view object
     */
    public void connectBluetooth(View view) {
        mPSoCconnection.connect();

        /* After this we wait for the gatt callback to report the device is connected */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * This method handles the Discover Services and Characteristics button
     *
     * @param view the view object
     */
    public void discoverServices(View view) {
        /* This will discover both services and characteristics */
        mPSoCconnection.discoverServices();

        /* After this we wait for the gatt callback to report the services and characteristics */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * This method handles the Disconnect button
     *
     * @param view the view object
     */
    public void Disconnect(View view) {
        mPSoCconnection.disconnect();

        /* After this we wait for the gatt callback to report the device is disconnected */
        /* That event broadcasts a message which is picked up by the mGattUpdateReceiver */
    }

    /**
     * Listener for BLE event broadcasts
     */
    private final BroadcastReceiver mBleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case PSoCconnection.ACTION_BLESCAN_CALLBACK:
                    // Disable the search button and enable the connect button
                    search_button.setEnabled(false);
                    connect_button.setEnabled(true);
                    Log.d(TAG, "device found");
                    break;

                case PSoCconnection.ACTION_CONNECTED:
                    /* This if statement is needed because we sometimes get a GATT_CONNECTED */
                    /* action when sending Capsense notifications */
                    if (!mConnectState) {
                        // Dsable the connect button, enable the discover services and disconnect buttons
                        connect_button.setEnabled(false);
                        discover_button.setEnabled(true);
                        disconnect_button.setEnabled(true);
                        mConnectState = true;
                        Log.d(TAG, "Connected to Device");
                    }
                    break;
                case PSoCconnection.ACTION_DISCONNECTED:
                    // Disable the disconnect, discover svc, discover char button, and enable the search button
                    disconnect_button.setEnabled(false);
                    discover_button.setEnabled(false);
                    search_button.setEnabled(true);
                    // Turn off and disable the LED and CapSense switches
                    led_switch.setChecked(false);
                    led_switch.setEnabled(false);
                    cap_switch.setChecked(false);
                    cap_switch.setEnabled(false);
                    mConnectState = false;
                    Log.d(TAG, "Disconnected");
                    break;
                case PSoCconnection.ACTION_SERVICES_DISCOVERED:
                    // Disable the discover services button
                    discover_button.setEnabled(false);
                    // Enable the LED and CapSense switches
                    led_switch.setEnabled(true);
                    cap_switch.setEnabled(true);
                    Log.d(TAG, "Services Discovered");
                    break;
                case PSoCconnection.ACTION_DATA_RECEIVED:
                    // This is called after a notify or a read completes
                    // Check LED switch Setting
                    if(mPSoCconnection.getLedSwitchState()){
                        led_switch.setChecked(true);
                    } else {
                        led_switch.setChecked(false);
                    }
                    // Get CapSense Slider Value
                    String CapSensePos = mPSoCconnection.getCapSenseValue();
                    if (CapSensePos.equals("-1")) {  // No Touch returns 0xFFFF which is -1
                        if(!CapSenseNotifyState) { // Notifications are off
                            mCapsenseValue.setText(R.string.NotifyOff);
                        } else { // Notifications are on but there is no finger on the slider
                            mCapsenseValue.setText(R.string.NoTouch);
                        }
                    } else { // Valid CapSense value is returned
                        mCapsenseValue.setText(CapSensePos);
                    }
                default:
                    break;
            }
        }
    };
}
