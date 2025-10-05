package com.vladih.computer_vision.flutter_vision.models;

import android.content.Context;
import android.util.Log;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * YOLO11 (YOLOv11) implementation
 * Similar to YOLOv8 but with improved architecture and optimizations
 * Output format: [batch, features, detections] where features = [x, y, w, h, class_scores...]
 */
public class Yolo11 extends Yolo {
    private static final String TAG = "Yolo11";
    
    public Yolo11(Context context,
                  String model_path,
                  boolean is_assets,
                  int num_threads,
                  boolean quantization,
                  boolean use_gpu,
                  String label_path,
                  int rotation) {
        super(context, model_path, is_assets, num_threads, quantization, use_gpu, label_path, rotation);
    }

    @Override
    public List<Map<String, Object>> detect_task(ByteBuffer byteBuffer,
                                                 int source_height,
                                                 int source_width,
                                                 float iou_threshold,
                                                 float conf_threshold,
                                                 float class_threshold) throws Exception {
        if (interpreter == null) {
            throw new Exception("YOLO11 interpreter not initialized");
        }

        try {
            int[] input_shape = this.interpreter.getInputTensor(0).shape();
            this.interpreter.run(byteBuffer, this.output);
            
            // YOLO11 uses the same output format as YOLOv8
            List<float[]> boxes = filter_box_v11(this.output, iou_threshold, conf_threshold,
                    class_threshold, input_shape[1], input_shape[2]);
            boxes = restore_size(boxes, input_shape[1], input_shape[2], source_width, source_height);
            return out(boxes, this.labels);
        } catch (Exception e) {
            Log.e(TAG, "YOLO11 detection failed", e);
            throw new Exception("YOLO11 detection failed: " + e.getMessage());
        } finally {
            byteBuffer.clear();
        }
    }

    /**
     * YOLO11 specific box filtering with optimized processing
     * Format: [x, y, w, h, class1, class2, ...] (no objectness score)
     */
    protected List<float[]> filter_box_v11(float[][][] model_outputs, float iou_threshold,
                                           float conf_threshold, float class_threshold, 
                                           float input_width, float input_height) {
        try {
            List<float[]> pre_box = new ArrayList<>();
            int class_start_index = 4;
            int total_features = model_outputs[0].length;
            int total_detections = model_outputs[0][0].length;
            
            // Process each detection
            for (int i = 0; i < total_detections; i++) {
                // Extract box coordinates (normalized)
                float center_x = model_outputs[0][0][i];
                float center_y = model_outputs[0][1][i];
                float width = model_outputs[0][2][i];
                float height = model_outputs[0][3][i];
                
                // Convert to pixel coordinates and xyxy format
                float x1 = (center_x - width / 2f) * input_width;
                float y1 = (center_y - height / 2f) * input_height;
                float x2 = (center_x + width / 2f) * input_width;
                float y2 = (center_y + height / 2f) * input_height;
                
                // Find best class and confidence
                int best_class_index = class_start_index;
                float best_confidence = model_outputs[0][best_class_index][i];
                
                for (int j = class_start_index + 1; j < total_features; j++) {
                    float current_confidence = model_outputs[0][j][i];
                    if (current_confidence > best_confidence) {
                        best_confidence = current_confidence;
                        best_class_index = j;
                    }
                }
                
                // Apply confidence threshold
                if (best_confidence > class_threshold) {
                    float[] detection = new float[6];
                    detection[0] = Math.max(0, x1);
                    detection[1] = Math.max(0, y1);
                    detection[2] = x2;
                    detection[3] = y2;
                    detection[4] = best_confidence;
                    detection[5] = (best_class_index - class_start_index) * 1f;
                    pre_box.add(detection);
                }
            }
            
            if (pre_box.isEmpty()) {
                Log.d(TAG, "No detections found above threshold");
                return new ArrayList<>();
            }
            
            Log.d(TAG, String.format("Found %d detections before NMS", pre_box.size()));
            
            // Sort by confidence (descending order)
            Comparator<float[]> confidenceComparator = (detection1, detection2) -> 
                Float.compare(detection2[4], detection1[4]);
            Collections.sort(pre_box, confidenceComparator);
            
            // Apply Non-Maximum Suppression
            List<float[]> final_detections = nms_optimized(pre_box, iou_threshold);
            Log.d(TAG, String.format("Final detections after NMS: %d", final_detections.size()));
            
            return final_detections;
        } catch (Exception e) {
            Log.e(TAG, "YOLO11 box filtering failed", e);
            throw new RuntimeException("YOLO11 box filtering failed: " + e.getMessage());
        }
    }

    /**
     * Optimized NMS implementation for YOLO11
     */
    private static List<float[]> nms_optimized(List<float[]> boxes, float iou_threshold) {
        if (boxes.isEmpty()) return new ArrayList<>();
        
        List<float[]> result = new ArrayList<>();
        List<float[]> remaining = new ArrayList<>(boxes);
        
        while (!remaining.isEmpty()) {
            // Take the box with highest confidence
            float[] best_box = remaining.remove(0);
            result.add(best_box);
            
            // Remove boxes with high IoU with the best box
            remaining.removeIf(box -> calculateIoU(best_box, box) > iou_threshold);
        }
        
        return result;
    }

    /**
     * Calculate Intersection over Union (IoU) between two boxes
     */
    private static float calculateIoU(float[] box1, float[] box2) {
        float x1_inter = Math.max(box1[0], box2[0]);
        float y1_inter = Math.max(box1[1], box2[1]);
        float x2_inter = Math.min(box1[2], box2[2]);
        float y2_inter = Math.min(box1[3], box2[3]);
        
        // Check if boxes intersect
        if (x1_inter >= x2_inter || y1_inter >= y2_inter) {
            return 0.0f;
        }
        
        float intersection_area = (x2_inter - x1_inter) * (y2_inter - y1_inter);
        float box1_area = (box1[2] - box1[0]) * (box1[3] - box1[1]);
        float box2_area = (box2[2] - box2[0]) * (box2[3] - box2[1]);
        float union_area = box1_area + box2_area - intersection_area;
        
        return union_area > 0 ? intersection_area / union_area : 0.0f;
    }

    @Override
    protected List<Map<String, Object>> out(List<float[]> yolo_result, Vector<String> labels) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (float[] box : yolo_result) {
                Map<String, Object> output = new HashMap<>();
                output.put("box", new float[]{box[0], box[1], box[2], box[3], box[4]});
                
                // Ensure label index is within bounds
                int labelIndex = (int) box[5];
                if (labelIndex >= 0 && labelIndex < labels.size()) {
                    output.put("tag", labels.get(labelIndex));
                } else {
                    Log.w(TAG, String.format("Invalid label index: %d, using 'unknown'", labelIndex));
                    output.put("tag", "unknown");
                }
                result.add(output);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "YOLO11 output formatting failed", e);
            throw new RuntimeException("YOLO11 output formatting failed: " + e.getMessage());
        }
    }
}