package com.example.letterland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

// ML Kit Ink import
import com.google.mlkit.vision.digitalink.Ink;

public class DrawingView extends View {
    private Path drawPath;
    private Paint drawPaint, canvasPaint;
    private Canvas drawCanvas;
    private Bitmap canvasBitmap;

    // MAGNIFYING GLASS VARIABLES
    private boolean isDrawing = false;
    private float currentX = 0f;
    private float currentY = 0f;
    private Paint magnifierBorderPaint;
    private Paint crosshairPaint;

    // A layer that floats above the entire app!
    private View magnifierOverlay;

    // TRACKS THE EXACT EDGES OF THEIR DRAWING
    private float minX = Float.MAX_VALUE;
    private float minY = Float.MAX_VALUE;
    private float maxX = 0;
    private float maxY = 0;

    // ML KIT DIGITAL INK TRACKERS
    private Ink.Builder inkBuilder = Ink.builder();
    private Ink.Stroke.Builder strokeBuilder;

    // PHASE 1: BACKGROUND TRACING TEMPLATE VARIABLES
    private String tracingWord = "";
    private Paint tracingPaint;

    public interface OnDrawListener {
        void onDrawStarted();
        void onDrawFinished();
    }
    private OnDrawListener drawListener;

    public void setOnDrawListener(OnDrawListener listener) {
        this.drawListener = listener;
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupDrawing();
    }

