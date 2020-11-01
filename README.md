## Kotlin + 协程 + Flow + LiveData + Glide图片识别 + 邻近、双线性采样算法 实现鲁班图片压缩框架

### 欢迎改进 fork 和 star


用法如下：

```kotlin
Luban.with(this)                         //Lifecycle 获取,可以不填写参数也可使用ProcessLifecycleOwner
        .load(uri, uri)                  //支持 File,Uri,InputStream,String,和以上数据数组和集合
        .setOutPutDir(path)              //输出目录文件夹
        .concurrent(true)                //多文件压缩是否并行,内部优化并行数量防止OOM
        .useDownSample(true)             //压缩算法 true采用邻近采样,否则使用双线性采样(纯文字图片效果绝佳)
        .format(Bitmap.CompressFormat.PNG)      //压缩后输出文件格式 支持 JPG,PNG,WEBP
        .ignoreBy(200)                          //期望大小,大小和图片呈现质量不能均衡所以压缩后不一定小于此值,
        .quality(95)                            //质量压缩系数  0-100
        .rename { "pic$it" }                    //文件重命名
        .filter { it!=null }                    // 过滤器
        .compressObserver {
            onSuccess = { }
            onStart = {}
            onCompletion = {}
            onError = { e, s -> }
        }.launch()
```
### 缺点分析

Luban是基于Android原生图片压缩框架，主打特点是近乎微信的图像采样压缩算法。由于技术迭代，已经不能满足产品需求。

```
File compress() throws IOException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    //计算邻近采样主要算法
    options.inSampleSize = computeSize();
    //解码 主要OOM发生在此处
    Bitmap tagBitmap = BitmapFactory.decodeStream(srcImg.open(), null, options);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    //判断为JPEG格式进行角度
    if (Checker.SINGLE.isJPG(srcImg.open())) {
      tagBitmap = rotatingImage(tagBitmap, Checker.SINGLE.getOrientation(srcImg.open()));
    }
    //根据传递的focusAlpha来处理输出图片格式  质量系数为60
    tagBitmap.compress(focusAlpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 60, stream);
    tagBitmap.recycle();
	... 写入文件
    return tagImg;
 }
```

- 解码前没有对内存做出预判，进行合理优化
- 质量压缩写死 60
- 没有提供图片输出格式选择
- JPEG图像没有alpha层，经过压缩后为应该使用JPG格式更加节省内存
- 只提供邻近采样算法、双线性采样在某些场景更加合适（纯文字图像）
- 不支持多文件合理并行压缩，输出顺序和压缩顺序不能保证一致
- 检测文件格式和图像的角度多次重复创建InputStream,增加不必要开销，增加OOM风险
- 可能出现内存泄漏，需要自己合理处理生命周期

### 改造技术分析

- 针对质量压缩时候，外部传入需要的质量系数
- 解码前利用获取的图片宽高对内存占用做出计算，超出内存的使用RGB-565尝试解码
- 压缩前获取图片真实格式，对输出文件智能判断，例如是否包含 alpha、输出格式
- 参考Glide对字节数组的复用，以及InputStream的mark()、reset()来优化重复打开开销
- 利用LiveData来实现监听，自动注销监听。
- 利用协程来实现异步压缩和并行压缩任务，可以在合适时机取消携程来终止任务

### Glide 图片识别算法

当我们修改图片后缀或者没有后缀，Glide依旧可以正常解码显示图像。它是怎么做到的，主要依靠`ImageHeaderParserUtils`这个类,

```
public static ImageType getType(
    @NonNull List<ImageHeaderParser> parsers,
    @Nullable InputStream is,
    @NonNull ArrayPool byteArrayPool){...}
通过InputStream，获取ImageType.通过字面意思也可以猜出这是Image的类型，这是一个枚举类。
```



### 内存 性能优化

### Flow 并行和自定义线程调度控制并发任务数量

### 压缩效果 详细请查看Luban 感谢原Luban作者开源贡献