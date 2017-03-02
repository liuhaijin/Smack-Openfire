package com.xmpp.service;

import com.xmpp.util.PreferencesUtils;

import zkzy.xmpp.SmackConnection.SmackConnection;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;



public class SmackService extends Service {
	
	private static SmackService mInstance = null;
	
	private String username;
	private String password;
    public static SmackConnection.ConnectionState sConnectionState;

    public static SmackConnection.ConnectionState getState() {
        if(sConnectionState == null){
            return SmackConnection.ConnectionState.DISCONNECTED;
        }
        return sConnectionState;
    }

    private boolean mActive;
    private Thread mThread;
    private Handler mTHandler;
    private SmackConnection mConnection;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
    }

    public static SmackService getInstance(){
    	return mInstance;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	username = intent.getStringExtra("username");
    	password = intent.getStringExtra("password");
    	PreferencesUtils.putSharePre(this, "username", username);
		PreferencesUtils.putSharePre(this, "password", password);
        start();
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("SmackService", "onDestroy");
        stop();
    }

    public void start() {
        if (!mActive) {
            mActive = true;

            // Create ConnectionThread Loop
            if (mThread == null || !mThread.isAlive()) {
                mThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Looper.prepare();
                        mTHandler = new Handler();
                        initConnection();
                        Looper.loop();
                    }

                });
                mThread.start();
            }

        }
    }

    public void stop() {
        mActive = false;
        mTHandler.post(new Runnable() {

            @Override
            public void run() {
                if(mConnection != null){
                    mConnection.disconnect();
                }
            }
        });

    }

    private void initConnection() {
        if(mConnection == null){
            mConnection = new SmackConnection(this, username, password);
        }
        mConnection.connect();
    }
}
