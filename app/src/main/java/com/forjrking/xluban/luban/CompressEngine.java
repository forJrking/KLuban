package com.forjrking.xluban.luban;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * @创建者 froJrking
 * @创建时间 2017/6/27 17:43
 * @描述 ${压缩引擎}
 */

public class CompressEngine {

    private ExifInterface srcExif;
    private File srcFile;
    private File resFile;

    private ByteArrayOutputStream stream;

    private static float REFERENCE_WIDTH = 1080f;
    private static float SCALE_REFERENCE_WIDTH = 1280f;
    private static float LIMITED_WIDTH = 1000f;
    private static float MIN_WIDTH = 640f;
    //常用压缩比
    private static int DEFAULT_QUALITY = 66;
    private static int DEFAULT_LOW_QUALITY = 60;
    private static int DEFAULT_HEIGHT_QUALITY = 82;
    private static int DEFAULT_X_HEIGHT_QUALITY = 88;
    private static int DEFAULT_XX_HEIGHT_QUALITY = 94;

    CompressEngine(File srcFile, File resFile) throws IOException {
        this.srcFile = srcFile;
        this.resFile = resFile;
        if (isJpeg(this.srcFile)) {
            this.srcExif = new ExifInterface(this.srcFile.getAbsolutePath());
        }
    }

    private boolean isJpeg(File photo) {
        String name = photo.getName().toLowerCase();
        return name.contains(".jpeg") || name.contains(".jpg");
    }

    /**
     * 判断内存是否足够 32位每个像素占用4字节
     */
    private boolean hasEnoughMemory(int width, int height, boolean isAlpha32) {
        Runtime runtime = Runtime.getRuntime();
        long free = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory();
        int allocation = (width * height) << (isAlpha32 ? 2 : 1);
        Log.d("Luban", "free : " + (free >> 20) + "MB, need : " + (allocation >> 20) + "MB");
        return allocation < free;
    }

    File compress(boolean compress4Sample, Bitmap.CompressFormat compressFormat, Bitmap.Config compressConfig) throws IOException {
        return customCompress(-1, compress4Sample, 50, compressFormat, compressConfig);
    }

