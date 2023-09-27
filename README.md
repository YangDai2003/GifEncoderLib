# GifEncoder

GifEncoder 基于 MediaMetadataRetriever，使用串表压缩算法，实现视频、多张图片转GIF。

[![jitpack](https://jitpack.io/v/YangDai2003/GifEncoderLib.svg)](https://jitpack.io/#YangDai2003/GifEncoderLib)

## How to import?

### Step 1. Add the JitPack repository to your build file

Gradle

Add it in your root build.gradle at the end of repositories:

```code
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

Maven

```code
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```

### Step 2. Add the dependency

Gradle

```code
	dependencies {
	    implementation 'com.github.YangDai2003:GifEncoderLib:latest_version'
	}
```

Maven

```code
	<dependency>
	    <groupId>com.github.YangDai2003</groupId>
	    <artifactId>GifEncoderLib</artifactId>
	    <version>Tag</version>
	</dependency>
```

## How to use?

JAVA

```code
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
```
