package net.ossrs.yasea;

import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Leo Ma on 4/1/2016.
 */
public class SrsEncoder {
    private static final String TAG = "SrsEncoder";

    public static final String VCODEC = "video/avc";
    public static final String ACODEC = "audio/mp4a-latm";
    public static  int VWIDTH = 1920;
    public static  int VHEIGHT = 1080;
    public static final int VCROP_WIDTH = 720;
    public static final int VCROP_HEIGHT = 960;
    public static int vCropWidth = VCROP_HEIGHT;   // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
    public static int vCropHeight = VCROP_WIDTH;  // Since Y component is quadruple size as U and V component, the stride must be set as 32x
    public static final int VBITRATE = 700 * 1000;  // 500kbps
    public static final int VFPS = 24;
    public static final int VGOP = 48;
    public static int VFORMAT = ImageFormat.YV12;
    public static final int ASAMPLERATE = 44100;
    public static final int ACHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    public static final int AFORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int ABITRATE = 32 * 1000;  // 32kbps

    private volatile int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    private SrsFlvMuxer flvMuxer;
    private SrsMp4Muxer mp4Muxer;

    private MediaCodecInfo vmci;
    private MediaCodec vencoder;
    private MediaCodec aencoder;
    private MediaCodec.BufferInfo vebi = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo aebi = new MediaCodec.BufferInfo();

    private byte[] mRotatedFrameBuffer = new byte[VCROP_WIDTH * VCROP_HEIGHT * 3 / 2];
    private byte[] mFlippedFrameBuffer = new byte[VCROP_WIDTH * VCROP_HEIGHT * 3 / 2];
    private byte[] mCroppedFrameBuffer = new byte[VCROP_WIDTH * VCROP_HEIGHT * 3 / 2];

    private boolean mCameraFaceFront = true;

    private long mPresentTimeUs;

    private int mVideoColorFormat;

    private int videoFlvTrack;
    private int videoMp4Track;
    private int audioFlvTrack;
    private int audioMp4Track;

    private Thread yuvPreprocessThread = null;
    private ConcurrentLinkedQueue<byte[]> yuvQueue = new ConcurrentLinkedQueue<>();
    private final Object yuvLock = new Object();
    private volatile long yuvCacheNum = 0;
    private MediaFormat videoFormat;

