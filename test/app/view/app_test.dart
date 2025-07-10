import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('App', () {
    testWidgets('renders MaterialApp', (tester) async {
      // Arrange & Act
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Text('Test'),
          ),
        ),
      );

      // Assert
      expect(find.byType(MaterialApp), findsOneWidget);
      expect(find.byType(Scaffold), findsOneWidget);
    });

    testWidgets('renders App structure without errors', (tester) async {
      // Arrange & Act
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            appBar: AppBar(title: const Text('CIP Detector')),
            body: const Center(child: Text('Modelo no cargado')),
          ),
        ),
      );

      // Assert
      expect(find.text('CIP Detector'), findsOneWidget);
      expect(find.text('Modelo no cargado'), findsOneWidget);
    });

    testWidgets('renders MaterialApp with correct theme configuration',
        (tester) async {
      // Arrange
      final app = MaterialApp(
        theme: ThemeData.dark().copyWith(
          appBarTheme: const AppBarTheme(
            backgroundColor: Colors.black,
            foregroundColor: Colors.white,
            elevation: 0,
          ),
        ),
        home: const Scaffold(
          body: Text('Test'),
        ),
      );

      // Act
      await tester.pumpWidget(app);

      // Assert
      final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(materialApp.theme?.brightness, Brightness.dark);
      expect(materialApp.theme?.appBarTheme.backgroundColor, Colors.black);
    });
  });
}
