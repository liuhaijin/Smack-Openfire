package com.xmpp.activity;

import zkzy.xmpp.SmackConnection.SmackConst;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.xmpp.service.SmackService;
import com.xmpp.util.ToastUtil;

public class MainActivity extends Activity {
	
	private EditText receiverName;
	private EditText content;
	
	private SmackReciver SmackReciver;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		receiverName = (EditText) findViewById(R.id.receiverName);
		content = (EditText) findViewById(R.id.content);
		
		SmackReciver=new SmackReciver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(SmackConst.ACTION_NEW_MESSAGE);
		intentFilter.addAction(SmackConst.ACTION_ERROR_DISCONNECTED);
		intentFilter.addAction(SmackConst.ACTION_RECONNECT_SUCCESS);
		registerReceiver(SmackReciver, intentFilter);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(SmackReciver);
	}

	public void send(View v){
		Intent intent = new Intent(SmackConst.SEND_MESSAGE);
        intent.setPackage(this.getPackageName());
        intent.putExtra(SmackConst.BUNDLE_MESSAGE_BODY, content.getText().toString());
        intent.putExtra(SmackConst.BUNDLE_TO, receiverName.getText().toString());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        sendBroadcast(intent);
	}
	
	public void cancel(View v){
		try{
			SmackService.getInstance().stopSelf();
		}catch (Exception e) {
			
		}
		Intent intent=new Intent(MainActivity.this, LoginActivity.class);
		startActivity(intent);
		finish();
	}
	
	public class SmackReciver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			switch(intent.getAction()){
			case SmackConst.ACTION_NEW_MESSAGE:
				String from = intent.getStringExtra(SmackConst.BUNDLE_FROM_JID);
				String body = intent.getStringExtra(SmackConst.BUNDLE_MESSAGE_BODY);
				ToastUtil.showLongToast(context, "收到"+from+"消息："+body);
				break;
			case SmackConst.ACTION_ERROR_DISCONNECTED:
				ToastUtil.showShortToast(context, "连接断开，正在重连...");
				break;
			case SmackConst.ACTION_RECONNECT_SUCCESS:
				ToastUtil.showShortToast(context, "重连成功");
				break;
			}
		}

	}
}
