## 支持多文件、文件格式的鲁班图片压缩
### 欢迎改进 fork 和 star


用法如下：

```
        val path: String = "D://t.jpg"
        val file: File = File("D://t.jpg")
        val uri: Uri = Uri.fromFile(file)
        Luban.with(this)                         //Lifecycle 获取,可以不填写参数也可使用ProcessLifecycleOwner
                .load(uri, uri)                  //支持 File,Uri,InputStream,String,和以上数据数组和集合
                .setOutPutDir(path)              //输出目录文件夹
                .concurrent(true)                //多文件压缩是否并行,内部优化并行数量防止OOM
                .useDownSample(true)             //压缩算法 true采用邻近采样,否则使用双线性采样(纯文字图片效果绝佳)
                .format(Bitmap.CompressFormat.PNG)      //压缩后输出文件格式 支持 JPG,PNG,WEBP
                .ignoreBy(200)                          //期望大小,大小和图片呈现质量不能均衡所以压缩后不一定小于此值,
                .quality(95)                            //质量压缩系数  0-100
                .rename { "pic$it" }                    //文件重命名
                .filter { true }                        // 过滤器
                .compressObserver {
                    onSuccess = { }
                    onStart = {}
                    onCompletion = {}
                    onError = { e, s -> }
                }.launch()
```
### 以下文档待补充


### kt协程 + LiveData

### Glide 图片识别算法

### 内存 性能优化

### Flow 并行和自定义线程调度控制并发任务数量

### 压缩效果 详细请查看Luban 感谢原Luban作者开源贡献