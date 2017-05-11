package tcking.github.com.ijkplayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import tcking.github.com.giraffeplayer.R;

public class SendMsgPopupWindow extends PopupWindow {

    public  EditText mSmall_chattext;
    private ColorDrawable dw;
    private  ImageButton mSmall_send;
    private Activity activity;
    private Context mContext;

    private View view;

    public static EditText mFull_chattext;
    private ImageButton mFull_send;

    //弹幕暂停播放接口
    public interface SendMsgListener{
        public void setSendOnclick();
    }
    SendMsgListener sendMsgListener;
    public void onSendClick(SendMsgListener sendMsgListener){
        this.sendMsgListener = sendMsgListener;
    }
    public SendMsgPopupWindow(Context mContext, View.OnClickListener itemsOnClick, final boolean isSmall) {

        this.activity = (Activity) mContext;

        if (isSmall){
            this.view = LayoutInflater.from(mContext).inflate(R.layout.sendmsg_popupwindowsmall, null);
            mSmall_chattext = (EditText) view.findViewById(R.id.small_chattext);
            mSmall_send = (ImageButton) view.findViewById(R.id.small_send);
            // 设置按钮监听
            mSmall_send.setOnClickListener(itemsOnClick);
            mSmall_chattext.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    //Log.i("123id", actionId + ","+EditorInfo.IME_ACTION_DONE+","+EditorInfo.IME_ACTION_GO+","+EditorInfo.IME_ACTION_NEXT);
                    sendMsgListener.setSendOnclick();
                    return true;
                }
            });

        }else {
            this.view = LayoutInflater.from(mContext).inflate(R.layout.sendmsg_popupwindow, null);
            mFull_chattext = (EditText) view.findViewById(R.id.full_chattext);
            mFull_send = (ImageButton) view.findViewById(R.id.full_send);
            // 设置按钮监听
            mFull_send.setOnClickListener(itemsOnClick);
            mFull_chattext.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    Log.i("123id", actionId + "");
                    mFull_send.callOnClick();
                    return true;
                }
            });
        }


        // 设置外部可点击
        this.setOutsideTouchable(true);
        // mMenuView添加OnTouchListener监听判断获取触屏位置如果在选择框外面则销毁弹出框
        this.view.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {

                if (isSmall){
                    HideSoftInput(mSmall_chattext.getWindowToken());
                }else{
                    HideSoftInput(mFull_chattext.getWindowToken());
                }
                dismiss();
                return true;
            }
        });


        /* 设置弹出窗口特征 */
        // 设置视图
        this.setContentView(this.view);
        // 设置弹出窗体的宽和高
        if (isSmall){
            // 设置弹出窗体的宽和高
            this.setWidth(RelativeLayout.LayoutParams.MATCH_PARENT);
            this.setHeight(RelativeLayout.LayoutParams.MATCH_PARENT);
            // 实例化一个ColorDrawable颜色为半透明
            dw = new ColorDrawable(0x00000000);
        }else{
            DisplayMetrics dm = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeigh = dm.heightPixels;

            Log.i("popwin", screenWidth + "," + screenHeigh);

            this.setWidth(screenWidth);
            this.setHeight(screenHeigh);
            // 实例化一个ColorDrawable颜色为半透明
            dw = new ColorDrawable(0xb0000000);
        }


        // 设置弹出窗体可点击
        this.setFocusable(true);

        // 设置弹出窗体的背景
        this.setBackgroundDrawable(dw);

        // 设置弹出窗体显示时的动画，从底部向上弹出
        this.setAnimationStyle(R.style.AnimBottom);

    }
    // 隐藏软键盘
    private void HideSoftInput(IBinder token) {
        if (token != null) {
            InputMethodManager manager = (InputMethodManager) activity.getSystemService(activity.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(token,
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
}
