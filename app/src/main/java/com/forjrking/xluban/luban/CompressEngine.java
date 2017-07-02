package com.forjrking.xluban.luban;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * @创建者 froJrking
 * @创建时间 2017/6/27 17:43
 * @描述 ${压缩引擎}
 * @公司 浙江云集科技
 */

public class CompressEngine {

    private ExifInterface srcExif;
    private File srcImg;
    private File tagImg;

    private ByteArrayOutputStream mStream;
    private Bitmap.CompressFormat compressFormat;
    private Bitmap.Config compressConfig;

    CompressEngine(File srcImg, File tagImg, Bitmap.CompressFormat compressFormat, Bitmap.Config compressConfig) throws IOException {
        if (isJpeg(srcImg)) {
            this.srcExif = new ExifInterface(srcImg.getAbsolutePath());
        }
        this.srcImg = srcImg;
        this.tagImg = tagImg;
        this.compressFormat = compressFormat;
        this.compressConfig = compressConfig;

    }

    private boolean isJpeg(File photo) {
        String name = photo.getName().toLowerCase();
        return name.contains(".jpeg") || name.contains(".jpg");
    }

    /**
     * @return 采样率  核心算法
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

    private Bitmap rotatingImage(Bitmap bitmap) {
        if (srcExif == null) return bitmap;

        Matrix matrix = new Matrix();
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

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    File compress() throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 1;

        BitmapFactory.decodeFile(srcImg.getAbsolutePath(), options);
        //计算采样率
        options.inSampleSize = computeSize(options.outWidth, options.outHeight);
        options.inPreferredConfig = compressConfig;
        options.inJustDecodeBounds = false;
        Bitmap tagBitmap = BitmapFactory.decodeFile(srcImg.getAbsolutePath(), options);

        if (mStream == null) {
            mStream = new ByteArrayOutputStream();
        } else {
            mStream.reset();
        }

        tagBitmap = rotatingImage(tagBitmap);
        tagBitmap.compress(compressFormat, 50, mStream);
        tagBitmap.recycle();

        FileOutputStream fos = new FileOutputStream(tagImg);
        fos.write(mStream.toByteArray());
        fos.flush();
        fos.close();
        mStream.close();

        return tagImg;
    }

    public File customCompress(int maxSize, int maxWidth, int maxHeight) throws IOException {
        long rqSize = srcImg.length() / 1024;
//         不压缩
        if (maxSize >= rqSize) {
            return srcImg;
        }
        rqSize = maxSize;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; //不加载进内存
        options.inSampleSize = 1;
        BitmapFactory.decodeFile(srcImg.getAbsolutePath(), options);
        // //计算采样率 值再次解析图片
        if (maxWidth == 0 && maxHeight == 0) {
            options.inSampleSize = computeSize(options.outWidth, options.outHeight);
        } else {
            options.inSampleSize = computeSampleSize(options.outWidth, options.outHeight, maxWidth, maxHeight);
        }
        options.inPreferredConfig = compressConfig;
        options.inJustDecodeBounds = false;
        //此处OOM
        Bitmap bitmap = BitmapFactory.decodeFile(srcImg.getAbsolutePath(), options);

        if (mStream == null) {
            mStream = new ByteArrayOutputStream();
        } else {
            mStream.reset();
        }
        bitmap = rotatingImage(bitmap);

        int quality = 100;

        bitmap.compress(compressFormat, quality, mStream);

        if (compressFormat != Bitmap.CompressFormat.PNG) {
//            PNG不支持
            while (mStream.size() / 1024 > rqSize && quality > 6) {
                mStream.reset();
                quality -= 6;
                bitmap.compress(compressFormat, quality, mStream);
            }
        }

        bitmap.recycle();
        FileOutputStream fos = new FileOutputStream(tagImg);
        mStream.writeTo(fos);
        fos.flush();
        fos.close();
        mStream.close();

        return tagImg;
    }

    /**
     * 计算目标宽度，目标高度，inSampleSize
     */
    private static int computeSampleSize(int outWidth, int outHeight, int reqWidth, int reqHeight) {

        reqWidth = getResizedDimension(reqWidth, reqHeight, outWidth, outHeight);
        reqHeight = getResizedDimension(reqHeight, reqWidth, outHeight, outWidth);
        // 根据现在得到计算inSampleSize
        return calculateBestInSampleSize(outWidth, outHeight, reqWidth, reqHeight);
    }

    private static int getResizedDimension(int maxPrimary, int maxSecondary, int actualPrimary, int actualSecondary) {
        double ratio = (double) actualSecondary / (double) actualPrimary;
        int resized = maxPrimary;
        if (resized * ratio > maxSecondary) {
            resized = (int) (maxSecondary / ratio);
        }
        return resized;
    }

    private static int calculateBestInSampleSize(int actualWidth, int actualHeight, int desiredWidth, int desiredHeight) {
        double wr = (double) actualWidth / desiredWidth;
        double hr = (double) actualHeight / desiredHeight;
        double ratio = Math.min(wr, hr);
        float inSampleSize = 1.0f;
        while ((inSampleSize * 2) <= ratio) {
            inSampleSize *= 2;
        }

        return (int) inSampleSize;
    }
}
