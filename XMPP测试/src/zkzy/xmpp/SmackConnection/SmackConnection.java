package zkzy.xmpp.SmackConnection;

import java.util.Timer;
import java.util.TimerTask;

import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.ping.PingFailedListener;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo.State;
import android.util.Log;

/**
 * Created by Furuha on 27.12.2014.
 * Edit by 刘海金   on 8.3.2016.
 */
public class SmackConnection implements ConnectionListener, PingFailedListener, ChatManagerListener, ChatMessageListener {

    public static enum ConnectionState {
        CONNECTED, CONNECTING, RECONNECTING, DISCONNECTED;
    }
    public static ConnectionState sConnectionState = ConnectionState.DISCONNECTED;
    private static final String TAG = "SMACK";
    private final Context mService;
    private final String mPassword;
    private final String mUsername;

    private XMPPTCPConnection mConnection;
    private BroadcastReceiver mReceiver;
//    private ArrayList<String> mRoster;

    public SmackConnection(Context service, String username, String password) {
        Log.i(TAG, "ChatConnection()");

        this.mService = service;
        mUsername = username;
		mPassword = password;

    }

    public void connect() {
        Log.i(TAG, "connect()");
        XMPPTCPConnectionConfiguration.Builder configBuilder = XMPPTCPConnectionConfiguration.builder();
        configBuilder.setHost(SmackConst.XMPP_HOST);
        configBuilder.setServiceName(SmackConst.SERVICE_NAME);
        configBuilder.setUsernameAndPassword(mUsername, mPassword);
        configBuilder.setSecurityMode(SecurityMode.disabled);
        mConnection = new XMPPTCPConnection(configBuilder.build());
        //Set ConnectionListener here to catch initial connect();
        mConnection.addConnectionListener(this);
        try {
        	mConnection.connect();
        	mConnection.login();
        	if(mConnection.isAuthenticated()){//登录成功
				MyPingManager.setDefaultPingInterval(10);//Ping every 10 seconds
				MyPingManager myPingManager = MyPingManager.getInstanceFor(mConnection);
				//Set PingListener here to catch connect status
				myPingManager.registerPingFailedListener(SmackConnection.this);
				setupSendMessageReceiver();
				//Set ChatListener here to catch receive message and send NEW_MESSAGE broadcast
        		ChatManager.getInstanceFor(mConnection).addChatListener(this);
        		//Set ChatListener here to catch roster change and rebuildRoster
        		//Roster.getInstanceFor(mConnection).addRosterListener(this);
        		sendLoginBroadcast(true);
        	}else{
        		mConnection.disconnect();
        		Log.i(TAG, "Authentication failure");
        		sendLoginBroadcast(false);
        	}
		} catch (Exception e) {
			e.printStackTrace();
			sendLoginBroadcast(false);
			Intent intent = new Intent(mService, mService.getClass());
			mService.stopService(intent);
		}

    }

    public void disconnect() {
        Log.i(TAG, "disconnect()");
        if(mConnection != null){
		    mConnection.disconnect();
		    sendCancleBroadcast();//发送退出登录广播
		}
        
        mConnection = null;
        if(mReceiver != null){
//            mService.unregisterReceiver(mReceiver);
        	try {  
        		mService.unregisterReceiver(mReceiver);  
        	} catch (IllegalArgumentException e) {  
        	    if (e.getMessage().contains("Receiver not registered")) {  
        	        // Ignore this exception. This is exactly what is desired 
        	    	e.printStackTrace();
        	    } else {  
        	        // unexpected, re-throw  
        	        throw e;  
        	    }  
        	}  
            mReceiver = null;
        }
    }
    
    /**
	 * 发送登录状态广播
	 * @param isLoginSuccess
	 */
	public void sendLoginBroadcast(boolean isLoginSuccess){
		Intent intent =new Intent(SmackConst.ACTION_IS_LOGIN_SUCCESS);
		intent.putExtra("isLoginSuccess", isLoginSuccess);
		mService.sendBroadcast(intent);
	}

	/**
	 * 发送退出登录广播
	 * @param isLoginSuccess
	 */
	public void sendCancleBroadcast(){
		Intent intent =new Intent(SmackConst.ACTION_CONNECT_LOGOUT);
		mService.sendBroadcast(intent);
	}
	
