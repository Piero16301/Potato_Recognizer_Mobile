import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:potato_recognizer_mobile/app/app.dart';
import 'package:potato_recognizer_mobile/bootstrap.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  DartPluginRegistrant.ensureInitialized();

  await bootstrap(() => const App());
}
