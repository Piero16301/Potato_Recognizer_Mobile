import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import '../helpers/helpers.dart';

void main() {
  group('PumpApp extension', () {
    testWidgets('creates MaterialApp with localizations', (tester) async {
      const testWidget = Text('Test Widget');

      await tester.pumpApp(testWidget);

      expect(find.byType(MaterialApp), findsOneWidget);
      expect(find.text('Test Widget'), findsOneWidget);

      final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(materialApp.localizationsDelegates, isNotNull);
      expect(materialApp.supportedLocales, isNotNull);
    });

    testWidgets('properly sets up localizations', (tester) async {
      const testWidget = Scaffold(
        body: Text('Localized Test'),
      );

      await tester.pumpApp(testWidget);

      final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));
      expect(materialApp.localizationsDelegates, isNotEmpty);
      expect(materialApp.supportedLocales, isNotEmpty);
    });
  });
}
