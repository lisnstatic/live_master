package net.ossrs.yasea;

import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import net.ossrs.yasea.rtmp.RtmpPublisher;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import tcking.github.com.ijkplayer.Constants;
import tcking.github.com.giraffeplayer.example.LogUtil;
import tcking.github.com.giraffeplayer.example.MessageAdapter;
import tcking.github.com.giraffeplayer.example.R;

public class PushLiveActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback,View.OnClickListener {
    private static final String TAG = "Yasea";

    ImageButton btnPublish = null;
    ImageButton btnSwitch = null;
    private AudioRecord mic = null;
    private boolean aloop = false;
    private Thread aworker = null;
    private SurfaceView mCameraView = null;
    private Camera mCamera = null;
    private Camera.AutoFocusCallback myAutoFocusCallback = null;
    private int mPreviewRotation;
    private int mCamId = Camera.getNumberOfCameras() - 1; // default camera
    private byte[] mYuvFrameBuffer;
    private String mNotifyMsg;
    private long TIME = 3000;

    private SrsFlvMuxer flvMuxer = new SrsFlvMuxer(new RtmpPublisher.EventHandler() {
        @Override
        public void onRtmpConnecting(String msg) {
            rtmpConnect = Contanst.RtmpConnect.CONNECTTING;
        }

        @Override
        public void onRtmpConnected(String msg) {
            rtmpConnect = Contanst.RtmpConnect.CONNECTED;
            handler.sendEmptyMessage(2);
        }
        @Override
        public void onRtmpVideoStreaming(String msg) {
        }

        @Override
        public void onRtmpAudioStreaming(String msg) {
        }
        @Override
        public void onRtmpStopped(String msg) {
            rtmpConnect = Contanst.RtmpConnect.STOPPED;
        }

        @Override
        public void onRtmpDisconnected(String msg) {
            rtmpConnect = Contanst.RtmpConnect.DISCONNECTED;
            handler.sendEmptyMessage(2);
            log.i("rtmp", rtmpConnect + "");
        }
        @Override
        public void onRtmpOutputFps(final double fps) {
            log.i(TAG, String.format("Output Fps: %f", fps));
        }
    });

    private SrsMp4Muxer mp4Muxer = new SrsMp4Muxer(new SrsMp4Muxer.EventHandler() {
        @Override
        public void onRecordPause(String msg) {
        }

        @Override
        public void onRecordResume(String msg) {

        }

        @Override
        public void onRecordStarted(String msg) {
        }

        @Override
        public void onRecordFinished(String msg) {
        }
    });

