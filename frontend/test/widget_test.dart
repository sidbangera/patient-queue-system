import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:patient_queue_flutter/main.dart';

void main() {
  testWidgets('Displays the CareFlow login screen', (tester) async {
    await tester.pumpWidget(const PatientQueueApp());

    expect(find.text('PQueue'), findsOneWidget);
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.byType(TextField), findsNWidgets(2));
  });
}