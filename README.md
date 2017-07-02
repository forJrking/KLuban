## 支持多文件、文件格式的鲁班图片压缩
### 欢迎改进 fork 和 start

>基于鲁班1.1.2剔除了 `rxjava` 重新封装

用法如下：

    Luban2
        .with(this)
        .load(path...) //单文件或者路径 另外支持File数组、集合  String路径数组、集合
        .setConfig(Bitmap.Config.RGB_565) //设置转换图片配置  jpg 使用RGB_565可以减低压缩使用内存
        .setFormat(Bitmap.CompressFormat.JPEG) //输出的文件格式可以用 JPEG/PNG/WEBP
        .executeMulti(new Luban2.OnMultiCompressListener(...) );多文件压缩监听
        //.execute(new Luban2.OnCompressListener(...)) //单文件
        //.executeCustom(maxSize,maxWith,maxHeight,new Luban2.OnCompressListener()//自定义压缩


###### 压缩效果 详细请查看luban

#### 感谢原Luban作者 开源贡献
