package com.vladih.computer_vision.flutter_vision.models;

import android.content.Context;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class Yolov5 extends Yolo {
    
    public Yolov5(Context context,
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
            throw new Exception("Interpreter not initialized");
        }

        try {
            int[] input_shape = this.interpreter.getInputTensor(0).shape();
            this.interpreter.run(byteBuffer, this.output);
            
            // YOLOv5 specific processing - uses confidence threshold differently
            List<float[]> boxes = filter_box_v5(this.output, iou_threshold, conf_threshold,
                    class_threshold, input_shape[1], input_shape[2]);
            boxes = restore_size(boxes, input_shape[1], input_shape[2], source_width, source_height);
            return out(boxes, this.labels);
        } catch (Exception e) {
            throw new Exception("YOLOv5 detection failed: " + e.getMessage());
        } finally {
            byteBuffer.clear();
        }
    }

    /**
     * YOLOv5 specific box filtering
     * Format: [x, y, w, h, objectness, class1, class2, ...]
     */
    protected List<float[]> filter_box_v5(float[][][] model_outputs, float iou_threshold,
                                          float conf_threshold, float class_threshold, 
                                          float input_width, float input_height) {
        try {
            List<float[]> pre_box = new ArrayList<>();
            int conf_index = 4; // objectness score index
            int class_start_index = 5; // classes start index
            int dimension = model_outputs[0][0].length;
            int rows = model_outputs[0].length;
            
            for (int i = 0; i < rows; i++) {
                // Get objectness score
                float objectness = model_outputs[0][i][conf_index];
                if (objectness < conf_threshold) continue;
                
                // Convert xywh to xyxy format
                float x1 = (model_outputs[0][i][0] - model_outputs[0][i][2] / 2f) * input_width;
                float y1 = (model_outputs[0][i][1] - model_outputs[0][i][3] / 2f) * input_height;
                float x2 = (model_outputs[0][i][0] + model_outputs[0][i][2] / 2f) * input_width;
                float y2 = (model_outputs[0][i][1] + model_outputs[0][i][3] / 2f) * input_height;
                
                // Find best class
                int max_class_index = class_start_index;
                float max_class_score = model_outputs[0][i][max_class_index];
                
                for (int j = class_start_index + 1; j < dimension; j++) {
                    float current_class_score = model_outputs[0][i][j];
                    if (current_class_score > max_class_score) {
                        max_class_score = current_class_score;
                        max_class_index = j;
                    }
                }
                
                // Calculate final confidence (objectness * class_score)
                float final_confidence = objectness * max_class_score;
                
                if (final_confidence > class_threshold) {
                    float[] box = new float[6];
                    box[0] = x1;
                    box[1] = y1;
                    box[2] = x2;
                    box[3] = y2;
                    box[4] = final_confidence;
                    box[5] = (max_class_index - class_start_index) * 1f;
                    pre_box.add(box);
                }
            }
            
            if (pre_box.isEmpty()) return new ArrayList<>();
            
            // Sort by confidence (descending)
            Comparator<float[]> compareByConfidence = (box1, box2) -> 
                Float.compare(box2[4], box1[4]);
            Collections.sort(pre_box, compareByConfidence);
            
            return nms(pre_box, iou_threshold);
        } catch (Exception e) {
            throw new RuntimeException("YOLOv5 box filtering failed: " + e.getMessage());
        }
    }

    @Override
    protected List<Map<String, Object>> out(List<float[]> yolo_result, Vector<String> labels) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (float[] box : yolo_result) {
                Map<String, Object> output = new HashMap<>();
                output.put("box", new float[]{box[0], box[1], box[2], box[3], box[4]});
                output.put("tag", labels.get((int) box[5]));
                result.add(output);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("YOLOv5 output formatting failed: " + e.getMessage());
        }
    }
}