package com.xmpp.app;

import org.jivesoftware.smack.XMPPConnection;

import android.app.Application;

public class MyApplication extends Application{
	
	public static XMPPConnection xmppConnection;
	
	@Override
	public void onCreate() {
		super.onCreate();
	}

}
