package com.serenegiant.glutils;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: RenderHandler.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

/**
 * Helper class to draw texture to whole view on private thread
 */
@SuppressWarnings("WeakerAccess")
public final class RenderHandler implements Runnable {

    private static final String TAG = "RenderHandler";

    private final Object mSync = new Object();
    private EGLContext mShared_context;
    private boolean mIsRecordable;
    private Object mSurface;
    private int mTexId = -1;

    private float[] mMatrix = new float[32];

    private boolean mRequestSetEglContext;
    private boolean mRequestRelease;
    private int mRequestDraw;
    //********************************************************************************
    //********************************************************************************
    private EGLBase mEgl;
    private EGLBase.EglSurface mInputSurface;
    private GLDrawer2D mDrawer;
    private int mProgramId;

    public RenderHandler(GLDrawer2D drawer) {
        mDrawer = drawer;
    }

    public static RenderHandler createHandler(final String name, GLDrawer2D drawer) {
        Log.d(TAG, "createHandler:");

        final RenderHandler handler = new RenderHandler(drawer);
        synchronized (handler.mSync) {
            new Thread(handler, !TextUtils.isEmpty(name) ? name : TAG).start();
            try {
                handler.mSync.wait();
            } catch (final InterruptedException ignore) {
            }
        }
        return handler;
    }

    public final void setEglContext(final EGLContext shared_context, final int tex_id,
                                    final Object surface, final boolean isRecordable) {
        Log.d(TAG, "setEglContext:");

        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)
                && !(surface instanceof SurfaceHolder)) {
            throw new RuntimeException("unsupported window type:" + surface);
        }

        synchronized (mSync) {
            if (mRequestRelease) return;
            mShared_context = shared_context;
            mTexId = tex_id;
            mSurface = surface;
            mIsRecordable = isRecordable;
            mRequestSetEglContext = true;
            Matrix.setIdentityM(mMatrix, 0);
            Matrix.setIdentityM(mMatrix, 16);
            mSync.notifyAll();
            try {
                mSync.wait();
            } catch (final InterruptedException ignore) {
            }
        }
    }

    public final void draw() {
        draw(mTexId, mMatrix, null);
    }

    public final void draw(final int tex_id) {
        draw(tex_id, mMatrix, null);
    }

    public final void draw(final float[] tex_matrix) {
        draw(mTexId, tex_matrix, null);
    }

    public final void draw(final float[] tex_matrix, final float[] mvp_matrix) {
        draw(mTexId, tex_matrix, mvp_matrix);
    }

    public final void draw(final int tex_id, final float[] tex_matrix) {
        draw(tex_id, tex_matrix, null);
    }

    public final void draw(final int tex_id, final float[] tex_matrix, final float[] mvp_matrix) {
        synchronized (mSync) {
            if (mRequestRelease) return;
            mTexId = tex_id;
            if ((tex_matrix != null) && (tex_matrix.length >= 16)) {
                System.arraycopy(tex_matrix, 0, mMatrix, 0, 16);
            } else {
                Matrix.setIdentityM(mMatrix, 0);
            }

            if ((mvp_matrix != null) && (mvp_matrix.length >= 16)) {
                System.arraycopy(mvp_matrix, 0, mMatrix, 16, 16);
            } else {
                Matrix.setIdentityM(mMatrix, 16);
            }

            mRequestDraw++;
            mSync.notifyAll();
/*			try {
				mSync.wait();
			} catch (final InterruptedException e) {
			} */
        }
    }

    public boolean isValid() {
        synchronized (mSync) {
            return !(mSurface instanceof Surface) || ((Surface) mSurface).isValid();
        }
    }

    public final void release() {
        Log.d(TAG, "release:");

        synchronized (mSync) {
            if (mRequestRelease) return;
            mRequestRelease = true;
            mSync.notifyAll();
            try {
                mSync.wait();
            } catch (final InterruptedException ignore) {
            }
        }
    }

    @Override
    public final void run() {
        Log.d(TAG, "RenderHandler thread started:");

        synchronized (mSync) {
            mRequestSetEglContext = mRequestRelease = false;
            mRequestDraw = 0;
            mSync.notifyAll();
        }

        boolean localRequestDraw;
        for (; ; ) {
            synchronized (mSync) {
                if (mRequestRelease) break;

                if (mRequestSetEglContext) {
                    mRequestSetEglContext = false;
                    internalPrepare();
                }

                localRequestDraw = mRequestDraw > 0;

                if (localRequestDraw) {
                    mRequestDraw--;
                    // mSync.notifyAll();
                }
            }


            if (localRequestDraw) {
                if ((mEgl != null) && mTexId >= 0) {
                    mInputSurface.makeCurrent();
                    mDrawer.draw(mProgramId, mTexId, mMatrix);
                    mInputSurface.swap();
                }
            } else {
                synchronized (mSync) {
                    try {
                        mSync.wait();
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            }
        }

        synchronized (mSync) {
            mRequestRelease = true;
            internalRelease();
            mSync.notifyAll();
        }

        Log.d(TAG, "RenderHandler thread finished:");
    }

    private void internalPrepare() {
        Log.d(TAG, "internalPrepare:");
        internalRelease();
        mEgl = new EGLBase(mShared_context, false, mIsRecordable);

        mInputSurface = mEgl.createFromSurface(mSurface);

        mInputSurface.makeCurrent();
        mProgramId = mDrawer.init();
        mSurface = null;
        mSync.notifyAll();
    }

    private void internalRelease() {
        Log.d(TAG, "internalRelease:");

        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }

        if (mDrawer != null) {
            mDrawer.release(mProgramId);
        }

        if (mEgl != null) {
            mEgl.release();
            mEgl = null;
        }
    }
}