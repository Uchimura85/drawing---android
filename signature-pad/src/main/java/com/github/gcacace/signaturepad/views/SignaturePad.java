package com.github.gcacace.signaturepad.views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;

import com.github.gcacace.signaturepad.R;
import com.github.gcacace.signaturepad.utils.Bezier;
import com.github.gcacace.signaturepad.utils.ControlTimedPoints;
import com.github.gcacace.signaturepad.utils.Point;
import com.github.gcacace.signaturepad.utils.SvgBuilder;
import com.github.gcacace.signaturepad.utils.TimedPoint;
import com.github.gcacace.signaturepad.view.ViewCompat;
import com.github.gcacace.signaturepad.view.ViewTreeObserverCompat;

import java.util.ArrayList;
import java.util.List;

public class SignaturePad extends View {

    public ArrayList<Point> mPts;
    private int intevalPixel;
    private int chartMode;

    private final int ECG_MODE = 1;

    //View state
    private List<TimedPoint> mPoints;
    private boolean mIsEmpty;
    private float mLastTouchX;
    private float mLastTouchY;
    private float mLastVelocity;
    private float mLastWidth;
    private RectF mDirtyRect;

    private final SvgBuilder mSvgBuilder = new SvgBuilder();

    // Cache
    private List<TimedPoint> mPointsCache = new ArrayList<>();
    private ControlTimedPoints mControlTimedPointsCached = new ControlTimedPoints();
    private Bezier mBezierCached = new Bezier();

    //Configurable parameters
    private int mMinWidth;
    private int mMaxWidth;
    private float mVelocityFilterWeight;
    private OnSignedListener mOnSignedListener;
    private boolean mClearOnDoubleClick;

    //Click values
    private long mFirstClick;
    private int mCountClick;
    private static final int DOUBLE_CLICK_DELAY_MS = 200;

    //Default attribute values
    private final static int DEFAULT_ATTR_PEN_MIN_WIDTH_PX = 3;
    private final static int DEFAULT_ATTR_PEN_MAX_WIDTH_PX = 7;
    private final static int DEFAULT_ATTR_PEN_COLOR = Color.BLACK;
    private final static float DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT = 0.9f;
    private final static boolean DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK = false;

    private Paint mPaintECG = new Paint();
    private Paint mPaintSpider = new Paint();

    private Paint mPaintHRT = new Paint();
    private Paint mPaintCALM = new Paint();
    private Paint mPaint = new Paint();
    private Paint mPaintMotion = new Paint();

    private ScaleGestureDetector mScaleDetector;
    private boolean isZoom = false;
    private float prevY = 0;
    private float curY = 0;
    private float offsetY = 0;
    private boolean twoFingure = false;
    private float prevPinchYSpace = 0;
    private float curPinchYSpace = 0;
    private boolean vertZoom = false;
    private float mZoom = 0;
    private float mScaleFactorX = 1.0f;
    private float mScaleFactorY = 1.0f;

    private float mPreScaleFactor = 1.f;
    private int plotType = 3;
    private Bitmap mSignatureBitmap = null;
    private Canvas mSignatureBitmapCanvas = null;

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSignatureBitmap != null) {
            canvas.drawBitmap(mSignatureBitmap, 0, 0, mPaint);
        }
