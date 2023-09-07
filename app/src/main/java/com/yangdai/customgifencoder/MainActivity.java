package com.yangdai.customgifencoder;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Toast;

import com.yangdai.gifencoderlib.BitmapRetriever;
import com.yangdai.gifencoderlib.GifEncoder;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private void convertVideoToGif(String inputVideoPath, String outputGifPath) {
        new Thread(() -> {
            BitmapRetriever extractor = new BitmapRetriever();
            extractor.setFPS(10);
            List<Bitmap> bitmaps = extractor.generateBitmaps(inputVideoPath);

            GifEncoder encoder = new GifEncoder();
            encoder.init(bitmaps.get(0));
            encoder.start(outputGifPath);
            for (int i = 1; i < bitmaps.size(); i++) {
                encoder.addFrame(bitmaps.get(i));
            }
            encoder.finish();
            runOnUiThread(() -> Toast.makeText(this, "finish", Toast.LENGTH_SHORT).show());
        }).start();

    }
}