    private SrsEncoder mEncoder = new SrsEncoder(flvMuxer, mp4Muxer);
    private float previewRate = -1;
    private ImageButton btnBack;
    private Socket mSocket;
    private String mChatUrl;
    private String mChatId;
    private List<tcking.github.com.giraffeplayer.example.Message> mMessages = new ArrayList<tcking.github.com.giraffeplayer.example.Message>();
    private RecyclerView.Adapter mAdapter;
    private RecyclerView mMessagesView;
    private TextView txtState;
    private float mRotation;
    private boolean isStartCamera = false;
    private Contanst.RtmpConnect rtmpConnect;
    private String rtmpUrl = "";
    private int mWidth;
    private int mHeight;
    private LogUtil log;
    private LinearLayoutManager linearLayoutManager;
    private ExecutorService cachedThreadPool;
    private ReentrantLock lock;
    private Contanst.SocketConnect socketConnect;
    private Timer mTimer;
    private MyTimerTask mTimerTask;
    private CameraHelper mCameraHelper;
    private int mCurrentCameraId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持播放器界面长亮状态
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_push_living);
        mCameraHelper = new CameraHelper(this);
        log = LogUtil.getInstance();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        log.i("systime--", System.currentTimeMillis() + "");
        rtmpConnect = Contanst.RtmpConnect.DISCONNECTED;
        previewRate = DisplayUtil.getScreenRate(this);
        WindowManager manager = this.getWindowManager();
        DisplayMetrics outMetrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(outMetrics);
        mWidth = outMetrics.widthPixels;
        mHeight = outMetrics.heightPixels;
        mAdapter = new MessageAdapter(this,mMessages);
        rtmpUrl = getIntent().getStringExtra("pushUrl");
        if (rtmpUrl == null || rtmpUrl.equals("")){
            //73.16是可用的
            rtmpUrl = "rtmp://video-center.alivecdn.com/beijing/beijing_1?vhost=t1.livecdn.yicai.com";
            /*rtmpUrl = "rtmp://video-center.alivecdn.com/app/stream?vhost=t1.livecdn.yicai.com";*/
            /*rtmpUrl = "rtmp://video-center.alivecdn.com/alicdn/c1?vhost=t1.livecache.yicai.com";*/
            /* rtmpUrl = "rtmp://180.168.73.16/live/c1";*/
            /*rtmpUrl = "rtmp://ossrs.net:1935/live/demo";*/
        }
        log.i("====",rtmpUrl);
        mChatUrl = "http://180.168.73.44:3000";
        mChatId = "1";

        cachedThreadPool = Executors.newCachedThreadPool();
        socketConnect();
        mTimer = new Timer(true);

        initViews();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                mNotifyMsg = ex.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //Toast.makeText(getApplicationContext(), mNotifyMsg+"1233333333", Toast.LENGTH_LONG).show();
                        stopEncoder();
                        flvMuxer.stop();
                        mp4Muxer.stop();
                    }
                });
            }
        });
        log.i("systime===", System.currentTimeMillis() + "");
    }

    private void socketConnect() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    mSocket = IO.socket(mChatUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mSocket.on("connect", onConnected);
                mSocket.on("message", onNewMessage);
                mSocket.on("disconnect", onDisconnect);
                mSocket.connect();
                JSONObject obj = new JSONObject();
                try {
                    obj.put("roomid", mChatId);
                    obj.put("username", "");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mSocket.emit("join", obj);
            }
        }.start();
    }


    /*监听断开soket连接断开*/
    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            PushLiveActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i("systime2", System.currentTimeMillis() + "");
                    log.i("infoconnect000", ""+socketConnect);
                    socketReConnectTimer();
                }
            });
        }
    };
    /*监听断开soket连接*/
    private Emitter.Listener onConnected = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            PushLiveActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log.i("infoconnect111", ""+socketConnect);
                    if (mTimerTask!=null){
                        mTimerTask.cancel();
                    }
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


    /*onStart*/
    @Override
    protected void onStart() {
        super.onStart();
        log.i("systime.....", System.currentTimeMillis() + "");
    }

    /*初始化控件*/
    private void initViews() {
        btnPublish = (ImageButton) findViewById(R.id.publish);
        btnSwitch = (ImageButton) findViewById(R.id.swCam);
        btnBack = (ImageButton) findViewById(R.id.back);
        txtState = (TextView) findViewById(R.id.state);
        mCameraView = (SurfaceView) findViewById(R.id.preview);
        mMessagesView = (RecyclerView) findViewById(R.id.push_messages_receive_layout);
        linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);
        mMessagesView.setLayoutManager(linearLayoutManager);
        mMessagesView.setAdapter(mAdapter);
        mCameraView.getHolder().addCallback(this);
        btnPublish.setOnClickListener(this);
        btnSwitch.setOnClickListener(this);
        btnBack.setOnClickListener(this);
    }

    /*Handler处理消息*/
    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 2){
                btnPublish.setEnabled(true);
                if (rtmpConnect == Contanst.RtmpConnect.CONNECTED){
                    txtState.setText("直播中");
                    txtState.setBackgroundResource(R.drawable.text_view_border_red);
                }else if (rtmpConnect == Contanst.RtmpConnect.DISCONNECTED){
                    txtState.setText("未连接");
                    txtState.setBackgroundResource(R.drawable.text_view_border_green);
                }
            }if (msg.what == Constants.SOCKETRECONNECT) {

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

    /*开始预览*/
    private void startCamera() {
        if (mCamera != null) {
            log.i("mCamera", "start camera, already started. return");
            return;
        }
        if (mCamId > (Camera.getNumberOfCameras() - 1) || mCamId < 0) {
            log.e(TAG, "####### start camera failed, inviald params, camera No.=" + mCamId);
            return;
        }
        mCamera = Camera.open(mCamId);
        //mCamera = getCameraInstance(mCamId);
        initCamera();
        int degrees = mCameraHelper.getDisplayOritation(mCameraHelper.getDispalyRotation(this), mCamId);
        mCamera.setDisplayOrientation(degrees);
        //mCamera.setDisplayOrientation(mPreviewRotation);
        mCamera.addCallbackBuffer(mYuvFrameBuffer);
        mCamera.setPreviewCallbackWithBuffer(this);
        try {
            mCamera.setPreviewDisplay(mCameraView.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
        //mCamera.autoFocus(myAutoFocusCallback);
        isStartCamera = true;
        btnSwitch.setEnabled(true);
        //log.i("sys1----", System.currentTimeMillis() + "");
    }

    /*停止预览*/
    private void stopCamera() {
        if (mCamera != null) {
            btnSwitch.setEnabled(false);
            //log.i("sys----",System.currentTimeMillis()+"");
            // need to SET NULL CB before stop preview!!!
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            isStartCamera = false;
        }
    }

    /*点击事件监听处理*/
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.publish:
                //log.i("rtmpUrl", rtmpUrl+","+rtmpConnect+",isStartCamera="+isStartCamera+"---"+System.currentTimeMillis());
                if (rtmpConnect == Contanst.RtmpConnect.DISCONNECTED) {
                    btnPublish.setBackgroundResource(R.drawable.app_push_pause);
                    btnPublish.setEnabled(false);
                    try {
                        flvMuxer.start(rtmpUrl);
                    } catch (IOException e) {
                        //log.e(TAG, "start FLV muxer failed.");
                        e.printStackTrace();
                        return;
                    }
                    flvMuxer.setVideoResolution(mEncoder.VCROP_WIDTH, mEncoder.VCROP_HEIGHT);
                    if (!isStartCamera){
                        startCamera();
                    }

                    new Thread(){
                        @Override
                        public void run() {
                            super.run();
                            startEncoder();
                        }
                    }.start();

                } else if (rtmpConnect == Contanst.RtmpConnect.CONNECTED){
                    rtmpConnect = Contanst.RtmpConnect.DISCONNECTTING;
                    btnPublish.setBackgroundResource(R.drawable.app_enter_towatch);
                    btnPublish.setEnabled(false);
                    //log.i("rtmptime", System.currentTimeMillis() + "");
                    new Thread(){
                        @Override
                        public void run() {
                            super.run();
                            stopEncoder();
                            flvMuxer.stop();
                            mp4Muxer.stop();
                        }
                    }.start();
                }
                break;
            case R.id.swCam:
                if (mCamera != null && mEncoder != null) {
                    mCamId = (mCamId + 1) % Camera.getNumberOfCameras();
                    //mCurrentCameraId = (mCurrentCameraId + 1) % mCameraHelper.getNumberOfCameras();
                    stopCamera();
                    mEncoder.swithCameraFace();
                    startCamera();
                }
                break;
            case R.id.back:
                finish();
                break;
        }
    }


    /*从摄像头取数据*/
    @Override
    public void onPreviewFrame(byte[] data, Camera c) {
        //log.i("yuv","yuv");
        onGetYuvFrame(data);
        c.addCallbackBuffer(mYuvFrameBuffer);
    }

    /*摄像头数据传递给srsencoder做处理*/
    private void onGetYuvFrame(byte[] data) {
        mEncoder.onGetYuvFrame(data);
    }

    /*从话筒取声音*/
    private void onGetPcmFrame(byte[] pcmBuffer, int size) {
        mEncoder.onGetPcmFrame(pcmBuffer, size);
    }

    /*开始Audio*/
    private void startAudio() {
        if (mic != null) {
            return;
        }

        int bufferSize = 2 * AudioRecord.getMinBufferSize(SrsEncoder.ASAMPLERATE, SrsEncoder.ACHANNEL, SrsEncoder.AFORMAT);
        mic = new AudioRecord(MediaRecorder.AudioSource.MIC, SrsEncoder.ASAMPLERATE, SrsEncoder.ACHANNEL, SrsEncoder.AFORMAT, bufferSize);
        mic.startRecording();

        final byte pcmBuffer[] = new byte[4096];
        while (aloop && !Thread.interrupted()) {
            int size = mic.read(pcmBuffer, 0, pcmBuffer.length);
            if (size <= 0) {
                log.i(TAG, "***** audio ignored, no data to read.");
                break;
            }
            onGetPcmFrame(pcmBuffer, size);
        }
    }

    /*停止Audio*/
    private void stopAudio() {
        aloop = false;
        if (aworker != null) {
            log.i(TAG, "stop audio worker thread");
            aworker.interrupt();
            try {
                aworker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                aworker.interrupt();
            }
            aworker = null;
        }

        if (mic != null) {
            mic.setRecordPositionUpdateListener(null);
            mic.stop();
            mic.release();
            mic = null;
        }
    }

    /*开始推流*/
    private void startEncoder() {
        log.i("starten","starten");
        int ret = mEncoder.start();
        if (ret < 0) {
            return;
        }
        aworker = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                startAudio();
            }
        });
        aloop = true;
        aworker.start();
    }

    /*停止推流*/
    private void stopEncoder() {
        stopAudio();
        stopCamera();
        mEncoder.stop();
    }

    /*找到合适预览的帧率*/
    private static int[] findClosestFpsRange(int expectedFps, List<int[]> fpsRanges) {
        expectedFps *= 1000;
        int[] closestRange = fpsRanges.get(0);
        int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
        for (int[] range : fpsRanges) {
            if (range[0] <= expectedFps && range[1] >= expectedFps) {
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) {
                    closestRange = range;
                    measure = curMeasure;
                }
            }
        }
        return closestRange;
    }

    /*surfaceChanged*/
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }
    /*相机参数的初始化设置*/
    private void initCamera()
    {
        Camera.Parameters params = mCamera.getParameters();

        // TODO adjust by getting supportedPreviewSizes and then choosing
        // the best one for screen size (best fill screen)
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes != null) {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }
        }

        /*CamParaUtil.getInstance().printSupportPreviewSize(params);
        Camera.Size previewSize = CamParaUtil.getInstance().getPreviewSize(
                params.getSupportedPreviewSizes(), 720, previewRate);
        log.i("preview", previewSize.width + "," + previewSize.height);*/
        /*Camera.Size size = mCamera.new Size(mHeight, mWidth);
        mCameraHelper.selectCameraPreviewWH(params,size);*/
       /* SrsEncoder.VWIDTH = previewSize.width;
        SrsEncoder.VHEIGHT = previewSize.height;*/
        mYuvFrameBuffer = new byte[SrsEncoder.VWIDTH * SrsEncoder.VHEIGHT * 3 / 2];
        //params.setPreviewSize(SrsEncoder.VWIDTH, SrsEncoder.VHEIGHT);
        log.i("preview", SrsEncoder.VWIDTH + "," + SrsEncoder.VHEIGHT);
        int[] range = findClosestFpsRange(SrsEncoder.VFPS, params.getSupportedPreviewFpsRange());
        params.setPreviewFpsRange(range[0], range[1]);
        params.setPreviewFormat(SrsEncoder.VFORMAT);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        mCamera.setParameters(params);
    }


    /*surfaceCreated*/
    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        log.i("surfaceCreated", "surfaceCreated");
        startCamera();
    }

    /*surfaceDestroyed*/
    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        log.i("surfaceDestroyed", "surfaceDestroyed");
        stopCamera();
    }

    /*onResume*/
    @Override
    protected void onResume() {
        super.onResume();
        mp4Muxer.resume();
    }

    /*onPause*/
    @Override
    protected void onPause() {
        super.onPause();
        mp4Muxer.pause();
    }


    /*onDestroy*/
    @Override
    protected void onDestroy() {
        super.onDestroy();
        log.i("onDestroy", "onDestroy" + rtmpConnect);
        if (rtmpConnect == Contanst.RtmpConnect.CONNECTED) {
            isStartCamera = false;
            new Thread(){
                @Override
                public void run() {
                    super.run();
                    stopEncoder();
                    flvMuxer.stop();
                    mp4Muxer.stop();
                }
            }.start();
        }
        log.i("isStartCamera", isStartCamera + "");
        if (isStartCamera){
            stopCamera();
        }
        mSocket.disconnect();
        mSocket.off();
    }

    /*聊天消息回调*/
    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            PushLiveActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String username = "";
                    String message = "";
                    String color = "";
                    log.i("getjson1", "" + args[0]);
                    try {
                        JSONObject data = new JSONObject((String)args[0]);
                        username = data.getString("username");
                        message = data.getString("text");
                        color = data.getString("color");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    addMessage(username, message, color);
                    log.i("json1", username + "," + message);

                }
            });
        }
    };

    /*聊天消息显示*/
    private void addMessage(String username, String message, String color) {
        mMessages.add(new tcking.github.com.giraffeplayer.example.Message.Builder(tcking.github.com.giraffeplayer.example.Message.TYPE_MESSAGE)
                .username(username).message(message).messagecolor(color).build());
        log.i("us123", username + "," + message + "," + mMessages.size());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        mMessagesView.getLayoutManager().scrollToPosition(mAdapter.getItemCount()-1);
        linearLayoutManager.setStackFromEnd(true);
    }


    /** A safe way to get an instance of the Camera object. */
    private Camera getCameraInstance(final int id) {
        Camera c = null;
        try {
            c = mCameraHelper.openCamera(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c;
    }






}
