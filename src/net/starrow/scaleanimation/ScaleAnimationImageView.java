/**
 * ScaleAnimationImageView
 *
 * Copyright (c) 2014 starrow
 *
 * This software is released under the MIT License.
 *
 * http://opensource.org/licenses/mit-license.php
 */

package net.starrow.scaleanimation;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.widget.ImageView;

/**
 * ImageView with scale animation, which supports pinch-in, pinch-out and
 * double-tap gestures.
 */
public class ScaleAnimationImageView extends ImageView {
    /**
     * Options for arranging the image when scaled image size is smaller than
     * view.
     */
    public enum FitType {
        START, CENTER, END
    }

    private static final float MAX_SCALE = 2.0f;
    private static final float MIN_SCALE = 1.0f;
    private static final float SCALE_RESTRICT_FACTOR = 0.3f;
    private static final long ANIMATION_DURATION = 150;

    private final Matrix mMatrix = new Matrix();

    private int mImageWidth;
    private int mImageHeight;

    // Package scope for performance(often accessed by inner class).
    float mCurrentScale;
    float mCurrentX;
    float mCurrentY;

    private float mFitScale;
    private float mMaxScale;
    private float mMinScale;

    private FitType mHorizontalFitType = FitType.CENTER;
    private FitType mVerticalFitType = FitType.CENTER;

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;

    private final TransformAnimator mAnimator = new TransformAnimator();

    public ScaleAnimationImageView(Context context) {
        this(context, null);
    }

