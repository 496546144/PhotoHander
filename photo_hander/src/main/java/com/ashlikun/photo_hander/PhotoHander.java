package com.ashlikun.photo_hander;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;

import com.ashlikun.photo_hander.bean.ImageSelectData;
import com.ashlikun.photo_hander.compress.Luban;

import java.util.ArrayList;

/**
 * 作者　　: 李坤
 * 创建时间: 16:28 Administrator
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：图片选择器
 */

public class PhotoHander {
    //拍照code
    public final static int REQUEST_CAMERA = 100;
    //读写存储卡和拍照权限code
    public static final int REQUEST_STORAGE_WRITE_ACCESS_PERMISSION = 110;
    /**
     * 已经选择的数据
     */
    private ArrayList<ImageSelectData> mOriginData;
    /**
     * 额外添加到顶部的数据,一般是网络图
     */
    private ArrayList<String> mAddImages;
    /**
     * 配置参数
     */
    private PhotoOptionData optionData;


    private PhotoHander() {
        optionData = new PhotoOptionData();
    }

    public static PhotoHander create() {
        //返回一个实例
        return new PhotoHander();
    }

    /**
     * 是否显示摄像头
     *
     * @param mShowCamera
     * @return
     */
    public PhotoHander showCamera(boolean mShowCamera) {
        optionData.isShowCamera = mShowCamera;
        return this;
    }

    /**
     * 是否只能拍照
     *
     * @param isMustCamera
     * @return
     */
    public PhotoHander isMustCamera(boolean isMustCamera) {
        optionData.isMustCamera = isMustCamera;
        return this;
    }

    /**
     * 最大多少张
     *
     * @param count
     * @return
     */
    public PhotoHander count(int count) {
        optionData.mDefaultCount = count;
        return this;
    }

    /**
     * 单选
     *
     * @return
     */
    public PhotoHander single() {
        optionData.selectMode = PhotoOptionData.MODE_SINGLE;
        return this;
    }

    /**
     * 多选
     *
     * @return
     */
    public PhotoHander multi() {
        optionData.selectMode = PhotoOptionData.MODE_MULTI;
        return this;
    }

    /**
     * 已选
     *
     * @param images
     * @return
     */
    public PhotoHander origin(ArrayList<ImageSelectData> images) {
        mOriginData = images;
        return this;
    }

    /**
     * 额外添加到顶部的数据,一般是网络图
     */
    public PhotoHander addImage(ArrayList<String> addImages) {
        mAddImages = addImages;
        return this;
    }

    /**
     * 压缩
     *
     * @param isCompress
     * @return
     */
    public PhotoHander compress(boolean isCompress) {
        optionData.isCompress = isCompress;
        return this;
    }

    /**
     * 3级压缩,高，一般在100-400kb
     *
     * @return
     */
    public PhotoHander compressRankThird() {
        optionData.compressRank = Luban.THIRD_GEAR;
        return this;
    }

    /**
     * 2级压缩,中，一般在200-1024kb
     *
     * @return
     */
    public PhotoHander compressRankDouble() {
        optionData.compressRank = Luban.DOUBLE_GEAR;
        return this;
    }

    /**
     * 1级压缩,低,一般在60-文件大小/5
     *
     * @return
     */
    public PhotoHander compressRankFirst() {
        optionData.compressRank = Luban.FIRST_GEAR;
        return this;
    }

    /**
     * 裁剪
     *
     * @param isCrop
     * @return
     */
    public PhotoHander crop(boolean isCrop) {
        optionData.mIsCrop = isCrop;
        return this;
    }

    /**
     * 裁剪框
     *
     * @param cropWidth
     * @param cropHeight
     * @return
     */
    public PhotoHander crop(int cropWidth, int cropHeight) {
        crop(true);
        optionData.cropWidth = cropWidth;
        optionData.cropHeight = cropHeight;
        return this;
    }

    /**
     * 裁剪框圆形
     *
     * @param showCircle
     * @return
     */
    public PhotoHander cropCircle(boolean showCircle) {
        crop(true);
        optionData.cropShowCircle = showCircle;
        return this;
    }

    /**
     * 裁剪框颜色
     *
     * @param color
     * @return
     */
    public PhotoHander color(int color) {
        optionData.cropColor = color;
        return this;
    }

    /**
     * 开启
     *
     * @param activity
     * @param requestCode
     */
    public void start(Activity activity, int requestCode) {
        final Context context = activity;
        activity.startActivityForResult(createIntent(context), requestCode);
    }

    /**
     * 开启
     *
     * @param fragment
     * @param requestCode
     */
    public void start(Fragment fragment, int requestCode) {
        final Context context = fragment.getContext();
        fragment.startActivityForResult(createIntent(context), requestCode);
    }

    private Intent createIntent(Context context) {
        Intent intent = new Intent();
        intent.setClass(context, PhotoHanderActivity.class);
        //只有多选才会有原始数据
        if (optionData.isModeMulti() && mOriginData != null) {
            intent.putParcelableArrayListExtra(IntentKey.EXTRA_DEFAULT_SELECTED_LIST, mOriginData);
        }
        if (mAddImages != null) {
            intent.putStringArrayListExtra(IntentKey.EXTRA_DEFAULT_ADD_IMAGES, mAddImages);
        }
        intent.putExtra(IntentKey.EXTRA_OPTION_DATA, optionData);
        optionData = null;
        mOriginData = null;
        mAddImages = null;
        return intent;
    }

    /**
     * 获取照片选择后的地址
     *
     * @param data
     * @return
     */
    public static ArrayList<ImageSelectData> getIntentResult(Intent data) {
        if (data == null) {
            return null;
        }
        return data.getParcelableArrayListExtra(IntentKey.EXTRA_RESULT);
    }

}
