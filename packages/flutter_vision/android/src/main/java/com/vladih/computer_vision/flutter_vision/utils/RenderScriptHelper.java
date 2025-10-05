package com.vladih.computer_vision.flutter_vision.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;

public class RenderScriptHelper implements AutoCloseable {
    private static final String TAG = "RenderScriptHelper";
    private static RenderScriptHelper instance;
    private static final Object lock = new Object();

    private final RenderScript rs;
    private final ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
    private Allocation inputAllocation;
    private Allocation outputAllocation;
    private volatile boolean isClosed = false;
    
    // Cache for allocation dimensions
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int lastNv21Length = -1;

    private RenderScriptHelper(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        
        try {
            rs = RenderScript.create(context);
            yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
            Log.d(TAG, "RenderScript initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RenderScript", e);
            throw new RuntimeException("RenderScript initialization failed", e);
        }
    }

    public static RenderScriptHelper getInstance(Context context) {
        if (instance == null || instance.isClosed) {
            synchronized (lock) {
                if (instance == null || instance.isClosed) {
                    instance = new RenderScriptHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Convert NV21 data to RGBA allocation with improved memory management
     */
    public Allocation renderScriptNV21ToRGBA888(int width, int height, byte[] nv21) {
        if (isClosed) {
            throw new IllegalStateException("RenderScriptHelper has been closed");
        }
        
        if (nv21 == null || nv21.length == 0) {
            throw new IllegalArgumentException("Invalid NV21 data");
        }
        
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions: " + width + "x" + height);
        }

        try {
            // Recreate input allocation if NV21 array size changes
            if (nv21.length != lastNv21Length || inputAllocation == null) {
                if (inputAllocation != null) {
                    inputAllocation.destroy();
                }
                
                Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.U8(rs))
                        .setX(nv21.length);
                inputAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(), 
                        Allocation.USAGE_SCRIPT);
                lastNv21Length = nv21.length;
                
                Log.d(TAG, String.format("Created new input allocation for %d bytes", nv21.length));
            }

            // Recreate output allocation if dimensions change
            if (width != lastWidth || height != lastHeight || outputAllocation == null) {
                if (outputAllocation != null) {
                    outputAllocation.destroy();
                }
                
                Type.Builder rgbaTypeBuilder = new Type.Builder(rs, Element.RGBA_8888(rs))
                        .setX(width)
                        .setY(height);
                outputAllocation = Allocation.createTyped(rs, rgbaTypeBuilder.create(), 
                        Allocation.USAGE_SCRIPT);
                lastWidth = width;
                lastHeight = height;
                
                Log.d(TAG, String.format("Created new output allocation for %dx%d", width, height));
            }

            // Validate NV21 data size
            int expectedSize = width * height * 3 / 2; // YUV420 format
            if (nv21.length < expectedSize) {
                Log.w(TAG, String.format("NV21 data size (%d) smaller than expected (%d)", 
                        nv21.length, expectedSize));
            }

            // Convert YUV to RGBA
            inputAllocation.copyFrom(nv21);
            yuvToRgbIntrinsic.setInput(inputAllocation);
            yuvToRgbIntrinsic.forEach(outputAllocation);

            return outputAllocation;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting NV21 to RGBA", e);
            throw new RuntimeException("NV21 to RGBA conversion failed: " + e.getMessage());
        }
    }

    /**
     * Convert NV21 byte array to Bitmap with improved error handling
     */
    public static Bitmap getBitmapFromNV21(Context context, byte[] nv21, int width, int height) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        
        if (nv21 == null || nv21.length == 0) {
            throw new IllegalArgumentException("Invalid NV21 data");
        }
        
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions: " + width + "x" + height);
        }

        RenderScriptHelper rsHelper;
        Bitmap bitmap = null;
        
        try {
            rsHelper = getInstance(context);
            Allocation allocation = rsHelper.renderScriptNV21ToRGBA888(width, height, nv21);
            
            if (allocation == null) {
                throw new Exception("Failed to create RGBA allocation");
            }

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            allocation.copyTo(bitmap);

            Log.d(TAG, String.format("Successfully converted NV21 to bitmap (%dx%d)", width, height));
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating bitmap from NV21", e);
            
            // Clean up bitmap if creation failed
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            
            throw new RuntimeException("Bitmap creation from NV21 failed: " + e.getMessage());
        }
    }

    /**
     * Clean up resources - implements AutoCloseable
     * This method is idempotent and can be called multiple times safely
     */
    @Override
    public void close() {
        if (isClosed) {
            return; // Already closed, no-op
        }
        
        synchronized (lock) {
            if (isClosed) {
                return; // Double-check after acquiring lock
            }
            
            try {
                if (inputAllocation != null) {
                    inputAllocation.destroy();
                    inputAllocation = null;
                }
                
                if (outputAllocation != null) {
                    outputAllocation.destroy();
                    outputAllocation = null;
                }
                
                if (yuvToRgbIntrinsic != null) {
                    yuvToRgbIntrinsic.destroy();
                }
                
                if (rs != null) {
                    rs.destroy();
                }
                
                isClosed = true;
                Log.d(TAG, "RenderScript resources cleaned up");
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up RenderScript resources", e);
            }
        }
    }

    /**
     * @deprecated Use close() instead. This method is provided for backwards compatibility.
     */
    @Deprecated
    public void cleanup() {
        close();
    }

    /**
     * Check if this instance has been closed
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Get current memory usage info
     */
    public String getMemoryInfo() {
        if (isClosed) {
            return "RenderScript: CLOSED";
        }
        return String.format("RenderScript Allocations - Input: %s, Output: %s", 
                inputAllocation != null ? "Active" : "None",
                outputAllocation != null ? "Active" : "None");
    }

    /**
     * Static method to safely close and reset the singleton instance
     * Useful for testing or when you need to completely reset the instance
     */
    public static void resetInstance() {
        synchronized (lock) {
            if (instance != null) {
                instance.close();
                instance = null;
                Log.d(TAG, "RenderScriptHelper instance reset");
            }
        }
    }
}