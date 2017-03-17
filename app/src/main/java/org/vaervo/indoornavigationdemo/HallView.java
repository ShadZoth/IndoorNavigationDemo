package org.vaervo.indoornavigationdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class HallView extends View {
    public static final int HEIGHT = 100;
    private static final int NOT_SET = -10;

    private final Paint mPaint;
    private double currentCoord = NOT_SET;
    private int highlightedCoord = NOT_SET;
    private OnHighlightedCoordChangedListener mOnHighlightedCoordChangedListener;

    public HallView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint = new Paint();
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, canvas.getWidth(), HEIGHT, mPaint);
        drawLine(currentCoord, canvas, Color.YELLOW);
        drawLine(highlightedCoord, canvas, Color.GREEN);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d("onTouchEvent", String.valueOf(event));
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            setHighlightedCoord((int) (event.getX() * 100 / getWidth()));
        }
        return super.onTouchEvent(event);
    }

    public double getCurrentCoord() {
        return currentCoord;
    }

    public void setCurrentCoord(double currentCoord) {
        this.currentCoord = currentCoord;
        invalidate();
    }

    public int getHighlightedCoord() {
        return highlightedCoord;
    }

    public void setHighlightedCoord(int highlightedCoord) {
        this.highlightedCoord = highlightedCoord;
        if (mOnHighlightedCoordChangedListener != null) {
            mOnHighlightedCoordChangedListener.onHighlightedCoordChanged();
        }
        invalidate();
    }

    private void drawLine(double coord, Canvas canvas, int color) {
        int canvasCoord = (int) (coord * canvas.getWidth() / 100);
        mPaint.setColor(color);
        canvas.drawRect(canvasCoord - 5, 0, canvasCoord + 5, HEIGHT, mPaint);
        mPaint.setColor(Color.BLACK);
    }

    public void setOnHighlightedCoordChangedListener(OnHighlightedCoordChangedListener onHighlightedCoordChangedListener) {
        mOnHighlightedCoordChangedListener = onHighlightedCoordChangedListener;
    }

    interface OnHighlightedCoordChangedListener {
        void onHighlightedCoordChanged();
    }
}
