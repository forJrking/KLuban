## 本项目是基于Luban算法，重构后实现的图片压缩框架 ![](https://jitpack.io/v/forJrking/KLuban.svg)

KLuban 使用Kotlin + 协程 + Flow(并行任务) + LiveData(监听回调) + Glide图片识别和内存优化 + 邻近(鲁班)、双线性采样图片算法压缩框架，欢迎改进 fork 和 star

## 集成和使用 

Step 1.Add it in your root build.gradle at the end of repositories:

```css
allprojects {
   repositories {
   	...
   	maven { url 'https://jitpack.io' }
   }
}
```

Step 2.Add the dependency

```css
dependencies {
  implementation 'com.github.forJrking:KLuban:1.0.7'
}
```

Step 3.Api：

```kotlin
Luban.with(LifecycleOwner)               //(可选)Lifecycle,可以不填写内部使用ProcessLifecycleOwner
        .load(uri)                       //支持 File,Uri,InputStream,String,Bitmap 和以上数据数组和集合
        .setOutPutDir(path)              //(可选)输出目录文件夹
        .concurrent(true)                //(可选)多文件压缩时是否并行,内部优化线程并行数量防止OOM
        .useDownSample(true)             //(可选)压缩算法 true采用邻近采样,否则使用双线性采样(纯文字图片效果绝佳)
        .format(Bitmap.CompressFormat.PNG)//(可选)压缩后输出文件格式 支持 JPG,PNG,WEBP
        .ignoreBy(200)                   //(可选)期望大小,大小和图片呈现质量不能均衡所以压缩后不一定小于此值,
        .quality(95)                     //(可选)质量压缩系数  0-100
        .rename { "pic$it" }             //(可选)文件重命名
        .filter { it!=null }             //(可选)过滤器
        .compressObserver {
            onSuccess = { }
            onStart = {}
            onCompletion = {}
            onError = { e, _ -> }
        }.launch()
```
## 原框架问题分析和技术预估

Luban是基于Android原生API的图片压缩框架，主打特点是近乎微信的图像采样压缩算法。由于技术迭代，已经不能满足产品需求。下面为核心压缩实现，列出鲁班存在的问题：

```java
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

- 解码前没有对内存做出预判
- 质量压缩写死 60
- 没有提供图片输出格式选择
- JPEG图像没有alpha层，经过压缩后为应该使用JPG格式更加节省内存
- 只提供邻近采样算法、双线性采样在某些场景更加合适（纯文字图像）
- 不支持多文件合理并行压缩，输出顺序和压缩顺序不能保证一致
- 检测文件格式和图像的角度多次重复创建InputStream,增加不必要开销，增加OOM风险
- 可能出现内存泄漏，需要自己合理处理生命周期

### 改造技术分析

- 针对质量压缩时候，需要外部传入质量系数
- 解码前利用获取的图片宽高对内存占用做出计算，超出内存的使用RGB-565尝试解码
- 压缩前获取图片真实格式，对输出文件智能判断，例如是否包含 alpha、输出格式
- 参考Glide对字节数组的复用，以及`InputStream的mark()`、`reset()`来优化重复打开开销
- 利用`LiveData`来实现监听，自动注销监听。
- 利用协程来实现异步压缩和并行压缩任务，可以在合适时机取消携程来终止任务


## 源码分析和优化

### 借鉴Glide图片识别

不能准确识别文件是什么格式的图像文件，解码时候指定不合适的格式会浪费内存，而且输出时候可能导致透明层丢失等问题。来看看Glide怎么做。当我们修改图片后缀或者没有后缀，Glide依旧可以正常解码显示图像。它是怎么做到的，主要依靠`ImageHeaderParserUtils`这个类：

```java
public final class ImageHeaderParserUtils {
	... //通过ImageHeaderParser获取ImageType和图片角度
  public static ImageType getType(List<ImageHeaderParser> parsers,
     InputStream is,ArrayPool byteArrayPool)
   public static int getOrientation(List<ImageHeaderParser> parsers,
     InputStream is,final ArrayPool byteArrayPool)
    ...
}
//接口
interface ImgHeaderParser {
		//获取图片类型
    fun getType(input: InputStream): ImageType
		//获取图片原始方向
    fun getOrientation(input: InputStream): Int
}
//实现类//内部通过InputStream 来读取字节来判断文件格式
DefaultImageHeaderParser和ExifInterfaceImageHeaderParser
```
做优化只需分析下调用链，拷贝需要的类即可。（由于源码较多，容易犯困，这里只简单说明功能和改造思路，有兴趣的自己阅读。）
```kotlin
//ImgHeaderParser解析用到InputStream，glie中实际实现是 RecyclableBufferedInputStream ，主要作用包装InputStream为其实现字节数组复用
//以及支持 mark()\reset() 作用就是在流中做标记可以重复使用流对象降低开销，这个在后面内存优化中我们再说

