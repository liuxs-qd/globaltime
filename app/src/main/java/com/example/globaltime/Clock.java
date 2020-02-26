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

package com.example.globaltime;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * A class that draws an analog clock face with information about the current
 * time in a given city.
 */
public class Clock {

    static final int MILLISECONDS_PER_MINUTE = 60 * 1000;
    static final int MILLISECONDS_PER_HOUR = 60 * 60 * 1000;
    static private final String TAG = "Clock";
    

    private City mCity = null;
    private long mCitySwitchTime;
    private long mTime;

    private float mColorRed = 1.0f;
    private float mColorGreen = 1.0f;
    private float mColorBlue = 1.0f;

    private long mOldOffset;

    static public final int buttonsSum = 7;
    private RectF[] buttons = new RectF[buttonsSum];
    private boolean[] buttonshow = new boolean[buttonsSum];
    private int width = 0;
    
    private Interpolator mClockHandInterpolator =
        new AccelerateDecelerateInterpolator();
	private Context context;

    public Clock(Context context) {
        this.context = context;
    }

    /**
     * Adds a line to the given Path.  The line extends from
     * radius r0 to radius r1 about the center point (cx, cy),
     * at an angle given by pos.
     * 
     * @param path the Path to draw to
     * @param radius the radius of the outer rim of the clock
     * @param pos the angle, with 0 and 1 at 12:00
     * @param cx the X coordinate of the clock center
     * @param cy the Y coordinate of the clock center
     * @param r0 the starting radius for the line
     * @param r1 the ending radius for the line
     */
    private static void drawLine(Path path,
        float radius, float pos, float cx, float cy, float r0, float r1) {
        float theta = pos * Shape.TWO_PI - Shape.PI_OVER_TWO;
        float dx = (float) Math.cos(theta);
        float dy = (float) Math.sin(theta);
        float p0x = cx + dx * r0;
        float p0y = cy + dy * r0;
        float p1x = cx + dx * r1;
        float p1y = cy + dy * r1;

        float ox =  (p1y - p0y);
        float oy = -(p1x - p0x);

        float norm = (radius / 2.0f) / (float) Math.sqrt(ox * ox + oy * oy);
        ox *= norm;
        oy *= norm;

        path.moveTo(p0x - ox, p0y - oy);
        path.lineTo(p1x - ox, p1y - oy);
        path.lineTo(p1x + ox, p1y + oy);
        path.lineTo(p0x + ox, p0y + oy);
        path.close();
    }

    /**
     * Adds a vertical arrow to the given Path.
     * 
     * @param path the Path to draw to
     */
    private static void drawVArrow(Path path,
        float cx, float cy, float width, float height) {
        path.moveTo(cx - width / 2.0f, cy);
        path.lineTo(cx, cy + height);
        path.lineTo(cx + width / 2.0f, cy);
        path.close();
    }

    /**
     * Adds a horizontal arrow to the given Path.
     * 
     * @param path the Path to draw to
     */
    private static void drawHArrow(Path path,
        float cx, float cy, float width, float height) {
        path.moveTo(cx, cy - height / 2.0f);
        path.lineTo(cx + width, cy);
        path.lineTo(cx, cy + height / 2.0f);
        path.close();
    }

    /**
     * Returns an offset in milliseconds to be subtracted from the current time
     * in order to obtain an smooth interpolation between the previously
     * displayed time and the current time.
     */
    private long getOffset(float lerp) {
        long doffset = (long) (mCity.getOffset() *
            (float) MILLISECONDS_PER_HOUR - mOldOffset);
        int sign;
        if (doffset < 0) {
            doffset = -doffset;
            sign = -1;
        } else {
            sign = 1;
        }

        while (doffset > 12L * MILLISECONDS_PER_HOUR) {
            doffset -= 12L * MILLISECONDS_PER_HOUR;
        }
        if (doffset > 6L * MILLISECONDS_PER_HOUR) {
            doffset = 12L * MILLISECONDS_PER_HOUR - doffset;
            sign = -sign;
        }

        // Interpolate doffset towards 0
        doffset = (long)((1.0f - lerp)*doffset);

        // Keep the same seconds count
        long dh = doffset / (MILLISECONDS_PER_HOUR);
        doffset -= dh * MILLISECONDS_PER_HOUR;
        long dm = doffset / MILLISECONDS_PER_MINUTE;
        doffset = sign * (60 * dh + dm) * MILLISECONDS_PER_MINUTE;
    
        return doffset;
    }

