package com.nissanvit.mycarapp;

import android.os.Process;
import android.util.Log;

enum MotionStatus {
    SetSteerAngle(0),
    SetAccAngle(1),
    ResetSteerAngle(2),
    ResetAccAngle(3),
    SetAccRatio(4);
    private final int val;
    MotionStatus(int v) {
        val = v;
    }
    public int getVal() {
        return val;
    }
}
class Motion {
    static class MyMove {
        boolean MotionButton;
        int MotionStatus;
        float data;

        public MyMove(boolean type, int status, float d) {
            MotionButton = type;
            MotionStatus = status;
            data = d;
        }
    }

    public volatile float motionPitch = 0.0f;
    private Thread dataSubmitThread;
    private volatile boolean dataSubmitShouldStop;
    public volatile float leftVal = 0.0f;
    private final int MAX_WAIT_TIME = 1000;
    private final int DATA_UPDATE_FREQ = 5;
    private final MainActivity2 mainActivity;
    private final MainActivity2.MyBuffer globalBuffer;

    public Motion(MainActivity2 activity, MainActivity2.MyBuffer buffer) {
        this.mainActivity = activity;
        this.globalBuffer = buffer;
    }

    public void start() {
        if (dataSubmitThread != null && dataSubmitThread.isAlive()) {
            stop();
        }
        dataSubmitShouldStop = false;

        dataSubmitThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
            while (!dataSubmitShouldStop) {
                try {
                    updatePitch(leftVal);
                    globalBuffer.addData(readPitch(), 0.0f);

                    Thread.sleep(DATA_UPDATE_FREQ);
                } catch (InterruptedException e) {

                    Log.d(mainActivity.getString(R.string.logTagMotion), e.toString());
                    break;
                }
            }
        });
        dataSubmitThread.start();
    }

    public void stop() {
        dataSubmitShouldStop = true;
        if (dataSubmitThread != null && dataSubmitThread.isAlive()) {
            try {
                dataSubmitThread.join(MAX_WAIT_TIME);
            } catch (InterruptedException e) {
                Log.d(mainActivity.getString(R.string.logTagMotion), e.toString());
            }
        }
    }

    public synchronized void updateLeftVal(float value) {
        this.leftVal = value;
    }

    public synchronized float readPitch() {
        return motionPitch;
    }

    private synchronized void updatePitch(float newPitch) {
        motionPitch = newPitch;
    }
}
