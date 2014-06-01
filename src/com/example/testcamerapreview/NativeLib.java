package com.example.testcamerapreview;

import android.graphics.Bitmap;
import android.util.Log;

public class NativeLib {
	
	static {
		try {
			System.loadLibrary("TestCameraPreview");
		} catch (UnsatisfiedLinkError e) {
			Log.e("NativeLib", "Error loading native lib!!");	
		}
	}
    /* bmpInOut destroys original Content of bmpInOut */
	public static native void runNativeDecoder(Bitmap bmpInOut, int width, int height);
}
