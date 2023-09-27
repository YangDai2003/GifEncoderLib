package com.yangdai.gifencoderlib;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
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
    private static final Boolean DEBUG = false;

    private final List<Bitmap> bitmaps;
    private int width = 0;
    private int height = 0;
    private int start = 0;
    private int end = 0;
    private int fps = 5;

    public BitmapRetriever() {
        bitmaps = new ArrayList<>();
    }

    public List<Bitmap> generateBitmaps(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);

        width = getIntMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        height = getIntMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

        double interval = (double) INTERVAL / fps;
        long duration = getLongMetadata(retriever) * 1000;
        if (end > 0) {
            duration = (long) end * INTERVAL;
        }

        for (long i = (long) start * INTERVAL; i < duration; i += interval) {
        /* 在给定的时间位置上获取一帧图片
          (视频质量不高或其他原因 可能出现总是获取为同一帧画面,
          也就是 假设获取50帧画面,实际只有10帧有效,其余有重复画面)
         */
            Bitmap frame = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST);
            if (frame != null) {
                try {
                    bitmaps.add(scale(frame));
                    debugSaveBitmap(frame, String.valueOf(i));
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
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
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
    public void setFPS(int fps) {
        this.fps = fps;
    }

    private Bitmap scale(Bitmap bitmap) {
        return Bitmap.createScaledBitmap(bitmap,
                width > 0 ? width : bitmap.getWidth(),
                height > 0 ? height : bitmap.getHeight(),
                true);
    }

    public void debugSaveBitmap(Bitmap bm, String picName) {
        if (DEBUG) {
            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/Screenshots/";
            File file = new File(path);
            if (!file.exists()) {
                file.mkdirs();
            }
            File f = new File(file.getAbsolutePath(), "DEBUG__" + picName + ".png");
            if (f.exists()) {
                f.delete();
            }
            try {
                FileOutputStream out = new FileOutputStream(f);
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}