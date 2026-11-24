package com.expensetracker.app.ui.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.expensetracker.app.R;
import com.expensetracker.app.ui.Fmt;

// A flat donut drawn on a Canvas: ring segments, the selected one pulled outward,
// and a centre disc. The category icon and text in the middle are normal views laid over it.
public class DonutChartView extends View {

    public interface Listener {
        void onSliceSelected(int index);
    }

    private static final float START_ANGLE = -90f;   // 12 o'clock, going clockwise
    private static final float HOLE_RATIO = 0.6f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF outerRect = new RectF();
    private final RectF innerRect = new RectF();
    private final int panel;
    private final int panelPink;

    private float[] fractions = new float[0];   // each slice's share of the whole, adding up to 1
    private int[] colors = new int[0];
    private int selected = -1;
    private int downHit = -1;
    private Listener listener;

    private float cx;
    private float cy;
    private float outerR;
    private float innerR;
    private float explode;

    public DonutChartView(Context context) {
        this(context, null);
    }

    public DonutChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DonutChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        panel = Fmt.color(context, R.color.surface_variant);
        panelPink = Fmt.color(context, R.color.surface_tint);

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Fmt.dp(context, 2));
        strokePaint.setColor(Fmt.color(context, R.color.white));

        explode = Fmt.dp(context, 8);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSlices(float[] fractions, int[] colors) {
        this.fractions = fractions;
        this.colors = colors;
        if (selected >= fractions.length) {
            selected = fractions.length - 1;
        }
        invalidate();
    }

    public void setSelectedSlice(int index) {
        selected = index;
        invalidate();
    }

    // ------------------------------------------------------------ layout

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = resolveSize(Fmt.dp(getContext(), 300), widthMeasureSpec);
        size = Math.min(size, Fmt.dp(getContext(), 340));
        setMeasuredDimension(size, size);   // always square
    }

    private void computeGeometry() {
        cx = getWidth() / 2f;
        cy = getHeight() / 2f;
        float margin = explode + strokePaint.getStrokeWidth();   // room for the pulled-out slice
        outerR = Math.min(cx, cy) - margin;
        innerR = outerR * HOLE_RATIO;
        outerRect.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR);
        innerRect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR);
    }

    // ------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        computeGeometry();
        if (fractions.length == 0) {
            drawEmptyRing(canvas);
        } else {
            drawSlices(canvas);
        }
        drawCenter(canvas);
    }

    private Path slicePath(float start, float sweep) {
        Path path = new Path();
        if (sweep >= 359.9f) {   // a single slice is a whole ring
            path.setFillType(Path.FillType.EVEN_ODD);
            path.addCircle(cx, cy, outerR, Path.Direction.CW);
            path.addCircle(cx, cy, innerR, Path.Direction.CW);
        } else {
            path.arcTo(outerRect, start, sweep);
            path.arcTo(innerRect, start + sweep, -sweep);
            path.close();
        }
        return path;
    }

    private void drawSlices(Canvas canvas) {
        int count = fractions.length;
        Path[] paths = new Path[count];
        float[] dx = new float[count];
        float[] dy = new float[count];

        float angle = START_ANGLE;
        for (int i = 0; i < count; i++) {
            float sweep = fractions[i] * 360f;
            paths[i] = slicePath(angle, sweep);
            if (i == selected) {
                double mid = Math.toRadians(angle + sweep / 2f);
                dx[i] = (float) Math.cos(mid) * explode;
                dy[i] = (float) Math.sin(mid) * explode;
            }
            angle += sweep;
        }

        // slices, with the selected one on top
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < count; i++) {
                if ((i == selected) != (pass == 1)) {
                    continue;
                }
                fillPaint.setColor(colors[i]);
                canvas.save();
                canvas.translate(dx[i], dy[i]);
                canvas.drawPath(paths[i], fillPaint);
                canvas.drawPath(paths[i], strokePaint);
                canvas.restore();
            }
        }
    }

    private void drawEmptyRing(Canvas canvas) {
        Path ring = slicePath(START_ANGLE, 360f);
        fillPaint.setColor(panelPink);
        canvas.drawPath(ring, fillPaint);
    }

    private void drawCenter(Canvas canvas) {
        float radius = innerR - Fmt.dp(getContext(), 5);
        fillPaint.setColor(panel);
        canvas.drawCircle(cx, cy, radius, fillPaint);
    }

    // ------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                computeGeometry();
                downHit = hitTest(event.getX(), event.getY());
                return downHit >= 0;   // a touch off the ring falls through, so the page can still scroll
            case MotionEvent.ACTION_UP:
                if (downHit >= 0 && downHit == hitTest(event.getX(), event.getY())) {
                    selected = downHit;
                    invalidate();
                    if (listener != null) {
                        listener.onSliceSelected(selected);
                    }
                    performClick();
                }
                downHit = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                downHit = -1;
                return true;
            default:
                return downHit >= 0;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // Which slice is under this point? -1 if the touch is outside the ring.
    private int hitTest(float x, float y) {
        if (fractions.length == 0) {
            return -1;
        }
        float dx = x - cx;
        float dy = y - cy;
        double distance = Math.hypot(dx, dy);
        if (distance < innerR || distance > outerR + explode) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx)) - START_ANGLE;   // 0 at the top, clockwise
        angle = ((angle % 360) + 360) % 360;
        float sum = 0;
        for (int i = 0; i < fractions.length; i++) {
            sum += fractions[i] * 360f;
            if (angle < sum) {
                return i;
            }
        }
        return fractions.length - 1;
    }
}