import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:potato_recognizer_mobile/app/app.dart';
import 'package:potato_recognizer_mobile/bootstrap.dart';

List<CameraDescription> cameras = [];

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  cameras = await availableCameras();

  await bootstrap(() => const App());
}
