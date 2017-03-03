# Smack-Openfire
im with Android, include heartbeat and auto reconnect

图文详情地址：http://www.cnblogs.com/code0001/p/6495851.html

Smack是一个开源，易于使用的XMPP（jabber）客户端类库。优点：简单的，功能强大，给用户发送信息只需三行代码便可完成。缺点：API并非为大量并发用户设计，每个客户要1个线程，占用资源大。
OpenFire是开源的、基于可拓展通讯和表示协议(XMPP)、采用Java编程语言开发的实时协作服务器。 Openfire安装和使用都非常简单，并利用Web进行管理。单台服务器可支持上万并发用户。
1、首先到网址 http://www.igniterealtime.org 下载OpenFire服务器和Smack jar包

2、安装OpenFire登陆到控制台，设置多长时间关闭闲置连接，可以判断用户是否在线的最长反应时间

3、创建两个测试账号，先用Spark登陆一个账号

4、手机端登陆，使用Service保持连接，并与spark端发送消息，实现双向通信（代码和程序在后面）

5、关键代码

配置连接OpenFire服务器，连接成功后设置响应Linstener和Receiver，这里因业务需求设置ping间隔为10s

 1     public void connect() {
 2         Log.i(TAG, "connect()");
 3         XMPPTCPConnectionConfiguration.Builder configBuilder = XMPPTCPConnectionConfiguration.builder();
 4         configBuilder.setHost(SmackConst.XMPP_HOST);
 5         configBuilder.setServiceName(SmackConst.SERVICE_NAME);
 6         configBuilder.setUsernameAndPassword(mUsername, mPassword);
 7         configBuilder.setSecurityMode(SecurityMode.disabled);
 8         mConnection = new XMPPTCPConnection(configBuilder.build());
 9         //Set ConnectionListener here to catch initial connect();
10         mConnection.addConnectionListener(this);
11         try {
12             mConnection.connect();
13             mConnection.login();
14             if(mConnection.isAuthenticated()){//登录成功
15                 MyPingManager.setDefaultPingInterval(10);//Ping every 10 seconds
16                 MyPingManager myPingManager = MyPingManager.getInstanceFor(mConnection);
17                 //Set PingListener here to catch connect status
18                 myPingManager.registerPingFailedListener(SmackConnection.this);
19                 setupSendMessageReceiver();
20                 //Set ChatListener here to catch receive message and send NEW_MESSAGE broadcast
21                 ChatManager.getInstanceFor(mConnection).addChatListener(this);
22                 //Set ChatListener here to catch roster change and rebuildRoster
23                 //Roster.getInstanceFor(mConnection).addRosterListener(this);
24                 sendLoginBroadcast(true);
25             }else{
26                 mConnection.disconnect();
27                 Log.i(TAG, "Authentication failure");
28                 sendLoginBroadcast(false);
29             }
30         } catch (Exception e) {
31             e.printStackTrace();
32             sendLoginBroadcast(false);
33             Intent intent = new Intent(mService, mService.getClass());
34             mService.stopService(intent);
35         }
36 
37     }

自动重连TimerTask，Ping失败后启动，重连成功后关闭

 1     private Timer reConnectTimer;
 2     private int delay = 10000;
 3     //pingFailed时启动重连线程
 4     class ReConnectTimer extends TimerTask {  
 5         @Override  
 6         public void run() {
 7             // 无网络连接时,直接返回
 8             if (getNetworkState(mService) == NETWORN_NONE) {
 9                 Log.i(TAG, "无网络连接，"+delay/1000+"s后重新连接");
10                 reConnectTimer.schedule(new ReConnectTimer(), delay);
11                 //reConnectTimer.cancel();
12                 return;
13             }
14             // 连接服务器 
15             try {
16                 mConnection.connect();
17                 if(!mConnection.isAuthenticated()){
18                     mConnection.login();
19                     reConnectTimer.cancel();
20                 }
21                 Log.i(TAG, "重连成功");
22                 Intent intent = new Intent(SmackConst.ACTION_RECONNECT_SUCCESS);
23                 mService.sendBroadcast(intent);
24             } catch (Exception e) {
25                 Log.i(TAG, "重连失败，"+delay/1000+"s后重新连接");
26                 e.printStackTrace();
27                 reConnectTimer.schedule(new ReConnectTimer(), delay);
28             } 
29             
30         }  
31     }

菜鸟一枚，共同学习~~

 