//简单改造下 suffix：图片的后缀，hasAlpha：图片是否包含透明层，format：输出时候支持的格式
enum class ImageType(val suffix: String, val hasAlpha: Boolean, val format: Bitmap.CompressFormat) {
    GIF("gif", true, Bitmap.CompressFormat.PNG),
    JPEG("jpg", false, Bitmap.CompressFormat.JPEG),
    RAW("raw", false, Bitmap.CompressFormat.JPEG),
    /* PNG type with alpha.  */
    PNG_A("png", true, Bitmap.CompressFormat.PNG),
    /* PNG type without alpha.  */
    PNG("png", false, Bitmap.CompressFormat.PNG),
    /* WebP type with alpha.  */
    WEBP_A("webp", true, Bitmap.CompressFormat.WEBP),
    /* WebP type without alpha.  */
    WEBP("webp", false, Bitmap.CompressFormat.WEBP),
    /* Unrecognized type.  */
    UNKNOWN("jpeg", false, Bitmap.CompressFormat.JPEG);
}
```

### 内存和性能优化

1. 内存占用优化

   图片处理中内存占用主要分2个部分，图片解码后Bitmap占用的内存空间，解码过程中产生字节数组。

   图像较好的展示效果和内存占用不能同时拥有，我们可以先处理解码过程中的资源开销，因为Glide已经实现这个思路和技术，我们套用即可。


   - 字节数组池化

     在解码过程中创建大量的`Byte[]`,我们知道Glide为了内存和性能做出很多优化，对`Byte[]`做了池化，只需要调用如下

     ```kotlin
     val byteArrayPool = com.bumptech.glide.Glide.get(context).arrayPool
     byteArrayPool.get(bufferSize, ByteArray::class.java)
     ```

     但是由于有些项目可能没有引入Glide,为了做出兼容，一般我们会拷贝代码来使用。这样显然不合适，我们这里采用反射检测的方式来使用Glide已经实现的功能。

     1. 首先在我们的lib中使用`compileOnly ("com.github.bumptech.glide:glide:4.11.0@aar")`引入用于编译可以通过
     2. 紧接着实现一个工具类，获取和销毁字节数组，注意在调用`Glide.get(Checker.context).arrayPool`必须使用显示指定全包名的方式，不然项目在类加载`ArrayProvide`会加载其导包的类，如果没有使用Glide就会抛出类加载异常。最终实现如下：

     ```kotlin
     object ArrayProvide {
         private val hasGlide: Boolean by lazy {
             try {
                 //检查是否引入glide
                 Class.forName("com.bumptech.glide.Glide")
                 true
             } catch (e: Exception) {
                 false
             }
         }
     
         @JvmStatic
         fun get(bufferSize: Int): ByteArray = if (hasGlide) {
             //反射判断是否包含glide
             val byteArrayPool = com.bumptech.glide.Glide.get(Checker.context).arrayPool
             byteArrayPool.get(bufferSize, ByteArray::class.java)
         } else {
             ByteArray(bufferSize)
         }
     
         @JvmStatic
         fun put(buf: ByteArray) {
             if (hasGlide && buf.isNotEmpty()) {
                 //反射判断是否包含glide
                 val byteArrayPool = com.bumptech.glide.Glide.get(Checker.context).arrayPool
                 byteArrayPool.put(buf)
             }
         }
     }
     ```

     3. 替换所有 `new byte[]`的使用的地方，后续项目中有其他需要优化字节数组获取的地方也可以使用这个类。
     
    - 解码过程中内存预判

      2.3-7.1之间，Bitmap的像素存储在Dalvik的Java堆上，利用图片解码前可以获取真实宽高和图片的位图配置，计算JVM内存占用，做出代码执行结果预判。如果内存不足就终止。

      ```kotlin
      //判断图片解码位图配置  内存不足就不进行压缩，抛出异常捕获而不是让其OOM程序崩溃
      val isAlpha = compressConfig == Bitmap.Config.ARGB_8888
      if (!hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, isAlpha)) {
         //TODO 8.0一下内存不足使用降级策略
       if (!isAlpha || !hasEnoughMemory(width / options.inSampleSize, height / options.inSampleSize, false)) {
           throw IOException("image memory is too large")
       } else {
           Checker.logger("memory warring 降低位图像素")
         	//减低像素 减低内存
           options.inPreferredConfig = Bitmap.Config.RGB_565
       }
      }
      ```

2. `InputStream`优化

      鲁班在获取图片格式和图片解码前获取必要的图片宽高，以及获取图片原始角度都使用`InputStreamProvider.open()`这个方法

      ```java
      public abstract class InputStreamAdapter implements InputStreamProvider {
        private InputStream inputStream;
        public InputStream open() throws IOException {
          ...
          inputStream = openInternal();
          return inputStream;
        }....
        public abstract InputStream openInternal() throws IOException;
      }
      ```

      查看Luban.java中的具体实现

      ```java
      class Luban{
        ...
      	public Builder load(final String string) {
      	  mStreamProviders.add(new InputStreamAdapter() {
      	    @Override
      	    public InputStream openInternal() throws IOException {
              //每次都重新创建流对象
      	      return new FileInputStream(string);
      	    }....
      }
      ```

      这里每次都使用新的流对象，资源开销较大。再看Glide使用了`RecyclableBufferedInputStream`,内部对使用`InputStream`的地方进行包装，然后调用`mark()`、`reset()`来优化重复打开的开销。我们copy源码`BufferedInputStreamWrap`改造下：

      ```kotlin
      abstract class InputStreamAdapter<T> : InputStreamProvider<T> {
      		//BufferedInputStreamWrap 来自Glide内部对字节数组复用和 mark\reset做了优化 字节拷贝
          private lateinit var inputStream: BufferedInputStreamWrap
      
          @Throws(IOException::class)
          abstract fun openInternal(): InputStream
      
          @Throws(IOException::class)
          override fun rewindAndGet(): InputStream {
              if (::inputStream.isInitialized) {
                  inputStream.reset()
              } else {
                  inputStream = BufferedInputStreamWrap(openInternal())
                  inputStream.mark(MARK_READ_LIMIT)
              }
              return inputStream
          }
      
          override fun close() {
              if (::inputStream.isInitialized) {
                  try {
                      inputStream.close()
                  } catch (ignore: IOException) {
                      ignore.printStackTrace()
                  }
              }
          }
      }
      ```

### Flow使用和自定义线程调度控制并发任务数量

1. 首先我们把压缩图片的的方法添加 `suspend` 声明为挂起函数

```kotlin
   suspend fun compress(): File = withContext(customerDispatcher) {
      //解析和压缩
      return@withContext file
   }
 ```

2. Flow的协程选择

   由于`LiveData`需要使用`LifecycleOwner`，这里使用flow的协程为`LifecycleOwner.lifecycleScope`,由于协程几个线程调度，在并行执行图片压缩时候，一旦图片过多同时执行解码的图片数量不可控，就会导致内存占用瞬间增加极可能导致OOM。这里我们需要自定义协程线程调度。

3. 自定义线程调度

   ```kotlin
   //可以使用协程的扩展方法 .asCoroutineDispatcher()
   例如：Executors.newFixedThreadPool(2).asCoroutineDispatcher()
   ```
   
   当然这样还不够好，我使用了自定义线程池方式这样可以在不同版本的手机上使用不同策略，而且提供自定义线程名称，在线上可以用来方便定位异常业务

   ```kotlin
   companion object {
       //主要作用用于并行执行时候可以限制执行任务个数 防止OOM
       internal val supportDispatcher: ExecutorCoroutineDispatcher
       init {
           //Android O之后Bitmap内存放在native  https://www.jianshu.com/p/d5714e8987f3
           val corePoolSize = when {
               Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                   (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
               }
               Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> { 2 }
               else -> { 1 }
           }
           val threadPoolExecutor = ThreadPoolExecutor(corePoolSize, corePoolSize,
                   5L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(), CompressThreadFactory())
           // DES：预创建线程 threadPoolExecutor.prestartAllCoreThreads()
           // DES：让核心线程也可以回收
           threadPoolExecutor.allowCoreThreadTimeOut(true)
           // DES：转换为协程调度器
           supportDispatcher = threadPoolExecutor.asCoroutineDispatcher()
       }
   }
   ```
   
4. Flow并行2种方式

   ```kotlin
   //控制同时在此自定义协程调度内执行任务数量2个
   val customerDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
   suspend fun compressV(int: Int): String = withContext(customerDispatcher) {
      //模拟压缩文件
      println("compress 开始:$int \tthread:${Thread.currentThread().name}")
      Thread.sleep(300)
      val toString = int.toString() + "R"
      println("compress 结束:${toString} \tthread:${Thread.currentThread().name}")
      return@withContext toString
   }
   ```

- `flatMapMerge`操作符

   ```kotlin
   @Test
    fun testFlowFlat() = runBlocking<Unit> {
     val time = measureTimeMillis {
         listOf(1, 2, 3).asFlow().flatMapMerge {
             flow {
                 println("emit: $it  t:${Thread.currentThread().name}")
                 delay(500)
                 emit(it)
             }.flowOn(customerDispatcher)
         }.onStart {
             println("onStart: t:${Thread.currentThread().name}")
         }.onCompletion {
             println("onCompletion: $it  t:${Thread.currentThread().name}")
         }.catch {
             println("catch: $it  t:${Thread.currentThread().name}")
         }.collect {
             println("success: $it  t:${Thread.currentThread().name}")
         }
     }
     println("time: $time")
    }
    print:
    onStart: t:main @coroutine#1
    emit: 2  t:pool-2-thread-2 @coroutine#7
    emit: 1  t:pool-2-thread-1 @coroutine#6
    emit: 3  t:pool-2-thread-1 @coroutine#8
    success: 2  t:main @coroutine#1
    success: 1  t:main @coroutine#1
    success: 3  t:main @coroutine#1
    onCompletion: null  t:main @coroutine#1
    time: 561
    //结果确实实现了并行，但是 collect 中的结果因为并行不确定性收集结果不是原有顺序
   ```


- `map + async + await()`

  ```kotlin
  @Test
   fun testBinFa() = runBlocking {
    val time = measureTimeMillis {
        listOf(1, 2, 3).asFlow().map { i ->
            async { compressV(i) }
        }.buffer().flowOn(Dispatchers.Unconfined).map {
            it.await()
        }.onStart {
            println("onStart: t:${Thread.currentThread().name}")
        }.onCompletion {
            println("onCompletion: $it  t:${Thread.currentThread().name}")
        }.catch {
            println("catch: $it  t:${Thread.currentThread().name}")
        }.collect {
            println("success: $it  t:${Thread.currentThread().name}")
        }
    }
    println("Collected in $time ms")
  }
  print:
  onStart: t:main @coroutine#1
  compress 开始:1 	thread:pool-1-thread-1 @coroutine#3
  compress 开始:2 	thread:pool-1-thread-2 @coroutine#4
  compress 结束:2R 	thread:pool-1-thread-2 @coroutine#4
  compress 结束:1R 	thread:pool-1-thread-1 @coroutine#3
  compress 开始:3 	thread:pool-1-thread-2 @coroutine#5
  success: 1R  t:main @coroutine#1
  success: 2R  t:main @coroutine#1
  compress 结束:3R 	thread:pool-1-thread-2 @coroutine#5
  success: 3R  t:main @coroutine#1
  onCompletion: null  t:main @coroutine#1
  Collected in 678 ms
  //success结果显示 可以并行且有序
  ```

### 图片压缩算法

- 邻近采样 使用鲁班算法不多介绍

  优点不用加载Bitmap进入内存，用于压缩拍照图片较好。缺点在某些场景压缩效果会丢失像素细节。

- 双线性采样

  ```kotlin
  matrix.setScale(scale, scale)
  Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  ```

  优点对于纯文字类图片压缩，显示效果优于邻近采样。缺点先加载进入内存，如果内存占用过大容易OOM

  图片压缩算法优劣：[QQ音乐技术团队-Android中图片压缩分析](https://cloud.tencent.com/developer/article/1006352)

### 压缩效果请查看[Luban](https://github.com/Curzibn/Luban)