	private void setupSendMessageReceiver() {
        mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(SmackConst.SEND_MESSAGE)) {
                    sendMessage(intent.getStringExtra(SmackConst.BUNDLE_MESSAGE_BODY), intent.getStringExtra(SmackConst.BUNDLE_TO));
                }
            }

        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(SmackConst.SEND_MESSAGE);
        mService.registerReceiver(mReceiver, filter);
    }
	
    private void sendMessage(String body, String toJid) {
        Log.i(TAG, "sendMessage():toJid:"+toJid+",body:"+body);
        Chat chat = ChatManager.getInstanceFor(mConnection).createChat(toJid+"@"+SmackConst.SERVICE_NAME+"/Smack", null);
        try {
            chat.sendMessage(body);
        } catch (NotConnectedException e) {
            e.printStackTrace();
        }
    }

    //ConnectionListener

    @Override
	public void authenticated(XMPPConnection connection, boolean resume) {
    	sConnectionState = ConnectionState.CONNECTED;
        Log.i(TAG, "authenticated()");
	}

    @Override
    public void connected(XMPPConnection connection) {
        sConnectionState = ConnectionState.CONNECTED;
        Log.i(TAG, "connected()");
    }

    @Override
    public void connectionClosed() {
        sConnectionState = ConnectionState.DISCONNECTED;
        Log.i(TAG, "connectionClosed()");
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        sConnectionState = ConnectionState.DISCONNECTED;
        Log.i(TAG, "connectionClosedOnError()");
        
        if(mConnection.isConnected()){
			mConnection.disconnect();
		}
		reConnectTimer = new Timer();
		reConnectTimer.schedule(new ReConnectTimer(), delay);
		Intent intent = new Intent(SmackConst.ACTION_ERROR_DISCONNECTED);
		mService.sendBroadcast(intent);
    }

    @Override
    public void reconnectingIn(int seconds) {
        sConnectionState = ConnectionState.RECONNECTING;
        Log.i(TAG, "reconnectingIn()");
    }

    @Override
    public void reconnectionSuccessful() {
        sConnectionState = ConnectionState.CONNECTED;
        Log.i(TAG, "reconnectionSuccessful()");
    }

    @Override
    public void reconnectionFailed(Exception e) {
        sConnectionState = ConnectionState.DISCONNECTED;
        Log.i(TAG, "reconnectionFailed()");
    }

    //PingFailedListener
    
	@Override
	public void pingFailed(){
		Log.i(TAG, "pingFailed()");
		if(mConnection.isConnected()){
			mConnection.disconnect();
		}
		reConnectTimer = new Timer();
		reConnectTimer.schedule(new ReConnectTimer(), delay);
		Intent intent = new Intent(SmackConst.ACTION_ERROR_DISCONNECTED);
		mService.sendBroadcast(intent);
	}
	
	private Timer reConnectTimer;
	private int delay = 10000;
	//pingFailed时启动重连线程
	class ReConnectTimer extends TimerTask {  
        @Override  
        public void run() {
    		// 无网络连接时,直接返回
    		if (getNetworkState(mService) == NETWORN_NONE) {
    			Log.i(TAG, "无网络连接，"+delay/1000+"s后重新连接");
    			reConnectTimer.schedule(new ReConnectTimer(), delay);
    			//reConnectTimer.cancel();
    			return;
    		}
            // 连接服务器 
    		try {
				mConnection.connect();
				if(!mConnection.isAuthenticated()){
					mConnection.login();
					reConnectTimer.cancel();
				}
				Log.i(TAG, "重连成功");
				Intent intent = new Intent(SmackConst.ACTION_RECONNECT_SUCCESS);
				mService.sendBroadcast(intent);
			} catch (Exception e) {
				Log.i(TAG, "重连失败，"+delay/1000+"s后重新连接");
				e.printStackTrace();
				reConnectTimer.schedule(new ReConnectTimer(), delay);
			} 
    		
        }  
    }
	
	public static final int NETWORN_NONE = 0;
	public static final int NETWORN_WIFI = 1;
	public static final int NETWORN_MOBILE = 2;

	public static int getNetworkState(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		// Wifi
		State state = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
				.getState();
		if (state == State.CONNECTED || state == State.CONNECTING) {
			return NETWORN_WIFI;
		}

		return NETWORN_NONE;
	}
	
	//ChatManagerListener
	
	@Override
	public void chatCreated(Chat chat, boolean createdLocally) {
		Log.i(TAG, "chatCreated()");
        chat.addMessageListener(this);
	}

	//ChatMessageListener
	
	@Override
	public void processMessage(Chat chat, Message message) {
		Log.i(TAG, "processMessage()");
        if (message.getType().equals(Message.Type.chat) || message.getType().equals(Message.Type.normal)) {
            if (message.getBody() != null) {
                Intent intent = new Intent(SmackConst.ACTION_NEW_MESSAGE);
                intent.setPackage(mService.getPackageName());
                intent.putExtra(SmackConst.BUNDLE_MESSAGE_BODY, message.getBody());
                intent.putExtra(SmackConst.BUNDLE_FROM_JID, message.getFrom());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                }
                mService.sendBroadcast(intent);
                Log.i(TAG, "processMessage() BroadCast send&"+message.getFrom()+":"+message.getBody());
            }
        }
		
	}

	//RosterListener
	
//	@Override
//    public void entriesAdded(Collection<String> addresses) {
//        Log.i(TAG, "entriesAdded()");
//        rebuildRoster();
//    }
//
//    @Override
//    public void entriesUpdated(Collection<String> addresses) {
//        Log.i(TAG, "entriesUpdated()");
//        rebuildRoster();
//    }
//
//    @Override
//    public void entriesDeleted(Collection<String> addresses) {
//        Log.i(TAG, "entriesDeleted()");
//        rebuildRoster();
//    }
//
//    @Override
//    public void presenceChanged(Presence presence) {
//        Log.i(TAG, "presenceChanged()");
//        rebuildRoster();
//    }
//    
//    private void rebuildRoster() {
//        mRoster = new ArrayList<>();
//        String status;
//        for (RosterEntry entry : Roster.getInstanceFor(mConnection).getEntries()) {
//            if(Roster.getInstanceFor(mConnection).getPresence(entry.getUser()).isAvailable()){
//                status = "Online";
//            } else {
//                status = "Offline";
//            }
//            mRoster.add(entry.getUser()+ ": " + status);
//            Log.i(TAG, entry.getUser()+ ": " + status);
//        }
//
//        Intent intent = new Intent(SmackService.NEW_ROSTER);
//        intent.setPackage(mService.getPackageName());
//        intent.putStringArrayListExtra(SmackService.BUNDLE_ROSTER, mRoster);
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
//            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
//        }
//        mService.sendBroadcast(intent);
//    }


}