    public SrsEncoder(SrsFlvMuxer flvMuxer, SrsMp4Muxer mp4Muxer) {
        this.flvMuxer = flvMuxer;
        this.mp4Muxer = mp4Muxer;

        mVideoColorFormat = chooseVideoEncoder();
        if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            VFORMAT = ImageFormat.YV12;
        } else if (mVideoColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            VFORMAT = ImageFormat.NV21;
        } else {
            throw new IllegalStateException("Unsupported color format!");
        }
    }

    public int start() {
        // the referent PTS for video and audio encoder.
        mPresentTimeUs = System.nanoTime() / 1000;

        // aencoder pcm to aac raw stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            aencoder = MediaCodec.createEncoderByType(ACODEC);
        } catch (IOException e) {
            Log.e(TAG, "create aencoder failed.");
            e.printStackTrace();
            return -1;
        }

        // setup the aencoder.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        int ach = ACHANNEL == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, ASAMPLERATE, ach);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, ABITRATE);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        aencoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the audio tracker to muxer.
        audioFlvTrack = flvMuxer.addTrack(audioFormat);
        audioMp4Track = mp4Muxer.addTrack(audioFormat);

        // vencoder yuv to 264 es stream.
        // requires sdk level 16+, Android 4.1, 4.1.1, the JELLY_BEAN
        try {
            vencoder = MediaCodec.createByCodecName(vmci.getName());
        } catch (IOException e) {
            Log.e(TAG, "create vencoder failed.");
            e.printStackTrace();
            return -1;
        }

        // setup the vencoder.
        // Note: landscape to portrait, 90 degree rotation, so we need to switch width and height in configuration

        videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vCropWidth, vCropHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
        videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, VBITRATE);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VFPS);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VGOP / VFPS);
        //videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10);
        //videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel11 );
        vencoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // add the video tracker to muxer.
        videoFlvTrack = flvMuxer.addTrack(videoFormat);
        videoMp4Track = mp4Muxer.addTrack(videoFormat);

        // start device and encoder.
        vencoder.start();
        aencoder.start();

        // better process YUV data in threading
        yuvPreprocessThread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (!Thread.interrupted()) {
                    while (!yuvQueue.isEmpty()) {
                        byte[] data = yuvQueue.poll();

                        Log.i("mOrientation",mOrientation+","+Configuration.ORIENTATION_PORTRAIT);

                       /* if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
                            portraitPreprocessYuvFrame(data);
                        } else {*/
                        Log.i("w--h",vCropWidth+","+VCROP_WIDTH);
                        landscapePreprocessYuvFrame(data);
                        //}

                        ByteBuffer[] inBuffers = vencoder.getInputBuffers();
                        ByteBuffer[] outBuffers = vencoder.getOutputBuffers();

                        int inBufferIndex = vencoder.dequeueInputBuffer(-1);
                        if (inBufferIndex >= 0) {
                            ByteBuffer bb = inBuffers[inBufferIndex];
                            bb.clear();
                            bb.put(mRotatedFrameBuffer, 0, mRotatedFrameBuffer.length);
                            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
                            vencoder.queueInputBuffer(inBufferIndex, 0, mRotatedFrameBuffer.length, pts, 0);
                        }

                        for (; ; ) {
                            int outBufferIndex = vencoder.dequeueOutputBuffer(vebi, 0);
                            if (outBufferIndex >= 0) {
                                ByteBuffer bb = outBuffers[outBufferIndex];
                                onEncodedAnnexbFrame(bb, vebi);
                                vencoder.releaseOutputBuffer(outBufferIndex, false);
                                yuvCacheNum--;
                            } else {
                                break;
                            }
                        }
                    }
                    // Wait for next yuv
                    synchronized (yuvLock) {
                        try {
                            // isEmpty() may take some time, so time out should be set to wait the next one.
                            yuvLock.wait(500);
                        } catch (InterruptedException ex) {
                            yuvPreprocessThread.interrupt();
                        }
                    }
                }
            }
        });
        yuvPreprocessThread.start();

        return 0;
    }

    public void stop() {
        if (yuvPreprocessThread != null) {
            yuvPreprocessThread.interrupt();
            try {
                yuvPreprocessThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                yuvPreprocessThread.interrupt();
            }
            yuvPreprocessThread = null;
            yuvCacheNum = 0;
        }

        if (aencoder != null) {
            Log.i(TAG, "stop aencoder");
            aencoder.stop();
            aencoder.release();
            aencoder = null;
        }

        if (vencoder != null) {
            Log.i(TAG, "stop vencoder");
            vencoder.stop();
            vencoder.release();
            vencoder = null;
        }
    }

    public void swithCameraFace() {
        if (mCameraFaceFront) {
            mCameraFaceFront = false;
        } else {
            mCameraFaceFront = true;
        }
    }

    public void setScreenOrientation(int orientation) {
        mOrientation = orientation;
        if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
            vCropWidth = VCROP_WIDTH;
            vCropHeight = VCROP_HEIGHT;
        } else {
            vCropWidth = VCROP_HEIGHT;
            vCropHeight = VCROP_WIDTH;
        }
    }


    // when got encoded h264 es stream.
    private void onEncodedAnnexbFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        try {
            ByteBuffer record = es.duplicate();
            mp4Muxer.writeSampleData(videoMp4Track, record, bi);
            flvMuxer.writeSampleData(videoFlvTrack, es, bi);
        } catch (Exception e) {
            Log.e(TAG, "muxer write video sample failed.");
            e.printStackTrace();
        }
    }

    public void onGetYuvFrame(byte[] data) {
        if (yuvCacheNum < VGOP) {
            // Check video frame cache number to judge the networking situation.
            // Just cache GOP / FPS seconds data according to latency.
            if (flvMuxer.getVideoFrameCacheNumber().get() < VGOP) {
                yuvQueue.add(data);
                yuvCacheNum++;
                synchronized (yuvLock) {
                    yuvLock.notifyAll();
                }
            } else {
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(),
                        new IOException("Network is weak"));
            }
        }
    }

    // when got encoded aac raw stream.
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        try {
            ByteBuffer record = es.duplicate();
            mp4Muxer.writeSampleData(audioMp4Track, record, bi);
            flvMuxer.writeSampleData(audioFlvTrack, es, bi);
        } catch (Exception e) {
            Log.e(TAG, "muxer write audio sample failed.");
            e.printStackTrace();
        }
    }

    public void onGetPcmFrame(byte[] data, int size) {
        ByteBuffer[] inBuffers = aencoder.getInputBuffers();
        ByteBuffer[] outBuffers = aencoder.getOutputBuffers();

        int inBufferIndex = aencoder.dequeueInputBuffer(-1);
        if (inBufferIndex >= 0) {
            ByteBuffer bb = inBuffers[inBufferIndex];
            bb.clear();
            bb.put(data, 0, size);
            long pts = System.nanoTime() / 1000 - mPresentTimeUs;
            aencoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
        }

        for (; ; ) {
            int outBufferIndex = aencoder.dequeueOutputBuffer(aebi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                onEncodedAacFrame(bb, aebi);
                aencoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    private void portraitPreprocessYuvFrame(byte[] data) {
        if (mCameraFaceFront) {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    cropYUV420PlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropHeight, vCropWidth);
                    flipYUV420PlannerFrame(mCroppedFrameBuffer, mFlippedFrameBuffer, vCropHeight, vCropWidth);
                    rotateYUV420PlannerFrame(mFlippedFrameBuffer, mRotatedFrameBuffer, vCropHeight, vCropWidth);
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    cropYUV420SemiPlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropHeight, vCropWidth);
                    flipYUV420SemiPlannerFrame(mCroppedFrameBuffer, mFlippedFrameBuffer, vCropHeight, vCropWidth);
                    rotateYUV420SemiPlannerFrame(mFlippedFrameBuffer, mRotatedFrameBuffer, vCropHeight, vCropWidth);
                    break;
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        } else {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    cropYUV420PlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropHeight, vCropWidth);
                    rotateYUV420PlannerFrame(mCroppedFrameBuffer, mRotatedFrameBuffer, vCropHeight, vCropWidth);
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    cropYUV420SemiPlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropHeight, vCropWidth);
                    rotateYUV420SemiPlannerFrame(mCroppedFrameBuffer, mRotatedFrameBuffer, vCropHeight, vCropWidth);
                    break;
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        }
    }

    private void landscapePreprocessYuvFrame(byte[] data) {
        if (mCameraFaceFront) {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    cropYUV420PlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropWidth, vCropHeight);
                    flipYUV420PlannerFrame(mCroppedFrameBuffer, mFlippedFrameBuffer, vCropHeight, vCropWidth);
                    unrotateYUV420PlannerFrame(mFlippedFrameBuffer, mRotatedFrameBuffer, vCropWidth, vCropHeight);
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    cropYUV420SemiPlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropWidth, vCropHeight);
                    flipYUV420SemiPlannerFrame(mCroppedFrameBuffer, mFlippedFrameBuffer, vCropWidth, vCropHeight);
                    unrotateYUV420SemiPlannerFrame(mFlippedFrameBuffer, mRotatedFrameBuffer, vCropWidth, vCropHeight);
                    break;
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        } else {
            switch (mVideoColorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    cropYUV420PlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropWidth, vCropHeight);
                    unrotateYUV420PlannerFrame(mCroppedFrameBuffer, mRotatedFrameBuffer, vCropWidth, vCropHeight);
                    break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    cropYUV420SemiPlannerFrame(data, VWIDTH, VHEIGHT, mCroppedFrameBuffer, vCropWidth, vCropHeight);
                    unrotateYUV420SemiPlannerFrame(mCroppedFrameBuffer, mRotatedFrameBuffer, vCropWidth, vCropHeight);
                    break;
                default:
                    throw new IllegalStateException("Unsupported color format!");
            }
        }
    }

    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv
    private byte[] cropYUV420SemiPlannerFrame(byte[] input, int iw, int ih, byte[] output, int ow, int oh) {
        if (iw < ow || ih < oh) {
            throw new AssertionError("Crop revolution size must be less than original one");
        }
        if (ow % 32 != 0 || oh % 32 != 0) {
            // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
            // Since Y component is quadruple size as U and V component, the stride must be set as 32x
            if (vmci.getName().contains("MTK")) {
                throw new AssertionError("MTK encoding revolution stride must be 32x");
            }
        }

        int iFrameSize = iw * ih;
        int oFrameSize = ow * oh;

        int i = 0;
        for (int row = (ih - oh) / 2; row < oh + (ih - oh) / 2; row++) {
            for (int col = (iw - ow) / 2; col < ow + (iw - ow) / 2; col++) {
                output[i++] = input[iw * row + col];  // Y
            }
        }

        i = 0;
        for (int row = (ih - oh) / 4; row < oh / 2 + (ih - oh) / 4; row++) {
            for (int col = (iw - ow) / 4; col < ow / 2 + (iw - ow) / 4; col++) {
                output[oFrameSize + 2 * i] = input[iFrameSize + iw * row + 2 * col];  // U
                output[oFrameSize + 2 * i + 1] = input[iFrameSize + iw * row + 2 * col + 1];  // V
                i++;
            }
        }

        return output;
    }

    private byte[] cropYUV420PlannerFrame(byte[] input, int iw, int ih, byte[] output, int ow, int oh) {
        if (iw < ow || ih < oh) {
            throw new AssertionError("Crop revolution size must be less than original one");
        }
        if (ow % 32 != 0 || oh % 32 != 0) {
            // Note: the stride of resolution must be set as 16x for hard encoding with some chip like MTK
            // Since Y component is quadruple size as U and V component, the stride must be set as 32x
            if (vmci.getName().contains("MTK")) {
                throw new AssertionError("MTK encoding revolution stride must be 32x");
            }
        }

        int iFrameSize = iw * ih;
        int iQFrameSize = iFrameSize / 4;
        int oFrameSize = ow * oh;
        int oQFrameSize = oFrameSize / 4;

        int i = 0;
        for (int row = (ih - oh) / 2; row < oh + (ih - oh) / 2; row++) {
            for (int col = (iw - ow) / 2; col < ow + (iw - ow) / 2; col++) {
                output[i++] = input[iw * row + col];  // Y
            }
        }

        i = 0;
        for (int row = (ih - oh) / 4; row < oh / 2 + (ih - oh) / 4; row++) {
            for (int col = (iw - ow) / 4; col < ow / 2 + (iw - ow) / 4; col++) {
                output[oFrameSize + i] = input[iFrameSize + iw / 2 * row + col];  // U
                i++;
            }
        }

        i = 0;
        for (int row = (ih - oh) / 4; row < oh / 2 + (ih - oh) / 4; row++) {
            for (int col = (iw - ow) / 4; col < ow / 2 + (iw - ow) / 4; col++) {
                output[oFrameSize + oQFrameSize + i] = input[iFrameSize + iQFrameSize + iw / 2 * row + col];  // V
                i++;
            }
        }

        return output;
    }

    // 1. rotate 90 degree clockwise
    // 2. convert NV21 to NV12
    private byte[] rotateYUV420SemiPlannerFrame(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;

        int i = 0;
        for (int col = 0; col < width; col++) {
            for (int row = height - 1; row >= 0; row--) {
                output[i++] = input[width * row + col]; // Y
            }
        }

        i = 0;
        for (int col = 0; col < width / 2; col++) {
            for (int row = height / 2 - 1; row >= 0; row--) {
                output[frameSize + i * 2 + 1] = input[frameSize + width * row + col * 2]; // Cb (U)
                output[frameSize + i * 2] = input[frameSize + width * row + col * 2 + 1]; // Cr (V)
                i++;
            }
        }

        return output;
    }

    // 1. rotate 90 degree clockwise
    // 2. convert YV12 to I420
    private byte[] rotateYUV420PlannerFrame(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;
        int qFrameSize = frameSize / 4;

        int i = 0;
        for (int col = 0; col < width; col++) {
            for (int row = height - 1; row >= 0; row--) {
                output[i++] = input[width * row + col]; // Y
            }
        }

        i = 0;
        for (int col = 0; col < width / 2; col++) {
            for (int row = height / 2 - 1; row >= 0; row--) {
                output[frameSize + i] = input[frameSize + qFrameSize + width / 2 * row + col]; // Cb (U)
                i++;
            }
        }

        i = 0;
        for (int col = 0; col < width / 2; col++) {
            for (int row = height / 2 - 1; row >= 0; row--) {
                output[frameSize + qFrameSize + i] = input[frameSize + width / 2 * row + col]; // Cr (V)
                i++;
            }
        }

        return output;
    }

    // convert NV21 to NV12
    private byte[] unrotateYUV420SemiPlannerFrame(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;

        int i = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                output[i++] = input[width * row + col]; // Y
            }
        }

        i = 0;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                output[frameSize + i * 2 + 1] = input[frameSize + width * row + col * 2]; // Cb (U)
                output[frameSize + i * 2] = input[frameSize + width * row + col * 2 + 1]; // Cr (V)
                i++;
            }
        }

        return output;
    }

    // convert YV12 to I420
    private byte[] unrotateYUV420PlannerFrame(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;
        int qFrameSize = frameSize / 4;

        int i = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                output[i++] = input[width * row + col]; // Y
            }
        }

        i = 0;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                output[frameSize + i] = input[frameSize + qFrameSize + width / 2 * row + col]; // Cb (U)
                i++;
            }
        }

        i = 0;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                output[frameSize + qFrameSize + i] = input[frameSize + width / 2 * row + col]; // Cr (V)
                i++;
            }
        }

        return output;
    }

    private byte[] flipYUV420SemiPlannerFrame(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;

        int i = 0;
        for (int row = 0; row < height; row++) {
            for (int col = width - 1; col >= 0; col--) {
                output[i++] = input[width * row + col]; // Y
            }
        }

        i = 0;
        for (int row = 0; row < height / 2; row++) {
            for (int col = width / 2 - 1; col >= 0; col--) {
                output[frameSize + i * 2] = input[frameSize + width * row + col * 2]; // Cb (U)
                output[frameSize + i * 2 + 1] = input[frameSize + width * row + col * 2 + 1]; // Cr (V)
                i++;
            }
        }

        return output;
    }

    private byte[] flipYUV420PlannerFrame(byte[] input, byte[] output, int width, int height) {
        int frameSize = width * height;
        int qFrameSize = frameSize / 4;

        int i = 0;
        for (int row = 0; row < height; row++) {
            for (int col = width - 1; col >= 0; col--) {
                output[i++] = input[width * row + col]; // Y
            }
        }

        i = 0;
        for (int row = 0; row < height / 2; row++) {
            for (int col = width / 2 - 1; col >= 0; col--) {
                output[frameSize + i] = input[frameSize + width / 2 * row + col]; // Cr (V)
                i++;
            }
        }

        i = 0;
        for (int row = 0; row < height / 2; row++) {
            for (int col = width / 2 - 1; col >= 0; col--) {
                output[frameSize + qFrameSize + i] = input[frameSize + qFrameSize + width / 2 * row + col]; // Cb (U)
                i++;
            }
        }

        return output;
    }

    // choose the video encoder by name.
    private MediaCodecInfo chooseVideoEncoder(String name) {
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(VCODEC)) {
                    Log.i(TAG, String.format("vencoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return null;
    }

    // choose the right supported color format. @see below:
    private int chooseVideoEncoder() {
        // choose the encoder "video/avc":
        //      1. select default one when type matched.
        //      2. google avc is unusable.
        //      3. choose qcom avc.
        vmci = chooseVideoEncoder(null);
        //vmci = chooseVideoEncoder("google");
        //vmci = chooseVideoEncoder("qcom");

        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = vmci.getCapabilitiesForType(VCODEC);
        for (int i = 0; i < cc.colorFormats.length; i++) {
            int cf = cc.colorFormats[i];
            Log.i(TAG, String.format("vencoder %s supports color fomart 0x%x(%d)", vmci.getName(), cf, cf));

            // choose YUV for h.264, prefer the bigger one.
            // corresponding to the color space transform in onPreviewFrame
            if (cf >= cc.COLOR_FormatYUV420Planar && cf <= cc.COLOR_FormatYUV420SemiPlanar) {
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }
        }

        for (int i = 0; i < cc.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            Log.i(TAG, String.format("vencoder %s support profile %d, level %d", vmci.getName(), pl.profile, pl.level));
        }

        Log.i(TAG, String.format("vencoder %s choose color format 0x%x(%d)", vmci.getName(), matchedColorFormat, matchedColorFormat));
        return matchedColorFormat;
    }
}
