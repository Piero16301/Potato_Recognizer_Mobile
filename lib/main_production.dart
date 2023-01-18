import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:potato_recognizer/app/app.dart';
import 'package:potato_recognizer/bootstrap.dart';

List<CameraDescription> cameras = [];

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  cameras = await availableCameras();

  await bootstrap(() => const App());
}