    public File customCompress(int maxSize, boolean compress4Sample, int quality, Bitmap.CompressFormat compressFormat, Bitmap.Config compressConfig) throws IOException {
        long rqSize = srcFile.length() / 1024;
//      不压缩
        if (maxSize >= rqSize) {
            return srcFile;
        }
        rqSize = maxSize;

        //解析Bitmap
        BitmapFactory.Options options = new BitmapFactory.Options();
        //不加载进内存
        options.inJustDecodeBounds = true;
        //默认采样率 采样率是2的次幂
        options.inSampleSize = 1;
        //不加载进内存解析一次 获取宽高
        BitmapFactory.decodeFile(srcFile.getAbsolutePath(), options);
        //解析出宽高
        int width = options.outWidth;
        int height = options.outHeight;
        //重新 计算采样率 来压缩
        if (compress4Sample) {
            options.inSampleSize = computeSize(width, height);
        }
        // 指定图片 ARGB 或者RGB
        options.inPreferredConfig = compressConfig;
        //内存不足情况
        boolean isAlpha = compressConfig == Bitmap.Config.ARGB_8888;

        if (!hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, isAlpha)) {
            //TODO 内存不足使用
            options.inPreferredConfig = compressFormat == Bitmap.CompressFormat.PNG ?
                    Bitmap.Config.ARGB_4444 : Bitmap.Config.RGB_565;
            //减低像素喂 减低内存
            if (!hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, false)) {
                //并且重新计算采样率
                options.inSampleSize = computeSize(width, height);
                if (!hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, false)) {
                    throw new RuntimeException("image length is too large");
                }
            }
        }
        //加载入内存中
        options.inJustDecodeBounds = false;
        //此处OOM
        Bitmap bitmap = BitmapFactory.decodeFile(srcFile.getAbsolutePath(), options);
        //解析出错
        if (bitmap == null) {
            return srcFile;
        }
        //精准缩放压缩
        if (!compress4Sample) {
            float scale = calculateScaleSize(width, height);
            Log.d("Luban", "scale : " + scale);
            bitmap = transformBitmap(bitmap, scale);
        } else {
            //旋转默认角度
            bitmap = transformBitmap(bitmap, 1f);
        }
        // 获取解析流
        if (stream == null) {
            stream = new ByteArrayOutputStream();
        } else {
            stream.reset();
        }
        //压缩开始
        bitmap.compress(compressFormat, quality, stream);
        //无损不支持压缩
        if (compressFormat != Bitmap.CompressFormat.PNG && rqSize > 0) {
            //耗时由此处触发
            while (stream.size() / 1024 > rqSize && quality > 6) {
                stream.reset();
                quality -= 6;
                bitmap.compress(compressFormat, quality, stream);
            }
        }
        //标记释放
        bitmap.recycle();
        //输出文件
        FileOutputStream fos = new FileOutputStream(resFile);
        stream.writeTo(fos);
        fos.flush();
        fos.close();
        stream.close();
        return resFile;
    }

    /**
     * 采样率  核心算法
     */
    private int computeSize(int width, int height) {
        int mSampleSize;

        width = width % 2 == 1 ? width + 1 : width;
        height = height % 2 == 1 ? height + 1 : height;

        width = width > height ? height : width;
        height = width > height ? width : height;

        double scale = ((double) width / height);

        if (scale <= 1 && scale > 0.5625) {
            if (height < 1664) {
                mSampleSize = 1;
            } else if (height >= 1664 && height < 4990) {
                mSampleSize = 2;
            } else if (height >= 4990 && height < 10240) {
                mSampleSize = 4;
            } else {
                mSampleSize = height / 1280 == 0 ? 1 : height / 1280;
            }
        } else if (scale <= 0.5625 && scale > 0.5) {
            mSampleSize = height / 1280 == 0 ? 1 : height / 1280;
        } else {
            mSampleSize = (int) Math.ceil(height / (1280.0 / scale));
        }

        return mSampleSize;
    }

    /**
     * 精确缩放
     */
    private float calculateScaleSize(int width, int height) {
        float scale = 1f;
        int max = Math.max(width, height);
        int min = Math.min(width, height);
        float ratio = min / (max * 1f);
        if (ratio >= 0.5f) {
            if (max > SCALE_REFERENCE_WIDTH) scale = SCALE_REFERENCE_WIDTH / (max * 1f);
        } else {
            int multiple = max / min;
            if (multiple < 10) {
                if (min > LIMITED_WIDTH && (1f - (ratio / 2f)) * min > LIMITED_WIDTH) {
                    scale = 1f - (ratio / 2f);
                }
            } else {
                int arg = (int) Math.pow(multiple, 2);
                scale = 1f - (arg / LIMITED_WIDTH) + (multiple > 10 ? 0.01f : 0.03f);
                if (min * scale < MIN_WIDTH) {
                    scale = 1f;
                }
            }
        }
        return scale;
    }

    /**
     * 缩放 旋转bitmap scale =1f不缩放
     */
    private Bitmap transformBitmap(Bitmap bitmap, float scale) {
        //过滤无用的压缩
        if (srcExif == null && scale == 1f) return bitmap;
        Matrix matrix = new Matrix();
        if (srcExif != null) {
            int angle = 0;
            int orientation = srcExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    angle = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    angle = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    angle = 270;
                    break;
            }
            matrix.postRotate(angle);
        }

        if (scale != 1f) {
            matrix.setScale(scale, scale);
        }
        try {
            Bitmap converted = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (!bitmap.sameAs(converted)) {
                bitmap = converted;
            }
        } catch (OutOfMemoryError error) {
            System.gc();
            System.runFinalization();
        }
        return bitmap;
    }

    /**
     * @return 计算合适的压缩率
     */
    public static int calculateQuality(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(dm);
        float density = dm.density;
        if (density > 3f) {
            return DEFAULT_LOW_QUALITY;
        } else if (density > 2.5f && density <= 3f) {
            return DEFAULT_QUALITY;
        } else if (density > 2f && density <= 2.5f) {
            return DEFAULT_HEIGHT_QUALITY;
        } else if (density > 1.5f && density <= 2f) {
            return DEFAULT_X_HEIGHT_QUALITY;
        } else {
            return DEFAULT_XX_HEIGHT_QUALITY;
        }
    }
}
