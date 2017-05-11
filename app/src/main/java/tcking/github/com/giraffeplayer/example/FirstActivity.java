package tcking.github.com.giraffeplayer.example;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;


public class FirstActivity extends AppCompatActivity implements View.OnClickListener{

    private ImageButton live_send;
    private ImageButton live_watch;
    private int mWidthPixels;
    private int mHeightPixels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);
        mWidthPixels = getResources().getDisplayMetrics().widthPixels;
        mHeightPixels = getResources().getDisplayMetrics().heightPixels;
        SharedPreferences mySharedPreferences= getSharedPreferences("screeninfo",
                Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = mySharedPreferences.edit();
        editor.putInt("widthPixels", mWidthPixels);
        editor.putInt("heightPixels", mHeightPixels);
        editor.commit();
        live_send = (ImageButton) findViewById(R.id.live_send);
        live_watch = (ImageButton) findViewById(R.id.live_watch);
        live_send.setOnClickListener(this);
        live_watch.setOnClickListener(this);
    }

    /*点击事件处理*/
    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.live_send:
                startActivity(new Intent(FirstActivity.this,BeforePushLiveActivity.class));
                overridePendingTransition(R.anim.magnify_fade_in, R.anim.magnify_fade_out);
                break;
            case R.id.live_watch:
                startActivity(new Intent(FirstActivity.this,BeforeWatchLiveActivity.class));
                overridePendingTransition(R.anim.magnify_fade_in, R.anim.magnify_fade_out);
                break;
        }
    }
}
