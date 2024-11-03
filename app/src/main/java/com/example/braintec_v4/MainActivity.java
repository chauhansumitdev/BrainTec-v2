package com.example.braintec_v4;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_LOCATION = 2;
    private static final int REQUEST_SEND_SMS_PERMISSION = 3;

    private BluetoothAdapter bluetoothAdapter;
    private AudioManager audioManager;
    private TextView connectionStatusTextView, voiceAssistanceTextView, muteCallsTextView, emergencyMsgTextView, speedTextView;
    private ProgressBar speedProgressBar;
    private LocationManager locationManager;
    private Location lastKnownLocation;
    private float lastKnownSpeed;
    private EditText customPhoneNumberEditText;
    private String emergencyContactNumber;
    private String statusConnected = "Connected", statusNotConnected = "Not Connected";
    private String voiceAssistanceOn = "Voice Assistance: On", voiceAssistanceOff = "Voice Assistance: Off";
    private String muteCallsOn = "Mute Calls: On", muteCallsOff = "Mute Calls: Off";
    private String emergencyMsgOn = "Emergency Msg: On", emergencyMsgOff = "Emergency Msg: Off";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        customPhoneNumberEditText = findViewById(R.id.customPhoneNumberEditText);



        Button openMapsButton = findViewById(R.id.openMapsButton);

        openMapsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGoogleMaps();
            }
        });


        requestSendSmsPermission();
        initializeViews();
        initializeBluetooth();
        initializeLocationServices();
        registerBluetoothReceiver();
        requestLocationPermission();
    }


    private void openGoogleMaps() {
        if (lastKnownLocation != null) {
            double get_longitude = lastKnownLocation.getLongitude();
            double get_latitude = lastKnownLocation.getLatitude();

            String uri = String.format(Locale.ENGLISH, "geo:%f,%f", get_latitude, get_longitude);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri gmmIntentUri = Uri.parse(uri);
            intent.setData(gmmIntentUri);
            intent.setPackage("com.google.android.apps.maps");

            try {
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "No map application found", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Location not available yet. Please wait.", Toast.LENGTH_SHORT).show();
        }
    }




    private void initializeViews() {
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);
        voiceAssistanceTextView = findViewById(R.id.voiceAssistanceTextView);
        muteCallsTextView = findViewById(R.id.muteCallsTextView);
        emergencyMsgTextView = findViewById(R.id.emergencyMsgTextView);
        speedTextView = findViewById(R.id.speedTextView);
        speedProgressBar = findViewById(R.id.speedProgressBar);

        connectionStatusTextView.setText("Status: " + statusNotConnected);
        voiceAssistanceTextView.setText(voiceAssistanceOff);
        muteCallsTextView.setText(muteCallsOff);
        emergencyMsgTextView.setText(emergencyMsgOff);
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void initializeLocationServices() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        }
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            lastKnownLocation = location;
            lastKnownSpeed = location.getSpeed(); // Speed in m/s
            updateSpeedDisplay(lastKnownSpeed);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(@NonNull String provider) {}

        @Override
        public void onProviderDisabled(@NonNull String provider) {}
    };

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(locationListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        }
    }

    private void updateSpeedDisplay(float speed) {
        float speedKmH = speed * 3.6f; // Convert m/s to km/h
        speedTextView.setText(String.format("Speed: %.2f km/h", speedKmH));
        speedProgressBar.setProgress((int) Math.min(speedKmH, speedProgressBar.getMax()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
    }

    private void requestLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
        }
    }

    private void requestSendSmsPermission() {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, REQUEST_SEND_SMS_PERMISSION);
        }
    }

    private boolean checkPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Toast.makeText(context, "Device Connected Successfully", Toast.LENGTH_SHORT).show();
                updateStatusText(statusConnected);
                updateMuteCallsText(true);
                updateEmergencyMsgText(true);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                updateStatusText(statusNotConnected);
                updateMuteCallsText(false);
                updateEmergencyMsgText(false);
                showEmergencyMessagePopup();
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SEND_SMS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("SMSPermission", "SEND_SMS permission granted");
                sendEmergencyMessage(emergencyContactNumber);
            } else {
                Log.d("SMSPermission", "SEND_SMS permission denied");
                Toast.makeText(this, "Permission denied. Cannot send emergency message.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void updateStatusText(String status) {
        connectionStatusTextView.setText("Status: " + status);
        voiceAssistanceTextView.setText(status.equals(statusConnected) ? voiceAssistanceOn : voiceAssistanceOff);

        if (status.equals(statusConnected)) {
            connectionStatusTextView.setTextColor(getResources().getColor(R.color.colorConnected)); 
        } else {
            connectionStatusTextView.setTextColor(getResources().getColor(R.color.colorNotConnected)); 
        }
    }


    private void updateMuteCallsText(boolean isMuted) {
        muteCallsTextView.setText(isMuted ? muteCallsOn : muteCallsOff);
        audioManager.setStreamMute(AudioManager.STREAM_RING, isMuted);
    }

    private void updateEmergencyMsgText(boolean isEnabled) {
        emergencyMsgTextView.setText(isEnabled ? emergencyMsgOn : emergencyMsgOff);
    }

    private void sendEmergencyMessage(String phoneNumber) {

        if (emergencyContactNumber.isEmpty()) {
            Toast.makeText(this, "Please enter a valid phone number "+ emergencyContactNumber, Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastKnownLocation != null) {
            String message = String.format(
                    "Please help, I'm in a medical emergency!\n" +
                            "Map Link: http://maps.google.com/?q=%f,%f\n"+
                            "Speed: %.2f km/h",
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude(),
                    lastKnownSpeed * 3.6f
            );


            Log.d("EmergencyMessage", "Message to send: " + message); // Debug log

            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                Toast.makeText(this, "Emergency message sent to " + phoneNumber, Toast.LENGTH_SHORT).show();
                Log.d("EmergencyMessage", "Message sent successfully");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("EmergencyMessage", "Failed to send message: " + e.getMessage()); // Error log
                Toast.makeText(this, "Failed to send emergency message.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.w("EmergencyMessage", "Location is not available"); // Warning log
            Toast.makeText(this, "Unable to send location. Location is not available.", Toast.LENGTH_SHORT).show();
        }
    }




    private void showEmergencyMessagePopup() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.popup_layout, null);
        dialogBuilder.setView(dialogView);
        final AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.show();

        Button cancelButton = dialogView.findViewById(R.id.cancelButton);


        new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                if (alertDialog.isShowing()) {
                    alertDialog.dismiss();
                    emergencyContactNumber = customPhoneNumberEditText.getText().toString().trim();
                    sendEmergencyMessage(emergencyContactNumber);
                }
            }
        }.start();

        cancelButton.setOnClickListener(v -> alertDialog.dismiss());
    }


}
