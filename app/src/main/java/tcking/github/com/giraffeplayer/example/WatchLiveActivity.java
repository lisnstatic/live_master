package tcking.github.com.giraffeplayer.example;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser;
import tcking.github.com.ijkplayer.Constants;
import tcking.github.com.ijkplayer.IjkPlayer;
import tcking.github.com.ijkplayer.SendMsgPopupWindow;
import tv.danmaku.ijk.media.player.IMediaPlayer;

import static net.ossrs.yasea.Contanst.ScreenState;

public class WatchLiveActivity extends AppCompatActivity implements View.OnClickListener {
    private IjkPlayer mPlayer;
    private Socket mSocket;
    private DanmakuContext mContext;
    private IDanmakuView mDanmakuView;
    private BaseDanmakuParser mParser;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private RecyclerView mMessagesView;
    private LinearLayout mMessagesendlayout;
    private RelativeLayout mZhubolayout;
    private RelativeLayout mChatzhubomsg;
    private TextView mChatTxt;
    private TextView mHostTxt;
    private EditText mEditMsg;
    private  SendMsgPopupWindow sendMsgPopWin_small;
    private Intent enter_intent;
    private String mPushurl = "";
    private String mChatUrl = "";
    private ExecutorService cachedThreadPool;
    private boolean isSelf;
    private boolean isFirstStart = true;
    private LogUtil log;
    private MyOrientationEventListener orientationEventListener;
    private ScreenState mScreenState;
    public static String mUserId = "";
    private String mChatRoom = "";
    private Toast toast;
    private boolean isFirstConnect;
    private boolean isLastDisconnect;
    private Timer mTimer;
    private MyTimerTask mTimerTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持播放器界面长亮状态
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        mScreenState = ScreenState.PORIN;
        orientationEventListener = new MyOrientationEventListener(this);
        orientationEventListener.enable();
        toast = new Toast(this);
        log = LogUtil.getInstance();
        log.i("systime1", System.currentTimeMillis() + "");
        //获取传过来的值
        enter_intent = getIntent();
        mPushurl = enter_intent.getExtras().getString("pushUrl");
        mChatUrl = enter_intent.getExtras().getString("chatUrl");
        mChatRoom = enter_intent.getExtras().getString("chatRoom");
        mUserId = enter_intent.getExtras().getString("userId");
        if (mPushurl.equals("") || mPushurl == null) {
            mPushurl = "rtmp://t1.livecdn.yicai.com/beijing/beijing_1";
           /* mPushurl = "rtmp://t1.livecache.yicai.com/alicdn/c1";*/
           /* mPushurl = "rtmp://180.168.73.14/live/beijing";
            mPushurl = "rtmp://ossrs.net:1935/live/demo";
            mPushurl = "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4";*/
        }
        if (mChatUrl.equals("") || mChatUrl == null) {
            mChatUrl = "http://180.168.73.44:3000";
        }
        if (mChatRoom.equals("") || mChatRoom == null) {
            //房间号
            mChatRoom = "1";
        }
        if (mUserId.equals("") || mUserId == null) {
            //用户名
            mUserId = "test";
        }

        log.i("====", mPushurl + "--" + mChatUrl + "--" + mChatRoom + "--" + mUserId);

        cachedThreadPool = Executors.newCachedThreadPool();
        isFirstConnect = true;
        socketConnect();
        mTimer = new Timer(true);

