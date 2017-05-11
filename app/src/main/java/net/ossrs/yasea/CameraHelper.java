/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.ossrs.yasea;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import android.view.Surface;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.GINGERBREAD;

public class CameraHelper {
    private final CameraHelperImpl mImpl;

    public CameraHelper(final Context context) {
        if (SDK_INT >= GINGERBREAD) {
            mImpl = new CameraHelperGB();
        } else {
            mImpl = new CameraHelperBase(context);
        }
    }

    public interface CameraHelperImpl {
        int getNumberOfCameras();

        Camera openCamera(int id);

        Camera openDefaultCamera();

        Camera openCameraFacing(int facing);

        boolean hasCamera(int cameraFacingFront);

        void getCameraInfo(int cameraId, CameraInfo2 cameraInfo);
    }

    public int getNumberOfCameras() {
        return mImpl.getNumberOfCameras();
    }

    public Camera openCamera(final int id) {
        return mImpl.openCamera(id);
    }

    public Camera openDefaultCamera() {
        return mImpl.openDefaultCamera();
    }

    public Camera openFrontCamera() {
        return mImpl.openCameraFacing(CameraInfo.CAMERA_FACING_FRONT);
    }

    public Camera openBackCamera() {
        return mImpl.openCameraFacing(CameraInfo.CAMERA_FACING_BACK);
    }

    public boolean hasFrontCamera() {
        return mImpl.hasCamera(CameraInfo.CAMERA_FACING_FRONT);
    }

    public boolean hasBackCamera() {
        return mImpl.hasCamera(CameraInfo.CAMERA_FACING_BACK);
    }

    public void getCameraInfo(final int cameraId, final CameraInfo2 cameraInfo) {
        mImpl.getCameraInfo(cameraId, cameraInfo);
    }

    public int getDisplayOritation(int degrees, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    public int getDispalyRotation(Activity activity) {
        int i = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (i) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
        }
        return 0;
    }

    public static void selectCameraPreviewWH(Camera.Parameters parameters,Camera.Size targetSize) {
        List<Camera.Size> previewsSizes = parameters.getSupportedPreviewSizes();
        Collections.sort(previewsSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {
                if ((lhs.width * lhs.height) > (rhs.width * rhs.height)) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        for (Camera.Size size : previewsSizes) {
            Log.i("preview1", size.width + "," + size.height);
            if (size.width >= targetSize.width && size.height >= targetSize.height) {
                SrsEncoder.VWIDTH = size.width;
                SrsEncoder.VHEIGHT = size.height;
                return;
            }
        }
    }

    public static class CameraInfo2 {
        public int facing;
        public int orientation;
    }
}