    private void setupDrawing() {
        drawPath = new Path();
        drawPaint = new Paint();
        drawPaint.setColor(Color.BLACK);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(20f);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        canvasPaint = new Paint(Paint.DITHER_FLAG);

        magnifierBorderPaint = new Paint();
        magnifierBorderPaint.setColor(Color.parseColor("#29B6F6"));
        magnifierBorderPaint.setStyle(Paint.Style.STROKE);
        magnifierBorderPaint.setStrokeWidth(15f);
        magnifierBorderPaint.setAntiAlias(true);

        crosshairPaint = new Paint();
        crosshairPaint.setColor(Color.argb(120, 255, 0, 0)); // Semi-transparent Red
        crosshairPaint.setStyle(Paint.Style.FILL);
        crosshairPaint.setAntiAlias(true);

        // PHASE 1: INITIALIZE FAINT BACKGROUND TEXT DESIGN FOR KIDS
        tracingPaint = new Paint();
        tracingPaint.setColor(Color.parseColor("#E0E0E0")); // Very light gray visual prompt
        tracingPaint.setAntiAlias(true);
        tracingPaint.setTextSize(150f); // Large scale matching young fine-motor tracking
        tracingPaint.setTextAlign(Paint.Align.CENTER);
        tracingPaint.setStyle(Paint.Style.FILL);
        tracingPaint.setFakeBoldText(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewGroup root = (ViewGroup) getRootView().findViewById(android.R.id.content);
        if (root != null && magnifierOverlay == null) {
            magnifierOverlay = new View(getContext()) {
                @Override
                protected void onDraw(Canvas canvas) {
                    drawMagnifierOnOverlay(canvas);
                }
            };
            magnifierOverlay.setClickable(false);
            magnifierOverlay.setFocusable(false);
            // FIX: Prevent 'clipPath' from causing black screen bugs on Android 14+
            magnifierOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            root.addView(magnifierOverlay, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (magnifierOverlay != null) {
            final View overlayToRemove = magnifierOverlay;
            final ViewGroup parent = (ViewGroup) overlayToRemove.getParent();

            if (parent != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        parent.removeView(overlayToRemove);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            magnifierOverlay = null;
        }

        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
        drawCanvas.drawColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);

        // PHASE 1: RENDER FAINT BACKGROUND TRACING TEXT GUIDES UNDER FINGER STROKES
        if (tracingWord != null && !tracingWord.isEmpty()) {
            float xPos = getWidth() / 2f;
            float yPos = (getHeight() / 2f) - ((tracingPaint.descent() + tracingPaint.ascent()) / 2f);
            canvas.drawText(tracingWord, xPos, yPos, tracingPaint);
        }

        canvas.drawPath(drawPath, drawPaint);
    }

    private void drawMagnifierOnOverlay(Canvas canvas) {
        if (!isDrawing) return;
        float magRadius = 240f;
        int[] location = new int[2];
        getLocationInWindow(location);

        float screenX = currentX + location[0];
        float screenY = currentY + location[1];

        float magX = screenX;
        float magY = screenY - 320f;

        int screenW = magnifierOverlay.getWidth();
        if (magX - magRadius < 0) magX = magRadius;
        if (magX + magRadius > screenW) magX = screenW - magRadius;
        if (magY - magRadius < 0) magY = magRadius;

        canvas.save();

        Path magClip = new Path();
        magClip.addCircle(magX, magY, magRadius, Path.Direction.CW);
        canvas.clipPath(magClip);
        canvas.drawColor(Color.WHITE);

        float zoom = 2.0f;
        canvas.translate(magX, magY);
        canvas.scale(zoom, zoom);
        canvas.translate(-screenX, -screenY);
        canvas.translate(location[0], location[1]);

        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);

        // PHASE 1: ALSO RENDER THE GUIDE INSIDE THE ZOOM LENS SO IT STAYS ALIGNED
        if (tracingWord != null && !tracingWord.isEmpty()) {
            float xPos = getWidth() / 2f;
            float yPos = (getHeight() / 2f) - ((tracingPaint.descent() + tracingPaint.ascent()) / 2f);
            canvas.drawText(tracingWord, xPos, yPos, tracingPaint);
        }

        canvas.drawPath(drawPath, drawPaint);

        canvas.restore();

        canvas.drawCircle(magX, magY, magRadius, magnifierBorderPaint);
        canvas.drawCircle(magX, magY, 15f, crosshairPaint);
    }

    private void updateBounds(float x, float y) {
        float padding = 50f;
        if (x - padding < minX) minX = x - padding;
        if (y - padding < minY) minY = y - padding;
        if (x + padding > maxX) maxX = x + padding;
        if (y + padding > maxY) maxY = y + padding;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float brushRadius = drawPaint.getStrokeWidth() / 2f;
        float safetyBuffer = brushRadius + 2f;

        currentX = Math.max(safetyBuffer, Math.min(event.getX(), getWidth() - safetyBuffer));
        currentY = Math.max(safetyBuffer, Math.min(event.getY(), getHeight() - safetyBuffer));
        long t = System.currentTimeMillis();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDrawing = true;
                drawPath.moveTo(currentX, currentY);
                updateBounds(currentX, currentY);

                strokeBuilder = Ink.Stroke.builder();
                strokeBuilder.addPoint(Ink.Point.create(currentX, currentY, t));

                if (drawListener != null) drawListener.onDrawStarted();
                break;
            case MotionEvent.ACTION_MOVE:
                drawPath.lineTo(currentX, currentY);
                updateBounds(currentX, currentY);

                if (strokeBuilder != null) {
                    strokeBuilder.addPoint(Ink.Point.create(currentX, currentY, t));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDrawing = false;
                drawCanvas.drawPath(drawPath, drawPaint);
                drawPath.reset();

                if (strokeBuilder != null) {
                    strokeBuilder.addPoint(Ink.Point.create(currentX, currentY, t));
                    inkBuilder.addStroke(strokeBuilder.build());
                    strokeBuilder = null;
                }

                if (drawListener != null) drawListener.onDrawFinished();
                break;

            default:
                return false;
        }

        invalidate();
        if (magnifierOverlay != null) magnifierOverlay.invalidate();
        return true;
    }

    // 🚀 FIXED OPTIMIZATION: Consolidated single worker redrawing template framework logic cleanly here
    // 🚀 FIXED: Clears drawing lines without layering white paint over the text guide canvas
    public void setTracingWord(String word) {
        this.tracingWord = word;

        // Reset only the ink tracking data variables safely
        inkBuilder = Ink.builder();
        minX = Float.MAX_VALUE;
        minY = Float.MAX_VALUE;
        maxX = 0;
        maxY = 0;

        // Re-clear the finger stroke canvas bitmap back to pure clean states
        if (drawCanvas != null) {
            drawCanvas.drawColor(Color.WHITE);
        }

        invalidate(); // Forces immediate redraw on the main thread
    }
    public void clearCanvas() {
        drawCanvas.drawColor(Color.WHITE);
        minX = Float.MAX_VALUE;
        minY = Float.MAX_VALUE;
        maxX = 0;
        maxY = 0;

        inkBuilder = Ink.builder();
        invalidate();
    }

    public void resetFullCanvas() {
        this.tracingWord = "";
        clearCanvas();
    }

    public Bitmap getBitmap() {
        return canvasBitmap;
    }

    public Bitmap getCroppedBitmap() {
        if (maxX <= minX || maxY <= minY) return null;
        int left = (int) Math.max(0, minX);
        int top = (int) Math.max(0, minY);
        int right = (int) Math.min(canvasBitmap.getWidth(), maxX);
        int bottom = (int) Math.min(canvasBitmap.getHeight(), maxY);

        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return null;

        return Bitmap.createBitmap(canvasBitmap, left, top, width, height);
    }

    public Ink getInk() {
        return inkBuilder.build();
    }
}