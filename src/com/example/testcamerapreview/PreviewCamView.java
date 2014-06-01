package com.example.testcamerapreview;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Size;

public class PreviewCamView extends SurfaceView implements Camera.PreviewCallback, SurfaceHolder.Callback{

	private Camera mCamera;
	private byte [] mVideoSrc;
	private byte [] refBuffer;
	private Bitmap mBackBuffer;
	private Paint mPaint;
	int [] rgb;
	private Size lCamSize;
	SurfaceHolder mSurfaceHolder;
	Thread mDecoder, mRenderer;
	Semaphore mDecoderSem, mRendererSem;
	private RenderWorker rWorker;
	private DecodeWorker dWorker;
	//AtomicInteger countPush, countPop;
	public PreviewCamView(Context context) {
		super(context);
		getHolder().addCallback(this);
		setWillNotDraw(true);
	}
	/*
	@Override
	protected void onDraw(Canvas pCanvas) {
		if (mCamera != null) {
			//pCanvas.drawBitmap(mBackBuffer, 0, 0, mPaint);
			pCanvas.drawBitmap(mBackBuffer, null, pCanvas.getClipBounds(), mPaint);
			//pCanvas.drawBitmap(rgb, 0, pCanvas.getWidth(), 0, 0, pCanvas.getWidth(), pCanvas.getHeight(), false, mPaint);
			mCamera.addCallbackBuffer(mVideoSrc);
		}
	}
	*/
	public void onPreviewFrame(byte[] data, Camera camera) {
		//mCamera.addCallbackBuffer(mVideoSrc[count.getAndAdd(1)%BUF_SIZE]);
		//decodeYCrCb2ARGB(mBackBuffer, data, lCamSize.width, lCamSize.height);
		//invalidate();
		if (dWorker != null)
			dWorker.setDecodeBuffer(data);
		mDecoderSem.release();	
	}



	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		
		mCamera.stopPreview();
		
		lCamSize = findBestResolution(width, height);
		PixelFormat lpf = new PixelFormat();
		PixelFormat.getPixelFormatInfo(mCamera.getParameters().getPreviewFormat(), lpf);
		int lSourceSize = lCamSize.width * lCamSize.height * lpf.bitsPerPixel / 8;
		
		mVideoSrc = new byte[lSourceSize];
		rgb = new int[lCamSize.width * lCamSize.height];
		mBackBuffer = Bitmap.createBitmap(lCamSize.width, lCamSize.height, Bitmap.Config.ARGB_8888);
		
		Camera.Parameters params = mCamera.getParameters();
		params.setPreviewSize(lCamSize.width, lCamSize.height);
		params.setPreviewFormat(ImageFormat.NV21);
		mCamera.setParameters(params);
		
		mCamera.addCallbackBuffer(mVideoSrc);
		mCamera.startPreview();	
		
		mDecoderSem = new Semaphore(0, false);
		mRendererSem = new Semaphore(0, false);
		Semaphore [] inputSem = {mDecoderSem, mRendererSem};
		//starting out our threads 
		rWorker = new RenderWorker(holder, mBackBuffer, mRendererSem);
	    dWorker = new DecodeWorker(inputSem, mVideoSrc, mCamera, rWorker);
		
		mRenderer = new Thread(rWorker);
		mDecoder = new Thread(dWorker);
		
		mRenderer.start();
		mDecoder.start();
	}
	
	private Size findBestResolution(int w, int h) {
		List<Size> lSizes = mCamera.getParameters().getSupportedPictureSizes();
		for (Size size: lSizes) {
			//find first matching size then return it
			if ( (size.width <= w) && (size.height <= 480)) {
				Size lMatchedSize = size;
				return lMatchedSize;
			}
		}
		return lSizes.get(0);
		
	}

	public void surfaceCreated(SurfaceHolder holder) {
		try{
			mCamera = Camera.open();
			mCamera.setDisplayOrientation(0);
			mCamera.setPreviewDisplay(null);
			mCamera.setPreviewCallbackWithBuffer(this);
			mSurfaceHolder = holder;
		} catch (IOException ioe) {
			mCamera.release();
			mCamera = null;
			throw new IllegalStateException();
		}
		
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			mVideoSrc = null;
			rgb = null;
			mBackBuffer = null;
		}
		//stoping all of threads
		if (mDecoder != null) {
			mDecoder.interrupt(); dWorker = null;
		}
		if (mRenderer != null) {
			mRenderer.interrupt(); rWorker = null;
		}
	}

}