        mAdapter = new MessageAdapter(this, mMessages);
        initView();
        initPlayer();
        initDanmu();
        log.i("systime5", System.currentTimeMillis() + "");
    }

    private void socketConnect() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    IO.Options opts = new IO.Options();
                    opts.reconnection = false;
                    mSocket = IO.socket(mChatUrl,opts);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mSocket.on("connect", onConnected);
                mSocket.on("message", onNewMessage);
                mSocket.on("disconnect", onDisconnect);
                mSocket.connect();
                JSONObject obj = new JSONObject();
                try {
                    obj.put("roomid", mChatRoom);
                    obj.put("username", mUserId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mSocket.emit("join", obj);
            }
        }.start();
    }

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            WatchLiveActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(WatchLiveActivity.this,
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    /*监听断开soket连接断开*/
    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            WatchLiveActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i("systime2",System.currentTimeMillis()+"");
                    socketReConnectTimer();
                    if (!isLastDisconnect) {
                        toastShow("聊天通道断开...");
                    }
                }
            });
        }
    };
    /*监听断开soket连接*/
    private Emitter.Listener onConnected = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            WatchLiveActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mTimerTask!=null){
                        mTimerTask.cancel();
                    }
                    if (!isFirstConnect) {
                        toastShow("聊天通道已连接...");
                    }
                    isFirstConnect = false;
                }
            });
        }
    };

    /*
  * 定时重连
  */
    public void socketReConnectTimer() {
        if (mTimer != null) {
            if (mTimerTask != null) {
                mTimerTask.cancel();  //将原任务从队列中移除
            }
            mTimerTask = new MyTimerTask();  // 新建一个任务*/
            mTimer.schedule(mTimerTask,0,5000);
        }
    }

    /*
     * 定时器任务
     */
    class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            handler.sendEmptyMessage(Constants.SOCKETRECONNECT);
        }

    }
    /*Handler处理消息*/
    Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == Constants.SOCKETRECONNECT) {

                Log.i("systime1",System.currentTimeMillis()+"");
                mSocket.disconnect();
                mSocket.off();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                socketConnect();
            }
        };
    };


    /*初始化控件*/
    private void initView() {
        mChatTxt = (TextView) findViewById(R.id.app_watch_chattxt);
        mChatTxt.setOnClickListener(this);
        mHostTxt = (TextView) findViewById(R.id.app_watch_hosttxt);
        mHostTxt.setOnClickListener(this);
        mMessagesendlayout = (LinearLayout) findViewById(R.id.app_watch_msg_send_layout);
        mZhubolayout = (RelativeLayout) findViewById(R.id.app_watch_zhubo_layout);
        mChatzhubomsg = (RelativeLayout) findViewById(R.id.app_watch_chat_zhubo_msg);
        mDanmakuView = (IDanmakuView) findViewById(R.id.app_watch_danmakuview);
        mMessagesView = (RecyclerView) findViewById(R.id.app_watch_msg_receive_layout);
        mMessagesView.setLayoutManager(new LinearLayoutManager(this));
        mMessagesView.setAdapter(mAdapter);
        mEditMsg = (EditText) findViewById(R.id.app_watch_editimg);
        mEditMsg.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    showPopFormBottom();
                }
            }
        });
        mEditMsg.setOnClickListener(this);
    }

    private void toastShow(String msg){
        LayoutInflater inflater = LayoutInflater
                .from(getApplicationContext());
        View view = inflater.inflate(R.layout.toast,
                (ViewGroup) findViewById(R.id.toast_layout_root));
        TextView textView = (TextView) view.findViewById(R.id.text);
        textView.setText(msg);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(view);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }
    /*创建播放器*/
    private void initPlayer() {
        mPlayer = new IjkPlayer(WatchLiveActivity.this);
        mPlayer.setScaleType(IjkPlayer.SCALETYPE_4_3);
        mPlayer.onComplete(new Runnable() {
            @Override
            public void run() {
                //播放结束
            }
        }).onInfo(new IjkPlayer.OnInfoListener() {
            @Override
            public void onInfo(int what, int extra) {
                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                        //开始缓冲
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                        //缓冲结束
                        break;
                    case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                        //download speed
                        break;
                    case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        //do something when video rendering
                        break;
                }
            }
        }).onError(new IjkPlayer.OnErrorListener() {
            @Override
            public void onError(int what, int extra) {
                //播放错误
            }
        });
        log.i("pushurl", mPushurl);
        mPlayer.play(mPushurl);
        mPlayer.setTitle(mPushurl);
        mPlayer.onDanmuClick(new IjkPlayer.DanmuClickListener() {
            @Override
            public void setOnclick(boolean isDanmuing) {
                if (isDanmuing) {
                    mDanmakuView.pause();
                } else {
                    mDanmakuView.resume();
                }
            }
        });
        mPlayer.onFullSendMsgClick(new IjkPlayer.FullSendMsgListener() {
            @Override
            public void setSendOnclick(String msg) {
                attemptSend(msg);
            }
        });
        mPlayer.onFullScreen(new IjkPlayer.FullListener() {
            @Override
            public void setFull() {
                if (sendMsgPopWin_small != null) {
                    hideSoftInput(sendMsgPopWin_small.mSmall_chattext.getWindowToken());
                    if (sendMsgPopWin_small.isShowing()) {
                        sendMsgPopWin_small.dismiss();
                        //log.i("sendMsgPopWin", "sendMsgPopWin456" + sendMsgPopWin_small.isShowing());
                    }
                }
            }
        });
    }

    /*初始化弹幕*/
    private void initDanmu() {
        mContext = DanmakuContext.create();
        mParser = createParser(this.getResources().openRawResource(R.raw.comments));
        mDanmakuView.setCallback(new master.flame.danmaku.controller.DrawHandler.Callback() {
            @Override
            public void updateTimer(DanmakuTimer timer) {
            }

            @Override
            public void drawingFinished() {
            }

            @Override
            public void danmakuShown(BaseDanmaku danmaku) {
            }

            @Override
            public void prepared() {
                mDanmakuView.start();
            }
        });

        mDanmakuView.prepare(mParser, mContext);
        mDanmakuView.enableDanmakuDrawingCache(true);
    }

    /*隐藏软键盘*/
    private void hideSoftInput(IBinder token) {
        log.i("fas", getWindow().getAttributes().softInputMode + "," + WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        if (token != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(this.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(token,
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /*将弹幕渲染到界面*/
    private void addDanmaku(boolean islive, String message, boolean isSelf) {
        log.i("adddanmu", "add");
        BaseDanmaku danmaku = mContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL);
        if (danmaku == null || mDanmakuView == null) {
            return;
        }
        danmaku.text = message;
        danmaku.padding = 5;
        danmaku.priority = 2;  // 可能会被各种过滤器过滤并隐藏显示
        danmaku.isLive = islive;
        danmaku.time = mDanmakuView.getCurrentTime() + 1500;
        danmaku.textSize = IjkPlayer.mDanmuTextSize * (mParser.getDisplayer().getDensity() - 0.6f);
        danmaku.textColor = (int) (Math.random() * (16777216) + (-16777216));
        log.i("hex", "" + (int) (Math.random() * (16777216) + (-16777216)));
        //透明度
        log.i("curDanmuTrans123", IjkPlayer.curDanmuTrans + "," + 1.2f* IjkPlayer.curDanmuSpeed + "-----" + 25f * (mParser.getDisplayer().getDensity() - 0.6f) + "===" + mParser.getDisplayer().getDensity());
        mContext.setDanmakuTransparency(IjkPlayer.curDanmuTrans);
        //速度
        mContext.setScrollSpeedFactor(1.2f* IjkPlayer.curDanmuSpeed);
        // danmaku.underlineColor = Color.GREEN;
        if (isSelf) {
            danmaku.borderColor = Color.WHITE;
        }
        mDanmakuView.addDanmaku(danmaku);
    }

    /*点击事件处理*/
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.app_watch_chattxt:
                chatVisibile();
                break;
            case R.id.app_watch_hosttxt:
                hostVisibile();
                break;
            case R.id.app_watch_editimg:
                log.i("chattext", "chattext");
                showPopFormBottom();
                break;
            case R.id.small_send:
                smallSend();
                break;

        }
    }

    /*显示聊天popuwindow*/
    public void showPopFormBottom() {
        log.i("win", "win");
        sendMsgPopWin_small = new SendMsgPopupWindow(WatchLiveActivity.this, this, true);
        sendMsgPopWin_small.showAtLocation(this.findViewById(R.id.main_layout), Gravity.CENTER_HORIZONTAL, 0, 0);
        sendMsgPopWin_small.onSendClick(new SendMsgPopupWindow.SendMsgListener() {
            @Override
            public void setSendOnclick() {
                smallSend();
            }
        });
    }

    /*发送消息*/
    private void smallSend() {
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                attemptSend(sendMsgPopWin_small.mSmall_chattext.getText().toString());
            }
        });
        hideSoftInput(sendMsgPopWin_small.mSmall_chattext.getWindowToken());
        sendMsgPopWin_small.dismiss();
    }

    /*发送消息*/
    private void attemptSend(String msg) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("mode","1");
            jsonObject.put("text",msg);
            jsonObject.put("stime","0");
            jsonObject.put("size","25");
            jsonObject.put("color","0");
            jsonObject.put("dur","10000");
            jsonObject.put("username",mUserId);
            jsonObject.put("roomid",mChatRoom);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        log.i("jsonobject", jsonObject.toString());
        mSocket.emit("message", jsonObject.toString());
        /*mSocket.emit("message", "{\"mode\":1,\"text\":\"" + msg + "\",\"stime\":0,\"size\":25,\"color\":0,\"dur\":10000,\"username\":\"" + mUserId + "\",\"roomid\":\"" + mChatRoom + "\"}");*/
        log.i("msg", msg);
    }

    /*显示软键盘*/
    private void openKeyboard() {

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);

            }
        }, 200);
    }


    /*初始化控件*/
    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            WatchLiveActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String username = "";
                    String message = "";
                    String color = "";
                    try {
                        JSONObject data = new JSONObject((String) args[0]);
                        username = data.getString("username");
                        if (username.equals(mUserId)) {
                            isSelf = true;
                        }else{
                            isSelf = false;
                        }
                        message = data.getString("text");
                        color = data.getString("color");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //log.i("json", username + "," + message+","+mUserId+","+isSelf);
                    addMessage(username, message, color);
                    addDanmaku(true, message, isSelf);

                }
            });
        }
    };

    /*将消息显示到界面*/
    private void addMessage(String username, String message, String color) {
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).messagecolor(color).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    /*recyleview消息定位到底部*/
    private void scrollToBottom() {
        Log.i("scroollbottom", (mAdapter.getItemCount() - 1) + "");
        mMessagesView.getLayoutManager().smoothScrollToPosition(mMessagesView, null, mAdapter.getItemCount() - 1);
        //mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }


    /*显示聊天布局*/
    private void chatVisibile() {
        mChatTxt.setTextColor(Color.RED);
        mHostTxt.setTextColor(Color.BLACK);
        mZhubolayout.setVisibility(View.GONE);
        mChatzhubomsg.setVisibility(View.VISIBLE);
        mMessagesendlayout.setVisibility(View.VISIBLE);
        mMessagesView.setVisibility(View.VISIBLE);
    }

    /*显示主播布局*/
    private void hostVisibile() {
        mHostTxt.setTextColor(Color.RED);
        mChatTxt.setTextColor(Color.BLACK);
        mZhubolayout.setVisibility(View.VISIBLE);
        mChatzhubomsg.setVisibility(View.GONE);
        mMessagesendlayout.setVisibility(View.GONE);
        mMessagesView.setVisibility(View.GONE);
    }

    /*创建弹幕对象*/
    private BaseDanmakuParser createParser(InputStream stream) {
        if (stream == null) {
            return new BaseDanmakuParser() {

                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }
        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);
        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            e.printStackTrace();
        }
        BaseDanmakuParser parser = new BiliDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;
    }


    /*onPause*/
    @Override
    protected void onPause() {
        super.onPause();
        if (mPlayer != null) {
            mPlayer.onPause();
        }
        unregisterHomeKeyReceiver(this);
    }

    /*onResume*/
    @Override
    protected void onResume() {
        super.onResume();
        mEditMsg.clearFocus();
        if (mPlayer != null) {
            mPlayer.onResume();
        }
        registerHomeKeyReceiver(this);
    }

    /*onDestroy*/
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDanmakuView != null) {
            mDanmakuView.release();
            mDanmakuView = null;
        }
        if (mPlayer != null) {
            new Thread(){
                @Override
                public void run() {
                    super.run();
                    mPlayer.onDestroy();
                }
            }.start();

        }
        isLastDisconnect = true;
        mSocket.disconnect();
        mSocket.off();
    }

    /*onConfigurationChanged*/
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mPlayer != null) {
            mPlayer.onConfigurationChanged(newConfig);
        }
    }

    /*点击返回按键*/
    @Override
    public void onBackPressed() {
        if (mPlayer != null && mPlayer.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    /*重写activityfinish*/
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.magnify_fade_in, R.anim.magnify_fade_out);
    }

    /*home按键监听*/
    private HomeWatcherReceiver mHomeKeyReceiver = null;
    private void registerHomeKeyReceiver(Context context) {
        log.i("registerHomeKeyReceiver", "registerHomeKeyReceiver");
        mHomeKeyReceiver = new HomeWatcherReceiver();
        final IntentFilter homeFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.registerReceiver(mHomeKeyReceiver, homeFilter);
    }

    private void unregisterHomeKeyReceiver(Context context) {
        log.i("unregister", "unregister");
        if (null != mHomeKeyReceiver) {
            context.unregisterReceiver(mHomeKeyReceiver);
        }
    }

    public class HomeWatcherReceiver extends BroadcastReceiver {
        private static final String log_TAG = "HomeReceiver";
        private static final String SYSTEM_DIAlog_REASON_KEY = "reason";
        private static final String SYSTEM_DIAlog_REASON_RECENT_APPS = "recentapps";
        private static final String SYSTEM_DIAlog_REASON_HOME_KEY = "homekey";
        private static final String SYSTEM_DIAlog_REASON_LOCK = "lock";
        private static final String SYSTEM_DIAlog_REASON_ASSIST = "assist";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log.i(log_TAG, "onReceive: action: " + action);
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                //android.intent.action.CLOSE_SYSTEM_DIAlogS
                String reason = intent.getStringExtra(SYSTEM_DIAlog_REASON_KEY);
                log.i(log_TAG, "reason: " + reason);

                if (SYSTEM_DIAlog_REASON_HOME_KEY.equals(reason)) {
                    // 短按Home键
                    log.i(log_TAG, "homekey");
                    if (sendMsgPopWin_small != null) {
                        log.i("onresume", "onresume" + sendMsgPopWin_small.isShowing());
                        hideSoftInput(sendMsgPopWin_small.mSmall_chattext.getWindowToken());
                        sendMsgPopWin_small.dismiss();
                    }

                } else if (SYSTEM_DIAlog_REASON_RECENT_APPS.equals(reason)) {
                    // 长按Home键 或者 activity切换键
                    //log.i(log_TAG, "long press home key or activity switch");

                } else if (SYSTEM_DIAlog_REASON_LOCK.equals(reason)) {
                    // 锁屏
                    //log.i(log_TAG, "lock");
                } else if (SYSTEM_DIAlog_REASON_ASSIST.equals(reason)) {
                    // samsung 长按Home键
                    //log.i(log_TAG, "assist");
                }

            }
        }
    }


    /*手机方向监听*/
    private class MyOrientationEventListener extends OrientationEventListener {

        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation == -1) {
                Log.i("ooooo", "手机平放" + orientation);
            } else if (orientation < 10 || orientation > 350) {

                if (isFirstStart){
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    isFirstStart = false;
                }
                if (mScreenState == ScreenState.LANDIN) {
                    Log.i("ooooo", "手机顶部向上" + orientation);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    mScreenState = ScreenState.TURNPOR;
                }
            } else if (orientation < 100 && orientation > 80) {

                if (isFirstStart) {
                    Log.i("ooooo", "手机左边向上" + orientation);
                    mScreenState = ScreenState.LANDIN;
                    isFirstStart = false;
                }

            } else if (orientation < 190 && orientation > 170) {
                Log.i("ooooo", "手机底边向上"+orientation);
            } else if (orientation < 280 && orientation > 260) {

                if (isFirstStart) {
                    Log.i("ooooo", "手机右边向上" + orientation);
                    mScreenState = ScreenState.LANDIN;
                    isFirstStart = false;
                }
            }
        }
    }
}
