package com.example.testcamerapreview;

import java.util.concurrent.Semaphore;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.view.SurfaceView;

public class DecodeWorker implements Runnable{

	//we have two major place for synchronization 1) between cam preview callback and decoder
	//                                            2) between decoder and renderer
	private Semaphore decodeSmph, renderSmph, cycleback;
	//we will have two buffers one is currently active used by decoder thread, another is free to be used by callback
	byte [] decodeBuffer, freeBuffer;
	// integer color buffer storing rgba values
	int [] rgbaBuffer;
	int width, height;
	Object mLock = new Object();
	private Camera mCam;
	private Bitmap bmpBuffer;
	RenderWorker renderWorker;
	
	public DecodeWorker(Semaphore [] smph, byte [] buff, Camera pCam, RenderWorker rw) {
		decodeSmph = smph[0];
		renderSmph = smph[1];
		cycleback = new Semaphore(0, false);
		//making sure we always have one free buffer to offer
		freeBuffer = new byte[buff.length];
		//decodeBuffer = buff[1];
		mCam = pCam;
		Size lSize = pCam.getParameters().getPreviewSize();
		width = lSize.width;
		height = lSize.height;
		rgbaBuffer = new int[width*height];
		bmpBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		renderWorker = rw;
	}
	
	public void setDecodeBuffer(byte [] decodeBuff) {
		try {
			cycleback.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		synchronized(mLock) {
			decodeBuffer = decodeBuff;
		}
	}
	
	public void offerFreeBuffer() {
		synchronized(mLock) {
			if (mCam != null)
				mCam.addCallbackBuffer(freeBuffer);
		}
	}
	@Override
	public void run() {
		//trying to acquire semaphore blocks if not available
		while (!Thread.interrupted()) {
			try {
				//this statement will block until either interrupt or semph available
				cycleback.release();
				decodeSmph.acquire();
				//first offer Camera preview a free buffer
				offerFreeBuffer();
				//the do decoding work
				decodeYCrCb2ARGB(bmpBuffer, decodeBuffer, width, height);
				//at the end our decoder buffer become free again.
				freeBuffer = decodeBuffer;
				
				//offer bitmap to renderer
				renderWorker.setBackBuffer(bmpBuffer.copy(Bitmap.Config.ARGB_8888, true));
				renderSmph.release();
				
			} catch (InterruptedException e) {
				//break out of run loop
				break;
			}
		}
		
	}


	private void decodeYCrCb2ARGB(Bitmap mBackBuffer, byte[] data, int width, int height) {
    	final int frameSize = width * height;
    	
    	for (int j = 0, yp = 0; j < height; j++) {
    		int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
    		for (int i = 0; i < width; i++, yp++) {
    			int y = (0xff & ((int) data[yp])) - 16;
    			if (y < 0) y = 0;
    			if ((i & 1) == 0) {
    				v = (0xff & data[uvp++]) - 128;
    				u = (0xff & data[uvp++]) - 128;
    			}
    			
    			int y1192 = 1192 * y;
    			int r = (y1192 + 1634 * v);
    			int g = (y1192 - 833 * v - 400 * u);
    			int b = (y1192 + 2066 * u);
    			
    			if (r < 0) r = 0; else if (r > 262143) r = 262143;
    			if (g < 0) g = 0; else if (g > 262143) g = 262143;
    			if (b < 0) b = 0; else if (b > 262143) b = 262143;
    			
    			rgbaBuffer[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    		}
    	}
    	mBackBuffer.setPixels(rgbaBuffer, 0, width, 0, 0, width, height);
	}
}
