/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ashlikun.photo_hander.crop;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.opengl.GLES10;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.ashlikun.photo_hander.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * 作者　　: 李坤
 * 创建时间: 2018/2/2 17:17
 * 邮箱　　：496546144@qq.com
 * <p>
 * 功能介绍：图片裁剪Activity
 */

public class CropImageActivity extends AppCompatActivity {

    private static final int SIZE_DEFAULT = 2048;
    private static final int SIZE_LIMIT = 4096;

    private final Handler handler = new Handler();

    /**
     * 图片旋转度数
     */
    public int exifRotation;

    /**
     * 配置参数
     */
    private CropOptionData optionData;

    private boolean isSaving;

    private int sampleSize;
    private RotateBitmap rotateBitmap;
    private CropImageView imageView;
    private HighlightView cropView;
    ProgressDialog dialog = null;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setupWindowFlags();
        setupViews();
        loadInput();
        if (rotateBitmap == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        startCrop();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setupWindowFlags() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    private void setupViews() {
        setContentView(R.layout.crop_activity_crop);

        imageView = (CropImageView) findViewById(R.id.crop_image);
        imageView.context = this;
        imageView.setRecycler(new ImageViewTouchBase.Recycler() {
            @Override
            public void recycle(Bitmap b) {
                b.recycle();
                System.gc();
            }
        });

        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        findViewById(R.id.btn_done).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveClicked();
            }
        });
    }

    private void loadInput() {
        Intent intent = getIntent();
        optionData = intent.getParcelableExtra(IntentKey.EXTRA_OPTION_DATA);
        if (optionData.source != null) {
            exifRotation = CropUtil.getExifRotation(CropUtil.getFromMediaUri(this, getContentResolver(), optionData.source));
            InputStream is = null;
            try {
                sampleSize = calculateBitmapSampleSize(optionData.source);
                is = getContentResolver().openInputStream(optionData.source);
                BitmapFactory.Options option = new BitmapFactory.Options();
                option.inSampleSize = sampleSize;
                rotateBitmap = new RotateBitmap(BitmapFactory.decodeStream(is, null, option), exifRotation);
            } catch (IOException e) {
                Log.e("Error reading image: " + e.getMessage(), e.toString());
                setResultException(e);
            } catch (OutOfMemoryError e) {
                Log.e("OOM reading image: " + e.getMessage(), e.toString());
                setResultException(e);
            } finally {
                CropUtil.closeSilently(is);
            }
        }
    }

    private int calculateBitmapSampleSize(Uri bitmapUri) throws IOException {
        InputStream is = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            is = getContentResolver().openInputStream(bitmapUri);
            BitmapFactory.decodeStream(is, null, options); // Just get image size
        } finally {
            CropUtil.closeSilently(is);
        }

        int maxSize = getMaxImageSize();
        int sampleSize = 1;
        while (options.outHeight / sampleSize > maxSize || options.outWidth / sampleSize > maxSize) {
            sampleSize = sampleSize << 1;
        }
        return sampleSize;
    }

    private int getMaxImageSize() {
        int textureLimit = getMaxTextureSize();
        if (textureLimit == 0) {
            return SIZE_DEFAULT;
        } else {
            return Math.min(textureLimit, SIZE_LIMIT);
        }
    }

    private int getMaxTextureSize() {
        // The OpenGL texture size is the maximum size that can be drawn in an ImageView
        int[] maxSize = new int[1];
        GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        return maxSize[0];
    }

    private void startCrop() {
        if (isFinishing()) {
            setResult(RESULT_CANCELED);
            return;
        }
        dialog = ProgressDialog.show(
                this, null, getResources().getString(R.string.crop__wait), true, false);
        imageView.setImageRotateBitmapResetBase(rotateBitmap, true);

        Observable.create(new ObservableOnSubscribe<CountDownLatch>() {
            @Override
            public void subscribe(ObservableEmitter<CountDownLatch> e) throws Exception {
                final CountDownLatch latch = new CountDownLatch(1);
                e.onNext(latch);
                try {
                    latch.await();
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
                new Cropper().crop();
                e.onComplete();
            }
        }).observeOn(Schedulers.io()).subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<CountDownLatch>() {
                    @Override
                    public void accept(CountDownLatch o) throws Exception {
                        if (imageView.getScale() == 1F) {
                            imageView.center();
                        }
                        o.countDown();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {
                        if (dialog != null) {
                            dialog.dismiss();
                        }
                    }
                });

    }

    private class Cropper {
        private void makeDefault() {
            if (rotateBitmap == null) {
                return;
            }

            HighlightView hv = new HighlightView(imageView);
            if (optionData.color != 0) {
                hv.setHighlightColor(optionData.color);
            }
            hv.setShowCircle(optionData.showCircle);

            final int width = rotateBitmap.getWidth();
            final int height = rotateBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            // Make the default size about 4/5 of the width or height
            int cropWidth = Math.min(width, height) * 4 / 5;
            @SuppressWarnings("SuspiciousNameCombination")
            int cropHeight = cropWidth;

            if (optionData.cropWidth != 0 && optionData.cropHeight != 0) {
                if (optionData.cropWidth > optionData.cropHeight) {
                    cropHeight = cropWidth * optionData.cropHeight / optionData.cropWidth;
                } else {
                    cropWidth = cropHeight * optionData.cropWidth / optionData.cropHeight;
                }
            }
            int x = (width - cropWidth) / 2;
            int y = (height - cropHeight) / 2;
            RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
            hv.setup(imageView.getUnrotatedMatrix(), imageRect, cropRect, (optionData.cropWidth != 0 && optionData.cropHeight != 0) || optionData.showCircle);
            imageView.add(hv);
        }

        public void crop() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    makeDefault();
                    imageView.invalidate();
                    if (imageView.highlightViews.size() == 1) {
                        cropView = imageView.highlightViews.get(0);
                        cropView.setFocus(true);
                    }
                }
            });
        }
    }

    private void onSaveClicked() {
        if (cropView == null || isSaving) {
            return;
        }
        isSaving = true;

        Bitmap croppedImage;
        Rect r = cropView.getScaledCropRect(sampleSize);
        int width = r.width();
        int height = r.height();

        int outWidth = width;
        int outHeight = height;
        if (optionData.outMaxWidth > 0 && optionData.outMaxHeight > 0 && (width > optionData.outMaxWidth || height > optionData.outMaxHeight)) {
            float ratio = (float) width / (float) height;
            if ((float) optionData.outMaxWidth / (float) optionData.outMaxHeight > ratio) {
                outHeight = optionData.outMaxHeight;
                outWidth = (int) ((float) optionData.outMaxHeight * ratio + .5f);
            } else {
                outWidth = optionData.outMaxWidth;
                outHeight = (int) ((float) optionData.outMaxWidth / ratio + .5f);
            }
        }

        try {
            croppedImage = decodeRegionCrop(r, outWidth, outHeight);
        } catch (IllegalArgumentException e) {
            setResultException(e);
            finish();
            return;
        }

        if (croppedImage != null) {
            imageView.setImageRotateBitmapResetBase(new RotateBitmap(croppedImage, exifRotation), true);
            imageView.center();
            imageView.highlightViews.clear();
        }
        saveImage(croppedImage);
    }

    private void saveImage(final Bitmap croppedImage) {
        if (croppedImage != null) {
            dialog = ProgressDialog.show(
                    this, null, getResources().getString(R.string.crop__saving), true, false);
            Observable.create(new ObservableOnSubscribe<Bitmap>() {
                @Override
                public void subscribe(ObservableEmitter<Bitmap> e) throws Exception {
                    saveOutput(croppedImage);
                }
            }).observeOn(Schedulers.io()).subscribeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<Bitmap>() {
                        @Override
                        public void accept(Bitmap bitmap) throws Exception {

                        }
                    });
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private Bitmap decodeRegionCrop(Rect rect, int outWidth, int outHeight) {
        // Release memory now
        clearImageView();

        InputStream is = null;
        Bitmap croppedImage = null;
        try {
            is = getContentResolver().openInputStream(optionData.source);
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
            final int width = decoder.getWidth();
            final int height = decoder.getHeight();

            if (exifRotation != 0) {
                // Adjust crop area to account for image rotation
                Matrix matrix = new Matrix();
                matrix.setRotate(-exifRotation);

                RectF adjusted = new RectF();
                matrix.mapRect(adjusted, new RectF(rect));

                // Adjust to account for origin at 0,0
                adjusted.offset(adjusted.left < 0 ? width : 0, adjusted.top < 0 ? height : 0);
                rect = new Rect((int) adjusted.left, (int) adjusted.top, (int) adjusted.right, (int) adjusted.bottom);
            }

            try {
                croppedImage = decoder.decodeRegion(rect, new BitmapFactory.Options());
                if (croppedImage != null && (rect.width() > outWidth || rect.height() > outHeight)) {
                    Matrix matrix = new Matrix();
                    matrix.postScale((float) outWidth / rect.width(), (float) outHeight / rect.height());
                    croppedImage = Bitmap.createBitmap(croppedImage, 0, 0, croppedImage.getWidth(), croppedImage.getHeight(), matrix, true);
                }
            } catch (IllegalArgumentException e) {
                // Rethrow with some extra information
                throw new IllegalArgumentException("Rectangle " + rect + " is outside of the image ("
                        + width + "," + height + "," + exifRotation + ")", e);
            }

        } catch (IOException e) {
            Log.e("Error cropping image: " + e.getMessage(), e.toString());
            setResultException(e);
        } catch (OutOfMemoryError e) {
            Log.e("OOM cropping image: " + e.getMessage(), e.toString());
            setResultException(e);
        } finally {
            CropUtil.closeSilently(is);
        }
        return croppedImage;
    }

    private void clearImageView() {
        imageView.clear();
        if (rotateBitmap != null) {
            rotateBitmap.recycle();
        }
        System.gc();
    }

    /**
     * 保存图片
     *
     * @param croppedImage 裁剪后的图片
     */
    private void saveOutput(Bitmap croppedImage) {
        if (optionData.saveUri != null) {
            OutputStream outputStream = null;
            try {
                outputStream = getContentResolver().openOutputStream(optionData.saveUri);
                if (outputStream != null) {
                    croppedImage.compress(optionData.outAsPng ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                            90,
                            outputStream);
                }


                CropUtil.copyExifRotation(
                        CropUtil.getFromMediaUri(this, getContentResolver(), optionData.source),
                        CropUtil.getFromMediaUri(this, getContentResolver(), optionData.saveUri)
                );
                setResultUri(optionData.saveUri);
            } catch (IOException e) {
                setResultException(e);
            } finally {
                CropUtil.closeSilently(outputStream);
            }
        }
        final Bitmap b = croppedImage;
        handler.post(new Runnable() {
            @Override
            public void run() {
                imageView.clear();
                b.recycle();
            }
        });
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rotateBitmap != null) {
            rotateBitmap.recycle();
        }
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (rotateBitmap != null) {
            rotateBitmap.recycle();
        }
        if (dialog != null) {
            dialog.dismiss();
        }
        setResult(RESULT_CANCELED);
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    public boolean isSaving() {
        return isSaving;
    }

    private void setResultUri(Uri uri) {
        setResult(RESULT_OK, new Intent().putExtra(MediaStore.EXTRA_OUTPUT, uri));
    }

    private void setResultException(Throwable throwable) {
        setResult(Crop.RESULT_ERROR, new Intent().putExtra(IntentKey.EXTRA_ERROR, throwable));
    }

}
