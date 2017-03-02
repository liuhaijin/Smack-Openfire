package com.xmpp.activity;

import zkzy.xmpp.SmackConnection.SmackConst;

import com.xmpp.service.SmackService;
import com.xmpp.util.PreferencesUtils;
import com.xmpp.util.ToastUtil;
import com.xmpp.activity.MainActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class LoginActivity extends Activity {
	
	private EditText username; 
	private EditText password;
	
	private BroadcastReceiver receiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
		username = (EditText) findViewById(R.id.username);
		password = (EditText) findViewById(R.id.password);
		
		initReceiver();
	}

	public void login(View v){
		Log.i("XMPP", "login");
		//启动SmackService
		Intent intent=new Intent(this,SmackService.class);
		intent.putExtra("username", username.getText().toString());
		intent.putExtra("password", password.getText().toString());
		startService(intent);
	}
	
	private void initReceiver() {
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if(intent.getAction().equals(SmackConst.ACTION_IS_LOGIN_SUCCESS)){
					boolean isLoginSuccess=intent.getBooleanExtra("isLoginSuccess", false);
					if(isLoginSuccess){//登录成功
						//默认开启声音和震动提醒
						PreferencesUtils.putSharePre(LoginActivity.this, SmackConst.MSG_IS_VOICE, true);
						PreferencesUtils.putSharePre(LoginActivity.this, SmackConst.MSG_IS_VIBRATE, true);
						Intent intent2=new Intent(LoginActivity.this,MainActivity.class);
						startActivity(intent2);
						finish();
					}else{
						ToastUtil.showShortToast(LoginActivity.this, "登录失败，请检您的网络是否正常以及用户名和密码是否正确");
					}
				}
			}
		};
		//注册广播接收者
		IntentFilter mFilter = new IntentFilter();
		mFilter.addAction(SmackConst.ACTION_IS_LOGIN_SUCCESS);
		registerReceiver(receiver, mFilter);
	}
	
	protected void onStart() {
		super.onStart();
		String un=PreferencesUtils.getSharePreStr(this, "username");//用户名
		String pwd=PreferencesUtils.getSharePreStr(this, "password");//密码
		if(!TextUtils.isEmpty(un)){
			username.setText(un);
		}
		if(!TextUtils.isEmpty(pwd)){
			password.setText(pwd);
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(receiver);
	}
}
