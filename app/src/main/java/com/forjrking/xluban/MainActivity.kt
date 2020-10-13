package com.forjrking.xluban

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lzy.imagepicker.ImagePicker
import com.lzy.imagepicker.bean.ImageItem
import com.lzy.imagepicker.ui.ImageGridActivity
import com.lzy.imagepicker.view.CropImageView
import com.lzy.imagepicker.view.CropImageView.OnBitmapSaveCompleteListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

        lifecycleScope.launch {
            flow {
                (1..5).forEach {
                    println("emit:" + Thread.currentThread().name)
                    emit(it)
                }
            }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.Default).onStart {
                println("onStart:" + Thread.currentThread().name)
            }.onCompletion {
                println("onCompletion:" + Thread.currentThread().name)
            }.catch {
                println("catch:" + Thread.currentThread().name)
            }.collect {
                println("collect:$it  " + Thread.currentThread().name)
            }
        }

    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.compress_img -> if (mImages != null) {
                val item = mImages!![0]

                Luban.with(this)
                        .load(item.uri)
                        .useDownSample(true)
                        .rename {
                            Log.d(TAG, "rename: $it")
                            it
                        }.filter {
                            true
                        }.observerCompress {
                            onStart = {
                                Log.d(TAG, "onStart: ")
                            }
                            onCompletion = {
                                Log.d(TAG, "onCompletion")
                            }
                            onSuccess = {
                                Toast.makeText(this@MainActivity, "file" + it.name, Toast.LENGTH_LONG).show()
                                mIv!!.setImageURI(Uri.fromFile(it))
                            }
                            onError = { a, b ->
                                Log.d(TAG, a.toString())
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
        const val TAG = "MainActivity"
        private const val IMAGE_PICKER = 1001
    }
}