    /**
     * Set the city to be displayed.  setCity(null) resets things so the clock
     * hand animation won't occur next time.
     */
    public void setCity(City city) {
        if (mCity != city) {
            if (mCity != null) {
                mOldOffset =
                    (long) (mCity.getOffset() * (float) MILLISECONDS_PER_HOUR);
            } else if (city != null) {
                mOldOffset =
                    (long) (city.getOffset() * (float) MILLISECONDS_PER_HOUR);
            } else {
                mOldOffset = 0L; // this will never be used
            }
            this.mCitySwitchTime = System.currentTimeMillis();
            this.mCity = city;
        }
    }

    public void setTime(long time) {
        this.mTime = time;
    }

    /**
     * Draws the clock face.
     * 
     * @param canvas the Canvas to draw to
     * @param cx the X coordinate of the clock center
     * @param cy the Y coordinate of the clock center
     * @param radius the radius of the clock face
     * @param alpha the translucency of the clock face
     * @param textAlpha the translucency of the text
     * @param showCityName if true, display the city name
     * @param showTime if true, display the time digitally
     * @param showUpArrow if true, display an up arrow
     * @param showDownArrow if true, display a down arrow
     * @param showLeftRightArrows if true, display left and right arrows
     * @param prefixChars number of characters of the city name to draw in bold
     */
    public void drawClock(Canvas canvas,
        float cx, float cy, float radius, float alpha, float textAlpha,
        boolean showCityName, boolean showTime,
        boolean showUpArrow,  boolean showDownArrow, boolean showLeftRightArrows,
        int prefixChars) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(20.0f);
        paint.setAlpha(127);

        int iradius = (int)radius;

        TimeZone tz = mCity.getTimeZone();

        // Compute an interpolated time to animate between the previously
        // displayed time and the current time
        float lerp = Math.min(1.0f,
            (System.currentTimeMillis() - mCitySwitchTime) / 500.0f);
        lerp = mClockHandInterpolator.getInterpolation(lerp);
        long doffset = lerp < 1.0f ? getOffset(lerp) : 0L;
    
        // Determine the interpolated time for the given time zone
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(mTime - doffset);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int milli = cal.get(Calendar.MILLISECOND);

        float offset = tz.getRawOffset() / (float) MILLISECONDS_PER_HOUR;
        float daylightOffset = tz.inDaylightTime(new Date(mTime)) ?
            tz.getDSTSavings() / (float) MILLISECONDS_PER_HOUR : 0.0f;

        float absOffset = offset < 0 ? -offset : offset;
        int offsetH = (int) absOffset;
        int offsetM = (int) (60.0f * (absOffset - offsetH));
        hour %= 12;

