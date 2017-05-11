package tcking.github.com.giraffeplayer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * Created by tcking on 15/10/27.
 */
public class GiraffePlayer{
    /**
     * 可能会剪裁,保持原视频的大小，显示在中心,当原视频的大小超过view的大小超过部分裁剪处理
     */
    public static final String SCALETYPE_FITPARENT="fitParent";
    /**
     * 可能会剪裁,等比例放大视频，直到填满View为止,超过View的部分作裁剪处理
     */
    public static final String SCALETYPE_FILLPARENT="fillParent";
    /**
     * 将视频的内容完整居中显示，如果视频大于view,则按比例缩视频直到完全显示在view中
     */
    public static final String SCALETYPE_WRAPCONTENT="wrapContent";
    /**
     * 不剪裁,非等比例拉伸画面填满整个View
     */
    public static final String SCALETYPE_FITXY="fitXY";
    /**
     * 不剪裁,非等比例拉伸画面到16:9,并完全显示在View中
     */
    public static final String SCALETYPE_16_9="16:9";
    /**
     * 不剪裁,非等比例拉伸画面到4:3,并完全显示在View中
     */
    public static final String SCALETYPE_4_3="4:3";

    private static final int MESSAGE_SHOW_PROGRESS = 1;
    private static final int MESSAGE_FADE_OUT = 2;
    private static final int MESSAGE_SEEK_NEW_POSITION = 3;
    private static final int MESSAGE_HIDE_BOX = 4;
    private static final int MESSAGE_SHOW_BOX = 6;
    private static final int MESSAGE_RESTART_PLAY = 5;
    private  Activity activity;
    private  IjkVideoView videoView;
    private  AudioManager audioManager;
    private  int mMaxVolume;
    private  SeekBar mLightbar;
    private  SeekBar mVoicebar;
    private  float mBrightness;
    private  VoiceThread voiceThread;
    private  SeekBar mDanmuTransbar;
    public static float curDanmuTrans = 0.3f;
    private  SeekBar mDanmuSpeedbar;
    public static float curDanmuSpeed = 0.2f;
    private LogUtil log;
    private RelativeLayout mTopRightlayout;
    private ArrayList<View> mViewListSide;
    private ArrayList<View> mViewListSmall;
    private ArrayList<View> mViewListFull;
    private LinearLayout mRightlayout;
    private LinearLayout mLeftlayout;
    private RelativeLayout mBottomlayout;
    private RelativeLayout mToplayout;
    private int mHeightPixels;
    private int mWidthPixels;
    private boolean playerSupport;
    private String url;
    private Query $;
    private int STATUS_ERROR=-1;
    private int STATUS_IDLE=0;
    private int STATUS_LOADING=1;
    private int STATUS_PLAYING=2;
    private int STATUS_PAUSE=3;
    private int STATUS_COMPLETED=4;
    private long pauseTime;
    private int status=STATUS_IDLE;
    public static OrientationEventListener orientationEventListener;
    final private int initHeight;
    private int defaultTimeout=5000;
    private boolean isFull = false;
    private boolean mIsDanmuing = true;
    public static float mDanmuTextSize = 24f;
    public static boolean mIsLock = false;
    private SendMsgPopupWindow sendMsgPopWin;
    private AlphaAnimation mHideAnimationFull;
    private AlphaAnimation mShowAnimationFull;
    private int mHideDuration = 1000;
    private int mShowDuration = 500;
    private boolean isShowing = true;
    private boolean portrait;
    private long newPosition = -1;
    private long defaultRetryTime=5000;
    private boolean isSettingShow = false;
    private boolean isRotating = false;


