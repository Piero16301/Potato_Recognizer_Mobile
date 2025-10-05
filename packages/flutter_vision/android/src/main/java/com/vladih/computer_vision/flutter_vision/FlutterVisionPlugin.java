package com.vladih.computer_vision.flutter_vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;

import com.vladih.computer_vision.flutter_vision.models.Yolo;
import com.vladih.computer_vision.flutter_vision.models.Yolo11;
import com.vladih.computer_vision.flutter_vision.models.Yolov8;
import com.vladih.computer_vision.flutter_vision.models.Yolov5;
import com.vladih.computer_vision.flutter_vision.models.Yolov8Seg;
import com.vladih.computer_vision.flutter_vision.utils.utils;

import org.opencv.android.OpenCVLoader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * FlutterVisionPlugin - Enhanced with YOLO11 support and improved error handling
 */
public class FlutterVisionPlugin implements FlutterPlugin, MethodCallHandler {
    private static final String TAG = "FlutterVisionPlugin";
    private static final String CHANNEL_NAME = "flutter_vision";
    private static final String SUPPORTED_VERSIONS = "yolov5, yolov8, yolov8seg, yolo11";
    
    private MethodChannel methodChannel;
    private Context context;
    private FlutterAssets assets;
    private Yolo yolo_model;
    private ExecutorService executor;
    
