package com.nissanvit.mycarapp.mainBackgroundService;

import android.app.Service;
import android.car.Car;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;


public class mainBackService extends Service {
    private String TAG = "mainBackService log tag";
    private final IBinder binder = new MyBinder();

    public Car CarApi;

    public class MyBinder extends Binder {
        public mainBackService getService() {
            return mainBackService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { //call this when startService() called
        if (CarApi == null) {
            Log.d(TAG, "my car api at background is NULL");
        }

        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            Log.e(TAG, "my back service is running...");
                            try {
                                Thread.sleep(4000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
        ).start();

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "Service Destroyed", Toast.LENGTH_LONG).show();
    }

}