    public ScaleAnimationImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScaleAnimationImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        super.setScaleType(ScaleType.MATRIX);
        mGestureDetector = new GestureDetector(context, new GestureListener());
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureListener());
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        mImageWidth = drawable.getIntrinsicWidth();
        mImageHeight = drawable.getIntrinsicHeight();

        // This method may be called in super constructor,
        // in that case, this class fields have not initialized yet.
        if (mMatrix != null) {
            calculateFitScale();
            adjustPos();
            updateMatrix();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        calculateFitScale();
        adjustPos();
        updateMatrix();
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        mAnimator.animate();
        super.onDraw(canvas);
    };

    @Override
    public void setScaleType(ScaleType scaleType) {
        // Do nothing (ScaleType must be MATRIX)
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleGestureDetector.onTouchEvent(event);
        if (!mScaleGestureDetector.isInProgress()) {
            mGestureDetector.onTouchEvent(event);
        }
        return true;
    }

    private void updateMatrix() {
        mMatrix.setScale(mCurrentScale, mCurrentScale);
        mMatrix.postTranslate(mCurrentX, mCurrentY);
        setImageMatrix(mMatrix);
    }

    private void calculateFitScale() {
        if (mImageWidth > 0 && mImageHeight > 0) {
            mFitScale = Math.min((float) getWidth() / mImageWidth, (float) getHeight()
                    / mImageHeight);
        } else {
            mFitScale = 1.0f;
        }

        // Scale range includes fit scale.
        mMaxScale = Math.max(mFitScale, MAX_SCALE);
        mMinScale = Math.min(mFitScale, MIN_SCALE);

        if (mCurrentScale <= 0) {
            // If current scale is not initialized, set fit scale.
            mCurrentScale = mFitScale;
        } else {
            mCurrentScale = ensureRange(mCurrentScale, mMinScale, mMaxScale);
        }
    }

    private void adjustPos() {
        mCurrentX = getAdjustedPos(mCurrentX, mCurrentScale, mImageWidth, getWidth(),
                mHorizontalFitType);
        mCurrentY = getAdjustedPos(mCurrentY, mCurrentScale, mImageHeight, getHeight(),
                mVerticalFitType);
    }

    private static float getAdjustedPos(float pos, float scale, int imageLength, int viewLength,
            FitType fitType) {
        final float margin = viewLength - imageLength * scale;
        if (margin >= 0) {
            // Scaled image is smaller than view.
            switch (fitType) {
                case START:
                    return 0;
                case CENTER:
                    return margin * 0.5f;
                case END:
                    return margin;
                default:
                    throw new IllegalArgumentException();
            }
        } else {
            // Scaled image is larger than view.
            // In this case, margin means minimum position.
            return ensureRange(pos, margin, 0);
        }
    }

    private static float ensureRange(float value, float min, float max) {
        if (value <= min) {
            return min;
        } else if (value >= max) {
            return max;
        } else {
            return value;
        }
    }

    private class GestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mCurrentX = getAdjustedPos(mCurrentX - distanceX, mCurrentScale, mImageWidth,
                    getWidth(), mHorizontalFitType);
            mCurrentY = getAdjustedPos(mCurrentY - distanceY, mCurrentScale, mImageHeight,
                    getHeight(), mVerticalFitType);
            updateMatrix();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            final float targetScale;
            if (mCurrentScale == mFitScale) {
                // Show original size image.
                targetScale = 1.0f;
            } else {
                // Fit image to view.
                targetScale = mFitScale;
            }

            mAnimator.startScaleAnimation(targetScale, e.getX(), e.getY());
            return true;
        }
    }

    private class ScaleGestureListener implements OnScaleGestureListener {
        private float mPreviousFocusX;
        private float mPreviousFocusY;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mPreviousFocusX = detector.getFocusX();
            mPreviousFocusY = detector.getFocusY();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();

            // Restrict scaling if current scale is out of range.
            if (mCurrentScale >= mMaxScale) {
                scaleFactor = 1f + (scaleFactor - 1f) * mMaxScale / mCurrentScale
                        * SCALE_RESTRICT_FACTOR;
            } else if (mCurrentScale <= mMinScale) {
                scaleFactor = 1f + (scaleFactor - 1f) * mCurrentScale / mMinScale
                        * SCALE_RESTRICT_FACTOR;
            }

            mCurrentScale *= scaleFactor;

            // Calculate position after scaling.
            final float focusX = detector.getFocusX();
            final float focusY = detector.getFocusY();
            mCurrentX = focusX + (mCurrentX - mPreviousFocusX) * scaleFactor;
            mCurrentY = focusY + (mCurrentY - mPreviousFocusY) * scaleFactor;
            updateMatrix();

            mPreviousFocusX = focusX;
            mPreviousFocusY = focusY;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            // Fit image scale within range.
            final float targetScale = ensureRange(mCurrentScale, mMinScale, mMaxScale);
            mAnimator.startScaleAnimation(targetScale, mPreviousFocusX, mPreviousFocusY);
        }
    }

    private class TransformAnimator {
        private float mStartScale;
        private float mStartX;
        private float mStartY;

        private float mTargetScale;
        private float mTargetX;
        private float mTargetY;

        private float mDiffScale;
        private float mDiffX;
        private float mDiffY;

        private long mDuration = ANIMATION_DURATION;
        private long mStartTime;

        private boolean mIsAnimating;

        /**
         * @param targetScale The scale after animation.
         * @param pivotX X position of rotation axis.
         * @param pivotY Y position of rotation axis.
         */
        void startScaleAnimation(float targetScale, float pivotX, float pivotY) {
            // Calculate the position after scaling.
            final float diffScaleFactor = targetScale / mCurrentScale - 1.0f;
            float targetX = mCurrentX + (mCurrentX - pivotX) * diffScaleFactor;
            float targetY = mCurrentY + (mCurrentY - pivotY) * diffScaleFactor;
            targetX = getAdjustedPos(targetX, targetScale, mImageWidth, getWidth(),
                    mHorizontalFitType);
            targetY = getAdjustedPos(targetY, targetScale, mImageHeight, getHeight(),
                    mVerticalFitType);

            startAnimation(targetScale, targetX, targetY);
        }

        void startAnimation(float targetScale, float targetX, float targetY) {
            if (mIsAnimating) {
                // Not override already running animation.
                return;
            }

            if (targetScale == mCurrentScale && targetX == mCurrentX && targetY == mCurrentY) {
                // Need not to change.
                return;
            }

            mStartScale = mCurrentScale;
            mStartX = mCurrentX;
            mStartY = mCurrentY;

            mTargetScale = targetScale;
            mTargetX = targetX;
            mTargetY = targetY;

            mDiffScale = mTargetScale - mStartScale;
            mDiffX = mTargetX - mStartX;
            mDiffY = mTargetY - mStartY;

            mStartTime = System.currentTimeMillis();
            mIsAnimating = true;
            invalidate();
        }

        void animate() {
            if (!mIsAnimating) {
                return;
            }

            final long diffTime = System.currentTimeMillis() - mStartTime;
            if (diffTime >= mDuration) {
                // Finish animation.
                mIsAnimating = false;
                mCurrentScale = mTargetScale;
                mCurrentX = mTargetX;
                mCurrentY = mTargetY;
            } else {
                // Calculate transform which is proportional to progress.
                final float progress = (float) diffTime / mDuration;
                mCurrentScale = mStartScale + mDiffScale * progress;
                mCurrentX = mStartX + mDiffX * progress;
                mCurrentY = mStartY + mDiffY * progress;
            }
            updateMatrix();
            invalidate();
        }
    }
}
