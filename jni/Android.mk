LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := TestCameraPreview
LOCAL_SRC_FILES := TestCameraPreview.cpp

include $(BUILD_SHARED_LIBRARY)
