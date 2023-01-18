// ignore_for_file: no_default_cases

import 'dart:io';
import 'dart:ui';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:google_ml_kit/google_ml_kit.dart';

class ObjectDetectorPainter extends CustomPainter {
  ObjectDetectorPainter(
    this._objects,
    this.absoluteSize,
    this.rotation,
  );

  final List<DetectedObject> _objects;
  final Size absoluteSize;
  final InputImageRotation rotation;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.0
      ..color = Colors.lightGreenAccent;

    final background = Paint()..color = Colors.black;

    for (final detectedObject in _objects) {
      final builder = ParagraphBuilder(
        ParagraphStyle(
          textAlign: TextAlign.left,
          fontSize: 16,
          textDirection: TextDirection.ltr,
        ),
      )..pushStyle(
          ui.TextStyle(
            color: Colors.lightGreenAccent,
            background: background,
          ),
        );

      for (final label in detectedObject.labels) {
        builder.addText('${label.text} ${label.confidence}\n');
      }

      builder.pop();

      final left = _translateX(
        detectedObject.boundingBox.left,
        rotation,
        size,
        absoluteSize,
      );
      final top = _translateY(
        detectedObject.boundingBox.top,
        rotation,
        size,
        absoluteSize,
      );
      final right = _translateX(
        detectedObject.boundingBox.right,
        rotation,
        size,
        absoluteSize,
      );
      final bottom = _translateY(
        detectedObject.boundingBox.bottom,
        rotation,
        size,
        absoluteSize,
      );

      canvas
        ..drawRect(
          Rect.fromLTRB(left, top, right, bottom),
          paint,
        )
        ..drawParagraph(
          builder.build()
            ..layout(
              ParagraphConstraints(
                width: right - left,
              ),
            ),
          Offset(left, top),
        );
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;

  double _translateX(
    double x,
    InputImageRotation rotation,
    Size size,
    Size absoluteSize,
  ) {
    switch (rotation) {
      case InputImageRotation.rotation90deg:
        return x *
            size.width /
            (Platform.isIOS ? absoluteSize.width : absoluteSize.height);

      case InputImageRotation.rotation270deg:
        return size.width -
            x *
                size.width /
                (Platform.isIOS ? absoluteSize.width : absoluteSize.height);

      default:
        return x * size.width / absoluteSize.width;
    }
  }

  double _translateY(
    double y,
    InputImageRotation rotation,
    Size size,
    Size absoluteSize,
  ) {
    switch (rotation) {
      case InputImageRotation.rotation90deg:
      case InputImageRotation.rotation270deg:
        return y *
            size.height /
            (Platform.isIOS ? absoluteSize.height : absoluteSize.width);

      default:
        return y * size.height / absoluteSize.height;
    }
  }
}
