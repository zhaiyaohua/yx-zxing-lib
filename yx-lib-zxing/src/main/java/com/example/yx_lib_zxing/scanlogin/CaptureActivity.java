package com.example.yx_lib_zxing.scanlogin;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.yx_lib_zxing.R;
import com.example.yx_lib_zxing.scanlogin.android.BeepManager;
import com.example.yx_lib_zxing.scanlogin.android.CaptureActivityHandler;
import com.example.yx_lib_zxing.scanlogin.android.DecodeFormatManager;
import com.example.yx_lib_zxing.scanlogin.android.InactivityTimer;
import com.example.yx_lib_zxing.scanlogin.android.Intents;
import com.example.yx_lib_zxing.scanlogin.android.ResultHandler;
import com.example.yx_lib_zxing.scanlogin.android.ResultHandlerFactory;
import com.example.yx_lib_zxing.scanlogin.android.ScanConstants;
import com.example.yx_lib_zxing.scanlogin.android.ViewfinderView;
import com.example.yx_lib_zxing.scanlogin.camera.CameraManager;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Created by rongkaifang
 * @date Created at 2:57 PM on 2020/11/17
 * @description
 */
public class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private CameraManager cameraManager;
    private ViewfinderView viewfinderView;
    private CaptureActivityHandler handler;

    private static final String PRODUCT_SEARCH_URL_PREFIX = "http://www.google";
    private static final String PRODUCT_SEARCH_URL_SUFFIX = "/m/products/scan";
    private static final String[] ZXING_URLS = {"http://zxing.appspot.com/scan", "zxing://scan/"};
    private Result savedResultToShow;
    private boolean hasSurface;
    private String sourceUrl;
    private Collection<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;
    protected CharSequence displayContents;//扫描结果
    public LinearLayout llCaptureTopContains;

    private boolean hasFlashLight = false;
    private Camera camera = null;

    private static final String TAG = CaptureActivity.class.getSimpleName();

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }

    private ShowMessagePermission showMessagePermission;

    public void setShowMessagePermission(ShowMessagePermission showMessagePermission) {
        this.showMessagePermission = showMessagePermission;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(getResources().getColor(R.color.c13));
        }
        setContentView(R.layout.capture);

        llCaptureTopContains = findViewById(R.id.llCaptureTopContains);
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        PackageManager pm = this.getPackageManager();
        hasFlashLight = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
        Log.v("wsg", "hasFlashLight ?? " + hasFlashLight);
    }

    /*
     * 取消与物理返回键需要回调code RESULT_CANCELED
     * */
    public void backIntentResult() {
        Intent data = new Intent();
        setResult(RESULT_CANCELED, data);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backIntentResult();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewfinderView = findViewById(R.id.viewfinder_view);
        cameraManager = new CameraManager(getApplication());
        viewfinderView.setCameraManager(cameraManager);
        handler = null;
        viewfinderView.setVisibility(View.VISIBLE);
        beepManager.updatePrefs();
        inactivityTimer.onResume();
        SurfaceView surfaceView = findViewById(R.id.preview_view);
        setSurfaceViewTouchListener(surfaceView);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        Intent intent = getIntent();
        decodeFormats = null;
        characterSet = null;
        if (intent != null) {
            String action = intent.getAction();
            String dataString = intent.getDataString();
            if (Intents.Scan.ACTION.equals(action)) {
                decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
                if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
                    int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
                    int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
                    if (width > 0 && height > 0) {
                        cameraManager.setManualFramingRect(width, height);
                    }
                }
            } else if (dataString != null &&
                    dataString.contains(PRODUCT_SEARCH_URL_PREFIX) &&
                    dataString.contains(PRODUCT_SEARCH_URL_SUFFIX)) {
                sourceUrl = dataString;
                decodeFormats = DecodeFormatManager.PRODUCT_FORMATS;
            } else if (isZxingURL(dataString)) {
                sourceUrl = dataString;
                Uri inputUri = Uri.parse(sourceUrl);
                decodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri);
            }
            characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);
        }
        getViewfinderView().setOnFlashLightStateChangeListener(new ViewfinderView.onFlashLightStateChangeListener() {
            @Override
            public void openFlashLight(boolean open) {
                turnOnFlashLight(open);
                getViewfinderView().reOnDraw();
            }
        });
    }

    protected void turnOnFlashLight(boolean open) {
        // 带闪光灯
        if (hasFlashLight) {
            if (!open) {
                if (camera == null) {
                    camera = CameraManager.getCamera();
                }
                Camera.Parameters parameters = camera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
                // tvFlash.setText(R.string.flash);
            } else {
                try {
                    if (camera == null) {
                        camera = CameraManager.getCamera();
                    }
                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    camera.setParameters(parameters);
                    // tvFlash.setText(R.string.flash);
                } catch (Exception e) {
                    Toast.makeText(CaptureActivity.this, "您的设备不支持闪光灯", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(CaptureActivity.this, "您的设备不支持闪光灯", Toast.LENGTH_SHORT).show();
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            cameraManager.openDriver(surfaceHolder);
            // Creating the handler starts the preview, which can also throw a RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, decodeFormats, characterSet, cameraManager);
            }
            decodeOrStoreSavedBitmap(null, null);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            showDialog();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            showDialog();
        }
    }

    private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
        if (handler == null) {
            savedResultToShow = result;
        } else {
            if (result != null) {
                savedResultToShow = result;
            }
            if (savedResultToShow != null) {
                Message message = Message.obtain(handler, R.id.decode_succeeded, savedResultToShow);
                handler.sendMessage(message);
            }
            savedResultToShow = null;
        }
    }

    public void handleDecode(Result rawResult, Bitmap barcode) {
        inactivityTimer.onActivity();
        ResultHandler resultHandler = ResultHandlerFactory.makeResultHandler(this, rawResult);
        if (barcode == null) {
            // This is from history -- no saved barcode
            handleDecodeInternally(resultHandler, null);
        } else {
            beepManager.playBeepSoundAndVibrate();
            drawResultPoints(barcode, rawResult);
            handleDecodeInternally(resultHandler, barcode);
        }
    }

    private void handleDecodeInternally(ResultHandler resultHandler, Bitmap barcode) {
        viewfinderView.setVisibility(View.GONE);
        displayContents = resultHandler.getDisplayContents();
        /*
         * 回调扫码的结果
         * */
        Intent data = new Intent();
        data.putExtra("CaptureResult", displayContents);
        setResult(ScanMethodUtil.REQUESTACTIVITYCODE, data);
        finish();
    }

    private static boolean isZxingURL(String dataString) {
        if (dataString == null) {
            return false;
        }
        for (String url : ZXING_URLS) {
            if (dataString.startsWith(url)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    private void drawResultPoints(Bitmap barcode, Result rawResult) {
        ResultPoint[] points = rawResult.getResultPoints();
        if (points != null && points.length > 0) {
            Canvas canvas = new Canvas(barcode);
            Paint paint = new Paint();
            paint.setColor(getResources().getColor(R.color.c01));
            paint.setStrokeWidth(3.0f);
            paint.setStyle(Paint.Style.STROKE);
            Rect border = new Rect(2, 2, barcode.getWidth() - 2, barcode.getHeight() - 2);
            canvas.drawRect(border, paint);

            paint.setColor(getResources().getColor(R.color.b24));
            if (points.length == 2) {
                paint.setStrokeWidth(4.0f);
                drawLine(canvas, paint, points[0], points[1]);
            } else if (points.length == 4 &&
                    (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A ||
                            rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
                // Hacky special case -- draw two lines, for the barcode and metadata
                drawLine(canvas, paint, points[0], points[1]);
                drawLine(canvas, paint, points[2], points[3]);
            } else {
                paint.setStrokeWidth(10.0f);
                for (ResultPoint point : points) {
                    canvas.drawPoint(point.getX(), point.getY(), paint);
                }
            }
        }
    }

    private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b) {
        canvas.drawLine(a.getX(), a.getY(), b.getX(), b.getY(), paint);
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    private float oldDist = 1f;

    @SuppressLint("ClickableViewAccessibility")
    private void setSurfaceViewTouchListener(SurfaceView surfaceView) {
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getPointerCount() > 1) {
                    switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_POINTER_DOWN:
                            oldDist = getFingerSpacing(motionEvent);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float newDist = getFingerSpacing(motionEvent);
                            if (newDist > oldDist) {
                                handleZoom(true);
                            } else if (newDist < oldDist) {
                                handleZoom(false);
                            }
                            oldDist = newDist;
                            break;
                    }
                }
                return true;
            }
        });
    }

    private static float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void handleZoom(boolean isZoomIn) {
        Camera camera = CameraManager.getCamera();
        if (camera == null) return;
        Camera.Parameters params = camera.getParameters();
        if (params.isZoomSupported()) {
            int maxZoom = params.getMaxZoom();
            int zoom = params.getZoom();
            if (isZoomIn && zoom < maxZoom) {
                zoom++;
            } else if (zoom > 0) {
                zoom--;
            }
            params.setZoom(zoom);
            camera.setParameters(params);
        } else {
            Log.i(TAG, "zoom not supported");
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        cameraManager.closeDriver();
        if (!hasSurface) {
            SurfaceView surfaceView = findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        ScanConstants.isWeakLight = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        ScanConstants.isWeakLight = false;
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ScanMethodUtil.REQUESTPERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {//允许后，刷新
                this.onResume();
            } else {
                showDialog();
            }
        }
    }

    public void showDialog() {
        if (showMessagePermission != null) {
            showMessagePermission.showMessageDialog();
        } else {
            Toast.makeText(this, "未实现ShowMessagePermission接口方法", Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * 设置
     * */
    public void startAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