    private final AtomicBoolean isDetecting = new AtomicBoolean(false);
    private static final ArrayList<Map<String, Object>> EMPTY_RESULT = new ArrayList<>();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        Log.d(TAG, "FlutterVisionPlugin attached to engine");
        setupChannel(binding.getApplicationContext(), binding.getFlutterAssets(), binding.getBinaryMessenger());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        Log.d(TAG, "FlutterVisionPlugin detached from engine");
        cleanup();
    }

    private void setupChannel(Context context, FlutterAssets assets, BinaryMessenger messenger) {
        try {
            // Initialize OpenCV
            if (!OpenCVLoader.initDebug()) {
                Log.w(TAG, "OpenCV initialization failed");
            } else {
                Log.d(TAG, "OpenCV initialized successfully");
            }
            
            this.assets = assets;
            this.context = context;
            this.methodChannel = new MethodChannel(messenger, CHANNEL_NAME);
            this.methodChannel.setMethodCallHandler(this);
            this.executor = Executors.newSingleThreadExecutor();
            
            Log.d(TAG, "Plugin setup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up plugin", e);
        }
    }

    private void cleanup() {
        try {
            this.context = null;
            
            if (this.methodChannel != null) {
                this.methodChannel.setMethodCallHandler(null);
                this.methodChannel = null;
            }
            
            this.assets = null;
            close_yolo();
            
            if (this.executor != null && !this.executor.isShutdown()) {
                this.executor.shutdownNow();
                this.executor = null;
            }
            
            Log.d(TAG, "Plugin cleanup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
            
            // Ensure executor is shutdown even if there's an error
            if (this.executor != null && !this.executor.isShutdown()) {
                this.executor.shutdownNow();
            }
        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            switch (call.method) {
                case "loadYoloModel":
                    loadYoloModel((Map<String, Object>) call.arguments, result);
                    break;
                case "yoloOnFrame":
                    yoloOnFrame((Map<String, Object>) call.arguments, result);
                    break;
                case "yoloOnImage":
                    yoloOnImage((Map<String, Object>) call.arguments, result);
                    break;
                case "closeYoloModel":
                    closeYoloModel(result);
                    break;
                case "getSupportedVersions":
                    result.success(SUPPORTED_VERSIONS);
                    break;
                case "getModelInfo":
                    getModelInfo(result);
                    break;
                default:
                    result.notImplemented();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling method call: " + call.method, e);
            result.error("PLUGIN_ERROR", "Method call failed: " + e.getMessage(), e);
        }
    }

    private void loadYoloModel(Map<String, Object> args, Result result) {
        try {
            if (args == null) {
                result.error("INVALID_ARGS", "Arguments cannot be null", null);
                return;
            }
            
            // Extract and validate parameters
            String modelPath = getStringArgument(args, "modelPath");
            String labelPath = getStringArgument(args, "labels");
            String version = getStringArgument(args, "modelVersion");
            
            final boolean isAsset = getBooleanArgument(args, "isAsset", false);
            final int numThreads = getIntArgument(args, "numThreads", 4);
            final boolean quantization = getBooleanArgument(args, "quantization", false);
            final boolean useGpu = getBooleanArgument(args, "useGpu", false);
            final int rotation = getIntArgument(args, "rotation", 0);
            
            // Resolve paths
            String resolvedModelPath = isAsset ? this.assets.getAssetFilePathByName(modelPath) : modelPath;
            String resolvedLabelPath = isAsset ? this.assets.getAssetFilePathByName(labelPath) : labelPath;
            
            Log.d(TAG, String.format("Loading YOLO model: version=%s, threads=%d, gpu=%b, quantized=%b", 
                    version, numThreads, useGpu, quantization));
            
            // Create appropriate model instance
            yolo_model = createYoloModel(version, context, resolvedModelPath, isAsset, 
                    numThreads, quantization, useGpu, resolvedLabelPath, rotation);
            
            // Initialize the model
            yolo_model.initialize_model();
            
            Log.d(TAG, "YOLO model loaded successfully: " + version);
            result.success("Model loaded successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading YOLO model", e);
            result.error("MODEL_LOAD_ERROR", "Failed to load YOLO model: " + e.getMessage(), e);
        }
    }

    private Yolo createYoloModel(String version, Context context, String modelPath, boolean isAsset,
                                int numThreads, boolean quantization, boolean useGpu, 
                                String labelPath, int rotation) throws Exception {
        switch (version.toLowerCase()) {
            case "yolov5":
                return new Yolov5(context, modelPath, isAsset, numThreads, quantization, useGpu, labelPath, rotation);
                
            case "yolov8":
                return new Yolov8(context, modelPath, isAsset, numThreads, quantization, useGpu, labelPath, rotation);
                
            case "yolov8seg":
                return new Yolov8Seg(context, modelPath, isAsset, numThreads, quantization, useGpu, labelPath, rotation);
                
            case "yolo11":
            case "yolov11":
                return new Yolo11(context, modelPath, isAsset, numThreads, quantization, useGpu, labelPath, rotation);
                
            default:
                throw new IllegalArgumentException("Unsupported model version: " + version + 
                        ". Supported versions: " + SUPPORTED_VERSIONS);
        }
    }

    private void yoloOnFrame(Map<String, Object> args, Result result) {
        if (yolo_model == null) {
            result.error("MODEL_NOT_LOADED", "YOLO model not loaded", null);
            return;
        }
        
        if (isDetecting.compareAndSet(false, true)) {
            DetectionTask detectionTask = new DetectionTask(yolo_model, args, "frame", result, isDetecting);
            executor.submit(detectionTask);
        } else {
            // Return empty result if already detecting
            result.success(EMPTY_RESULT);
        }
    }

    private void yoloOnImage(Map<String, Object> args, Result result) {
        if (yolo_model == null) {
            result.error("MODEL_NOT_LOADED", "YOLO model not loaded", null);
            return;
        }
        
        if (isDetecting.compareAndSet(false, true)) {
            DetectionTask detectionTask = new DetectionTask(yolo_model, args, "img", result, isDetecting);
            executor.submit(detectionTask);
        } else {
            // Return empty result if already detecting
            result.success(EMPTY_RESULT);
        }
    }

    private void closeYoloModel(Result result) {
        try {
            close_yolo();
            Log.d(TAG, "YOLO model closed successfully");
            result.success("YOLO model closed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error closing YOLO model", e);
            result.error("MODEL_CLOSE_ERROR", "Failed to close YOLO model: " + e.getMessage(), e);
        }
    }

    private void getModelInfo(Result result) {
        try {
            if (yolo_model == null) {
                result.success("No model loaded");
            } else {
                String info = String.format("Model loaded, rotation: %d", yolo_model.getRotation());
                result.success(info);
            }
        } catch (Exception e) {
            result.error("INFO_ERROR", "Error getting model info: " + e.getMessage(), e);
        }
    }

    private void close_yolo() {
        if (yolo_model != null) {
            try {
                yolo_model.close();
                Log.d(TAG, "YOLO model closed");
            } catch (Exception e) {
                Log.e(TAG, "Error closing YOLO model", e);
            } finally {
                yolo_model = null;
            }
        }
    }

    // Utility methods for argument extraction
    private String getStringArgument(Map<String, Object> args, String key) throws IllegalArgumentException {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value.toString();
    }

    private boolean getBooleanArgument(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        return value != null ? (Boolean) value : defaultValue;
    }

    private int getIntArgument(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        return value != null ? (Integer) value : defaultValue;
    }

    /**
     * Enhanced DetectionTask with better error handling and memory management
     */
    private static class DetectionTask implements Runnable {
        private final Yolo yolo;
        private final byte[] image;
        private final List<byte[]> frame;
        private final int imageHeight;
        private final int imageWidth;
        private final float iouThreshold;
        private final float confThreshold;
        private final float classThreshold;
        private final String type;
        private final Result result;
        private final AtomicBoolean isDetecting;

        public DetectionTask(Yolo yolo, Map<String, Object> args, String type, 
                           Result result, AtomicBoolean isDetecting) {
            this.yolo = yolo;
            this.type = type;
            this.result = result;
            this.isDetecting = isDetecting;
            
            if ("img".equals(type)) {
                this.image = (byte[]) args.get("bytesList");
                this.frame = null;
            } else {
                this.frame = (ArrayList<byte[]>) args.get("bytesList");
                this.image = null;
            }
            
            this.imageHeight = getIntArgument(args, "imageHeight", 0);
            this.imageWidth = getIntArgument(args, "imageWidth", 0);
            this.iouThreshold = getFloatArgument(args, "iouThreshold", 0.5f);
            this.confThreshold = getFloatArgument(args, "confThreshold", 0.5f);
            this.classThreshold = getFloatArgument(args, "classThreshold", 0.5f);
        }
        
        @Override
        public void run() {
            Bitmap bitmap = null;
            ByteBuffer byteBuffer = null;
            
            try {
                // Create bitmap from input
                if ("img".equals(type)) {
                    bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                } else {
                    bitmap = utils.feedInputToBitmap(yolo.getContext(), frame, imageHeight, imageWidth, yolo.getRotation());
                }
                
                if (bitmap == null) {
                    throw new Exception("Failed to create bitmap from input");
                }
                
                // Prepare input tensor
                // int[] shape = yolo.getInputTensor().shape();
                // int srcWidth = bitmap.getWidth();
                // int srcHeight = bitmap.getHeight();
                
                // byteBuffer = utils.feedInputTensor(bitmap, shape[1], shape[2], srcWidth, srcHeight, 0, 255);

                // Prepare input tensor with shape validation
                int[] shape = yolo.getInputTensor().shape();
                int srcWidth = bitmap.getWidth();
                int srcHeight = bitmap.getHeight();

                int inputHeight, inputWidth;
                if (shape.length == 3) {
                    // Format: [height, width, channels] 
                    inputHeight = shape[0];
                    inputWidth = shape[1];
                } else if (shape.length == 4) {
                    // Format: [batch, height, width, channels]
                    inputHeight = shape[1];
                    inputWidth = shape[2];
                } else {
                    throw new Exception("Unsupported tensor shape length: " + shape.length);
                }

                byteBuffer = utils.feedInputTensor(bitmap, inputWidth, inputHeight, srcWidth, srcHeight, 0, 255);
                
                // Run detection
                List<Map<String, Object>> detections = yolo.detect_task(byteBuffer, srcHeight, srcWidth, 
                        iouThreshold, confThreshold, classThreshold);
                
                Log.d(TAG, String.format("Detection completed: %d objects found", detections.size()));
                result.success(detections);
                
            } catch (Exception e) {
                Log.e(TAG, "Detection task failed", e);
                result.error("DETECTION_ERROR", "Detection failed: " + e.getMessage(), e);
            } finally {
                // Clean up resources
                utils.safeRecycleBitmap(bitmap);
                if (byteBuffer != null) {
                    byteBuffer.clear();
                }
                isDetecting.set(false);
            }
        }
        
        private int getIntArgument(Map<String, Object> args, String key, int defaultValue) {
            Object value = args.get(key);
            return value != null ? (Integer) value : defaultValue;
        }
        
        private float getFloatArgument(Map<String, Object> args, String key, float defaultValue) {
            Object value = args.get(key);
            if (value instanceof Double) {
                return ((Double) value).floatValue();
            } else if (value instanceof Float) {
                return (Float) value;
            }
            return defaultValue;
        }
    }
}