//        mPaint.setTextSize(30);
//        canvas.drawText( ""+this.plotType, 20,40,mPaint);
    }

    private void clearPoints() {
        mPts.clear();
    }

    public void setPts(float[] arr) {
        clearPoints();
        for (int i = 0; i < arr.length; i++) {
            Point newPt = new Point();
            newPt.set(arr[i], 0);
            mPts.add(newPt);
        }
        this.update();
    }

    public void setPts(float[] arr, int[] grid_lines) {
        clearPoints();
        for (int i = 0; i < arr.length; i++) {
            mPts.add(new Point(arr[i], grid_lines[i]));
        }
        this.update();
    }

    public void setPts(float[] arr, int[] grid_lines, List<String> arr_time) {

        clearPoints();
        for (int i = 0; i < arr.length; i++) {
            mPts.add(new Point(arr[i], grid_lines[i], arr_time.get(i)));
        }
        this.update();
    }

    public void update() {
        initPainter();
        switch (this.plotType) {
            case 0: // ecg
                updatePaintECG();
                break;
            case 1://Camlness
                this.MAXHEIGHT = 200;
                updatePaintCalm();
                break;
            case 2://motion
                this.MAXHEIGHT = 200;
                updatePaintMotion();
                break;
            case 3://Heart rate
                this.MAXHEIGHT = 200;
                updatePaintHrt();
                break;
            case 4://ECG with Grid
                initPainter();
                updatePaintECGWithGrid();
                break;
            case 5://Spider
                updateSpiderChart();
                break;
            case 6:// sleep map
                this.MAXHEIGHT = 200;
                updatePaintSleepMap();
                break;
            case 7:// motion intensity
                updateMotionIntensityChart();
                break;
            case 8:// Calmness intensity
                updateCalmnessChart();
                break;

            default:
                updatePaintECG();
        }
    }

    public void updateMotionIntensityChart() {

        if (this.mPts.size() > 3) {
            ensureSignatureBitmap();
            if (mSignatureBitmapCanvas != null) {
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            float radius = mPts.get(0).val;
            float strokeValue = mPts.get(1).val;
            float realX = mPts.get(2).val;
            realX = 360 - realX;

            float originX = getWidth() / 2;
            float originY = getHeight() / 2;
            radius = radius < originY ? radius : originY * 0.9f;
            radius = radius < originX ? radius : originX * 0.9f;
            double PI = Math.PI;
            if (mSignatureBitmapCanvas != null) {
                LinearGradient linearGradient1 = new LinearGradient(0, 0, 0, getHeight(),
                        new int[]{
                                Color.rgb(0xF0, 0xF6, 0x06),
                                Color.rgb(0xF0, 0xF6, 0x06),
                                Color.rgb(0xA2, 0x28, 0x00),
                                Color.rgb(0xA2, 0x28, 0x00),
                        },
                        new float[]{
                                0, 0.3f, 0.7f, 1},
                        Shader.TileMode.MIRROR);
                Shader shaderGradient1 = linearGradient1;
                LinearGradient linearGradient2 = new LinearGradient(0, 0, 0, getHeight(),
                        new int[]{
                                Color.rgb(0xFB, 0x04, 0x04),
                                Color.rgb(0xA2, 0x28, 0x00),
                                Color.rgb(0xA2, 0x28, 0x00)},
                        new float[]{
                                0, 0.3f, 1},
                        Shader.TileMode.MIRROR);
                Shader shaderGradient2 = linearGradient2;

                Paint paintGradient1 = new Paint();
                paintGradient1.setStrokeWidth(strokeValue);
                paintGradient1.setShader(shaderGradient1);
                paintGradient1.setStyle(Paint.Style.STROKE);
                paintGradient1.setAntiAlias(true);

                Paint paintGradient2 = new Paint();
                paintGradient2.setStrokeWidth(strokeValue);
                paintGradient2.setShader(shaderGradient2);
                paintGradient2.setStyle(Paint.Style.STROKE);
                paintGradient2.setAntiAlias(true);

                Paint paintBack = new Paint();
                paintBack.setStyle(Paint.Style.STROKE);
                paintBack.setStrokeWidth(strokeValue + 0.7f);
                paintBack.setColor(Color.rgb(0xAA, 0xAA, 0xAA));
                paintBack.setAntiAlias(true);


                final RectF oval = new RectF();
                android.graphics.Point point1 = new android.graphics.Point((int) originX, (int) originY);
                oval.set(point1.x - radius, point1.y - radius, point1.x + radius, point1.y + radius);
                Path pathGradient1 = new Path();
                Path pathGradient2 = new Path();
                Path path3 = new Path();


                pathGradient1.arcTo(oval, -90, 180, true);
                pathGradient2.arcTo(oval, -90, -180, true);
                path3.arcTo(oval, -90, -realX, true);
                mSignatureBitmapCanvas.drawPath(pathGradient1, paintGradient1);
                mSignatureBitmapCanvas.drawPath(pathGradient2, paintGradient2);
                mSignatureBitmapCanvas.drawPath(path3, paintBack);

                invalidate();
            }
        }
    }

    public void updateCalmnessChart() {
        if (this.mPts.size() > 4) {
            ensureSignatureBitmap();
            if (mSignatureBitmapCanvas != null) {
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            float radius = mPts.get(0).val;
            float strokeValue = mPts.get(1).val;
            float realX = mPts.get(2).val;
            realX = 360 - realX;
            float calmValue = mPts.get(3).val;
            float calmValueF = mPts.get(4).val;

            float originX = getWidth() / 2;
            float originY = getHeight() / 2;
            radius = radius < originY ? radius : originY * 0.9f;
            radius = radius < originX ? radius : originX * 0.9f;
            double PI = Math.PI;
            if (mSignatureBitmapCanvas != null) {
                LinearGradient linearGradient1 = new LinearGradient(0, 0, 0, getHeight(),
                        new int[]{
                                Color.rgb(0xF0, 0xF6, 0x06),
                                Color.rgb(0xF0, 0xF6, 0x06),
                                Color.rgb(0xA2, 0x28, 0x00),
                                Color.rgb(0xA2, 0x28, 0x00),
                        },
                        new float[]{
                                0, 0.3f, 0.7f, 1},
                        Shader.TileMode.MIRROR);
                Shader shaderGradient1 = linearGradient1;
                LinearGradient linearGradient2 = new LinearGradient(0, 0, 0, getHeight(),
                        new int[]{
                                Color.rgb(0xFB, 0x04, 0x04),
                                Color.rgb(0xA2, 0x28, 0x00),
                                Color.rgb(0xA2, 0x28, 0x00)},
                        new float[]{
                                0, 0.3f, 1},
                        Shader.TileMode.MIRROR);
                Shader shaderGradient2 = linearGradient2;

                Paint paintCalmTxt = new Paint();
                paintCalmTxt.setTextSize(radius * 0.5f);
                paintCalmTxt.setAntiAlias(true);
                paintCalmTxt.setTextAlign(Paint.Align.RIGHT);
                mSignatureBitmapCanvas.drawText("" + (int) calmValue, originX - radius * 0.2f, originY, paintCalmTxt);
                paintCalmTxt.setTextSize(radius * 0.3f);
                mSignatureBitmapCanvas.drawText("" + (int) calmValueF, originX - radius * 0.2f, originY + radius * 0.3f, paintCalmTxt);


                Paint paintGradient1 = new Paint();
                paintGradient1.setStrokeWidth(strokeValue);
                paintGradient1.setShader(shaderGradient1);
                paintGradient1.setStyle(Paint.Style.STROKE);
                paintGradient1.setAntiAlias(true);

                Paint paintGradient2 = new Paint();
                paintGradient2.setStrokeWidth(strokeValue);
                paintGradient2.setShader(shaderGradient2);
                paintGradient2.setStyle(Paint.Style.STROKE);
                paintGradient2.setAntiAlias(true);

                Paint paintBack = new Paint();
                paintBack.setStyle(Paint.Style.STROKE);
                paintBack.setStrokeWidth(strokeValue + 0.7f);
                paintBack.setColor(Color.rgb(0xD8, 0xD8, 0xD8));
                paintBack.setAntiAlias(true);

                final RectF oval = new RectF();
                android.graphics.Point point1 = new android.graphics.Point((int) originX, (int) originY);
                oval.set(point1.x - radius, point1.y - radius, point1.x + radius, point1.y + radius);
                Path pathGradient1 = new Path();
                Path pathGradient2 = new Path();
                Path path3 = new Path();

                pathGradient1.arcTo(oval, -90, 180, true);
                pathGradient2.arcTo(oval, -90, -180, true);
                path3.arcTo(oval, -90, -realX, true);
                mSignatureBitmapCanvas.drawPath(pathGradient1, paintGradient1);
                mSignatureBitmapCanvas.drawPath(pathGradient2, paintGradient2);
                mSignatureBitmapCanvas.drawPath(path3, paintBack);
                invalidate();
            }
        }
    }

    public void updateSpiderChart() {
        float[] levels = {4.3f, 3, 2, 1.7f, 3.7f, 1, 2, 3, 2.2f, 5};
        int height = this.getHeight();
        int MAXHEIGHT = 2500;
        int marginBottom = 30;
        int marginTop = 30;
        int plotHEIGHT = height - marginBottom - marginTop;

        if (this.mPts.size() > 0) {
            mPaintSpider.setAntiAlias(true);
            mPaintSpider.setStrokeWidth(4);
            mPaintSpider.setTextSize(20);
            mPaintSpider.setColor(Color.rgb(0xDC, 0xDC, 0xDC));

            ensureSignatureBitmap();

            if (mSignatureBitmapCanvas != null) {

                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }

            TimedPoint[][] coordinate = new TimedPoint[5][5];
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    coordinate[i][j] = new TimedPoint();

                }
            }

            float unit = 35;
            float originX = getWidth() / 2;
            float originY = getHeight() / 2;
            double PI = Math.PI;

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    float x, y;
                    x = (unit * (i + 1)) * (float) Math.cos(PI / 2 + 2 * PI / 5 * j);
                    y = -(unit * (i + 1)) * (float) Math.sin(PI / 2 + 2 * PI / 5 * j);
                    coordinate[i][j].set(x, y);
                }
            }

            for (int i = 0; i < 5; i++) {
                TimedPoint start = coordinate[i][0];
                for (int j = 1; j < 5; j++) {
                    TimedPoint end = coordinate[i][j];
                    mSignatureBitmapCanvas.drawLine(start.x + originX, start.y + originY, end.x + originX, end.y + originY, mPaintSpider);
                    start = end;
                }
                TimedPoint end = coordinate[i][0];
                mSignatureBitmapCanvas.drawLine(start.x + originX, start.y + originY, end.x + originX, end.y + originY, mPaintSpider);
            }
            String[] labels = {"Total Sleep Time", "Deep Sleep", "Sleep Onset", "Breathing", "HRV Recovery"};
            Paint mPaintText = new Paint();
            mPaintText.setTextSize(20);
            mPaintText.setColor(Color.rgb(0x93, 0x93, 0x93));

            for (int i = 0; i < 5; i++) {
                TimedPoint point = coordinate[4][i];
                mSignatureBitmapCanvas.drawLine(originX, originY, point.x + originX, point.y + originY, mPaintSpider);

                if (i == 0) {
                    float textWidth1 = mPaintSpider.measureText("Total Sleep");
                    mSignatureBitmapCanvas.drawText("Total Sleep", point.x + originX - textWidth1 / 2, point.y - 35 + originY, mPaintText);
                    float textWidth2 = mPaintSpider.measureText("Time");
                    mSignatureBitmapCanvas.drawText("Time", point.x + originX - textWidth2 / 2, point.y + originY - 10, mPaintText);

                } else {
                    float textWidth = mPaintSpider.measureText(labels[i]);
                    float textPosX = 0;
                    float textPosY = 0;
                    if (i == 1) { //deep
                        textPosX = -70;
                    } else if (i == 2) { //onset
                        textPosX = -50;
                        textPosY = 17;
                    } else if (i == 3) {//breathing
                        textPosX = 50;
                        textPosY = 17;
                    } else if (i == 4) {//hrv
                        textPosX = 70;
                        textPosY = 0;
                    }
                    mSignatureBitmapCanvas.drawText(labels[i], point.x + originX - textWidth / 2 + textPosX, point.y + originY + textPosY, mPaintText);
                }

            }

            mPaintSpider.setARGB(0xFF, 0xFA, 0x98, 0X41);
            float startX = (unit * levels[0]) * (float) Math.cos(PI / 2);
            float startY = -(unit * levels[0]) * (float) Math.sin(PI / 2);
            for (int i = 0; i < 5; i++) {
                float level = levels[i];
                float x, y;
                x = (unit * level) * (float) Math.cos(PI / 2 + 2 * PI / 5 * i);
                y = -(unit * level) * (float) Math.sin(PI / 2 + 2 * PI / 5 * i);
                mSignatureBitmapCanvas.drawLine(startX + originX, startY + originY, x + originX, y + originY, mPaintSpider);
                startX = x;
                startY = y;
            }
            float endX = (unit * levels[0]) * (float) Math.cos(PI / 2);
            float endY = -(unit * levels[0]) * (float) Math.sin(PI / 2);
            mSignatureBitmapCanvas.drawLine(startX + originX, startY + originY, endX + originX, endY + originY, mPaintSpider);


            mPaintSpider.setARGB(0xFF, 0x57, 0x97, 0xC3);
            startX = (unit * levels[5]) * (float) Math.cos(PI / 2);
            startY = -(unit * levels[5]) * (float) Math.sin(PI / 2);
            for (int i = 5; i < 10; i++) {
                float level = levels[i];
                float x, y;
                x = (unit * level) * (float) Math.cos(PI / 2 + 2 * PI / 5 * i);
                y = -(unit * level) * (float) Math.sin(PI / 2 + 2 * PI / 5 * i);
                mSignatureBitmapCanvas.drawLine(startX + originX, startY + originY, x + originX, y + originY, mPaintSpider);
                startX = x;
                startY = y;
            }
            endX = (unit * levels[5]) * (float) Math.cos(PI / 2);
            endY = -(unit * levels[5]) * (float) Math.sin(PI / 2);
            mSignatureBitmapCanvas.drawLine(startX + originX, startY + originY, endX + originX, endY + originY, mPaintSpider);

            invalidate();
        }
    }

    public void addPoint(float point) {
        if (this.mPts != null) {
            this.mPts.add(new Point(point));
            this.mPts.remove(0);

        }
    }

    public void addPoint(Point point) {
        if (this.mPts != null) {
            this.mPts.add(point);
            this.mPts.remove(0);

        }
    }

    public void addPoint(float point, int grid_level) {
        if (this.mPts != null) {
            Point newPt = new Point();
            newPt.set(point, grid_level);
            this.mPts.add(newPt);
            this.mPts.remove(0);

        }
    }

    public void addPoint(float point, int grid_level, String strTime) {
        if (this.mPts != null) {
            Point newPt = new Point();
            newPt.set(point, grid_level, strTime);
            this.mPts.add(newPt);
            this.mPts.remove(0);

        }
    }


    public void addGridLine(boolean bThick) {

    }

    public void updatePaintECG() {
        int height = this.getHeight();
        int MAXHEIGHT = 2500;
        int marginBottom = 0;
        int marginTop = 0;
        int plotHEIGHT = height - marginBottom - marginTop;
        if (this.mPts.size() > 0) {
            mPaintECG.setAntiAlias(true);
            mPaintECG.setStrokeWidth(4);

            mPaintECG.setColor(Color.rgb(0x32, 0xb5, 0xe5));

            ensureSignatureBitmap();
            float interval = (float) this.getWidth() / mPts.size();
            float originY = (float) height - marginBottom;
            Point from = this.mPts.get(0);
            if (mSignatureBitmapCanvas != null) {
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            for (int i = 1; i < this.mPts.size(); i++) {
                Point to = this.mPts.get(i);
                Paint p = new Paint();
                p.setColor(Color.RED);
                if (mSignatureBitmapCanvas != null) {
                    mSignatureBitmapCanvas.drawLine((i - 1) * interval, -from.val * plotHEIGHT / MAXHEIGHT + originY, i * interval, -to.val * plotHEIGHT / MAXHEIGHT + originY, mPaintECG);
                }
                from = to;
            }
            invalidate();
        }
    }

    int MAXHEIGHT = 2500;

    public void setMaxHeight(int maxHeight) {
        this.MAXHEIGHT = maxHeight;
    }

    int HEIGHT;
    int WIDTH;

    int marginLeft = 100;
    int marginRight = 30;

    int marginTop = 30;
    int marginBottom = 70;

    float LEFT = marginLeft;
    float RIGHT = WIDTH - marginRight;
    float TOP = marginTop;
    float BOTTOM = HEIGHT - marginBottom;
    int plotHEIGHT = HEIGHT - marginBottom - marginTop;
    int plotWIDTH = WIDTH - marginLeft - marginRight;
    float originY = (float) plotHEIGHT + TOP;
    float originX = (float) LEFT;

    Paint paintGridLarge;
    Paint transparentPaint;
    Paint paintXValue = new Paint();

    public void initPainter() {
        mPaintECG.setColor(Color.rgb(0x32, 0xb5, 0xe5));
        HEIGHT = this.getHeight();
        WIDTH = this.getWidth();
        Paint paintGridLarge = new Paint();
        paintGridLarge.setColor(Color.BLUE);
        paintGridLarge.setStrokeWidth(1.5f);
        paintGridLarge.setColor(Color.rgb(0xB6, 0xB6, 0xB6));
        mPaintECG.setAntiAlias(true);
        mPaintECG.setStrokeWidth(4);
        transparentPaint = new Paint();
        transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
        transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mPaintECG.setColor(Color.rgb(0x32, 0xb5, 0xe5));
        paintXValue = new Paint();
        paintXValue.setColor(Color.BLACK);
        paintXValue.setTextSize(20);
        paintXValue.setAntiAlias(true);
        paintXValue.setTextAlign(Paint.Align.RIGHT);
    }

    public void updatePaintEcgGrid() {
        ensureSignatureBitmap();

        float interval = (float) plotWIDTH / mPts.size() * mScaleFactorX;
        Point from = this.mPts.get(this.mPts.size() - 1);
        if (mSignatureBitmapCanvas != null) {
            // draw Horizontal grid line
            mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            mSignatureBitmapCanvas.drawLine(
                    originX,
                    -1000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                    plotWIDTH + originX,
                    -1000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                    paintGridLarge);
            mSignatureBitmapCanvas.drawLine(
                    originX,
                    -2000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                    plotWIDTH + originX,
                    -2000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                    paintGridLarge);
            mSignatureBitmapCanvas.drawLine(
                    originX,
                    -3000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                    plotWIDTH + originX,
                    -3000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                    paintGridLarge);
            if (mScaleFactorY >= 1.5f) {
                mSignatureBitmapCanvas.drawLine(
                        originX,
                        -500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        plotWIDTH + originX,
                        -500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        paintGridLarge);
                mSignatureBitmapCanvas.drawLine(
                        originX,
                        -1500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        plotWIDTH + originX,
                        -1500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        paintGridLarge);
                mSignatureBitmapCanvas.drawLine(
                        originX,
                        -2500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        plotWIDTH + originX,
                        -2500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        paintGridLarge);
            }
        }

        if (mSignatureBitmapCanvas != null) {

            mSignatureBitmapCanvas.drawRect(LEFT, TOP, RIGHT, BOTTOM, paintGridLarge);

            mSignatureBitmapCanvas.drawRect(0, 0, LEFT - 1, HEIGHT, transparentPaint);
            mSignatureBitmapCanvas.drawRect(RIGHT + 0.5f, 0, WIDTH, HEIGHT, transparentPaint);
            mSignatureBitmapCanvas.drawRect(0, 0, WIDTH, TOP - 0.5f, transparentPaint);
            mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);

            mSignatureBitmapCanvas.drawText("0", LEFT - 10, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
            mSignatureBitmapCanvas.drawText("1,000", LEFT - 10, -1020 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
            mSignatureBitmapCanvas.drawText("2,000", LEFT - 10, -2000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
            mSignatureBitmapCanvas.drawText("3,000", LEFT - 10, -3000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
            mSignatureBitmapCanvas.drawText("4,000", LEFT - 10, -4000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
            mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);
            int j = 1;
            for (int i = this.mPts.size() - 1; i >= 0; i--) {

                Point to = this.mPts.get(i);
                if (mSignatureBitmapCanvas != null) {
                    if (to.grid_level == 2) {
                        Paint paintTime = new Paint();
                        paintTime.setColor(Color.BLACK);
                        paintTime.setTextSize(20);
                        paintTime.setAntiAlias(true);
                        String timeStr = to.strTime;

                        float textWidth = paintTime.measureText(timeStr);
                        mSignatureBitmapCanvas.drawText(timeStr, -textWidth / 2 + getWidth() - j * interval, BOTTOM + 20, paintTime);
                    }
                }
                from = to;
                j++;
            }

            if (mScaleFactorY >= 1.5f) {
                mSignatureBitmapCanvas.drawText("1,500", LEFT - 10, -1500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                mSignatureBitmapCanvas.drawText("2,500", LEFT - 10, -2500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
//                    mSignatureBitmapCanvas.drawText("500", LEFT - 10, -500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
            }
        }
    }

    public void updatePaintECGWithGrid() {
        if (this.mPts.size() > 0) {
            ensureSignatureBitmap();
            float interval = (float) plotWIDTH / mPts.size() * mScaleFactorX;
            Point from = this.mPts.get(this.mPts.size() - 1);
            if (mSignatureBitmapCanvas != null) {
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }

            int j = 1;
            for (int i = this.mPts.size() - 1; i >= 0; i--) {
                Point to = this.mPts.get(i);
                if (mSignatureBitmapCanvas != null) {
                    if (to.grid_level == 2) {
                        if(paintGridLarge != null)
                        mSignatureBitmapCanvas.drawLine(getWidth() - j * interval, TOP, getWidth() - j * interval, BOTTOM, paintGridLarge);
                        else{
                            Log.d("painter", "null");
                        }
                    }

                    mSignatureBitmapCanvas.drawLine(
                            plotWIDTH - (j - 1) * interval + originX,
                            -from.val * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                            plotWIDTH - j * interval + originX,
                            -to.val * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                            mPaintECG);
                    Paint paintAF = new Paint();
                    paintAF.setColor(Color.RED);
                    mSignatureBitmapCanvas.drawPoint( plotWIDTH - (j - 1) * interval + originX,
                            -from.val * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                            paintAF);
                }
                from = to;
                j++;
            }
            if (mSignatureBitmapCanvas != null) {
                mSignatureBitmapCanvas.drawRect(LEFT, TOP, RIGHT, BOTTOM, paintGridLarge);
                mSignatureBitmapCanvas.drawRect(0, 0, LEFT - 1, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(RIGHT + 0.5f, 0, WIDTH, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, 0, WIDTH, TOP - 0.5f, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawText("0", LEFT - 10, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                mSignatureBitmapCanvas.drawText("1,000", LEFT - 10, -1020 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                mSignatureBitmapCanvas.drawText("2,000", LEFT - 10, -2000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                mSignatureBitmapCanvas.drawText("3,000", LEFT - 10, -3000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                mSignatureBitmapCanvas.drawText("4,000", LEFT - 10, -4000 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);
                j = 1;
                for (int i = this.mPts.size() - 1; i >= 0; i--) {
                    Point to = this.mPts.get(i);
                    if (mSignatureBitmapCanvas != null) {
                        if (to.grid_level == 2) {
                            Paint paintTime = new Paint();
                            paintTime.setColor(Color.BLACK);
                            paintTime.setTextSize(20);
                            paintTime.setAntiAlias(true);
                            String timeStr = to.strTime;

                            float textWidth = paintTime.measureText(timeStr);
                            mSignatureBitmapCanvas.drawText(timeStr, -textWidth / 2 + getWidth() - j * interval, BOTTOM + 20, paintTime);
                        }
                    }
                    from = to;
                    j++;
                }

                if (mScaleFactorY >= 1.5f) {
                    mSignatureBitmapCanvas.drawText("1,500", LEFT - 10, -1500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                    mSignatureBitmapCanvas.drawText("2,500", LEFT - 10, -2500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
//                    mSignatureBitmapCanvas.drawText("500", LEFT - 10, -500 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintXValue);
                }

                invalidate();
            }
        }
    }

    public void setHrtMark(int index, String str) {
        if (index < this.mPts.size()) {
            this.mPts.get(index).grid_level = 2;
            this.mPts.get(index).strTime = str;
            update();
        }

    }

    public void updatePaintHrt() {

        LinearGradient linearGradientHrt = new LinearGradient(0, 0, 0, getHeight(),
                new int[]{
                        Color.rgb(0xff, 0x4e, 0x4b),
                        Color.rgb(0xff, 0xae, 0x64),
                        Color.rgb(0xff, 0xbd, 0x62),
                        Color.rgb(0x84, 0xfc, 0x82),
                        Color.rgb(0xbc, 0xec, 0xc1),
                },
                new float[]{
                        0, 0.17f, 0.47f, 0.63f, 1},
                Shader.TileMode.MIRROR);
        Shader shaderHrt = linearGradientHrt;
        mPaintHRT.setShader(shaderHrt);
        mPaintHRT.setStrokeWidth(3);
        int HEIGHT = this.getHeight();
        int WIDTH = this.getWidth();

        int marginLeft = 100;
        int marginRight = 0;

        int marginTop = 30;
        int marginBottom = 20;

        float LEFT = marginLeft;
        float RIGHT = WIDTH - marginRight;
        float TOP = marginTop;
        float BOTTOM = HEIGHT - marginBottom;
        int plotHEIGHT = HEIGHT - marginBottom - marginTop;
        int plotWIDTH = WIDTH - marginLeft - marginRight;
        if (this.mPts.size() > 0) {
            mScaleFactorY = 1;
            mPaintHRT.setAntiAlias(true);
            mPaintHRT.setStrokeWidth(4);

            mPaintHRT.setColor(Color.rgb(0x32, 0xb5, 0xe5));
            //khs
            ensureSignatureBitmap();

            float originY = (float) plotHEIGHT + TOP;
            float originX = (float) LEFT;
            float interval = (float) plotWIDTH / mPts.size() * mScaleFactorX;
            Point from = this.mPts.get(this.mPts.size() - 1);
            if (mSignatureBitmapCanvas != null) {
                Paint paintGrid = new Paint();
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -40 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -40 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -80 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -80 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -120 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -120 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -160 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -160 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mPaintHRT.setTextSize(20);
                mPaintHRT.setAntiAlias(true);
//                mPaintHRT.setTextAlign(Paint.Align.RIGHT);
                int textMarginBottom = -6;
                mSignatureBitmapCanvas.drawText("Warm Up", LEFT - LEFT + 6, textMarginBottom + -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
                mSignatureBitmapCanvas.drawText("Fat Burn", LEFT - LEFT + 6, textMarginBottom + -40 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
                mSignatureBitmapCanvas.drawText("Cardio", LEFT - LEFT + 6, textMarginBottom + -80 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
                mSignatureBitmapCanvas.drawText("Hard", LEFT - LEFT + 6, textMarginBottom + -120 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
                mSignatureBitmapCanvas.drawText("Maximum", LEFT - LEFT + 6, textMarginBottom + -160 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);

            }
            int j = 1;
            mPoints = new ArrayList<>(); // init beizer(prevent cycle
            for (int i = this.mPts.size() - 1; i >= 0; i--) {
                Point to = this.mPts.get(i);
                if (mSignatureBitmapCanvas != null) {

                    float tmpX = plotWIDTH - (j - 1) * interval + originX;
                    float tmpY = -from.val * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2;
                    addPointBeizer(getNewPoint(tmpX, tmpY));
//                    mSignatureBitmapCanvas.drawCircle(tmpX,tmpY,10,mPaintHRT);
//                    mSignatureBitmapCanvas.drawText(""+j,tmpX,tmpY,mPaintHRT);
//                    mSignatureBitmapCanvas.drawLine((i - 1) * interval, -from.val * plotHEIGHT / MAXHEIGHT + originY, i * interval, -to.val * plotHEIGHT / MAXHEIGHT + originY, mPaintHRT);

                    if (to.grid_level == 2) {
                        Paint paintTime = new Paint();
                        paintTime.setColor(Color.WHITE);
                        paintTime.setTextSize(20);
                        paintTime.setAntiAlias(true);
                        String timeStr = to.strTime;
                        Path path = new Path();
                        path.moveTo(tmpX, tmpY);
                        path.lineTo(tmpX - 15, tmpY - 15);
                        path.lineTo(tmpX - 70, tmpY - 15);
                        path.lineTo(tmpX - 70, tmpY + 15);
                        path.lineTo(tmpX - 15, tmpY + 15);

                        Paint paintPolygon = new Paint();
                        paintPolygon.setStyle(Paint.Style.FILL);
                        paintPolygon.setColor(Color.rgb(0x97, 0x97, 0x97));

                        mSignatureBitmapCanvas.drawPath(path, paintPolygon);
                        float textWidth = paintTime.measureText(timeStr);
                        mSignatureBitmapCanvas.drawText(timeStr, -20 - textWidth + tmpX, 5 + tmpY, paintTime);
                    }
                }
                from = to;
                j++;
            }
            if (mSignatureBitmapCanvas != null) {
                Paint paintBorder = new Paint();
                paintBorder.setStyle(Paint.Style.STROKE);
                paintBorder.setColor(Color.rgb(0xB6, 0xB6, 0xB6));
                paintBorder.setStrokeWidth(1.5f);

//                mSignatureBitmapCanvas.drawRect(LEFT, TOP, RIGHT, BOTTOM, paintBorder);
//                mSignatureBitmapCanvas.clipRect(LEFT, 0, RIGHT, height, Region.Op.REPLACE);
                Paint transparentPaint = new Paint();
                transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
                transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                transparentPaint.setAntiAlias(true);

                Paint testPaint = new Paint();
                testPaint.setColor(Color.RED);
//                mSignatureBitmapCanvas.drawRect(0, 0, LEFT - 1, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(RIGHT + 0.5f, 0, WIDTH, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, 0, WIDTH, TOP - 0.5f, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);

                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);
                invalidate();
            }
        }
    }

    public void updatePaintHrtBeizer() {
        LinearGradient linearGradientHrt = new LinearGradient(0, 0, 0, getHeight(),
                new int[]{
                        Color.rgb(0xbc, 0xec, 0xc1),
                        Color.rgb(0x84, 0xfc, 0x82),
                        Color.rgb(0xff, 0xbd, 0x62),
                        Color.rgb(0xff, 0xae, 0x64),
                        Color.rgb(0xff, 0x4e, 0x4b),
                },
                new float[]{
                        0, 0.17f, 0.47f, 0.63f, 1},
                Shader.TileMode.MIRROR);
        Shader shaderHrt = linearGradientHrt;
        mPaintHRT.setShader(shaderHrt);
        mPaintHRT.setStrokeWidth(3);
        if (this.mPts.size() > 0) {
            ensureSignatureBitmap();
            float interval = (float) this.getWidth() / mPts.size();
            float originY = (float) this.getHeight() / 2;
            Point from = this.mPts.get(0);
            if (mSignatureBitmapCanvas != null) {
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            for (int i = 1; i < this.mPts.size(); i++) {
                Point to = this.mPts.get(i);
                Paint p = new Paint();
                p.setColor(Color.RED);
                if (mSignatureBitmapCanvas != null) {
                    addPointBeizer(getNewPoint((i - 1) * interval, -from.val + originY));
                }
                from = to;
            }
            invalidate();
        }
    }

    public void updatePaintCalm() {
        LinearGradient linearGradientCALM = new LinearGradient(0, 0, 0, getHeight(),
                new int[]{
                        Color.rgb(100, 235, 102),
                        Color.rgb(36, 226, 31),
                        Color.rgb(120, 236, 19),
                        Color.rgb(192, 244, 2),
                        Color.rgb(236, 187, 0),
                        Color.rgb(247, 160, 16)},
                new float[]{
                        0, 0.01f, 0.21f, 0.41f, 0.81f, 1},
                Shader.TileMode.MIRROR);
        Shader shaderCalm = linearGradientCALM;
        mPaintCALM.setShader(shaderCalm);
        mPaintCALM.setStrokeWidth(50);


        int HEIGHT = this.getHeight();
        int WIDTH = this.getWidth();

        int marginLeft = 100;
        int marginRight = 0;

        int marginTop = 30;
        int marginBottom = 70;

        float LEFT = marginLeft;
        float RIGHT = WIDTH - marginRight;
        float TOP = marginTop;
        float BOTTOM = HEIGHT - marginBottom;
        int plotHEIGHT = HEIGHT - marginBottom - marginTop;
        int plotWIDTH = WIDTH - marginLeft - marginRight;
        if (this.mPts.size() > 0) {
            mScaleFactorY = 1;
            mPaintCALM.setAntiAlias(true);
            //khs
            ensureSignatureBitmap();

            float originY = (float) plotHEIGHT + TOP;
            float originX = (float) LEFT;
            float interval = (float) plotWIDTH / mPts.size() * mScaleFactorX;
            Point from = this.mPts.get(this.mPts.size() - 1);
            if (mSignatureBitmapCanvas != null) {
                Paint paintGrid = new Paint();
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -100 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -100 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
            }
            for (int i = 0; i < this.mPts.size() - 1; i++) {
                Point to = this.mPts.get(i);
                if (mSignatureBitmapCanvas != null) {
                    mPaintCALM.setStyle(Paint.Style.FILL);
                    mSignatureBitmapCanvas.drawRect(LEFT + (i + 1) * interval, getHeight(), LEFT + (i + 2) * interval, mPts.get(i).val, mPaintCALM);
                }
                from = to;
            }
            if (mSignatureBitmapCanvas != null) {
                Paint paintBorder = new Paint();
                paintBorder.setStyle(Paint.Style.STROKE);
                paintBorder.setColor(Color.rgb(0xB6, 0xB6, 0xB6));
                paintBorder.setStrokeWidth(1.5f);

//                mSignatureBitmapCanvas.drawRect(LEFT, TOP, RIGHT, BOTTOM, paintBorder);
//                mSignatureBitmapCanvas.clipRect(LEFT, 0, RIGHT, height, Region.Op.REPLACE);
                Paint transparentPaint = new Paint();
                transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
                transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                transparentPaint.setAntiAlias(true);

                Paint testPaint = new Paint();
                testPaint.setColor(Color.RED);
//                mSignatureBitmapCanvas.drawRect(0, 0, LEFT - 1, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(RIGHT + 0.5f, 0, WIDTH, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, 0, WIDTH, TOP - 0.5f, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);

                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);
                invalidate();
            }
        }
    }

    public void updatePaintSleepMap() {

        LinearGradient linearGradientCALM = new LinearGradient(0, 0, 0, getHeight(),
                new int[]{
                        Color.rgb(4, 43, 214),
                        Color.rgb(0, 48, 172),
                        Color.rgb(0, 96, 119),
                        Color.rgb(6, 141, 64),
                        Color.rgb(15, 176, 9),
                        Color.rgb(129, 172, 23),
                        Color.rgb(136, 170, 21),

                },
                new float[]{
                        0, 0.18f, 0.38f, 0.57f, 0.78f, 0.98f, 1},
                Shader.TileMode.MIRROR);
        Shader shaderCalm = linearGradientCALM;
        mPaintCALM.setShader(shaderCalm);
        mPaintCALM.setStrokeWidth(50);

        int HEIGHT = this.getHeight();
        int WIDTH = this.getWidth();

        int marginLeft = 100;
        int marginRight = 0;

        int marginTop = 30;
        int marginBottom = 70;

        float LEFT = marginLeft;
        float RIGHT = WIDTH - marginRight;
        float TOP = marginTop;
        float BOTTOM = HEIGHT - marginBottom;
        int plotHEIGHT = HEIGHT - marginBottom - marginTop;
        int plotWIDTH = WIDTH - marginLeft - marginRight;
        if (this.mPts.size() > 0) {
            mScaleFactorY = 1;
            mPaintCALM.setAntiAlias(true);
            //khs
            ensureSignatureBitmap();

            float originY = (float) plotHEIGHT + TOP;
            float originX = (float) LEFT;
            float interval = (float) plotWIDTH / mPts.size() * mScaleFactorX;
            Point from = this.mPts.get(this.mPts.size() - 1);
            if (mSignatureBitmapCanvas != null) {
                Paint paintGrid = new Paint();
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -40 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -40 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -80 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -80 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -120 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -120 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);

                mPaintHRT.setTextSize(20);
                mPaintHRT.setAntiAlias(true);
//                mPaintHRT.setTextAlign(Paint.Align.RIGHT);
                int textMarginBottom = -6;
                mSignatureBitmapCanvas.drawText("Deep Sleep", LEFT - LEFT + 6, textMarginBottom + -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
                mSignatureBitmapCanvas.drawText("Shallow Sleep", LEFT - LEFT + 6, textMarginBottom + -40 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
                mSignatureBitmapCanvas.drawText("REM", LEFT - LEFT + 6, textMarginBottom + -80 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
                mSignatureBitmapCanvas.drawText("Awake", LEFT - LEFT + 6, textMarginBottom + -120 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, mPaintHRT);
            }
            for (int i = 0; i < this.mPts.size() - 1; i++) {
                Point to = this.mPts.get(i);
                if (mSignatureBitmapCanvas != null) {
                    mPaintCALM.setStyle(Paint.Style.FILL);
                    mSignatureBitmapCanvas.drawRect(LEFT + (i + 1) * interval, BOTTOM, LEFT + (i + 2) * interval, -mPts.get(i).val * plotHEIGHT / MAXHEIGHT + originY, mPaintCALM);
                    mPaintCALM.setTextSize(40);
                    if (to.grid_level == 2) {
                        Paint paintTime = new Paint();
                        paintTime.setColor(Color.BLACK);
                        paintTime.setTextSize(20);
                        paintTime.setAntiAlias(true);
                        String timeStr = to.strTime;

                        float textWidth = paintTime.measureText(timeStr);
                        mSignatureBitmapCanvas.drawText(timeStr, -textWidth / 2 + LEFT + (i + 1) * interval, BOTTOM + 20, paintTime);
                    }
                }
                from = to;
            }
            if (mSignatureBitmapCanvas != null) {
                Paint paintBorder = new Paint();
                paintBorder.setStyle(Paint.Style.STROKE);
                paintBorder.setColor(Color.rgb(0xB6, 0xB6, 0xB6));
                paintBorder.setStrokeWidth(1.5f);

//                mSignatureBitmapCanvas.drawRect(LEFT, TOP, RIGHT, BOTTOM, paintBorder);
//                mSignatureBitmapCanvas.clipRect(LEFT, 0, RIGHT, height, Region.Op.REPLACE);
                Paint transparentPaint = new Paint();
                transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
                transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                transparentPaint.setAntiAlias(true);

                Paint testPaint = new Paint();
                testPaint.setColor(Color.RED);
//                mSignatureBitmapCanvas.drawRect(0, 0, LEFT - 1, HEIGHT, transparentPaint);
//                mSignatureBitmapCanvas.drawRect(RIGHT + 0.5f, 0, WIDTH, HEIGHT, transparentPaint);
//                mSignatureBitmapCanvas.drawRect(0, 0, WIDTH, TOP - 0.5f, transparentPaint);
//                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);

//                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);
                invalidate();
            }
        }
    }

    public void updatePaintMotion() {
        LinearGradient linearGradientMotion = new LinearGradient(0, 0, 0, getHeight(),
                new int[]{
                        Color.rgb(221, 130, 127),
                        Color.rgb(182, 6, 0),
                        Color.rgb(196, 72, 0),
                        Color.rgb(205, 147, 0),
                        Color.rgb(208, 178, 0),
                },
                new float[]{
                        0, 0.01f, 0.41f, 0.81f, 1},
                Shader.TileMode.MIRROR);

        Shader shaderMotion = linearGradientMotion;
        mPaintMotion.setShader(shaderMotion);
        mPaintMotion.setStrokeWidth(50);


        int HEIGHT = this.getHeight();
        int WIDTH = this.getWidth();

        int marginLeft = 100;
        int marginRight = 0;

        int marginTop = 30;
        int marginBottom = 70;

        float LEFT = marginLeft;
        float RIGHT = WIDTH - marginRight;
        float TOP = marginTop;
        float BOTTOM = HEIGHT - marginBottom;
        int plotHEIGHT = HEIGHT - marginBottom - marginTop;
        int plotWIDTH = WIDTH - marginLeft - marginRight;
        if (this.mPts.size() > 0) {
            mScaleFactorY = 1;
            mPaintCALM.setAntiAlias(true);
            //khs
            ensureSignatureBitmap();

            float originY = (float) plotHEIGHT + TOP;
            float originX = (float) LEFT;
            float interval = (float) plotWIDTH / mPts.size() * mScaleFactorX;
            Point from = this.mPts.get(this.mPts.size() - 1);
            if (mSignatureBitmapCanvas != null) {
                Paint paintGrid = new Paint();
                mSignatureBitmapCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -0 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
                mSignatureBitmapCanvas.drawLine(LEFT - LEFT, -100 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2,
                        RIGHT, -100 * plotHEIGHT / MAXHEIGHT * mScaleFactorY + originY + (plotHEIGHT) * (mScaleFactorY - 1.0f) / 2, paintGrid);
            }
            for (int i = 0; i < this.mPts.size() - 1; i++) {
                Point to = this.mPts.get(i);
                if (mSignatureBitmapCanvas != null) {
                    mPaintCALM.setStyle(Paint.Style.FILL);
                    mSignatureBitmapCanvas.drawRect(LEFT + (i + 1) * interval, getHeight(), LEFT + (i + 2) * interval, mPts.get(i).val, mPaintMotion);
                }
                from = to;
            }
            if (mSignatureBitmapCanvas != null) {
                Paint paintBorder = new Paint();
                paintBorder.setStyle(Paint.Style.STROKE);
                paintBorder.setColor(Color.rgb(0xB6, 0xB6, 0xB6));
                paintBorder.setStrokeWidth(1.5f);

//                mSignatureBitmapCanvas.drawRect(LEFT, TOP, RIGHT, BOTTOM, paintBorder);
//                mSignatureBitmapCanvas.clipRect(LEFT, 0, RIGHT, height, Region.Op.REPLACE);
                Paint transparentPaint = new Paint();
                transparentPaint.setColor(getResources().getColor(android.R.color.transparent));
                transparentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                transparentPaint.setAntiAlias(true);

                Paint testPaint = new Paint();
                testPaint.setColor(Color.RED);
//                mSignatureBitmapCanvas.drawRect(0, 0, LEFT - 1, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(RIGHT + 0.5f, 0, WIDTH, HEIGHT, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, 0, WIDTH, TOP - 0.5f, transparentPaint);
                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);

                mSignatureBitmapCanvas.drawRect(0, BOTTOM + 0.5f, WIDTH, HEIGHT, transparentPaint);
                invalidate();
            }
        }
    }

    public SignaturePad(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPts = new ArrayList<>();

        this.intevalPixel = 1;
        chartMode = ECG_MODE;

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.SignaturePad,
                0, 0);

        //Configurable parameters
        try {
            mMinWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_penMinWidth, convertDpToPx(DEFAULT_ATTR_PEN_MIN_WIDTH_PX));
            mMaxWidth = a.getDimensionPixelSize(R.styleable.SignaturePad_penMaxWidth, convertDpToPx(DEFAULT_ATTR_PEN_MAX_WIDTH_PX));
            mPaint.setColor(a.getColor(R.styleable.SignaturePad_penColor, DEFAULT_ATTR_PEN_COLOR));
            mVelocityFilterWeight = a.getFloat(R.styleable.SignaturePad_velocityFilterWeight, DEFAULT_ATTR_VELOCITY_FILTER_WEIGHT);
            mClearOnDoubleClick = a.getBoolean(R.styleable.SignaturePad_clearOnDoubleClick, DEFAULT_ATTR_CLEAR_ON_DOUBLE_CLICK);
        } finally {
            a.recycle();
        }

        //Fixed parameters
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);

        //Dirty rectangle to update only the changed portion of the view
        mDirtyRect = new RectF();

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());

        clear();
    }


    /**
     * Set the pen color from a given resource.
     * If the resource is not found, {@link android.graphics.Color#BLACK} is assumed.
     *
     * @param colorRes the color resource.
     */
    public void setPenColorRes(int colorRes) {
        try {
            setPenColor(getResources().getColor(colorRes));
        } catch (Resources.NotFoundException ex) {
            setPenColor(Color.parseColor("#000000"));
        }
    }

    /**
     * Set the pen color from a given color.
     *
     * @param color the color.
     */
    public void setPenColor(int color) {
//        mPaint.setColor(color);
        this.plotType = color;
        update();
    }

    /**
     * Set the minimum width of the stroke in pixel.
     *
     * @param minWidth the width in dp.
     */
    public void setMinWidth(float minWidth) {
        mMinWidth = convertDpToPx(minWidth);
    }

    /**
     * Set the maximum width of the stroke in pixel.
     *
     * @param maxWidth the width in dp.
     */
    public void setMaxWidth(float maxWidth) {
        mMaxWidth = convertDpToPx(maxWidth);
    }

    /**
     * Set the velocity filter weight.
     *
     * @param velocityFilterWeight the weight.
     */
    public void setVelocityFilterWeight(float velocityFilterWeight) {
        mVelocityFilterWeight = velocityFilterWeight;
    }

    public void clear() {
        mSvgBuilder.clear();
        mPoints = new ArrayList<>();
        mLastVelocity = 0;
        mLastWidth = (mMinWidth + mMaxWidth) / 2;

        if (mSignatureBitmap != null) {
            mSignatureBitmap = null;
            ensureSignatureBitmap();
        }

        setIsEmpty(true);

        invalidate();
    }

    public void setOnSignedListener(OnSignedListener listener) {
        mOnSignedListener = listener;
    }

    public boolean isEmpty() {
        return mIsEmpty;
    }

    public String getSignatureSvg() {
        int width = getTransparentSignatureBitmap().getWidth();
        int height = getTransparentSignatureBitmap().getHeight();
        return mSvgBuilder.build(width, height);
    }

    public Bitmap getSignatureBitmap() {
        Bitmap originalBitmap = getTransparentSignatureBitmap();
        Bitmap whiteBgBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(whiteBgBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(originalBitmap, 0, 0, null);
        return whiteBgBitmap;
    }

    public void setSignatureBitmap(final Bitmap signature) {
        // View was laid out...
        if (ViewCompat.isLaidOut(this)) {
            clear();
            ensureSignatureBitmap();

            RectF tempSrc = new RectF();
            RectF tempDst = new RectF();

            int dWidth = signature.getWidth();
            int dHeight = signature.getHeight();
            int vWidth = getWidth();
            int vHeight = getHeight();

            // Generate the required transform.
            tempSrc.set(0, 0, dWidth, dHeight);
            tempDst.set(0, 0, vWidth, vHeight);

            Matrix drawMatrix = new Matrix();
            drawMatrix.setRectToRect(tempSrc, tempDst, Matrix.ScaleToFit.CENTER);

            Canvas canvas = new Canvas(mSignatureBitmap);
            canvas.drawBitmap(signature, drawMatrix, null);
            setIsEmpty(false);
            invalidate();
        }
        // View not laid out yet e.g. called from onCreate(), onRestoreInstanceState()...
        else {
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // Remove layout listener...
                    ViewTreeObserverCompat.removeOnGlobalLayoutListener(getViewTreeObserver(), this);

                    // Signature bitmap...
                    setSignatureBitmap(signature);
                }
            });
        }
    }

    public Bitmap getTransparentSignatureBitmap() {
        ensureSignatureBitmap();
        return mSignatureBitmap;
    }

    public Bitmap getTransparentSignatureBitmap(boolean trimBlankSpace) {

        if (!trimBlankSpace) {
            return getTransparentSignatureBitmap();
        }

        ensureSignatureBitmap();

        int imgHeight = mSignatureBitmap.getHeight();
        int imgWidth = mSignatureBitmap.getWidth();

        int backgroundColor = Color.TRANSPARENT;

        int xMin = Integer.MAX_VALUE,
                xMax = Integer.MIN_VALUE,
                yMin = Integer.MAX_VALUE,
                yMax = Integer.MIN_VALUE;

        boolean foundPixel = false;

        // Find xMin
        for (int x = 0; x < imgWidth; x++) {
            boolean stop = false;
            for (int y = 0; y < imgHeight; y++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    xMin = x;
                    stop = true;
                    foundPixel = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Image is empty...
        if (!foundPixel)
            return null;

        // Find yMin
        for (int y = 0; y < imgHeight; y++) {
            boolean stop = false;
            for (int x = xMin; x < imgWidth; x++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    yMin = y;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Find xMax
        for (int x = imgWidth - 1; x >= xMin; x--) {
            boolean stop = false;
            for (int y = yMin; y < imgHeight; y++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    xMax = x;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        // Find yMax
        for (int y = imgHeight - 1; y >= yMin; y--) {
            boolean stop = false;
            for (int x = xMin; x <= xMax; x++) {
                if (mSignatureBitmap.getPixel(x, y) != backgroundColor) {
                    yMax = y;
                    stop = true;
                    break;
                }
            }
            if (stop)
                break;
        }

        return Bitmap.createBitmap(mSignatureBitmap, xMin, yMin, xMax - xMin, yMax - yMin);
    }

    private boolean isDoubleClick() {
        if (mClearOnDoubleClick) {
            if (mFirstClick != 0 && System.currentTimeMillis() - mFirstClick > DOUBLE_CLICK_DELAY_MS) {
                mCountClick = 0;
            }
            mCountClick++;
            if (mCountClick == 1) {
                mFirstClick = System.currentTimeMillis();
            } else if (mCountClick == 2) {
                long lastClick = System.currentTimeMillis();
                if (lastClick - mFirstClick < DOUBLE_CLICK_DELAY_MS) {
                    this.clear();
                    return true;
                }
            }
        }
        return false;
    }

    private TimedPoint getNewPoint(float x, float y) {
        int mCacheSize = mPointsCache.size();
        TimedPoint timedPoint;
        if (mCacheSize == 0) {
            // Cache is empty, create a new point
            timedPoint = new TimedPoint();
        } else {
            // Get point from cache
            timedPoint = mPointsCache.remove(mCacheSize - 1);
        }

        return timedPoint.set(x, y);
    }

    private void recyclePoint(TimedPoint point) {
        mPointsCache.add(point);
    }

    private void addPointBeizer(TimedPoint newPoint) {
        mPoints.add(newPoint);

        int pointsCount = mPoints.size();
        if (pointsCount > 3) {

            ControlTimedPoints tmp = calculateCurveControlPoints(mPoints.get(0), mPoints.get(1), mPoints.get(2));
            TimedPoint c2 = tmp.c2;
            recyclePoint(tmp.c1);

            tmp = calculateCurveControlPoints(mPoints.get(1), mPoints.get(2), mPoints.get(3));
            TimedPoint c3 = tmp.c1;
            recyclePoint(tmp.c2);

            Bezier curve = mBezierCached.set(mPoints.get(1), c2, c3, mPoints.get(2));

            TimedPoint startPoint = curve.startPoint;
            TimedPoint endPoint = curve.endPoint;

            float velocity = endPoint.velocityFrom(startPoint);
            velocity = Float.isNaN(velocity) ? 0.0f : velocity;

            velocity = mVelocityFilterWeight * velocity
                    + (1 - mVelocityFilterWeight) * mLastVelocity;

            // The new width is a function of the velocity. Higher velocities
            // correspond to thinner strokes.
            float newWidth = strokeWidth(velocity);

            // The Bezier's width starts out as last curve's final width, and
            // gradually changes to the stroke width just calculated. The new
            // width calculation is based on the velocity between the Bezier's
            // start and end mPoints.
            addBezier(curve, mLastWidth, newWidth);

            mLastVelocity = velocity;
            mLastWidth = newWidth;

            // Remove the first element from the list,
            // so that we always have no more than 4 mPoints in mPoints array.
            recyclePoint(mPoints.remove(0));

            recyclePoint(c2);
            recyclePoint(c3);

        } else if (pointsCount == 1) {
            // To reduce the initial lag make it work with 3 mPoints
            // by duplicating the first point
            TimedPoint firstPoint = mPoints.get(0);
            mPoints.add(getNewPoint(firstPoint.x, firstPoint.y));
        }
    }

    private void addBezier(Bezier curve, float startWidth, float endWidth) {
        mSvgBuilder.append(curve, (startWidth + endWidth) / 2);
        ensureSignatureBitmap();
        float originalWidth = mPaint.getStrokeWidth();
        float widthDelta = endWidth - startWidth;
        float drawSteps = (float) Math.floor(curve.length());

        for (int i = 0; i < drawSteps; i++) {
            // Calculate the Bezier (x, y) coordinate for this step.
            float t = ((float) i) / drawSteps;
            float tt = t * t;
            float ttt = tt * t;
            float u = 1 - t;
            float uu = u * u;
            float uuu = uu * u;

            float x = uuu * curve.startPoint.x;
            x += 3 * uu * t * curve.control1.x;
            x += 3 * u * tt * curve.control2.x;
            x += ttt * curve.endPoint.x;

            float y = uuu * curve.startPoint.y;
            y += 3 * uu * t * curve.control1.y;
            y += 3 * u * tt * curve.control2.y;
            y += ttt * curve.endPoint.y;

            // Set the incremental stroke width and draw.
            mPaint.setStrokeWidth(startWidth + ttt * widthDelta);
            mSignatureBitmapCanvas.drawPoint(x, y, mPaintHRT);
            expandDirtyRect(x, y);
        }

        mPaint.setStrokeWidth(originalWidth);
    }

    private ControlTimedPoints calculateCurveControlPoints(TimedPoint s1, TimedPoint s2, TimedPoint s3) {
        float dx1 = s1.x - s2.x;
        float dy1 = s1.y - s2.y;
        float dx2 = s2.x - s3.x;
        float dy2 = s2.y - s3.y;

        float m1X = (s1.x + s2.x) / 2.0f;
        float m1Y = (s1.y + s2.y) / 2.0f;
        float m2X = (s2.x + s3.x) / 2.0f;
        float m2Y = (s2.y + s3.y) / 2.0f;

        float l1 = (float) Math.sqrt(dx1 * dx1 + dy1 * dy1);
        float l2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);

        float dxm = (m1X - m2X);
        float dym = (m1Y - m2Y);
        float k = l2 / (l1 + l2);
        if (Float.isNaN(k)) k = 0.0f;
        float cmX = m2X + dxm * k;
        float cmY = m2Y + dym * k;

        float tx = s2.x - cmX;
        float ty = s2.y - cmY;

        return mControlTimedPointsCached.set(getNewPoint(m1X + tx, m1Y + ty), getNewPoint(m2X + tx, m2Y + ty));
    }

    private float strokeWidth(float velocity) {
        return Math.max(mMaxWidth / (velocity + 1), mMinWidth);
    }

    /**
     * Called when replaying history to ensure the dirty region includes all
     * mPoints.
     *
     * @param historicalX the previous x coordinate.
     * @param historicalY the previous y coordinate.
     */
    private void expandDirtyRect(float historicalX, float historicalY) {
        if (historicalX < mDirtyRect.left) {
            mDirtyRect.left = historicalX;
        } else if (historicalX > mDirtyRect.right) {
            mDirtyRect.right = historicalX;
        }
        if (historicalY < mDirtyRect.top) {
            mDirtyRect.top = historicalY;
        } else if (historicalY > mDirtyRect.bottom) {
            mDirtyRect.bottom = historicalY;
        }
    }

    /**
     * Resets the dirty region when the motion event occurs.
     *
     * @param eventX the event x coordinate.
     * @param eventY the event y coordinate.
     */
    private void resetDirtyRect(float eventX, float eventY) {

        // The mLastTouchX and mLastTouchY were set when the ACTION_DOWN motion event occurred.
        mDirtyRect.left = Math.min(mLastTouchX, eventX);
        mDirtyRect.right = Math.max(mLastTouchX, eventX);
        mDirtyRect.top = Math.min(mLastTouchY, eventY);
        mDirtyRect.bottom = Math.max(mLastTouchY, eventY);
    }

    private void setIsEmpty(boolean newValue) {
        mIsEmpty = newValue;
        if (mOnSignedListener != null) {
            if (mIsEmpty) {
                mOnSignedListener.onClear();
            } else {
                mOnSignedListener.onSigned();
            }
        }
    }

    private void ensureSignatureBitmap() {
        if (mSignatureBitmap == null) {
            int w = getWidth();
            int h = getHeight();

            if (w > 0 && h > 0) {
                mSignatureBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                        Bitmap.Config.ARGB_8888);
                mSignatureBitmapCanvas = new Canvas(mSignatureBitmap);
                mSignatureBitmapCanvas.scale(1f, 1f);
            }

        }
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactorX *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactorX = Math.max(0.1f, Math.min(mScaleFactorX, 5.0f));

            if (mScaleFactorX < 1)
                mScaleFactorX = 1;
            update();
            return true;
        }
    }

    public void zoomIn() {
        if (mZoom > 10)
            return;
        mZoom += 1;
        mSignatureBitmapCanvas.scale(1.05f, 1f, getWidth(), getHeight() / 2);
    }

    public void zoomOut() {
        if (mZoom == 0)
            return;
        mZoom -= 1;
        mSignatureBitmapCanvas.scale(0.95f, 1f, getWidth(), getHeight() / 2);
    }

    private int convertDpToPx(float dp) {
        return Math.round(getContext().getResources().getDisplayMetrics().density * dp);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled())
            return false;

        float eventX = event.getX();
        float eventY = event.getY();

        if (vertZoom)
            mScaleDetector.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                prevY = event.getY();
                twoFingure = false;
                Log.d("touch", "touch down");
                break;
            case MotionEvent.ACTION_MOVE:

                if (twoFingure) {
                    vertZoom = detectHorzPinch(event);
                    if (!vertZoom) {
                        prevPinchYSpace = curPinchYSpace;
                        curPinchYSpace = Math.abs(event.getY(0) - event.getY(1));
                        Log.d("zoom test", mScaleFactorY + " " + prevPinchYSpace + " " + curPinchYSpace);
                        if ((curPinchYSpace - prevPinchYSpace) > 3) {
                            mScaleFactorY += 0.1f;
                            if (mScaleFactorY > 2)
                                mScaleFactorY = 2;
                        } else if ((curPinchYSpace - prevPinchYSpace) < -3) {
                            mScaleFactorY -= 0.1f;
                            if (mScaleFactorY < 1)
                                mScaleFactorY = 1;
                        }
                    }
                } else {
                    curY = event.getY();
                    if (Math.abs(curY - prevY) > 3)
                        invalidate();
                    offsetY += (curY - prevY);
                    prevY = curY;
                    Log.d("offsetY", offsetY + "");
                }
                break;

            case MotionEvent.ACTION_UP:
                twoFingure = false;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d("touch", "touch pointer down");
                twoFingure = true;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                twoFingure = false;
                Log.d("touch", "touch pointer up");
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean detectHorzPinch(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        if (Math.abs(y) > Math.abs(x))
            return false;
        return true;
    }

    public interface OnSignedListener {
        void onStartSigning();

        void onSigned();

        void onClear();
    }
}

