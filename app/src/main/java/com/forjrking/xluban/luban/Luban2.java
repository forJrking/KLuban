package com.forjrking.xluban.luban;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * @创建者 froJrking
 * @创建时间 2017/6/27 17:41
 * @描述 ${鲁班工具2}
 * <p>
 * 鲁班2 可以指定 压缩后的格式，支持多文件压缩，可以指定之定义大小去压缩
 */

public class Luban2 {

    private static final String TAG = "Luban";

    private static final String DEFAULT_DISK_CACHE_DIR = "luban_cache";

    private File mFile;

    private List<File> mFiles;

    private static AsyncExecutor executor;

    private final File mImageCacheDir;

    private Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;

    private Bitmap.Config compressConfig = Bitmap.Config.ARGB_8888; //566可以减少oom

    private int bestQuality = 100;

    private boolean compress4Sample = true; //使用采样率压缩

    private Luban2(Context context) {
        mImageCacheDir = getImageCacheDir(context);
        if (executor == null)
            executor = new AsyncExecutor();
        //计算大致系数
        bestQuality = CompressEngine.calculateQuality(context);
    }

    public static Luban2 with(Context context) {
        return new Luban2(context);
    }

    public Luban2 load(Uri uri) {
//        this.mFile = ;
        return this;
    }

    public Luban2 load(File file) {
        this.mFile = file;
        return this;
    }
    /**
     * 多文件支持 executeMulti()
     */
    public Luban2 load(String... paths) {
        if (paths != null && paths.length > 0) {
            mFiles = new ArrayList<>();
            for (String path : paths) {
                mFiles.add(new File(path));
            }
        }
        return this;
    }

    /**
     * 多文件支持 executeMulti()
     */
    public Luban2 load(File... files) {
        List<File> fs = Arrays.asList(files);
        return load(fs);
    }

    /**
     * 多文件支持 executeMulti()
     */
    public Luban2 load(List<File> files) {
        this.mFiles = files;
        return this;
    }

    /**
     * @param compressFormat 图片格式
     * @return
     */
    public Luban2 format(Bitmap.CompressFormat compressFormat) {
        this.compressFormat = compressFormat;
        return this;
    }

    /**
     * @param compress4Sample 图片格式
     * @return
     */
    public Luban2 compressType(boolean compress4Sample) {
        this.compress4Sample = compress4Sample;
        return this;
    }

    /**
     * 图片的清晰度 内存策略
     * 优先级 ARGB_8888 >RGB_565>ARGB_4444
     */
    public Luban2 compressConfig(Bitmap.Config compressConfig) {
        this.compressConfig = compressConfig;
        return this;
    }


    private File getImageCacheDir(Context context) {
        return getImageCacheDir(context, DEFAULT_DISK_CACHE_DIR);
    }

