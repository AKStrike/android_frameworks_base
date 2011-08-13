/*
 * Copyright (C) 2011 doixanh@xda
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

package com.android.internal.policy.impl;

import com.android.internal.R;
import android.content.res.Configuration;
import com.android.internal.widget.LockPatternUtils;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

public class GdxLock extends LinearLayout implements KeyguardScreen {
	private boolean DBG = false;
	private String TAG = "GDXLock";
	
	// constants
	private int ringWidth = 160;
	private int ringHeight = 160;

	private static int SCREEN_WIDTH = 320;
	private static int SCREEN_HEIGHT = 480;

	// controls
	private ImageView ring;
	private Context mContext;
	private KeyguardScreenCallback mCallback;
    private TextView mdxLeft;
    private TextView mdxRight;

	// flinging calculation
	public GestureDetector gestureDetector;
	private DecelerateInterpolator interpolator;
	private AccelerateInterpolator zoomIntp;
	private long startTime;
	private long endTime;
	private int totalAnimDx;
	private int totalAnimDy;
	private int startFlingX;
	private int startFlingY;
	private boolean isFlinging;

    public GdxLock(Context context, Configuration configuration, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
		super(context);

		mContext = context;
		mCallback = callback;		

		// init gesture detector
		gestureDetector = new GestureDetector(new MyGestureDetector());

		// initialize lockscreen layout
		final LayoutInflater inflater = LayoutInflater.from(context);
		inflater.inflate(R.layout.gdxlock_sense3, this, true);

		// init ring
		ring = (ImageView) findViewById(R.id.ring);

		// move the ring to default position
		int left = (int) (SCREEN_WIDTH / 2 - ringWidth / 2);
		int top = (int) (SCREEN_HEIGHT - ringHeight / 2 - 20 + 110);
		ring.setPadding(left, top, 0, 0);

		// set background...
		ViewGroup lockWallpaper = (ViewGroup) findViewById(R.id.root);
		setBackground(mContext, lockWallpaper);
		
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        
        // label setup
        mdxLeft = (TextView) findViewById(R.id.dxLeft);
        mdxRight = (TextView) findViewById(R.id.dxRight);
        mdxLeft.setVisibility(View.VISIBLE);
        mdxLeft.setText("GingerDX." + android.os.SystemProperties.get("ro.build.display.id"));
        mdxLeft.setTextColor(0xffffffff);
        mdxRight.setVisibility(View.VISIBLE);
        mdxRight.setText("doixanh@xda");
        mdxRight.setTextColor(0xffffffff);


	}
	
	private void zoomRing(int newWidth, int newHeight) {
		ring.setLayoutParams(new LayoutParams(newWidth, newHeight));
		ringWidth = newWidth;
		ringHeight = newHeight;
	}

	// moves the ring to a specific position
	private void moveRing(int left, int top) {
		// check screen border
		if (left < -ringWidth / 2 || left > SCREEN_WIDTH - ringWidth / 2 || 
			top < -ringHeight / 2 || top > SCREEN_HEIGHT - ringHeight / 4)
			return;
		// above half?
		if ((int) (top + ringWidth / 2) < SCREEN_HEIGHT / 2) {
			// unlock it
			mCallback.goToUnlockScreen();			
		}
		ring.setPadding(left, top, 0, 0);
	}

	// handles touch event
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isFlinging) {
			int orgLeft = ring.getPaddingLeft();
			int orgTop = ring.getPaddingTop();
			int left = (int) (event.getRawX() - ringWidth / 2 - 10);
			int top = (int) (event.getRawY() - ringHeight / 2 - 20);
			// check touch position
			int dist = (left - orgLeft) * (left - orgLeft) + (top - orgTop)
					* (top - orgTop);
			if (DBG)
				Log.i(TAG, "distance : " + dist);
	
			if (dist < ringWidth * ringHeight) {
				// move the ring
				moveRing(left, top);
				// and check flinging
				gestureDetector.onTouchEvent(event);
			}
		}
		return false;
	}

	// detects fling gesture
	class MyGestureDetector extends SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			try {
				if (DBG)
					Log.i(TAG, "flinging : vx=" + velocityX + " vy="
							+ velocityY);
				final float distanceTimeFactor = 0.3f;
				final float totalDx = (distanceTimeFactor * velocityX / 2);
				final float totalDy = (distanceTimeFactor * velocityY / 2);

				animateFling(ring.getPaddingLeft(), ring.getPaddingTop(),
						totalDx, totalDy, (long) (1000 * distanceTimeFactor));
				return true;
			} catch (Exception e) {
				// nothing
			}
			return false;
		}
	}
	
	// initalize fling
	public void animateFling(int sx, int sy, float dx, float dy, long duration) {
		interpolator = new DecelerateInterpolator();
		startTime = System.currentTimeMillis();
		endTime = startTime + duration;
		totalAnimDx = (int) dx;
		totalAnimDy = (int) dy;
		startFlingX = sx;
		startFlingY = sy;
		isFlinging = true;
		post(new Runnable() {
			@Override
			public void run() {
				animateStep();
			}
		});
	}

	// fling steps
	private void animateStep() {
		long curTime = System.currentTimeMillis();
		float percentTime = (float) (curTime - startTime)
				/ (float) (endTime - startTime);
		float percentDistance = interpolator.getInterpolation(percentTime);
		float curDx = percentDistance * totalAnimDx;
		float curDy = percentDistance * totalAnimDy;
		// translate.set(animateStart);
		moveRing((int) (startFlingX + curDx), (int) (startFlingY + curDy));
		
		// not yet finished?
		if (percentTime < 1.0f) {
			// more!
			post(new Runnable() {
				@Override
				public void run() {
					animateStep();
				}
			});
		} else {
			// finished
			isFlinging = false;
		}
	}

	// set wallpaper as background
	private void setBackground(Context bcontext, ViewGroup layout) {
		// Settings.System.LOCKSCREEN_BACKGROUND
		String mLockBack = Settings.System.getString(
				bcontext.getContentResolver(), "lockscreen_background");
		if (mLockBack != null) {
			if (!mLockBack.isEmpty()) {
				try {
					layout.setBackgroundColor(Integer.parseInt(mLockBack));
				} catch (NumberFormatException e) {
				}
			} else {
				String lockWallpaper = "";
				try {
					lockWallpaper = bcontext.createPackageContext(
							"com.cyanogenmod.cmparts", 0).getFilesDir()
							+ "/lockwallpaper";
				} catch (NameNotFoundException e1) {
				}
				if (!lockWallpaper.isEmpty()) {
					Bitmap lockb = BitmapFactory.decodeFile(lockWallpaper);
					layout.setBackgroundDrawable(new BitmapDrawable(lockb));
				}
			}
		}
	}
	
    public boolean needsInput() {
        return false;
    }
    
    public void onPause() {

    }

    public void onResume() {
    }

    public void cleanUp() {
    }

}
