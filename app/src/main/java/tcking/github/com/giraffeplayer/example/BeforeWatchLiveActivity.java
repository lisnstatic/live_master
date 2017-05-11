package tcking.github.com.giraffeplayer.example;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.readystatesoftware.systembartint.SystemBarTintManager;

public class BeforeWatchLiveActivity extends Activity implements View.OnClickListener{

    private ImageButton enter_towatch;
    private EditText mChat_room_url;
    private EditText mChat_room_name;
    private String mChat_url;
    private String mPush_url;
    private EditText mPush_room_url;
    private ImageButton btnWatchBack;
    private RelativeLayout layout;
    private EditText mChat_user_id;
    private String mChat_room;
    private String mUser_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("systime00", System.currentTimeMillis() + "");
        setContentView(R.layout.activity_before_watch_live);
        setActionBarLayout(R.layout.actionbar_layout_watch);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setTranslucentStatus(true);
        }

        SystemBarTintManager tintManager = new SystemBarTintManager(this);
        tintManager.setStatusBarTintEnabled(true);
        tintManager.setStatusBarTintResource(R.color.red_bg);
        enter_towatch = (ImageButton) findViewById(R.id.enter_towatch);
        enter_towatch.setOnClickListener(this);
        mPush_room_url = (EditText) findViewById(R.id.push_url);
        mChat_room_url = (EditText) findViewById(R.id.chat_room_url);
        mChat_room_name = (EditText) findViewById(R.id.chat_room_name);
        mChat_user_id = (EditText) findViewById(R.id.chat_user_id);
        layout = (RelativeLayout) findViewById(R.id.layout);
        Log.i("systime11", System.currentTimeMillis() + "");

    }

    /*设置状态栏*/
    @TargetApi(19)
    private void setTranslucentStatus(boolean on) {
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        final int bits = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        win.setAttributes(winParams);
    }

    public void setActionBarLayout( int layoutId ){
        ActionBar actionBar = getActionBar( );
        if( null != actionBar ){
            actionBar.setTitle("");
            actionBar.setDisplayShowHomeEnabled( false );
            actionBar.setDisplayShowCustomEnabled(true);
            LayoutInflater inflator = (LayoutInflater)   this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflator.inflate(layoutId, null);
            ActionBar.LayoutParams layout = new ActionBar.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            actionBar.setCustomView(v,layout);
            btnWatchBack = (ImageButton) v.findViewById(R.id.before_watch_back);
            btnWatchBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.enter_towatch:
                mPush_url = mPush_room_url.getText().toString().trim();
                mChat_url = mChat_room_url.getText().toString().trim();
                mChat_room = mChat_room_name.getText().toString().trim();
                mUser_id = mChat_user_id.getText().toString().trim();
                Intent intent = new Intent(BeforeWatchLiveActivity.this,WatchLiveActivity.class);
                intent.putExtra("pushUrl",mPush_url);
                intent.putExtra("chatUrl",mChat_url);
                intent.putExtra("chatRoom",mChat_room);
                intent.putExtra("userId", mUser_id);
                startActivity(intent);
                overridePendingTransition(R.anim.magnify_fade_in, R.anim.magnify_fade_out);
                break;
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.magnify_fade_in, R.anim.magnify_fade_out);
    }
}
