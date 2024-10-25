package com.nissanvit.mycarapp;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.annotation.SuppressLint;
import android.car.Car;
import android.car.CarInfoManager;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity2 extends AppCompatActivity {

   // private Connection serviceConnection;
    private Thread threadConnect;
    private Thread threadDisconnect;
    private volatile boolean LTPressed = false;
    private volatile boolean RTPressed = false;
    private Motion serviceMotion;
    private Car mCarApi;
    private CarPropertyManager mCarPropertyManager;
    private CarInfoManager carInfoManager;
    private int prevValue = 0;
    private Handler turnSignalHandler = new Handler(); // Handler for delay-based reset
    private Runnable turnSignalOffRunnable;
    public float value = 0.0f;
    private boolean isRtpressed = false;
    private boolean isLtpressed = false;
    static class MyBuffer {
        private final int MAX_SIZE = 200;
        private boolean updatePitch = true;
        private boolean updateRoll = false;
        private final LinkedList<Motion.MyMove> buff = new LinkedList<>();
        private boolean running = false;
        public void addData(float pitch, float roll) {
            if (!running) return;
            synchronized (this) {
                if (updatePitch) {
                    buff.addLast(new Motion.MyMove(false, MotionStatus.SetSteerAngle.getVal(), pitch));
//                    Log.d("msg" ,"update pitch true"+pitch+"l "+ MotionStatus.SetSteerAngle.getVal());
                }

                if (updateRoll) {
                    buff.addLast(new Motion.MyMove(false, MotionStatus.SetAccAngle.getVal(), roll));
                }
                while (buff.size() > MAX_SIZE) {
                    buff.removeFirst();
                }
            }
        }


        public void addData(MotionStatus status, float val) {
            if (!running) return;
            synchronized (this) {
                buff.addLast(new Motion.MyMove(false, status.getVal(), val));
                while (buff.size() > MAX_SIZE) {
                    buff.removeFirst();
                }
            }
        }

        public Motion.MyMove getData() {
            synchronized (this) {
                if (buff.isEmpty()) return null;
                return buff.removeFirst();
            }
        }
        public synchronized void setUpdatePitch(boolean val) {
            updatePitch = val;
        }
        public synchronized void setUpdateRoll(boolean val) {
            updateRoll = val;
        }

        public synchronized void turnOn() {
            running = true;
        }

        public synchronized void turnOff() {
            running = false;
        }
    }
    private final MyBuffer globalBuffer = new MyBuffer();
    private final int DEVICE_CHECK_DATA = 123456;
    private final int DEVICE_CHECK_EXPECTED = 654321;
    private final long MAX_WAIT_TIME = 1500L;
    private final int DATA_SEPARATOR = 10086;
    public boolean connected = false;
    private boolean running = false;
    private Socket wifiSocket;
    private Thread wifiThread;
    public String wifiAddress = "192.168.168.36";
    private final int wifiPort = 55555;

    private final Handler handlerUpdateUI = new Handler(Looper.getMainLooper());
    private final Runnable runnableUpdateUI = new Runnable() {
        @SuppressLint("DefaultLocale")
        @Override
        public void run() {
            try {
                if (!LTPressed && !RTPressed) {
                    globalBuffer.addData(MotionStatus.ResetAccAngle, 0.0f);
                } else if (LTPressed) {
                    //ProgressBar bar = findViewById(R.id.progressBarLT);
                    globalBuffer.addData(MotionStatus.SetAccRatio, -0.5f);
                } else if (RTPressed) {
                    //ProgressBar bar = findViewById(R.id.progressBarRT);
                    globalBuffer.addData(MotionStatus.SetAccRatio, 0.5f);
                }
            } catch (Exception e) {
                Log.d(getString(R.string.logTagMain), Objects.requireNonNull(e.getMessage()));
            } finally {
                handlerUpdateUI.postDelayed(this, 20);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main2);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initCar();
        initSensors();
        globalBuffer.turnOn();
        globalBuffer.setUpdatePitch(true);
        globalBuffer.setUpdateRoll(false);

        serviceMotion = new Motion(this, globalBuffer);
        serviceMotion.start();
       // serviceConnection = new Connection(this, globalBuffer);
        handlerUpdateUI.postDelayed(runnableUpdateUI, 0);
    }
    private void initCar() {
        Log.i(TAG, "Enter initCar() STARTED: ");
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.d(TAG, "Error! Direct returned because no package manager found!");
            return;
        }

        if (mCarApi != null && mCarApi.isConnected()) {
            mCarApi.disconnect();
            mCarApi = null;
        }

        Log.i(TAG, "mCarApi start to Connecting ");
        mCarApi = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (Car car, boolean ready) -> {
                    if (ready) {
                        Log.i(TAG, "initCar: car is ready");
                    }
                });
        Log.i(TAG, "Exit initCar: check mCarApi is Connected? " + mCarApi.isConnected());
    }
    private void initSensors() {
        Log.i(TAG, "initSensors() start!  ");
        try {
            if (mCarPropertyManager == null) {
                if (mCarApi == null) {
                    Log.d(TAG, "Error : initSensors(): mCarApi is also NULL !");
                }
                mCarPropertyManager = (CarPropertyManager) mCarApi.getCarManager(android.car.Car.PROPERTY_SERVICE);

                Log.d(TAG, "initSensors() finish get mCarPropertyManager() : " + mCarPropertyManager.getPropertyList());
                if (mCarPropertyManager == null) {
                    Log.d(TAG, "Error on initSensors() : mCarPropertyManager is STILL NULL !!");
                }
            }

            //init managers
            carInfoManager = (CarInfoManager) mCarApi.getCarManager(Car.INFO_SERVICE);
            mCarPropertyManager = (CarPropertyManager) mCarApi.getCarManager(android.car.Car.PROPERTY_SERVICE);

        } catch (Exception e) {
            Log.e(TAG, "initSensors() exception caught Failed!! ", e);
        }
    }

    private boolean checkWifi() {
        NetworkInfo test = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (test == null) {
            new AlertDialog.Builder(this).setTitle("Not Compatible").setMessage("Your Car cannot access Wi-Fi").setPositiveButton("OK", (dialog, which) -> System.exit(0)).show();
            return false;
        }
        if (!test.isConnected()) {
            Toast.makeText(this, "Please connect Wi-Fi to PC", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }


    public boolean isConnected() {
        return connected;
    }

    public String connect() {
        if (connected){
            connected = false;
        }
        return connectWifi();
    }
    // connect to wifi
    private String connectWifi() {
        AtomicBoolean decided = new AtomicBoolean(false);
        AtomicBoolean validAddress = new AtomicBoolean(false);
        runOnUiThread(() -> {
            final String USER_SETTINGS_DEFAULT_IP = "DEFAULT_IP";
            final String PREFERENCES_NAME = "AndroidSteeringUserSettings";
            SharedPreferences userSettings = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            wifiAddress = userSettings.getString(USER_SETTINGS_DEFAULT_IP, wifiAddress);
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Set IP address displayed on PC");
            final EditText ipInput = new EditText(this);
            ipInput.setText(wifiAddress);
            ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
            builder.setView(ipInput);
            builder.setPositiveButton("OK", (dialog, which) -> {
                if (Patterns.IP_ADDRESS.matcher(ipInput.getText().toString()).matches()) {
                    if (!ipInput.getText().toString().isEmpty()) {
                        wifiAddress = ipInput.getText().toString();
                        userSettings.edit()
                                .putString(USER_SETTINGS_DEFAULT_IP, wifiAddress)
                                .apply();
                        validAddress.set(true);
                    }
                    decided.set(true);
                }
                decided.set(true);
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                dialog.cancel();
                decided.set(true);
            });
            builder.show();
        });
        while (!decided.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (!validAddress.get()) return "Invalid IP address";
        // validate connection
        if (!testConnection()) return "Cannot connect to " + wifiAddress;
        // create connection
        if (wifiSocket != null || wifiThread != null){
            connected = false;
        }
        wifiSocket = new Socket();
        try {
            wifiSocket.bind(null);
        } catch (IOException e) {
            Log.d(getString(R.string.logTagConnection), "[connectWifi] Cannot bind socket -> " + e.getMessage());
        }
        try {
            wifiSocket.connect(new InetSocketAddress(wifiAddress, wifiPort), (int) MAX_WAIT_TIME);
        } catch (IOException e) {
            Log.d(getString(R.string.logTagConnection), "[connectWifi] Cannot connect socket -> " + e.getMessage());
        }
        connected = true;
        // start thread loop
        wifiThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
            try {

                DataOutputStream streamOut = new DataOutputStream(wifiSocket.getOutputStream());
                while (running) {
                    Motion.MyMove data = globalBuffer.getData();
                    if (data == null) {
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }
                    streamOut.writeInt(DATA_SEPARATOR);
                    streamOut.writeBoolean(data.MotionButton);
                    streamOut.writeInt(data.MotionStatus);
                    streamOut.writeFloat(data.data);
                }
                streamOut.close();
            } catch (IOException e) {
                Log.d(getString(R.string.logTagConnection), "[wifiThread] -> " + e.getMessage());
            } finally {
                connected = false;
                unlockRadioGroup();
            }
        });
        running = true;
        wifiThread.start();
        return "";
    }

    private void unlockRadioGroup() {
        runOnUiThread(() -> {
            try {
                RadioGroup group = findViewById(R.id.radioGroup);
                for (int i = 0; i < group.getChildCount(); i++) {
                    group.getChildAt(i).setEnabled(true);
                }
            } catch (Exception e) {
                Log.d(getString(R.string.logTagConnection), "[unlockRadioGroup] -> " + e.getMessage());
            }
        });
    }
    private boolean testConnection() {
        Socket tmp = new Socket();
        try {
            tmp.bind(null);
        } catch (IOException e) {
            Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) -> " + e.getMessage());
            return false;
        }
        try {
            tmp.connect(new InetSocketAddress(wifiAddress, wifiPort), (int) MAX_WAIT_TIME);
        } catch (IOException e) {
            Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) -> " + e.getMessage());
            return false;
        }
        AtomicBoolean isValid = new AtomicBoolean(false);
        Thread validationThread = new Thread(() -> {
            try {
                DataOutputStream streamOut = new DataOutputStream(tmp.getOutputStream());
                streamOut.writeInt(DEVICE_CHECK_DATA);
                streamOut.flush();
                DataInputStream streamIn = new DataInputStream(tmp.getInputStream());
                if (streamIn.readInt() == DEVICE_CHECK_EXPECTED) isValid.set(true);
            } catch (IOException e) {
                Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) validationThread -> " + e.getMessage());
            }
        });
        try {
            validationThread.start();
            validationThread.join(MAX_WAIT_TIME);
            if (validationThread.isAlive()) {
                Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) validationThread exceeds max timeout");
                try {
                    tmp.close();
                } catch (Exception any) {
                    Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) -> " + any.getMessage());
                }
                return false;
            }
        } catch (InterruptedException e) {
            Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) validationThread exceeds max timeout -> " + e.getMessage());
            try {
                tmp.close();
            } catch (Exception any) {
                Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) -> " + any.getMessage());
            }
            return false;
        }
        try {
            tmp.close();
        } catch (Exception e) {
            Log.d(getString(R.string.logTagConnection), "[testConnection](Wifi) -> " + e.getMessage());
        }
        return true;
    }
    public void disconnect() {
        running = false;
        if (wifiThread != null && wifiThread.isAlive()) {
            try {
                wifiThread.join(MAX_WAIT_TIME);  // Wait for the thread to stop
            } catch (InterruptedException e) {
                Log.d(getString(R.string.logTagConnection), "[disconnect] wifiThread join interrupted -> " + e.getMessage());
            }
            wifiThread = null;
        }
        if (wifiSocket != null && !wifiSocket.isClosed()) {
            try {
                wifiSocket.close();
            } catch (IOException e) {
                Log.d(getString(R.string.logTagConnection), "[disconnect] Failed to close wifiSocket -> " + e.getMessage());
            }
        }
        connected = false;
    }

    public void connectionButtonOnClick(View view) {
        AtomicBoolean connected = new AtomicBoolean(isConnected());
        LinearLayout uiLayout = findViewById(R.id.buttoncontrolLogic);
        LinearLayout connectionLogic = findViewById(R.id.connectionLogic);


        RadioGroup group = findViewById(R.id.radioGroup);
        if (connected.get()) {
            if (threadDisconnect != null && threadDisconnect.isAlive()) {
                Toast.makeText(this, "Already Disconnecting", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Disconnecting...", Toast.LENGTH_SHORT).show();
            threadDisconnect = new Thread(() -> {
                globalBuffer.addData(MotionStatus.ResetSteerAngle, 0.0f);
                globalBuffer.addData(MotionStatus.ResetAccAngle, 0.0f);
                connected.set(false);
                runOnUiThread(() -> {
                    if (!isConnected()) {
                        ((Button) view).setText(R.string.buttonConnect);

                            uiLayout.setVisibility(View.GONE);

                            connectionLogic.setVisibility(View.VISIBLE);
                        for (int i = 0; i < group.getChildCount(); i++) {
                            group.getChildAt(i).setEnabled(true);
                        }
                        disconnect();
                    } else{
                        ((Button) view).setText(R.string.buttonDisconnect);
                    }
                });
            });
            threadDisconnect.start();
        } else {
            if (threadConnect != null && threadConnect.isAlive()) {
                Toast.makeText(this, "Already Connecting", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();


            if (group.getCheckedRadioButtonId() == R.id.radioButtonWifi && !checkWifi())
                return;
            threadConnect = new Thread(() -> {
                String result = connect();
                runOnUiThread(() -> {
                    if (result.length() > 0)
                        Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
                    if (isConnected()) {
                        for (int i = 0; i < group.getChildCount(); i++) {
                            group.getChildAt(i).setEnabled(false);
                        }
                        ((Button) view).setText(R.string.buttonDisconnect);
                        if (connectionLogic.getVisibility() == View.VISIBLE){
                            connectionLogic.setVisibility(View.GONE);
                        }
                        if(uiLayout.getVisibility() == View.GONE){
                            uiLayout.setVisibility(View.VISIBLE);
                        }

                    } else{
                        ((Button) view).setText(R.string.buttonConnect);

                    }
                });
            });
            threadConnect.start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


        mCarPropertyManager.registerCallback(new CarPropertyManager.CarPropertyEventCallback() {


            @Override
            public void onChangeEvent(CarPropertyValue carPropertyValue) {
                Log.d(TAG, "CarPropertyManager.TURN_SIGNAL_STATE onChangeEvent : "  + carPropertyValue.getValue());
                int currentgetValue = (int) carPropertyValue.getValue();
                if (currentgetValue == 1 && prevValue == 0) {

                    handleRTButtonPress();
                    if (isLtpressed){
                        handleLTButtonRelease();
                    }
                    resetTurnSignalOffTimer();
                } else if (currentgetValue == 0 && prevValue == 1) {

                    handleRTButtonPress();
                    resetTurnSignalOffTimer();
                } else if (currentgetValue == 0 && prevValue == 0) {


                }
                else if ((currentgetValue == 2 && prevValue == 0)){

                    handleLTButtonPress();
                    if (isRtpressed){
                        handleRTButtonRelease();
                    }
                    resetTurnSignalOffTimer();
                }
                else if (currentgetValue == 0 && prevValue == 2){

                    handleLTButtonPress();
                    resetTurnSignalOffTimer();
                }
                else if (currentgetValue == 2 && prevValue == 1){
                    handleLTButtonPress();
                    if (isRtpressed){
                        handleRTButtonRelease();
                    }
                    resetTurnSignalOffTimer();
                }
                else if (currentgetValue == 1 && prevValue == 2){
                    handleRTButtonPress();
                    if (isLtpressed){
                        handleLTButtonRelease();
                    }
                    resetTurnSignalOffTimer();

                }
                prevValue = currentgetValue;

            }

            @Override
            public void onErrorEvent(int i, int i1) {
                Log.e(TAG, "CarPropertyManager.IGNITION_STATE error");
            }
            private void resetTurnSignalOffTimer() {
                if (turnSignalOffRunnable != null) {
                    turnSignalHandler.removeCallbacks(turnSignalOffRunnable);
                }
                turnSignalOffRunnable = () -> {
                    Log.d(TAG, "Turn Signal is OFF (Timeout Detected, Stopped Blinking)");

                    if (isRtpressed){
                        handleRTButtonRelease();
                    }
                    if (isLtpressed){
                        handleLTButtonRelease();
                    }
                };
                turnSignalHandler.postDelayed(turnSignalOffRunnable, 500);
            }
            private void handleRTButtonPress() {

                    View buttonRT = findViewById(R.id.buttonRT);
                    MotionEvent actionDownEvent = MotionEvent.obtain(
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            MotionEvent.ACTION_DOWN, 0.0f, 0.0f, 0);
                    touchRT(buttonRT, actionDownEvent);
                    isRtpressed = true;
                    actionDownEvent.recycle();

            }
            private void handleRTButtonRelease() {

                    View buttonRT =findViewById(R.id.buttonRT);
                    MotionEvent actionUpEvent = MotionEvent.obtain(
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            MotionEvent.ACTION_UP, 0.0f, 0.0f, 0);
                    touchRT(buttonRT, actionUpEvent);
                    isRtpressed = false;
                    actionUpEvent.recycle();

            }
            private void handleLTButtonPress() {

                    View buttonLT = findViewById(R.id.buttonLT);
                    MotionEvent actionDownEvent = MotionEvent.obtain(
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            MotionEvent.ACTION_DOWN, 0.0f, 0.0f, 0);
                    touchLT(buttonLT, actionDownEvent);
                    isLtpressed = true;
                    actionDownEvent.recycle();

            }
            private void handleLTButtonRelease() {

                    View buttonLT = findViewById(R.id.buttonLT);
                    MotionEvent actionUpEvent = MotionEvent.obtain(
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            MotionEvent.ACTION_UP, 0.0f, 0.0f, 0);
                    touchLT(buttonLT, actionUpEvent);
                    isLtpressed = false;

                    actionUpEvent.recycle();
                }

        }, VehiclePropertyIds.TURN_SIGNAL_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE);



        mCarPropertyManager.registerCallback(new CarPropertyManager.CarPropertyEventCallback() {
            @Override
            public void onChangeEvent(CarPropertyValue carPropertyValue) {
                value = (float) carPropertyValue.getValue();
                Log.d("Tag", carPropertyValue.getValue().toString());
                serviceMotion.updateLeftVal(value);
            }

            @Override
            public void onErrorEvent(int propId, int zone) {
                Log.d(TAG, "PERF_STEERING_ANGLE: onErrorEvent(" + propId + ", " + zone + ")");
            }
        }, VehiclePropertyIds.PERF_STEERING_ANGLE, CarPropertyManager.SENSOR_RATE_FASTEST);
    }

    public void touchLT(View view, MotionEvent e) {

        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                LTPressed = true;

                return;

            case MotionEvent.ACTION_UP:
                LTPressed = false;

        }
    }

    public void touchRT(View view, MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                RTPressed = true;
                return;

            case MotionEvent.ACTION_UP:
                RTPressed = false;

        }
    }

}