    /*初始化GiraffePlayer*/
    public GiraffePlayer(final Activity activity) {
        log = LogUtil.getInstance();
        /*通过sharepreference得到屏幕参数*/
        SharedPreferences sharedPreferences= activity.getSharedPreferences("screeninfo",
                Activity.MODE_PRIVATE);
        mWidthPixels =sharedPreferences.getInt("widthPixels", 0);
        mHeightPixels =sharedPreferences.getInt("heightPixels",0);
        try {
            IjkMediaPlayer.loadLibrariesOnce(null);
            IjkMediaPlayer.native_profileBegin("libijkplayer.so");
            playerSupport=true;
        } catch (Throwable e) {
            log.e("GiraffePlayer", "loadLibraries error"+e);
        }
        this.activity=activity;
        $=new Query(activity);

        /*初始化控件*/
        initViews();

         /*声音*/
        voiceThread = new VoiceThread();
        voiceThread.start();

        /*初始化屏幕监听*/
        orientationEventListener = new OrientationEventListener(activity) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation >= 0 && orientation <= 30 || orientation >= 330 || (orientation >= 150 && orientation <= 210)) {
                    //竖屏
                    if (portrait) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                } else if ((orientation >= 90 && orientation <= 120) || (orientation >= 240 && orientation <= 300)) {
                    if (!portrait) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                        orientationEventListener.disable();
                    }
                }
            }
        };
        portrait=true;
        initHeight=activity.findViewById(R.id.app_video_box).getLayoutParams().height;
        if (!playerSupport) {
            showStatus(activity.getResources().getString(R.string.not_support));
        }

        /*初始化隐藏显示控件列表*/
        initViewList();
    }

    private void initViews(){
        videoView = (IjkVideoView) activity.findViewById(R.id.video_view);
        videoView.onSurfaceTouched(new IjkVideoView.TouchListener() {
            @Override
            public void setSingleTapUp() {
                showSettingControl(false);
                if (isShowing) {
                    log.i("isfu1235","isfu1235");
                    hide(mHideDuration);
                } else {
                    show(defaultTimeout,true);
                }
            }

            @Override
            public void setDoubleTapUp() {
                //doPauseResume();
                //show(defaultTimeout,false);
            }
        });
        videoView.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer mp) {
                statusChange(STATUS_COMPLETED);
                oncomplete.run();
            }
        });
        videoView.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mp, int what, int extra) {
                statusChange(STATUS_ERROR);
                onErrorListener.onError(what, extra);
                return true;
            }
        });
        videoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {
                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                        statusChange(STATUS_LOADING);
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                        statusChange(STATUS_PLAYING);
                        break;
                    case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                        //显示 下载速度
                        //Toaster.show("download rate:" + extra);
                        break;
                    case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                        statusChange(STATUS_PLAYING);
                        break;
                }
                onInfoListener.onInfo(what, extra);
                return false;
            }
        });
        mVoicebar = (SeekBar) activity.findViewById(R.id.app_video_voiceSeekBar);
        mVoicebar.setOnSeekBarChangeListener(mSeekListener);
        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mVoicebar.setMax(mMaxVolume);
        mVoicebar.setProgress(currentVolume);

        mToplayout = (RelativeLayout) activity.findViewById(R.id.app_video_top_box);
        mToplayout.setAlpha(0.6f);
        mToplayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        mBottomlayout = (RelativeLayout) activity.findViewById(R.id.app_video_bottom_box);
        mBottomlayout.setAlpha(0.6f);
        mBottomlayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        mLeftlayout = (LinearLayout) activity.findViewById(R.id.app_video_center_box_left);
        mRightlayout = (LinearLayout) activity.findViewById(R.id.app_video_center_box_right);
        mTopRightlayout = (RelativeLayout) activity.findViewById(R.id.app_video_top_box_right);
        LinearLayout settinglayout = (LinearLayout) activity.findViewById(R.id.app_video_settinglayout);
        settinglayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        $.id(R.id.app_video_play).clicked(onClickListener);
        $.id(R.id.app_video_fullscreen).clicked(onClickListener);
        $.id(R.id.app_video_finish).clicked(onClickListener);
        $.id(R.id.app_video_replay_icon).clicked(onClickListener);
        $.id(R.id.app_video_danmu).clicked(onClickListener);
        $.id(R.id.app_video_setting).clicked(onClickListener);
        $.id(R.id.app_video_plussize).clicked(onClickListener);
        $.id(R.id.app_video_decize).clicked(onClickListener);
        $.id(R.id.app_video_center_box_left).clicked(onClickListener);
        $.id(R.id.app_video_chat).clicked(onClickListener);

        /*亮度*/
        mLightbar =  (SeekBar) activity.findViewById(R.id.detailplayer_lightbar);
        mLightbar.setOnSeekBarChangeListener(mSeekListener);
        int screenBrightness = 0;
        try {
            screenBrightness = Settings.System.getInt(activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        mBrightness = screenBrightness / 255.0F;
        int tempBright = (int) (mBrightness * 100);
        $.id(R.id.app_video_textpercent).text(tempBright + "%");
        mLightbar.setProgress(tempBright);


        /*弹幕透明度*/
        mDanmuTransbar =  (SeekBar) activity.findViewById(R.id.detailplayer_danmutransbar);
        mDanmuTransbar.setOnSeekBarChangeListener(mSeekListener);
        mDanmuTransbar.setMax(100);
        mDanmuTransbar.setProgress(30);
        $.id(R.id.app_video_textdanmutrans).text(30 + "%");


        /*弹幕速度*/
        mDanmuSpeedbar =  (SeekBar) activity.findViewById(R.id.detailplayer_danmuspeedbar);
        mDanmuSpeedbar.setOnSeekBarChangeListener(mSeekListener);
        mDanmuSpeedbar.setMax(30);
        mDanmuSpeedbar.setProgress(5);
        $.id(R.id.app_video_textdanmuspeed).text("慢");

    }

    private void initViewList() {
        mViewListFull = new ArrayList<View>();
        mViewListFull.add(mToplayout);
        mViewListFull.add(mBottomlayout);
        mViewListFull.add(mLeftlayout);
        mViewListFull.add(mRightlayout);
        mViewListSmall = new ArrayList<View>();
        mViewListSmall.add(mToplayout);
        mViewListSmall.add(mBottomlayout);
        mViewListSide = new ArrayList<View>();
        mViewListSide.add(mLeftlayout);
        mViewListSide.add(mRightlayout);
        mViewListSide.add(mTopRightlayout);
        log.i("isfu1234","isfu1234");
        hide(1);
    }

    //弹幕暂停播放接口
    public interface DanmuClickListener{
        public void setOnclick(boolean isDanmuing);
    }
    DanmuClickListener danmuListener;
    public void onDanmuClick(DanmuClickListener danmuListener){
        this.danmuListener = danmuListener;
    }

    //全屏发送弹幕接口
    public interface FullSendMsgListener{
        public void setSendOnclick(String msg);
    }
    FullSendMsgListener fullSendMsgListener;
    public void onFullSendMsgClick(FullSendMsgListener fullSendMsgListener){
        this.fullSendMsgListener = fullSendMsgListener;
    }

    /*全屏旋转接口*/
    public interface FullListener{
        public void setFull();
    }
    FullListener fullListener;
    public void onFullScreen(FullListener fullListener){
        this.fullListener = fullListener;
    }

    /*点击事件监听处理*/
    private  View.OnClickListener onClickListener=new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            log.i("id",v.getId()+"");
            if (v.getId() == R.id.app_video_fullscreen) {
                log.i("danmu12", "danmu1");
                toggleFullScreen();
            } else if (v.getId() == R.id.app_video_play) {
                //doPauseResume();
                //show(defaultTimeout,false);
            }else if (v.getId() == R.id.app_video_replay_icon) {
                videoView.seekTo(0);
                videoView.start();
                doPauseResume();
            } else if (v.getId() == R.id.app_video_finish) {
                if (!portrait) {
                    toggleFullScreen();
                    isFull = false;
                } else {
                    activity.finish();
                }
            }else if (v.getId() == R.id.app_video_danmu) {
                danmuListener.setOnclick(mIsDanmuing);
                showDanmuControl(mIsDanmuing);
            } else if (v.getId() == R.id.app_video_setting) {
                log.i("max",Integer.MAX_VALUE+"");
                showSettingControl(true);
                show(Integer.MAX_VALUE,false);
            } else if (v.getId() == R.id.app_video_plussize) {
                mDanmuTextSize += 5f;
                if (mDanmuTextSize>60f)
                    mDanmuTextSize = 60f;
                log.i("mDanmuTextSize1", mDanmuTextSize+"");
            } else if (v.getId() == R.id.app_video_decize) {
                mDanmuTextSize -= 5f;
                if (mDanmuTextSize<10f)
                    mDanmuTextSize = 10f;
                log.i("mDanmuTextSize2", mDanmuTextSize + "");
            } else if (v.getId() == R.id.app_video_center_box_left) {
                log.i("onchange123",mIsLock+"");
                if (isFull) {
                    if (mIsLock) {
                        $.id(R.id.app_video_lock).image(R.drawable.app_watch_unlock);
                        mIsLock = false;
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                    } else {
                        $.id(R.id.app_video_lock).image(R.drawable.app_watch_lock);
                        mIsLock = true;
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                    }
                }
            }else if (v.getId() == R.id.app_video_chat){
                log.i("click", "click");
                if (isFull){
                    showPopFormBottom();
                }
            }else if(v.getId() == R.id.full_send){
                log.i("fasong","fa");
                HideSoftInput(SendMsgPopupWindow.mFull_chattext.getWindowToken());
                fullSendMsgListener.setSendOnclick(SendMsgPopupWindow.mFull_chattext.getText().toString());
                sendMsgPopWin.dismiss();
            }
        }
    };

    /*弹出键盘popuwindow*/
    public void showPopFormBottom() {
        log.i("win","win");
        sendMsgPopWin = new SendMsgPopupWindow(activity,onClickListener,false);
        sendMsgPopWin.showAtLocation(activity.findViewById(R.id.app_video_box), Gravity.LEFT, 0, 0);
        openKeyboard();
    }
    private void openKeyboard() {

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);

            }
        }, 200);
    }

    /*隐藏软键盘*/
    private void HideSoftInput(IBinder token) {
        if (token != null) {
            InputMethodManager manager = (InputMethodManager) activity.getSystemService(activity.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(token,
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    /*暂停播放控制方法*/
    private void doPauseResume() {
        if (status==STATUS_COMPLETED) {
            $.id(R.id.app_video_replay).gone();
            videoView.seekTo(0);
            videoView.start();
            mIsDanmuing = true;
            danmuListener.setOnclick(mIsDanmuing);
        } else if (videoView.isPlaying()) {
            statusChange(STATUS_PAUSE);
            videoView.pause();
            mIsDanmuing = false;
            danmuListener.setOnclick(mIsDanmuing);
        } else {
            videoView.start();
        }
        updatePausePlay();
    }

    /*改变播放暂停图标*/
    private void updatePausePlay() {
        if (videoView.isPlaying()) {
            $.id(R.id.app_video_play).image(R.drawable.app_watch_play_full);
        } else {
            $.id(R.id.app_video_play).image(R.drawable.app_watch_pause_full);
        }
    }


    /*弹幕的播放与暂停*/
    private void showDanmuControl(boolean isDanmuing) {
        if (isDanmuing) {
            $.id(R.id.app_video_danmu).image(R.drawable.app_watch_pausedanmu);
            mIsDanmuing = false;
        }else{
            $.id(R.id.app_video_danmu).image(R.drawable.app_watch_danmu);
            mIsDanmuing = true;
        }
    }

    /*seek改变监听*/
    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        int mProgress;
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mProgress = progress;
            if (seekBar.getId()==R.id.app_video_voiceSeekBar){

                onVolumeSlide((float)progress/(float)seekBar.getMax());
                mVoicebar.setProgress(progress);

            } else if (seekBar.getId()==R.id.detailplayer_lightbar){

                onBrightnessSlide((float)progress/(float)seekBar.getMax());

            }else if (seekBar.getId()==R.id.detailplayer_danmutransbar){

                curDanmuTrans = (100-progress)/100f;
                mDanmuTransbar.setProgress(progress);
                $.id(R.id.app_video_textdanmutrans).text(progress + "%");
                log.i("pro123", curDanmuTrans + "");

            }else if(seekBar.getId()==R.id.detailplayer_danmuspeedbar){

                mDanmuSpeedbar.setProgress(progress);
                if (progress>=0&&progress<10){
                    $.id(R.id.app_video_textdanmuspeed).text("慢");
                }else if (progress>=10&&progress<20){
                    $.id(R.id.app_video_textdanmuspeed).text("中");
                }else if (progress>=20&&progress<=30){
                    $.id(R.id.app_video_textdanmuspeed).text("快");
                }
                curDanmuSpeed = (100-progress)/100f;
                if (curDanmuSpeed>1){
                    curDanmuSpeed = 1f;
                }
                if (curDanmuSpeed<0.7){
                    curDanmuSpeed = 0.7f;
                }
                log.i("curDanmuSpeed123", curDanmuSpeed + "");
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (seekBar.getId()==R.id.app_video_voiceSeekBar){
                show(Integer.MAX_VALUE,false);
            }

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            log.i("mProgress",mProgress+"");
            if (seekBar.getId()==R.id.detailplayer_lightbar){

                mLightbar.setProgress(mProgress);

            }
            if (seekBar.getId()==R.id.app_video_voiceSeekBar){
                show(defaultTimeout,false);
            }
        }
    };


    /*handler处理消息*/
    @SuppressWarnings("HandlerLeak")
    private Handler handler=new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_FADE_OUT:
                    log.i("isfu123", "isfu123");
                    if (isShowing){
                        hide(1);
                    }
                    break;
                case MESSAGE_HIDE_BOX:
                    $.id(R.id.app_video_center_box_right).gone();
                    $.id(R.id.app_video_center_box_left).gone();
                    $.id(R.id.app_video_top_box_right).gone();
                    show(defaultTimeout,true);
                    break;
                case MESSAGE_SHOW_BOX:
                    show(defaultTimeout,true);
                    break;
                case MESSAGE_SEEK_NEW_POSITION:
                    break;
                case MESSAGE_RESTART_PLAY:
                    play(url);
                    break;
            }
        }
    };


    // 显示控件动画
    public void setShowAnimation(ArrayList<View> views,int duration) {

        for (int i = 0; i < views.size(); i++){
            if (null == views.get(i) || duration < 0) {
                return;
            }
        }
        if (null != mShowAnimationFull) {
            mShowAnimationFull.cancel();
            mShowAnimationFull = null;
        }
        mShowAnimationFull = new AlphaAnimation(0.0f, 1.0f);
        mShowAnimationFull.setDuration(duration);
        mShowAnimationFull.setFillAfter(true);
        mShowAnimationFull.setFillBefore(false);
        for (int j = 0; j < views.size();j++){
            views.get(j).startAnimation(mShowAnimationFull);
        }
    }

    //隐藏控件动画
    public void setHideAnimation(ArrayList<View> views, int duration) {
        for (int i = 0; i < views.size(); i++){
            if (null == views.get(i) || duration < 0) {
                return;
            }
        }
        if (null != mHideAnimationFull) {
            mHideAnimationFull.cancel();
            mHideAnimationFull = null;
        }
        mHideAnimationFull = new AlphaAnimation(1.0f, 0.0f);
        /*TranslateAnimation mShowAction = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF,
                0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f);*/

        /*mShowAction.setDuration(duration);
        mShowAction.setFillAfter(false);
        mShowAction.setFillBefore(false);*/
        mHideAnimationFull.setDuration(duration);
        mHideAnimationFull.setFillAfter(false);
        mHideAnimationFull.setFillBefore(false);
        for (int j = 0; j < views.size();j++){
            views.get(j).startAnimation(mHideAnimationFull);
        }

    }

    private void statusChange(int newStatus) {
        status=newStatus;
        if (newStatus == STATUS_ERROR) {
            showStatus(activity.getResources().getString(R.string.small_problem));
            if (defaultRetryTime>0) {
                handler.sendEmptyMessageDelayed(MESSAGE_RESTART_PLAY, defaultRetryTime);
            }
        } else if(newStatus==STATUS_LOADING){
            $.id(R.id.app_video_loading).visible();
        } else if (newStatus == STATUS_PLAYING) {
            $.id(R.id.app_video_loading).gone();
        }

    }

    public void onPause() {
        //show(0);//把系统状态栏显示出来
        if (status==STATUS_PLAYING) {
            videoView.pause();
            //currentPosition = videoView.getCurrentPosition();
        }
        Log.i("status1", status + "," + STATUS_PLAYING);
    }

    public void onResume() {
        Log.i("status", status + "," + STATUS_PLAYING);
        if (status==STATUS_PLAYING) {
            videoView.resume();
            videoView.start();
        }
    }

    public void onConfigurationChanged(final Configuration newConfig) {

        portrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
        doOnConfigurationChanged(portrait);
    }

    private void doOnConfigurationChanged(final boolean portrait) {
        log.i("lock1", isFull + "," + mIsLock);
        if (videoView != null) {
            if (isFull && !mIsLock || !isFull) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        tryFullScreen(!portrait);
                        if (portrait) {
                            log.i("lock2", "," + portrait);
                            smallSreen();
                        } else {
                            fullSreeen();
                        }
                        updateFullScreenButton();
                    }
                });
                orientationEventListener.enable();
            }
        }
    }

    private void smallSreen(){
        isFull = false;
        log.i("lock3", isFull + "," + mIsLock);
        if (sendMsgPopWin!=null) {
            HideSoftInput(SendMsgPopupWindow.mFull_chattext.getWindowToken());
            if (sendMsgPopWin.isShowing()) {
                sendMsgPopWin.dismiss();
            }
        }
        $.id(R.id.app_video_box).heightandwidth(initHeight, mWidthPixels, false);
        showSettingControl(false);
        if (isShowing) {
            handler.sendEmptyMessage(MESSAGE_HIDE_BOX);
        }

    }

    private void fullSreeen(){
        isFull = true;
        fullListener.setFull();
        log.i("lock4", isFull + "," + isShowing);
        $.id(R.id.app_video_box).heightandwidth(mWidthPixels, mHeightPixels, false);
        if (mIsLock){
            orientationEventListener.disable();
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        if (isShowing) {
            handler.sendEmptyMessage(MESSAGE_SHOW_BOX);
        }
    }


    /*工具板显示时间*/
    public void show(int timeout,boolean isShowAnim) {
        log.i("isfu11",isFull+"123"+isShowing);
        isShowing = true;
        if (isFull) {
            $.id(R.id.app_video_center_box_right).visible();
            $.id(R.id.app_video_center_box_left).visible();
            $.id(R.id.app_video_top_box_right).visible();
            $.id(R.id.app_video_top_box).visible();
            $.id(R.id.app_video_bottom_box).visible();
            if (isShowAnim) {
                setShowAnimation(mViewListFull, mShowDuration);
            }
        }else{
            $.id(R.id.app_video_center_box_right).gone();
            $.id(R.id.app_video_center_box_left).gone();
            $.id(R.id.app_video_top_box_right).gone();
            $.id(R.id.app_video_top_box).visible();
            $.id(R.id.app_video_bottom_box).visible();
            if (isShowAnim) {
                setShowAnimation(mViewListSmall, mShowDuration);
            }
        }
        //updatePausePlay();
        handler.removeMessages(MESSAGE_FADE_OUT);
        if (timeout != 0) {
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_FADE_OUT), timeout);
        }
    }

    /*隐藏工具板*/
    public void hide(int hideDuration) {
        log.i("isfu22", isFull + "123" + isShowing);
        isShowing = false;
        if (isFull){
            $.id(R.id.app_video_bottom_box).gone();
            $.id(R.id.app_video_top_box).gone();
            $.id(R.id.app_video_center_box_right).gone();
            $.id(R.id.app_video_center_box_left).gone();
            setHideAnimation(mViewListFull, hideDuration);
        }else{
            $.id(R.id.app_video_bottom_box).gone();
            $.id(R.id.app_video_top_box).gone();
            setHideAnimation(mViewListSmall, hideDuration);
        }
    }

    /*设置布局的显示隐藏*/
    private void showSettingControl(boolean show) {
        isSettingShow = show;
        $.id(R.id.app_video_settinglayout).visibility(show ? View.VISIBLE : View.GONE);
    }

    private void tryFullScreen(boolean fullScreen) {
        if (activity instanceof AppCompatActivity) {
            ActionBar supportActionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (supportActionBar != null) {
                if (fullScreen) {
                    supportActionBar.hide();
                } else {
                    supportActionBar.show();
                }
            }
        }
        setFullScreen(fullScreen);
    }
    private void setFullScreen(boolean fullScreen) {
        if (activity != null) {
            WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
            if (fullScreen) {
                attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                activity.getWindow().setAttributes(attrs);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            } else {
                attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
                activity.getWindow().setAttributes(attrs);
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
        }

    }

    /* 销毁*/
    public void onDestroy() {
        mIsLock = false;
        orientationEventListener.disable();
        handler.removeCallbacksAndMessages(null);
        videoView.stopPlayback();
    }


    private void showStatus(String statusText) {
        $.id(R.id.app_video_status).visible();
        $.id(R.id.app_video_status_text).text(statusText);
    }

    /* 播放*/
    public void play(String url) {
        this.url = url;
        if (playerSupport) {
            $.id(R.id.app_video_loading).visible();
            videoView.setVideoPath(url);
            videoView.start();
        }
    }

    private String generateTime(long time) {
        int totalSeconds = (int) (time / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
    }

    /* 得到屏幕方向*/
    private int getScreenOrientation() {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }
    /*
    * 声音变化时重置系统声音及声音进度条
    */
    private void setVolume() {
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mVoicebar.setProgress(currentVolume);
    }

    /*handler处理多任务*/
    public Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {

                case Constants.VOICECHANGE:
                    setVolume();
                    break;

                default:
                    break;
            }
            return false;
        }
    });

    /*
   * 更新声音进度条
   */
    public class VoiceThread extends Thread {
        @Override
        public void run() {
            do {
                try {
                    Thread.sleep(1000);
                    Message msg = new Message();
                    msg.what = Constants.VOICECHANGE;
                    mHandler.sendMessage(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (true);
        }
    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {

        int index = (int) (percent * mMaxVolume);
        if (index > mMaxVolume)
            index = mMaxVolume;
        else if (index < 0)
            index = 0;
        // 变更声音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
    }


    /**
     * 滑动改变亮度
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        WindowManager.LayoutParams lpa = activity.getWindow().getAttributes();
        lpa.screenBrightness = percent;
        if (lpa.screenBrightness > 1.0f){
            lpa.screenBrightness = 1.0f;
        }else if (lpa.screenBrightness < 0.01f){
            lpa.screenBrightness = 0.01f;
        }
        $.id(R.id.app_video_textpercent).text(((int) (lpa.screenBrightness * 100))+"%");
        activity.getWindow().setAttributes(lpa);

    }

    private void updateFullScreenButton() {
        if (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            $.id(R.id.app_video_fullscreen).image(R.drawable.ic_fullscreen_white_24dp);
            $.id(R.id.app_video_finish).image(R.drawable.app_video_backfull);
        } else {
            $.id(R.id.app_video_fullscreen).image(R.drawable.app_watch_tofull);
            $.id(R.id.app_video_finish).image(R.drawable.app_video_backsmall);
        }
        isRotating = false;
    }

    public void start() {
        videoView.start();
    }

    public void pause() {
        videoView.pause();
    }

    public boolean onBackPressed() {
        if (isFull) {
            toggleFullScreen();
            return true;
        }
        return false;
    }

    /**
     * <pre>
     *     fitParent:可能会剪裁,保持原视频的大小，显示在中心,当原视频的大小超过view的大小超过部分裁剪处理
     *     fillParent:可能会剪裁,等比例放大视频，直到填满View为止,超过View的部分作裁剪处理
     *     wrapContent:将视频的内容完整居中显示，如果视频大于view,则按比例缩视频直到完全显示在view中
     *     fitXY:不剪裁,非等比例拉伸画面填满整个View
     *     16:9:不剪裁,非等比例拉伸画面到16:9,并完全显示在View中
     *     4:3:不剪裁,非等比例拉伸画面到4:3,并完全显示在View中
     * </pre>
     * @param scaleType
     */
    public void setScaleType(String scaleType) {
        log.i("type", scaleType+"");
        if (SCALETYPE_FITPARENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_FIT_PARENT);
        }else if (SCALETYPE_FILLPARENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_FILL_PARENT);
        }else if (SCALETYPE_WRAPCONTENT.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_ASPECT_WRAP_CONTENT);
        }else if (SCALETYPE_FITXY.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_MATCH_PARENT);
        }else if (SCALETYPE_16_9.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_16_9_FIT_PARENT);
        }else if (SCALETYPE_4_3.equals(scaleType)) {
            videoView.setAspectRatio(IRenderView.AR_4_3_FIT_PARENT);
        }
    }

    class Query {
        private final Activity activity;
        private View view;

        public Query(Activity activity) {
            this.activity=activity;
        }

        public Query id(int id) {
            view = activity.findViewById(id);
            return this;
        }

        public Query image(int resId) {
            if (view instanceof ImageView) {
                ((ImageView) view).setImageResource(resId);
            }
            return this;
        }

        public Query visible() {
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
            return this;
        }

        public Query gone() {
            if (view != null) {
                view.setVisibility(View.GONE);
            }
            return this;
        }

        public Query invisible() {
            if (view != null) {
                view.setVisibility(View.INVISIBLE);
            }
            return this;
        }

        public Query clicked(View.OnClickListener handler) {
            log.i("id123",view.getId()+"");
            if (view != null) {
                view.setOnClickListener(handler);
            }
            return this;
        }

        public Query text(CharSequence text) {
            if (view!=null && view instanceof TextView) {
                ((TextView) view).setText(text);
            }
            return this;
        }

        public Query visibility(int visible) {
            if (view != null) {
                view.setVisibility(visible);
            }
            return this;
        }

        private void size(int h, int w,boolean dip){
            if(view != null){
                log.i("h---w",h+","+w);
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                lp.width = w;
                lp.height = h;
                view.setLayoutParams(lp);

            }

        }


        public void heightandwidth(int height,int width, boolean dip) {
            size(height, width,dip);
        }

        public int dip2pixel(Context context, float n){
            int value = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n, context.getResources().getDisplayMetrics());
            return value;
        }

        public float pixel2dip(Context context, float n){
            Resources resources = context.getResources();
            DisplayMetrics metrics = resources.getDisplayMetrics();
            float dp = n / (metrics.densityDpi / 160f);
            return dp;

        }
    }

    /**
     * is player support this device
     * @return
     */
    public boolean isPlayerSupport() {
        return playerSupport;
    }

    /**
     * 是否正在播放
     * @return
     */
    public boolean isPlaying() {
        return videoView!=null?videoView.isPlaying():false;
    }

    public void stop(){
        videoView.stopPlayback();
    }


    public void toggleFullScreen(){
        if (isFull) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            isFull = false;
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    public interface OnErrorListener{
        void onError(int what, int extra);
    }

    public interface OnControlPanelVisibilityChangeListener{
        void change(boolean isShowing);
    }

    public interface OnInfoListener{
        void onInfo(int what, int extra);
    }

    public GiraffePlayer onError(OnErrorListener onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    public GiraffePlayer onComplete(Runnable complete) {
        this.oncomplete = complete;
        return this;
    }

    public GiraffePlayer onInfo(OnInfoListener onInfoListener) {
        this.onInfoListener = onInfoListener;
        return this;
    }


    private static boolean checkDeviceHasNavigationBar(Context context) {
        boolean hasNavigationBar = false;
        Resources rs = context.getResources();
        int id = rs.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) {
            hasNavigationBar = rs.getBoolean(id);
        }
        try {
            Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method m = systemPropertiesClass.getMethod("get", String.class);
            String navBarOverride = (String) m.invoke(systemPropertiesClass, "qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                hasNavigationBar = false;
            } else if ("0".equals(navBarOverride)) {
                hasNavigationBar = true;
            }
        } catch (Exception e) {
        }

        return hasNavigationBar;

    }

    private OnErrorListener onErrorListener=new OnErrorListener() {
        @Override
        public void onError(int what, int extra) {
        }
    };
    private Runnable oncomplete =new Runnable() {
        @Override
        public void run() {

        }
    };
    private OnInfoListener onInfoListener=new OnInfoListener(){
        @Override
        public void onInfo(int what, int extra) {

        }
    };
    private OnControlPanelVisibilityChangeListener onControlPanelVisibilityChangeListener=new OnControlPanelVisibilityChangeListener() {
        @Override
        public void change(boolean isShowing) {

        }
    };

    /**
     * try to play when error(only for live video)
     * @param defaultRetryTime millisecond,0 will stop retry,default is 5000 millisecond
     */
    public void setDefaultRetryTime(long defaultRetryTime) {
        this.defaultRetryTime = defaultRetryTime;
    }

    public void setTitle(CharSequence title) {
        $.id(R.id.app_video_title).text(title);
    }


}
