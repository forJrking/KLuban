package com.forjrking.xluban;

/*
 * @创建者     Jrking
 * @创建时间   2017/7/2 1:04
 * @描述	      ${TODO}
 *
 * @更新者     $Author
 * @更新时间   $Date
 * @更新描述   ${TODO}
 */

import android.app.Activity;
import android.net.Uri;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.lzy.imagepicker.loader.ImageLoader;

public class GlideImageLoader implements ImageLoader {

    @Override
    public void displayImage(Activity activity, Uri uri, ImageView imageView, int width, int height) {
        Glide.with(activity)//
                .load(uri)//
                .centerCrop()//
                .override(width, height)
                .into(imageView);
    }

    @Override
    public void clearMemoryCache() {
//        Glide.get(this).clearDiskCache();
    }
}
