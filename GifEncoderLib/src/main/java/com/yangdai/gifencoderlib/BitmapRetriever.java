package com.yangdai.gifencoderlib;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 30415
 */
public class BitmapRetriever {
    /**
     * 1 μs
     */
    private static final int INTERVAL = 1000 * 1000;
    private final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    private final List<Bitmap> bitmaps = new ArrayList<>();
    private int videoWidth;
    private int videoHeight;
    private int start = 0;
    private int end = 0;
    private int fps = 5;

    public BitmapRetriever(String path) {
        retriever.setDataSource(path);
        videoWidth = getIntMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        videoHeight = getIntMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
    }

    public List<Bitmap> generateBitmaps() {
        double interval = (double) INTERVAL / fps;
        long duration = getLongMetadata(retriever) * 1000;
        if (end > 0) {
            duration = (long) end * INTERVAL;
        }

        for (long i = (long) start * INTERVAL; i < duration; i += interval) {
            // 在给定的时间位置上获取一帧图片
            // (视频质量不高或其他原因 可能出现总是获取为同一帧画面,
            // 也就是 假设获取50帧画面,实际只有10帧有效,其余有重复画面)
            Bitmap frame = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST);
            if (frame != null) {
                try {
                    bitmaps.add(scale(frame));
                } catch (OutOfMemoryError oom) {
                    oom.printStackTrace();
                    break;
                }
            }
        }
        return bitmaps;
    }

    private int getIntMetadata(MediaMetadataRetriever retriever, int key) {
        String value = retriever.extractMetadata(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    private long getLongMetadata(MediaMetadataRetriever retriever) {
        String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        return value != null ? Long.parseLong(value) : 0;
    }

    /**
     * 设置分辨率大小
     */
    public void setOutputBitmapSize(int width, int height) {
        this.videoWidth = width;
        this.videoHeight = height;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    /**
     * 截取视频的起始时间(单位 s)
     */
    public void setDuration(int begin, int end) {
        this.start = begin;
        this.end = end;
    }

    /**
     * 设置帧率
     */
    public void setFps(int fps) {
        this.fps = fps;
    }

    private Bitmap scale(Bitmap bitmap) {
        return Bitmap.createScaledBitmap(bitmap,
                videoWidth > 0 ? videoWidth : bitmap.getWidth(),
                videoHeight > 0 ? videoHeight : bitmap.getHeight(),
                true);
    }
}