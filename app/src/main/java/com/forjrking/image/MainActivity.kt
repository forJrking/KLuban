package com.forjrking.image

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.forjrking.lubankt.Luban
import com.lzy.imagepicker.ImagePicker
import com.lzy.imagepicker.bean.ImageItem
import com.lzy.imagepicker.ui.ImageGridActivity
import com.lzy.imagepicker.view.CropImageView
import com.lzy.imagepicker.view.CropImageView.OnBitmapSaveCompleteListener
import java.io.File


class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var mImages: ArrayList<ImageItem>? = null
    private var mIv: CropImageView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.compress_img).setOnClickListener(this)
        findViewById<View>(R.id.select_img).setOnClickListener(this)
        findViewById<View>(R.id.save_img).setOnClickListener(this)
        mIv = findViewById<View>(R.id.cropImag) as CropImageView
        val imagePicker = ImagePicker.getInstance()
        imagePicker.imageLoader = GlideImageLoader() //设置图片加载器
        imagePicker.isShowCamera = true //显示拍照按钮
        imagePicker.isCrop = false //允许裁剪（单选才有效）
//        imagePicker.isSaveRectangle = true //是否按矩形区域保存
        imagePicker.selectLimit = 1 //选中数量限制
        //        imagePicker.setStyle(CropImageView.Style.CIRCLE);  //裁剪框的形状
//        imagePicker.setFocusWidth(800);   //裁剪框的宽度。单位像素（圆形自动取宽高最小值）
//        imagePicker.setFocusHeight(800);  //裁剪框的高度。单位像素（圆形自动取宽高最小值）
//        imagePicker.setOutPutX(1000);//保存文件的宽度。单位像素
//        imagePicker.setOutPutY(1000);//保存文件的高度。单位像素
//        mIv!!.focusHeight = 400
//        mIv!!.focusWidth = 800
//        mIv!!.focusStyle = CropImageView.Style.RECTANGLE
        mIv!!.setOnBitmapSaveCompleteListener(object : OnBitmapSaveCompleteListener {
            override fun onBitmapSaveSuccess(file: File) {
                Toast.makeText(this@MainActivity, "file" + file.name, Toast.LENGTH_LONG).show()
            }

            override fun onBitmapSaveError(file: File) {}
        })

//        val path: String = "D://t.jpg"
        val file: File = File("D://t.jpg")
//        val uri: Uri = Uri.fromFile(file)
//        val arrays = ArrayList<String>()
        Luban.with(this)                         //Lifecycle 获取,可以不填写参数也可使用ProcessLifecycleOwner
                .load(file)                          //支持 File,Uri,InputStream,String,和以上数据数组和集合
//                .setOutPutDir(path)                      //输出目录文件夹
//                .concurrent(true)          //多文件压缩是否并行,内部优化并行数量防止OOM
//                .useDownSample(true)    //压缩算法 true采用邻近采样,否则使用双线性采样(纯文字图片效果绝佳)
                .format(Bitmap.CompressFormat.PNG)      //压缩后输出文件格式 支持 JPG,PNG,WEBP
                .ignoreBy(200)                     //期望大小,大小和图片呈现质量不能均衡所以压缩后不一定小于此值,
                .quality(95)                     //质量压缩系数  0-100
//                .rename { "pic$it" }                    //文件重命名
//                .filter { true }                        // 过滤器
                .compressObserver {
                    onStart = {
                        //Log.d(TAG, "onStart: ")
                    }
                    onCompletion = {
                        Log.d(TAG, "onCompletion")
                    }
                    onSuccess = {
                        Toast.makeText(this@MainActivity, "file" + it.name, Toast.LENGTH_LONG).show()
                        mIv!!.setImageURI(Uri.fromFile(it))
                    }
                    onError = { a, _ ->
                        Log.e(TAG, a.toString())
                    }
                }.launch()
        //美如画

    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.compress_img -> if (mImages != null) {
                val item = mImages!![0]
                Log.d(TAG, "do-> ${item.uri}")
                Luban.with(this)
                        .load(item.uri)
                        .ignoreBy(20000)
                        .compressObserver {
                            onStart = {
                                //Log.d(TAG, "onStart: ")
                            }
                            onCompletion = {
                                Log.d(TAG, "onCompletion")
                            }
                            onSuccess = {
                                Toast.makeText(this@MainActivity, "file" + it.name, Toast.LENGTH_LONG).show()
                                mIv!!.setImageURI(Uri.fromFile(it))
                            }
                            onError = { a, _ ->
                                Log.e(TAG, a.toString())
                            }
                        }.launch()
            }
            R.id.select_img -> {
                val intent = Intent(this, ImageGridActivity::class.java)
                startActivityForResult(intent, IMAGE_PICKER)
            }
            R.id.save_img -> mIv!!.saveBitmapToFile(Environment.getExternalStorageDirectory(), 600, 300, true)
            else -> {
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == ImagePicker.RESULT_CODE_ITEMS) {
            if (data != null && requestCode == IMAGE_PICKER) {
                mImages = data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS) as ArrayList<ImageItem>
            } else {
                Toast.makeText(this, "没有数据", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val TAG = "Luban"
        private const val IMAGE_PICKER = 1001
    }
}