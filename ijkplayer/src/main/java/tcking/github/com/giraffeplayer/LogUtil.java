package tcking.github.com.giraffeplayer;
import android.util.Log;

/**
 * Created by lsn on 2016/4/22.
 */

public class LogUtil {
    public static  final  boolean DEBUG = false;
    private static LogUtil sLogUtil;

    private LogUtil() {
    }

    public static LogUtil getInstance() {
        if (sLogUtil == null) {
            synchronized (LogUtil.class) {
                if (sLogUtil == null) {
                    sLogUtil = new LogUtil();
                }
            }
        }
        return sLogUtil;
    }

    public void d(String tag,String msg){
        if(DEBUG){
            Log.d(tag,msg);
        }
    }

    public void i(String tag,String msg){
        if(DEBUG){
            Log.i(tag,msg);
        }
    }

    public void e(String tag,String msg){
        if(DEBUG){
            Log.e(tag,msg);
        }
    }

    public void w(String tag,String msg){
        if(DEBUG){
            Log.w(tag,msg);
        }
    }
}

