package com.forjrking.xluban;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.forjrking.xluban.luban.Luban2;
import com.lzy.imagepicker.ImagePicker;
import com.lzy.imagepicker.bean.ImageItem;
import com.lzy.imagepicker.ui.ImageGridActivity;
import com.lzy.imagepicker.view.CropImageView;

import java.io.File;
import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "MainActivity";
    private static final int IMAGE_PICKER = 1001;
    private ArrayList<ImageItem> mImages;
    private CropImageView mIv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.compress_img).setOnClickListener(this);
        findViewById(R.id.select_img).setOnClickListener(this);
        findViewById(R.id.save_img).setOnClickListener(this);
        mIv = (CropImageView) findViewById(R.id.cropImag);
        ImagePicker imagePicker = ImagePicker.getInstance();
        imagePicker.setImageLoader(new GlideImageLoader());   //设置图片加载器
        imagePicker.setShowCamera(true);  //显示拍照按钮
        imagePicker.setCrop(true);        //允许裁剪（单选才有效）
        imagePicker.setSaveRectangle(true); //是否按矩形区域保存
        imagePicker.setSelectLimit(1);    //选中数量限制
//        imagePicker.setStyle(CropImageView.Style.CIRCLE);  //裁剪框的形状
//        imagePicker.setFocusWidth(800);   //裁剪框的宽度。单位像素（圆形自动取宽高最小值）
//        imagePicker.setFocusHeight(800);  //裁剪框的高度。单位像素（圆形自动取宽高最小值）
//        imagePicker.setOutPutX(1000);//保存文件的宽度。单位像素
//        imagePicker.setOutPutY(1000);//保存文件的高度。单位像素
        mIv.setFocusHeight(400);
        mIv.setFocusWidth(800);
        mIv.setFocusStyle(CropImageView.Style.RECTANGLE);
        mIv.setOnBitmapSaveCompleteListener(new CropImageView.OnBitmapSaveCompleteListener() {
            @Override
            public void onBitmapSaveSuccess(File file) {
                Toast.makeText(MainActivity.this, "file" + file.getName(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onBitmapSaveError(File file) {

            }
        });

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.compress_img:
                if (mImages != null) {
                    ImageItem item = mImages.get(0);
                    Luban2.with(this)
                            .load(item.uri)
                            .compressConfig(Bitmap.Config.RGB_565)
                            .format(Bitmap.CompressFormat.JPEG)
                            .execute(new Luban2.OnCompressListener() {

                                @Override
                                public void onStart() {
                                    Log.d(TAG, "onStart: ");
                                }

                                @Override
                                public void onSuccess(File file) {
                                    Toast.makeText(MainActivity.this, "file" + file.getName(), Toast.LENGTH_LONG).show();
                                    mIv.setImageURI(Uri.fromFile(file));
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.d(TAG, e.toString());
                                }
                            });
                }
                break;
            case R.id.select_img:
                Intent intent = new Intent(this, ImageGridActivity.class);
                startActivityForResult(intent, IMAGE_PICKER);
                break;
            case R.id.save_img:
                mIv.saveBitmapToFile(Environment.getExternalStorageDirectory(), 600, 300, true);
                break;

            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == ImagePicker.RESULT_CODE_ITEMS) {
            if (data != null && requestCode == IMAGE_PICKER) {
                mImages = (ArrayList<ImageItem>) data.getSerializableExtra(ImagePicker.EXTRA_RESULT_ITEMS);
            } else {
                Toast.makeText(this, "没有数据", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
