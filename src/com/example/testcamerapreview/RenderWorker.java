package com.example.testcamerapreview;

import java.util.concurrent.Semaphore;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.SurfaceHolder;

public class RenderWorker implements Runnable{

	SurfaceHolder mSurfaceHolder;
	//double buffering -- other threads can only update Backbuffer, Renderer only use FrontBuffer
	Bitmap mBackBuffer, mFrontBuffer;
	Object mLock = new Object();
	
	private Semaphore renderSmph;
	private Semaphore cycleback;
	private void switchBuffer() {
		synchronized(mLock) {
			Bitmap temp = mFrontBuffer;
			mFrontBuffer = mBackBuffer;
			mBackBuffer = temp;
		}
	}
	public void setBackBuffer(Bitmap bmp) {
		try {
			cycleback.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		synchronized(mLock) {
			mBackBuffer = bmp;
		}
	}
	public RenderWorker(SurfaceHolder sh, Bitmap backBuffer, Semaphore sph) {
		mSurfaceHolder = sh;
		renderSmph = sph;
		cycleback = new Semaphore(0, false);
		mFrontBuffer = Bitmap.createBitmap(backBuffer.getWidth(), backBuffer.getHeight(), Bitmap.Config.ARGB_8888);
	}
	@Override
	public void run() {
		
		while (!Thread.interrupted()) {
			
			try 
			{
				cycleback.release();
				//wait til one backbuffer is available
				renderSmph.acquire();
				switchBuffer();
				Canvas lCanvas = null;
				try {
					lCanvas = mSurfaceHolder.lockCanvas();
					    if (lCanvas!= null)
						//always draw from front buffer
					    lCanvas.drawBitmap(mFrontBuffer, null, lCanvas.getClipBounds(), null);
				} 
				finally {
                    if (lCanvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(lCanvas);
                    }
                }
			} catch (InterruptedException e) {
				// break out of run loop
				break;
			}
		}
		
		
	}

}
