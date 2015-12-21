package com.example.dyyao.assignment_3th;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    private final String TAG = this.getClass().getSimpleName();
    private final int FINGERS_NUM_MAX = 5;
    private Finger[] fingers;
    private int[] color_values = {Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.YELLOW};
    private FrameLayout frame;
    private Paper paper;
    private int screenWidth, screenHeight;
    private PointF leftMost, rightMost;
    private File directory, file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        frame = (FrameLayout)findViewById(R.id.frame);
        frame.setOnTouchListener(this);

        fingers = new Finger[FINGERS_NUM_MAX];
        for (int i = 0; i < FINGERS_NUM_MAX; i++) fingers[i] = new Finger(this, color_values[i]);

        paper = new Paper(this);
        frame.addView(paper);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
        //Log.i(TAG, "W: " + String.valueOf(screenWidth) + " H: " + String.valueOf(screenHeight));
        leftMost = new PointF((float)screenWidth, 0);
        rightMost = new PointF(0, 0);

        directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), getString(R.string.app_name));
        if (!directory.exists()) directory.mkdirs();
        file = new File(directory, "MeasurementLog.txt");
        if (!file.exists()) try {
            file.createNewFile();
        } catch (IOException e) {
            Toast.makeText(MainActivity.this, "Log File Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        int pointerIndex = event.getActionIndex();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                Finger finger_down = Finger.getFirstNotTracked(fingers);
                if (finger_down == null) break;
                finger_down.trackStatus = true;
                finger_down.trackId = event.getPointerId(pointerIndex);
                finger_down.position.set(event.getX(pointerIndex), event.getY(pointerIndex));
                frame.addView(finger_down);

                setLeftRightMost(event.getX(pointerIndex), event.getY(pointerIndex));

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                Finger finger_up = Finger.getFingerById(fingers, event.getPointerId(pointerIndex));
                if (finger_up == null) break;
                finger_up.trackStatus = false;
                frame.removeView(finger_up);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    //Log.i(TAG, "index: " + String.valueOf(i) + " id: " + String.valueOf(event.getPointerId(i)));
                    Finger finger_move = Finger.getFingerById(fingers, event.getPointerId(i));
                    if (finger_move == null) break;
                    finger_move.position.set(event.getX(i), event.getY(i));
                    finger_move.invalidate();
                }

                if (((Switch)findViewById(R.id.mode)).isChecked()) {
                    paper.points.add(new PointF(event.getX(pointerIndex), event.getY(pointerIndex)));
                    paper.invalidate();
                }

                setLeftRightMost(event.getX(pointerIndex), event.getY(pointerIndex));

                break;
            default: break;
        }

        return true;
    }

    public void setLeftRightMost(float x, float y) {
        if (x <= leftMost.x) {
            leftMost.x = x;
            leftMost.y = y;
        }

        if (x >= rightMost.x) {
            rightMost.x = x;
            rightMost.y = y;
        }
    }

    public void log(View view) {
        try {
            FileOutputStream writer = new FileOutputStream(file, true);
            writer.write(("screen width: " + screenWidth + " height: " + screenHeight + "\n" +
                    "leftMost: " + leftMost.x + " " + leftMost.y + "\n" +
                    "rightMost: " + rightMost.x + " " + rightMost.y).getBytes());
        } catch (FileNotFoundException e) {
            Toast.makeText(MainActivity.this, "File Output Error", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clearDrawing(View view) {
        paper.points.clear();
        paper.invalidate();
    }

    public class Paper extends View {

        public ArrayList<PointF> points;
        private Paint paint;

        public Paper(Context context) {
            super(context);
            points = new ArrayList<PointF>();
            paint = new Paint();
            paint.setStrokeWidth(10);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            for (int i = 0; i < points.size() - 1; i++) {
                canvas.drawLine(points.get(i).x, points.get(i).y, points.get(i + 1).x, points.get(i + 1).y, paint);
            }
        }
    }

    public static class Finger extends View {
        public static final float SIZE_CYCLE = 150;
        public static final float SIZE_TEXT = 50;
        public PointF position;
        public Paint paint_cycle;
        public Paint paint_text;
        public boolean trackStatus;
        public int trackId;

        public Finger(Context context, int color_value) {
            super(context);
            position = new PointF();
            paint_cycle = new Paint();
            paint_cycle.setColor(color_value);
            paint_cycle.setStyle(Paint.Style.FILL);
            paint_text = new Paint();
            paint_text.setColor(color_value);
            paint_text.setTextSize(SIZE_TEXT);
        }

        public static Finger getFirstNotTracked(Finger[] fingers) {
            for (int i = 0; i < fingers.length; i++) {
                if (!fingers[i].trackStatus) return fingers[i];
            }
            return null;
        }

        public static Finger getFingerById(Finger[] fingers, int id) {
            for (int i = 0; i < fingers.length; i++) {
                if (fingers[i].trackId == id) return fingers[i];
            }
            return null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            //Log.i("Finger", "onDraw called");
            canvas.drawCircle(position.x, position.y, SIZE_CYCLE, paint_cycle);
            String text = "id: " + trackId + " x: " + position.x + " y: " + position.y;
            canvas.drawText(text, 0, (trackId + 1) * paint_text.getTextSize(), paint_text);
        }
    }
}
