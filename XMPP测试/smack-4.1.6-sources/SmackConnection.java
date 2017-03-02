package de.meisterfuu.smackdemo.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;
import android.util.Log;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ChatMessageListener;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;


/**
 * Created by Furuha on 27.12.2014.
 */
public class SmackConnection implements ConnectionListener, ChatManagerListener, RosterListener, ChatMessageListener, PingFailedListener {

    public static enum ConnectionState {
        CONNECTED, CONNECTING, RECONNECTING, DISCONNECTED;
    }

    private static final String TAG = "SMACK";
    private final Context mApplicationContext;
    private final String mPassword;
    private final String mUsername;
    private final String mServiceName;

    private XMPPTCPConnection mConnection;
    private ArrayList<String> mRoster;
    private BroadcastReceiver mReceiver;

    public SmackConnection(Context pContext) {
        Log.i(TAG, "ChatConnection()");

        mApplicationContext = pContext.getApplicationContext();
        mPassword = PreferenceManager.getDefaultSharedPreferences(mApplicationContext).getString("xmpp_password", null);
        String jid = PreferenceManager.getDefaultSharedPreferences(mApplicationContext).getString("xmpp_jid", null);
        mServiceName = jid.split("@")[1];
        mUsername = jid.split("@")[0];

    }

    public void connect() throws IOException, XMPPException, SmackException {
        Log.i(TAG, "connect()");

        XMPPTCPConnectionConfiguration.XMPPTCPConnectionConfigurationBuilder builder = XMPPTCPConnectionConfiguration.builder();
        builder.setServiceName(mServiceName);
        builder.setResource("SmackAndroidTestClient");
        builder.setUsernameAndPassword(mUsername, mPassword);
        builder.setRosterLoadedAtLogin(true);


        mConnection = new XMPPTCPConnection(builder.build());

        //Set ConnectionListener here to catch initial connect();
        mConnection.addConnectionListener(this);

        mConnection.connect();
        mConnection.login();

        PingManager.setDefaultPingInterval(600); //Ping every 10 minutes
        PingManager pingManager = PingManager.getInstanceFor(mConnection);
        pingManager.registerPingFailedListener(this);

        setupSendMessageReceiver();

        ChatManager.getInstanceFor(mConnection).addChatListener(this);
        mConnection.getRoster().addRosterListener(this);

    }

    public void disconnect() {
        Log.i(TAG, "disconnect()");
        try {
            if(mConnection != null){
                mConnection.disconnect();
            }
        } catch (SmackException.NotConnectedException e) {
            SmackService.sConnectionState = ConnectionState.DISCONNECTED;
            e.printStackTrace();
        }

        mConnection = null;
        if(mReceiver != null){
            mApplicationContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }


    private void rebuildRoster() {
        mRoster = new ArrayList<>();
        String status;
        for (RosterEntry entry : mConnection.getRoster().getEntries()) {
            if(mConnection.getRoster().getPresence(entry.getUser()).isAvailable()){
                status = "Online";
            } else {
                status = "Offline";
            }
            mRoster.add(entry.getUser()+ ": " + status);
        }

        Intent intent = new Intent(SmackService.NEW_ROSTER);
        intent.setPackage(mApplicationContext.getPackageName());
        intent.putStringArrayListExtra(SmackService.BUNDLE_ROSTER, mRoster);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        mApplicationContext.sendBroadcast(intent);
    }

    private void setupSendMessageReceiver() {
        mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(SmackService.SEND_MESSAGE)) {
                    sendMessage(intent.getStringExtra(SmackService.BUNDLE_MESSAGE_BODY), intent.getStringExtra(SmackService.BUNDLE_TO));
                }
            }

        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(SmackService.SEND_MESSAGE);
        mApplicationContext.registerReceiver(mReceiver, filter);
    }

    private void sendMessage(String body, String toJid) {
        Log.i(TAG, "sendMessage()");
        Chat chat = ChatManager.getInstanceFor(mConnection).createChat(toJid, this);
        try {
            chat.sendMessage(body);
        } catch (SmackException.NotConnectedException | XMPPException e) {
            e.printStackTrace();
        }
    }

    //ChatListener

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        Log.i(TAG, "chatCreated()");
        chat.addMessageListener(this);
    }

    //MessageListener

    @Override
    public void processMessage(Chat chat, Message message) {
        Log.i(TAG, "processMessage()");
        if (message.getType().equals(Message.Type.chat) || message.getType().equals(Message.Type.normal)) {
            if (message.getBody() != null) {
                Intent intent = new Intent(SmackService.NEW_MESSAGE);
                intent.setPackage(mApplicationContext.getPackageName());
                intent.putExtra(SmackService.BUNDLE_MESSAGE_BODY, message.getBody());
                intent.putExtra(SmackService.BUNDLE_FROM_JID, message.getFrom());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                }
                mApplicationContext.sendBroadcast(intent);
                Log.i(TAG, "processMessage() BroadCast send");
            }
        }
    }

    //ConnectionListener

    @Override
    public void connected(XMPPConnection connection) {
        SmackService.sConnectionState = ConnectionState.CONNECTED;
        Log.i(TAG, "connected()");
    }

    @Override
    public void authenticated(XMPPConnection connection) {
        SmackService.sConnectionState = ConnectionState.CONNECTED;
        Log.i(TAG, "authenticated()");
    }

    @Override
    public void connectionClosed() {
        SmackService.sConnectionState = ConnectionState.DISCONNECTED;
        Log.i(TAG, "connectionClosed()");
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        SmackService.sConnectionState = ConnectionState.DISCONNECTED;
        Log.i(TAG, "connectionClosedOnError()");
    }

    @Override
    public void reconnectingIn(int seconds) {
        SmackService.sConnectionState = ConnectionState.RECONNECTING;
        Log.i(TAG, "reconnectingIn()");
    }

    @Override
    public void reconnectionSuccessful() {
        SmackService.sConnectionState = ConnectionState.CONNECTED;
        Log.i(TAG, "reconnectionSuccessful()");
    }

    @Override
    public void reconnectionFailed(Exception e) {
        SmackService.sConnectionState = ConnectionState.DISCONNECTED;
        Log.i(TAG, "reconnectionFailed()");
    }

    //RosterListener

    @Override
    public void entriesAdded(Collection<String> addresses) {
        Log.i(TAG, "entriesAdded()");
        rebuildRoster();
    }

    @Override
    public void entriesUpdated(Collection<String> addresses) {
        Log.i(TAG, "entriesUpdated()");
        rebuildRoster();
    }

    @Override
    public void entriesDeleted(Collection<String> addresses) {
        Log.i(TAG, "entriesDeleted()");
        rebuildRoster();
    }

    @Override
    public void presenceChanged(Presence presence) {
        Log.i(TAG, "presenceChanged()");
        rebuildRoster();
    }

    //PingFailedListener

    @Override
    public void pingFailed() {
        Log.i(TAG, "pingFailed()");
    }
}
