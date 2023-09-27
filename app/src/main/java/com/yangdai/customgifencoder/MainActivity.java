package com.yangdai.customgifencoder;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Toast;

import com.yangdai.gifencoderlib.BitmapRetriever;
import com.yangdai.gifencoderlib.GifEncoder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private void convertVideoToGif(String inputVideoPath, String outputGifPath) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            BitmapRetriever extractor = new BitmapRetriever(inputVideoPath);
            extractor.setFps(10);
            extractor.setOutputBitmapSize(extractor.getVideoWidth() / 2, extractor.getVideoHeight() / 2);
            List<Bitmap> bitmaps = extractor.generateBitmaps();

            GifEncoder encoder = new GifEncoder();
            encoder.init(bitmaps.get(0));
            encoder.start(outputGifPath);
            for (int i = 1; i < bitmaps.size(); i++) {
                encoder.addFrame(bitmaps.get(i));
            }
            encoder.finish();
            runOnUiThread(() -> Toast.makeText(this, "finish", Toast.LENGTH_SHORT).show());
        });
    }
}