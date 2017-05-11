package net.ossrs.yasea;

import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CamParaUtil {
	private static final String TAG = "yanzi";
	private CameraSizeComparator sizeComparator = new CameraSizeComparator();
	private static CamParaUtil myCamPara = null;
	private CamParaUtil(){

	}
	public static CamParaUtil getInstance(){
		if(myCamPara == null){
			myCamPara = new CamParaUtil();
			return myCamPara;
		}
		else{
			return myCamPara;
		}
	}

	/*public  Size getPreviewSize(List<Camera.Size> list, int width,int height){
		Collections.sort(list, sizeComparator);
		List<Camera.Size> list_temp = new ArrayList<Size>();
		//Log.i("list_temp0",list.get(0)+","+height);
		int i = 0;
		for(Size s:list){
			Log.i("size-----",list.get(i).width+","+list.get(i).height);
			i++;
		}
		return list.get(list.size()-1);
	}*/
/*	public  Size getPreviewSize(List<Camera.Size> list, int width,int height){
		Collections.sort(list, sizeComparator);
		List<Camera.Size> list_temp = new ArrayList<Size>();
		Log.i("list_temp0",width+","+height);
		int i = 0;
		for(Size s:list){
			if((s.height == width)){
				Log.i("list_temp1", "最终设置预览尺寸:w = " + s.width + "h = " + s.height);
				list_temp.add(list.get(i));
				Log.i("list_temp2", "最终设置预览尺寸:w = " + s.width + "h = " + s.height);
				break;
			}
			i++;
		}
		for (int q = 0 ;q<list_temp.size(); q++){
			Log.i("list_temp",list_temp.get(q).height+","+list_temp.get(q).width);
		}
		int min = Math.abs(list_temp.get(1).width-height);
		int t = 0;
		int j = 0;
		int k = 0;
		for(Size s:list_temp){
			t = Math.abs(list_temp.get(j).height-height);
			if (t<min){
				min = t;
				k = j;
			}
			j++;
		}
		return list.get(0);
	}*/
	public  Size getPreviewSize(List<Camera.Size> list, int width,float th){
		Collections.sort(list, sizeComparator);

		float rate = 1.77f;
		if (Math.abs(th-1.33f)<Math.abs(th-1.77f)){
			rate = 1.33f;
		}else{
			rate = 1.77f;
		}
		int i = 0;
		for(Size s:list){
			if((s.height > width) && equalRate(s, rate)){
				Log.i(TAG, "最终设置预览尺寸:w = " + s.width + "h = " + s.height+","+rate);
				break;
			}
			i++;
		}

		return list.get(i);
	}
	public Size getPropPictureSize(List<Size> list, float th, int minWidth){
		Collections.sort(list, sizeComparator);

		int i = 0;
		for(Size s:list){
			if((s.width >= minWidth) && equalRate(s, th)){
				Log.i(TAG, "PictureSize : w = " + s.width + "h = " + s.height);
				break;
			}
			i++;
		}
		if(i == list.size()){
			i = 0;//���û�ҵ�����ѡ��С��size
		}
		return list.get(i);
	}

	public boolean equalRate(Size s, float rate){
		float r = (float)(s.width)/(float)(s.height);
		if(Math.abs(r - rate) <= 0.01)
		{
			return true;
		}
		else{
			return false;
		}
	}


	public  class CameraSizeComparator implements Comparator<Size>{
		public int compare(Size lhs, Size rhs) {
			// TODO Auto-generated method stub
			if(lhs.width == rhs.width){
				return 0;
			}
			else if(lhs.width > rhs.width){
				return 1;
			}
			else{
				return -1;
			}
		}

	}

	/**��ӡ֧�ֵ�previewSizes
	 * @param params
	 */
	public  void printSupportPreviewSize(Camera.Parameters params){
		List<Size> previewSizes = params.getSupportedPreviewSizes();
		for(int i=0; i< previewSizes.size(); i++){
			Size size = previewSizes.get(i);
			Log.i(TAG, "previewSizes:width = "+size.width+" height = "+size.height);
		}

	}

	/**��ӡ֧�ֵ�pictureSizes
	 * @param params
	 */
	public  void printSupportPictureSize(Camera.Parameters params){
		List<Size> pictureSizes = params.getSupportedPictureSizes();
		for(int i=0; i< pictureSizes.size(); i++){
			Size size = pictureSizes.get(i);
			Log.i(TAG, "pictureSizes:width = "+ size.width
					+" height = " + size.height);
		}
	}
	/**��ӡ֧�ֵľ۽�ģʽ
	 * @param params
	 */
	public void printSupportFocusMode(Camera.Parameters params){
		List<String> focusModes = params.getSupportedFocusModes();
		for(String mode : focusModes){
			Log.i(TAG, "focusModes--" + mode);
		}
	}
}
