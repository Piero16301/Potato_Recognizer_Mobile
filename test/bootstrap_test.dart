import 'package:bloc/bloc.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:potato_recognizer_mobile/bootstrap.dart';

void main() {
  group('AppBlocObserver', () {
    test('can be instantiated', () {
      expect(const AppBlocObserver(), isNotNull);
    });

    test('onChange logs bloc changes', () {
      // Arrange
      const observer = AppBlocObserver();
      final bloc = MockBloc();
      const change = Change<String>(currentState: 'current', nextState: 'next');

      // Act & Assert - should not throw
      expect(
        () => observer.onChange(bloc, change),
        returnsNormally,
      );
    });

    test('onError logs bloc errors', () {
      // Arrange
      const observer = AppBlocObserver();
      final bloc = MockBloc();
      final error = Exception('test error');
      final stackTrace = StackTrace.current;

      // Act & Assert - should not throw
      expect(
        () => observer.onError(bloc, error, stackTrace),
        returnsNormally,
      );
    });
  });

  group('bootstrap', () {
    testWidgets('runs app successfully', (tester) async {
      // Arrange
      const testWidget = MaterialApp(
        home: Scaffold(
          body: Text('Test App'),
        ),
      );

      // Act & Assert - should complete without error
      await expectLater(
        bootstrap(() => testWidget),
        completes,
      );
    });
  });
}

// Mock class for testing
class MockBloc extends BlocBase<String> {
  MockBloc() : super('initial');
}
