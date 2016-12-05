package com.hezd.example.camera.activity;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.hezd.example.camera.R;
import com.hezd.example.camera.view.CameraSurfaceView;
import com.ym.idcard.reg.bean.IDCard;
import com.ym.idcard.reg.engine.OcrEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private RelativeLayout mScanRl;
    private Button mTakePicBtn;
    private Camera mCameraInstance;
    private View mScanV;

    // 扫描区域属性
    private int mScaLeft;
    private int mScanTop;
    private int mScanRight;
    private int mScanBottom;
    private int mScanWidth;
    private int mScanHeight;
    private ImageView mPicIv;
    private FrameLayout mSurfaceContainerFl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getViews();
        setViews();
        setListeners();
    }

    private void getViews() {
        mSurfaceContainerFl = (FrameLayout) findViewById(R.id.fl_surfaceview_container);
        mPicIv = (ImageView) findViewById(R.id.iv_pic);
        mScanRl = (RelativeLayout) findViewById(R.id.rl_scan);
        mScanV = findViewById(R.id.v_scan);
        mTakePicBtn = (Button) findViewById(R.id.btn_take_pic);
        mCameraInstance = getCameraInstance();
        CameraSurfaceView mSurfaceView = new CameraSurfaceView(this, mCameraInstance);
        mSurfaceContainerFl.addView(mSurfaceView);
    }

    private Camera getCameraInstance() {
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            showMessage("this device has no camera!");
            return null;
        }
        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return camera;
    }

    private void showMessage(String s) {
        Toast.makeText(this,s,Toast.LENGTH_SHORT).show();
    }

    private void setViews() {
        mScanV.post(new Runnable() {
            @Override
            public void run() {
                mScaLeft = mScanV.getLeft();
                mScanTop = mScanV.getTop();
                mScanRight = mScanV.getRight();
                mScanBottom = mScanV.getBottom();
                mScanWidth = mScanV.getWidth();
                mScanHeight = mScanV.getHeight();
                Log.d(TAG,"getleft value:"+mScaLeft);
                Log.d(TAG,"gettop value:"+mScanTop);
                Log.d(TAG,"getright value:"+mScanRight);
                Log.d(TAG,"getbottom value:"+mScanBottom);
                Log.d(TAG,"getwidth value:"+mScanWidth);
                Log.d(TAG,"getheight value:"+ mScanHeight);
            }
        });
    }

    private void setListeners() {
        mTakePicBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_take_pic:
                /**拍照并识别身份证，还有一种方式是previewcallback方式，周期性获取某一帧图片解析
                 * 判断是否解析成功的方法是判断身份证是否包含wrong number字符串，因为解析身份证错误时
                 * 会包含这个字符串
                 * */
                takePic();
                break;
        }
    }

    private void takePic() {

        mCameraInstance.takePicture(null, null,  new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
//                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                /**
                 * 图片截取的原理，我们预览surfaceview是全屏的，扫描区域是屏幕中的一部分.所以最终
                 * 需要截取图片，但是有一些需要注意的问题。
                 * 因为拍照图片尺寸跟扫描框父窗体尺寸是不一样的
                 * 要先缩放照片尺寸与扫描框父窗体一样在进行裁剪
                 * 另外还有一个问题，一定要设置预览分辨率否则拍摄照片是变形的。
                 * 预览的坐标是横屏的xy轴坐标，所以如果是竖屏对图片处理时需要先做旋转操作
                 * 分辨率设置方法是获取设备支持的预览分辨率利用最小差值法获取最优分辨率并设置
                 * 还有一种解决办法有人说是是设置拍照图片分辨率和预览分辨率，我没有尝试。
                 * */
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data,0,data.length,options);
                options.inJustDecodeBounds = false;

                try {
                    int outWidth = options.outWidth;
                    int outHeight = options.outHeight;
                    int count = outHeight+outWidth;
                    int bitmapW = outWidth<outHeight?outWidth:outHeight;
                    int bitmapH = count-bitmapW;
                    float difW = (float)mSurfaceContainerFl.getWidth()/bitmapW;
                    float difH = (float)mSurfaceContainerFl.getHeight()/bitmapH;


                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);
                    matrix.postScale(difW,difH);

                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    Bitmap scaleBitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);

                    // 图片截取两种方式，createbitmap或者bitmapRegionDecoder
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    scaleBitmap.compress(Bitmap.CompressFormat.JPEG,100,bos);
                    Rect rect = new Rect(mScaLeft,mScanTop,mScanRight,mScanBottom);
                    byte[] bitmapBytes = bos.toByteArray();
                    BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(bitmapBytes,0,bitmapBytes.length,false);
                    Bitmap cropBtimap = decoder.decodeRegion(rect, options);

                    ByteArrayOutputStream cropStream = new ByteArrayOutputStream();
                    cropBtimap.compress(Bitmap.CompressFormat.JPEG,100,cropStream);
                    /*身份证扫描核心代码*/
                    OcrEngine ocrEngine = new OcrEngine();
                    IDCard idCard = ocrEngine.recognize(MainActivity.this, 2, cropStream.toByteArray(), null);
                    Log.d("hezd","idcard info:"+idCard.toString());
                    mPicIv.setImageBitmap(cropBtimap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                mCameraInstance.startPreview();

            }
        });
    }
}
