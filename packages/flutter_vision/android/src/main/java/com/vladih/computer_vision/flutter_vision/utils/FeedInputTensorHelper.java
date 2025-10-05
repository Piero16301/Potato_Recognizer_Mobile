package com.vladih.computer_vision.flutter_vision.utils;

import android.graphics.Bitmap;
import android.util.Log;
import java.util.Arrays;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;

public class FeedInputTensorHelper {
    private static final String TAG = "FeedInputTensorHelper";
    private static FeedInputTensorHelper instance;
    private static final Object lock = new Object();
    
    private final TensorImage tensorImage;
    private final ImageProcessor downSizeImageProcessor;
    private final ImageProcessor upSizeImageProcessor;
    
    private final int previousWidth;
    private final int previousHeight;
    private final float previousMean;
    private final float previousStd;

    private FeedInputTensorHelper(int width, int height, float mean, float std) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions: " + width + "x" + height);
        }
        
        this.previousWidth = width;
        this.previousHeight = height;
        this.previousMean = mean;
        this.previousStd = std;
        
        try {
            // Initialize tensor image with FLOAT32 data type
            tensorImage = new TensorImage(DataType.FLOAT32);
            
            // Build downsize processor for larger images
            downSizeImageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(height, width, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(mean, std))
                    .build();
            
            // Build upsize processor for smaller images  
            upSizeImageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeWithCropOrPadOp(height, width))
                    .add(new NormalizeOp(mean, std))
                    .build();
            
            Log.d(TAG, String.format("FeedInputTensorHelper initialized: %dx%d, mean=%.2f, std=%.2f", 
                    width, height, mean, std));
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing FeedInputTensorHelper", e);
            throw new RuntimeException("Failed to initialize FeedInputTensorHelper: " + e.getMessage());
        }
    }

    /**
     * Get singleton instance with thread safety and parameter validation
     */
    public static FeedInputTensorHelper getInstance(int width, int height, float mean, float std) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new FeedInputTensorHelper(width, height, mean, std);
                } else {
                    // Check if parameters changed
                    if (instance.parametersChanged(width, height, mean, std)) {
                        Log.d(TAG, "Parameters changed, creating new instance");
                        instance = new FeedInputTensorHelper(width, height, mean, std);
                    }
                }
            }
        } else {
            // Check if parameters changed (outside synchronized block for performance)
            if (instance.parametersChanged(width, height, mean, std)) {
                synchronized (lock) {
                    // Double-check inside synchronized block
                    if (instance.parametersChanged(width, height, mean, std)) {
                        Log.d(TAG, "Parameters changed, creating new instance");
                        instance = new FeedInputTensorHelper(width, height, mean, std);
                    }
                }
            }
        }
        return instance;
    }

    /**
     * Check if parameters have changed
     */
    private boolean parametersChanged(int width, int height, float mean, float std) {
        return this.previousWidth != width || 
               this.previousHeight != height ||
               Float.compare(this.previousMean, mean) != 0 ||
               Float.compare(this.previousStd, std) != 0;
    }

    /**
     * Convert bitmap to TensorImage with improved error handling and validation
     */
    public static TensorImage getBytebufferFromBitmap(Bitmap bitmap,
                                                     int input_width,
                                                     int input_height, 
                                                     float mean, 
                                                     float std, 
                                                     String size_option) throws Exception {
        // Input validation
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("Invalid bitmap provided");
        }
        
        if (input_width <= 0 || input_height <= 0) {
            throw new IllegalArgumentException("Invalid input dimensions: " + input_width + "x" + input_height);
        }
        
        if (size_option == null || (!size_option.equals("downsize") && !size_option.equals("upsize"))) {
            throw new IllegalArgumentException("Invalid size_option. Must be 'downsize' or 'upsize'");
        }

        try {
            Log.d(TAG, String.format("Processing bitmap: %dx%d -> %dx%d, mode: %s", 
                    bitmap.getWidth(), bitmap.getHeight(), input_width, input_height, size_option));
            
            FeedInputTensorHelper helper = getInstance(input_width, input_height, mean, std);
            
            // Load bitmap into tensor image
            helper.tensorImage.load(bitmap);
            
            TensorImage processedImage;
            
            if ("downsize".equals(size_option)) {
                processedImage = helper.downSizeImageProcessor.process(helper.tensorImage);
            } else { // "upsize"
                processedImage = helper.upSizeImageProcessor.process(helper.tensorImage);
            }
            
            if (processedImage == null || processedImage.getBuffer() == null) {
                throw new Exception("Failed to process tensor image");
            }
            
            // Validate output dimensions
            int[] shape = processedImage.getTensorBuffer().getShape();
            Log.d(TAG, String.format("Processed tensor shape: %s", 
            java.util.Arrays.toString(shape)));
            
            return processedImage;
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing bitmap to TensorImage", e);
            throw new Exception("Bitmap to TensorImage conversion failed: " + e.getMessage());
        }
    }

    /**
     * Optimized version that automatically determines size option
     */
    public static TensorImage getBytebufferFromBitmapAuto(Bitmap bitmap,
                                                         int input_width,
                                                         int input_height, 
                                                         float mean, 
                                                         float std) throws Exception {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalArgumentException("Invalid bitmap provided");
        }
        
        // Automatically determine size option based on bitmap vs input dimensions
        String size_option;
        if (bitmap.getWidth() > input_width || bitmap.getHeight() > input_height) {
            size_option = "downsize";
        } else {
            size_option = "upsize";
        }
        
        return getBytebufferFromBitmap(bitmap, input_width, input_height, mean, std, size_option);
    }

    /**
     * Get current configuration info
     */
    public String getConfigInfo() {
        return String.format("Config: %dx%d, mean=%.2f, std=%.2f", 
                previousWidth, previousHeight, previousMean, previousStd);
    }

    /**
     * Validate tensor image output
     */
    public static boolean isValidTensorImage(TensorImage tensorImage) {
        if (tensorImage == null) return false;
        if (tensorImage.getBuffer() == null) return false;
        if (tensorImage.getTensorBuffer() == null) return false;
        
        try {
            int[] shape = tensorImage.getTensorBuffer().getShape();
            if (shape == null) return false;
            
            // Soportar tanto tensors 3D como 4D
            if (shape.length == 3) {
                return shape[0] > 0 && shape[1] > 0 && shape[2] > 0;
            } else if (shape.length == 4) {
                return shape[0] > 0 && shape[1] > 0 && shape[2] > 0 && shape[3] > 0;
            }
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Error validating tensor image", e);
            return false;
        }
    }

    /**
     * Create normalized tensor image with standard YOLO preprocessing
     */
    public static TensorImage createYoloTensorImage(Bitmap bitmap, int modelWidth, int modelHeight) throws Exception {
        // YOLO models typically use 0-255 range with normalization to 0-1
        return getBytebufferFromBitmapAuto(bitmap, modelWidth, modelHeight, 0.0f, 255.0f);
    }
}