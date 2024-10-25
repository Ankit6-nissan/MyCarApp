package com.nissanvit.mycarapp.mainForgroundService;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;


public class mainForegService extends Service {
    private String TAG ="mainForegService log tag";
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){ //call this when startService() called
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        while(true){
                            Log.e(TAG, "my foreground service is running...");
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
        return null;
    }

}