        // Get the city name and digital time strings
        String cityName = mCity.getName();
        cal.setTimeInMillis(mTime);
        String time = DateUtils.formatDateTime(context, cal.getTimeInMillis(),0) + " "  +
            DateUtils.getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK),
                    DateUtils.LENGTH_SHORT) + " " +
            " (UTC" +
            (offset >= 0 ? "+" : "-") +
            offsetH +
            (offsetM == 0 ? "" : ":" + offsetM) +
            (daylightOffset == 0 ? "" : "+" + daylightOffset) +
            ")";

        float th = paint.getTextSize();
        float tw;

        // Set the text color
        paint.setARGB((int) (textAlpha * 255.0f),
                      (int) (mColorRed * 205.0f),
                      (int) (mColorGreen * 255.0f),
                      (int) (mColorBlue * 255.0f));

        tw = paint.measureText(cityName);
        if (showCityName) {
            // Increment prefixChars to include any spaces
            for (int i = 0; i < prefixChars; i++) {
                if (cityName.charAt(i) == ' ') {
                    ++prefixChars;
                }
            }

            // Draw the city name
            canvas.drawText(cityName, cx - tw / 2, cy - radius - th, paint);
            // Overstrike the first 'prefixChars' characters
            canvas.drawText(cityName.substring(0, prefixChars),
                            cx - tw / 2 + 1, cy - radius - th, paint);
        }
        tw = paint.measureText(time);
        if (showTime) {
            canvas.drawText(time, cx - tw / 2, cy + radius + th + 5, paint);
        }

        paint.setARGB((int)(alpha * 255.0f),
                      (int)(mColorRed * 155.0f),
                      (int)(mColorGreen * 205.0f),
                      (int)(mColorBlue * 255.0f));

        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(new RectF(cx - 2, cy - 2, cx + 2, cy + 2), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(radius * 0.12f);

        canvas.drawOval(new RectF(cx - iradius, cy - iradius,
                                  cx + iradius, cy + iradius),
                        paint);

        float r0 = radius * 0.1f;
        float r1 = radius * 0.4f;
        float r2 = radius * 0.6f;
        float r3 = radius * 0.65f;
        float r4 = radius * 0.7f;
        float r5 = radius * 0.9f;

        Path path = new Path();

        float ss = second + milli / 1000.0f;
        float mm = minute + ss / 60.0f;
        float hh = hour + mm / 60.0f;

        // Tics for the hours
        for (int i = 0; i < 12; i++) {
            drawLine(path, radius * 0.12f, i / 12.0f, cx, cy, r4, r5);
        }

        // Hour hand
        drawLine(path, radius * 0.12f, hh / 12.0f, cx, cy, r0, r1); 
        // Minute hand
        drawLine(path, radius * 0.12f, mm / 60.0f, cx, cy, r0, r2); 
        // Second hand
        drawLine(path, radius * 0.036f, ss / 60.0f, cx, cy, r0, r3); 

        if (showUpArrow) {
            drawVArrow(path, cx + radius * 1.13f, cy - radius,
                radius * 0.15f, -radius * 0.1f);
        }
        if (showDownArrow) {
            drawVArrow(path, cx + radius * 1.13f, cy + radius,
                radius * 0.15f, radius * 0.1f);
        }
        if (showLeftRightArrows) {
            drawHArrow(path, cx - radius * 1.3f, cy, -radius * 0.1f,
                radius * 0.15f);
            drawHArrow(path, cx + radius * 1.3f, cy,  radius * 0.1f,
                radius * 0.15f);
        }

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    /**
     * Draws the clock face.
     * 
     * @param canvas the Canvas to draw to
     * @param cx the X coordinate of the clock center
     * @param cy the Y coordinate of the clock center
     * @param radius the radius of the clock face
     * @param alpha the translucency of the clock face
     */
    private void drawClockButton(Canvas canvas,
        float cx, float cy, float radius, float alpha ) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        int iradius = (int)radius;

        paint.setARGB((int)(alpha * 255.0f),
                      (int)(mColorRed * 205.0f),
                      (int)(mColorGreen * 205.0f),
                      (int)(mColorBlue * 255.0f));

        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(new RectF(cx - 2, cy - 2, cx + 2, cy + 2), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(radius * 0.12f);

        canvas.drawOval(new RectF(cx - iradius, cy - iradius,
                                  cx + iradius, cy + iradius),
                        paint);

        float r0 = radius * 0.1f;
        float r1 = radius * 0.4f;
        float r2 = radius * 0.6f;
        float r3 = radius * 0.65f;
        float r4 = radius * 0.7f;
        float r5 = radius * 0.9f;

        Path path = new Path();

        TimeZone tz = TimeZone.getDefault();
    
        // Determine the interpolated time for the given time zone
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(System.currentTimeMillis());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int milli = cal.get(Calendar.MILLISECOND);
        hour %= 12;

        float ss = second + milli / 1000.0f;
        float mm = minute + ss / 60.0f;
        float hh = hour + mm / 60.0f;


        // Tics for the hours
        for (int i = 0; i < 12; i++) {
            drawLine(path, radius * 0.12f, i / 12.0f, cx, cy, r4, r5);
        }

        // Hour hand
        drawLine(path, radius * 0.12f, hh / 12.0f, cx, cy, r0, r1); 
        // Minute hand
        drawLine(path, radius * 0.12f, mm / 60.0f, cx, cy, r0, r2); 
        // Second hand
        drawLine(path, radius * 0.06f, ss / 60.0f, cx, cy, r0, r3); 

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    /**
     * Draws the arrows.
     *
     * @param canvas the Canvas to draw to
     * @param w the width of the screen
     * @param h the height of the screen
     */
    public void drawButton(Canvas canvas,
                           int w, int h, boolean showV, boolean showH
                            ) {
        drawButton( canvas,  w,  h,  showV,  showH,  null);
        }
    /**
     * Draws the arrows.
     *
     * @param canvas the Canvas to draw to
     * @param w the width of the screen
     * @param h the height of the screen
     */
    public void drawButton(Canvas canvas,
                           int w, int h, boolean showV, boolean showH, Bitmap bm 
                            ) {
        float radius = 160.45f;//  * Math.abs(w - h);
        int bradius = (int)radius/3;
        int aradius = (int)radius/2;
        int iradius = (int)bradius+2;
        //Log.e(TAG, ""+radius);

        float cx = w -radius;
        float cy = h - radius;

        if(w != width)
            {
            //                       left           top           right           bottom
            buttons[0] = new RectF(cx - iradius, cy - iradius, cx + iradius, cy + iradius);//@
            buttons[1] = new RectF(cx - bradius, cy -  radius, cx + bradius, cy - aradius);//^
            buttons[2] = new RectF(cx - bradius, cy + aradius, cx + bradius, cy + radius);//v
            buttons[3] = new RectF(cx -  radius, cy - bradius, cx - aradius, cy + bradius);//<
            buttons[4] = new RectF(cx + aradius, cy - bradius, cx +  radius, cy + bradius);//>
            buttons[5] = new RectF(0,            0,            48,           48          );//home icon
            buttons[6] = new RectF(cx + bradius, cy - bradius, cx +  radius, cy + bradius);//
            buttonshow[0] = true;
            width = w;
            }
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Set the  color, lerp = 0.5f
        paint.setARGB((int)(0.5f * 255.0f),
                      (int)(mColorRed * 205.0f),
                      (int)(mColorGreen * 225.0f),
                      (int)(mColorBlue * 255.0f));

//        paint.setStyle(Paint.Style.FILL);

//        canvas.drawOval(buttons[0], paint);
        drawClockButton(canvas,buttons[0].centerX(),buttons[0].centerY(),buttons[0].width()/2,0.5f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(radius * 0.05f);

        //canvas.drawOval(new RectF(cx - iradius, cy - iradius,
        //                          cx + iradius, cy + iradius),
        //                paint);

        Path path = new Path();

        buttonshow[1] = false;
        buttonshow[2] = false;
        buttonshow[3] = false;
        buttonshow[4] = false;
        buttonshow[5] = false;
        buttonshow[6] = false;

        if(showV)
            {
            drawVArrow(path, buttons[1].centerX(), buttons[1].bottom, buttons[1].width(), -buttons[1].height()); buttonshow[1] = true;
            drawVArrow(path, buttons[2].centerX(), buttons[2].top      , buttons[2].width(),  buttons[2].height()); buttonshow[2] = true;
            }
        if(showH)
            {
            drawHArrow(path, buttons[3].right, buttons[3].centerY(), -buttons[3].width(), buttons[3].height()); buttonshow[3] = true;
            drawHArrow(path, buttons[4].left  , buttons[4].centerY(), buttons[4].width(), buttons[4].height()); buttonshow[4] = true;
            }

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
        
        if(bm != null)
        {
        	canvas.drawBitmap(bm, null, buttons[5], null); buttonshow[5] = true;
        }

    }
    
    public int checkButton(float x, float y){
    	int i;
    	if(buttons[0] == null)
    		return buttonsSum;
    	
    	for(i=0; i<buttonsSum; i++){
           if(buttonshow[i] == false)continue;
           if(buttons[i].contains(x, y))break;
    	}
    	return i;
    }
    
}
