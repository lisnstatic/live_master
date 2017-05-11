package tcking.github.com.giraffeplayer.example;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import net.ossrs.yasea.PushLiveActivity;

public class BeforePushLiveActivity extends Activity {

    private ImageButton enterPushBtn;
    private EditText mPush_room_url;
    private String mPushAd_url;
    private SystemBarTintManager mTintManager;
    private ImageButton btnPushBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_before_push_live);
        setActionBarLayout(R.layout.actionbar_layout_push);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setTranslucentStatus(true);
        }

        SystemBarTintManager tintManager = new SystemBarTintManager(this);
        tintManager.setStatusBarTintEnabled(true);
        tintManager.setStatusBarTintResource(R.color.green_bg);
        enterPushBtn = (ImageButton) findViewById(R.id.enter_topush);
        mPush_room_url = (EditText) findViewById(R.id.push_room_url);
        enterPushBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPushAd_url = mPush_room_url.getText().toString().trim();
                Intent intent = new Intent(BeforePushLiveActivity.this, PushLiveActivity.class);
                intent.putExtra("pushUrl",mPushAd_url);
                startActivity(intent);
                overridePendingTransition(R.anim.magnify_fade_in, R.anim.magnify_fade_out);
            }
        });


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
            btnPushBack = (ImageButton) v.findViewById(R.id.before_push_back);
            btnPushBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.magnify_fade_in, R.anim.magnify_fade_out);
    }
}
