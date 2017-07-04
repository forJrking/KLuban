## 支持多文件、文件格式的鲁班图片压缩
### 欢迎改进 fork 和 star

>基于鲁班1.1.2剔除了 `rxjava` 重新封装

用法如下：

    Luban2
        .with(this)
        .load(path...) //单文件或者路径 另外支持File数组、集合  String路径数组、集合
        .compressConfig(Bitmap.Config.RGB_565) //设置转换图片配置  jpg 使用RGB_565可以减低压缩使用内存
        .format(Bitmap.CompressFormat.JPEG) //输出的文件格式可以用 JPEG/PNG/WEBP
        .executeMulti(new Luban2.OnMultiCompressListener(...) );多文件压缩监听
        //.execute(new Luban2.OnCompressListener(...)) //单文件
        //.executeCustom(maxSize,maxWith,maxHeight,new Luban2.OnCompressListener()//自定义压缩

	Luban2.with(this).cancel();//停止压缩

###### 压缩效果 详细请查看Luban 感谢原Luban作者开源贡献

### 大图片加载 OOM解决思路


> 避免OOM 可以在加载进入内存时候先解析出大小 然后合理压缩可解决大部分OOM
> 
> 这里压缩图片有2种或更多方式不一一举例，只探究2种算法
> 
> 1 采样率压缩（采样率会压缩图片大小 甚至影响到宽高） 缺点：要指定大小去压缩效果较差

> 2 使用等比缩放Bitmap 达到压缩，缺点会先把Bitmap放入内存中 极易OOM
    
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
        options.inSampleSize = computeSize(width, height);
        // 指定图片 ARGB 或者RGB
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        //内存不足情况
        if (!hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, true)) {
            //TODO 内存不足使用
            options.inPreferredConfig = compressFormat == Bitmap.CompressFormat.PNG ?Bitmap.Config.ARGB_4444 : Bitmap.Config.RGB_565;
            //减低像素喂 减低内存
            if (!hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, false)) {
                //并且重新计算采样率
                options.inSampleSize = computeSize(width, height);
                if (!hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, false)) {
                    return srcFile;
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
	
#### 探究Luban算法 和 近微信图片压缩算法
		
	上面给出了图片加载 防止OOM的原理，这里贴出Luban的核心采样率算法

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

### 缩放算法
	
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
	......
	try {
            Bitmap converted = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (!bitmap.sameAs(converted)) {
                bitmap = converted;
            }
        } catch (OutOfMemoryError error) {
            System.gc();
            System.runFinalization();
        }

### 预期将会在 2期中完成一个3级缓存的 图片缩略图加载工具