    private File getImageCacheDir(Context context, String cacheName) {
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir != null) {
            File result = new File(cacheDir, cacheName);
            if (!result.mkdirs() && (!result.exists() || !result.isDirectory())) {
                return cacheDir;
            }
            return result;
        }
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "default disk cache dir is null");
        }
        return null;
    }

    /**
     * 清空Luban所产生的缓存
     */
    public Luban2 clearCache() {
        if (mImageCacheDir != null && mImageCacheDir.exists()) {
            deleteFile(mImageCacheDir);
        }
        return this;
    }

    /**
     * 清空目标文件或文件夹
     * Empty the target file or folder
     */
    private void deleteFile(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File file : fileOrDirectory.listFiles()) {
                deleteFile(file);
            }
        }
        fileOrDirectory.delete();
    }


    private File getImageCacheFile() {
        String fileExtension = null;
        switch (compressFormat) {
            case JPEG:
                fileExtension = ".jpg";
                break;
            case PNG:
                fileExtension = ".png";
                break;
            case WEBP:
                fileExtension = ".webp";
                break;
        }
        return new File(mImageCacheDir, System.nanoTime() + fileExtension);
    }

    //同步
    private File synExecute() throws IOException {
        return new CompressEngine(mFile, getImageCacheFile())
                .compress(compress4Sample, compressFormat, compressConfig);
    }

    //异步
    public void execute(OnCompressListener listener) {
        if (listener == null) {
            throw new NullPointerException("the listener must be attached!");
        }
        //多文件使用时候 先取第一个
        if (mFile == null && mFiles != null) {
            mFile = mFiles.get(0);
        }

        if (mFile == null || !mFile.exists() || !mFile.isFile()) {
            Log.e(TAG, "没有调用load()或者文件不存在");
            listener.onError(new RuntimeException("没有调用load()或者文件不存在"));
            return;
        }

        Luban2.CompressWorker worker = new Luban2.CompressWorker(mFile, 0, listener);
        listener.onStart();
        executor.execute(worker);
    }

    /**
     * @param maxSize  最大大小 必须指定 否则  {@use execute(...listener)}
     * @param listener 回调
     */
    public void executeCustom(int maxSize, OnCompressListener listener) {
        if (listener == null) {
            throw new NullPointerException("the listener must be attached!");
        }
        //多文件使用时候 先取第一个
        if (mFile == null && mFiles != null) {
            mFile = mFiles.get(0);
        }

        if (mFile == null || !mFile.exists() || !mFile.isFile()) {
            Log.e(TAG, "没有调用load()或者文件不存在");
            listener.onError(new RuntimeException("没有调用load()或者文件不存在"));
            return;
        }

        if (maxSize <= 0) {
            throw new NullPointerException("fuck bitch! Are you reader the method's doc?");
        }
        Luban2.CompressWorker worker = new Luban2.CompressWorker(mFile, maxSize, listener);
        listener.onStart();
        executor.execute(worker);
    }

    /**
     * 最好配合 RGB_565使用可以减低 OOM
     */
    public void executeMulti(OnMultiCompressListener listener) {

        if (mFiles == null) {
            throw new NullPointerException("the image files cannot be null, please call .load(List files) before this method!");
        }

        if (listener == null) {
            throw new NullPointerException("the listener must be attached!");
        }

        Luban2.MultiCompressWorker worker = new Luban2.MultiCompressWorker(mFiles, listener);
        listener.onStart();
        executor.execute(worker);
    }

    public static synchronized void cancel() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public interface OnMultiCompressListener {

        void onStart();

        void onSuccess(List<File> file);

        void onError(Throwable e, File file);
    }


    public interface OnCompressListener {

        void onStart();

        void onSuccess(File file);

        void onError(Throwable e);
    }

    private class CompressWorker extends AsyncExecutor.Worker<File> {

        private File mOrignal;
        private OnCompressListener listener;
        private int maxSize;


        public CompressWorker(File file, int maxSize, OnCompressListener l) {
            this.maxSize = maxSize;
            this.mOrignal = file;
            this.listener = l;
        }

        @Override
        protected File doInBackground() {
            File res = null;
            try {
                //必须指定最大尺寸
                if (maxSize <= 0) {
                    //单文件普通
                    res = new CompressEngine(mOrignal, getImageCacheFile())
                            .compress(compress4Sample, compressFormat, compressConfig);
                } else {
                    //自定义压缩
                    res = new CompressEngine(mOrignal, getImageCacheFile())
                            .customCompress(maxSize, compress4Sample, bestQuality, compressFormat, compressConfig);
                }
            } catch (Exception e) {
                e.printStackTrace();
                listener.onError(e);
            }
            return res;
        }

        @Override
        protected void onPostExecute(File data) {
            super.onPostExecute(data);
            listener.onSuccess(data);
        }
    }

    private class MultiCompressWorker extends AsyncExecutor.Worker<List<File>> {

        private List<File> files;
        private OnMultiCompressListener listener;

        public MultiCompressWorker(List<File> files, OnMultiCompressListener l) {
            this.files = files;
            listener = l;
        }

        @Override
        protected List<File> doInBackground() {
            List<File> outPuts = new ArrayList<>();

            for (int i = 0; i < files.size(); i++) {
                File res = files.get(i);
                try {
                    outPuts.add(new CompressEngine(res, getImageCacheFile())
                            .compress(compress4Sample, compressFormat, compressConfig));
                } catch (Exception ex) {
                    //失败多次回调
                    listener.onError(ex, res);
                }
            }

            return outPuts;
        }

        @Override
        protected void onPostExecute(List<File> data) {
            super.onPostExecute(data);
            listener.onSuccess(data);
        }
    }
}
