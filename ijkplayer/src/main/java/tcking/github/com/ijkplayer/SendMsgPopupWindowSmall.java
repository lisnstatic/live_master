package tcking.github.com.ijkplayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import tcking.github.com.giraffeplayer.R;

public class SendMsgPopupWindowSmall extends PopupWindow {

    private Activity activity;
    private Context mContext;

    private View view;

    public static EditText mSmall_chattext;
    private ImageButton mSmall_send;


    public SendMsgPopupWindowSmall(Context mContext, View.OnClickListener itemsOnClick) {

        this.activity = (Activity) mContext;

        this.view = LayoutInflater.from(mContext).inflate(R.layout.sendmsg_popupwindowsmall, null);

        mSmall_chattext = (EditText) view.findViewById(R.id.small_chattext);
        mSmall_send = (ImageButton) view.findViewById(R.id.small_send);

        // 设置按钮监听
        mSmall_send.setOnClickListener(itemsOnClick);


        // 设置弹出窗体可点击
        this.setFocusable(true);
        // 设置外部可点击
        this.setOutsideTouchable(true);
        // mMenuView添加OnTouchListener监听判断获取触屏位置如果在选择框外面则销毁弹出框
        this.view.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View v, MotionEvent event) {


                Log.i("dian","dian");

                //if (event.getAction() == MotionEvent.ACTION_UP) {

                    dismiss();

                //}
                return true;
            }
        });


        /* 设置弹出窗口特征 */
        // 设置视图
        this.setContentView(this.view);
        // 设置弹出窗体的宽和高
        this.setWidth(RelativeLayout.LayoutParams.MATCH_PARENT);
        this.setHeight(RelativeLayout.LayoutParams.MATCH_PARENT);


        // 实例化一个ColorDrawable颜色为半透明
        ColorDrawable dw = new ColorDrawable(0xb0000000);
        // 设置弹出窗体的背景
        this.setBackgroundDrawable(dw);

        // 设置弹出窗体显示时的动画，从底部向上弹出
        this.setAnimationStyle(R.style.AnimBottom);

    }


}
