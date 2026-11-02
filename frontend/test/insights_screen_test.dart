import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:patient_queue_flutter/src/api.dart';
import 'package:patient_queue_flutter/src/insights_screen.dart';

class FakeAnalyticsApi extends ApiClient {
  @override
  Future<dynamic> send(String method, String path,
      {Object? body, Map<String, String>? query}) async {
    if (path == '/api/analytics/dashboard') {
      return <String,dynamic>{
        'from': '2026-10-25', 'to': '2026-10-31', 'clinicToday': '2026-10-31',
        'clinicZone': 'Asia/Kolkata', 'asOf': '2026-10-31T10:00:00Z',
        'summary': await send('GET', '/api/analytics/summary'),
        'comparison': await send('GET', '/api/analytics/comparison'),
        'quality': await send('GET', '/api/analytics/quality'),
        'daily': await list('/api/analytics/daily'), 'doctors': await list('/api/analytics/doctors'),
        'hourly': await list('/api/analytics/hourly'),
      };
    }
    if (path == '/api/analytics/summary') {
      return <String, dynamic>{
        'total': 2,
        'completed': 1,
        'noShows': 0,
        'waiting': 1,
        'inConsultation': 0,
        'completionRatePct': 50.0,
        'noShowRatePct': 0.0,
        'waitSampleSize': 1,
        'avgWaitMinutes': 12.4,
        'p90WaitMinutes': 12.4,
        'avgConsultationMinutes': 10.0,
      };
    }
    if (path == '/api/analytics/comparison') {
      return <String, dynamic>{
        'currentFrom': '2026-10-20', 'currentTo': '2026-10-26',
        'previousFrom': '2026-10-13', 'previousTo': '2026-10-19',
        'current': {'total': 2, 'completed': 1, 'completionRatePct': 50.0},
        'previous': {'total': 1, 'completed': 1, 'completionRatePct': 100.0},
        'checkinsDelta': 1, 'completedDelta': 0,
        'checkinsChangePct': 100.0, 'completionRateDeltaPoints': -50.0,
      };
    }
    if (path == '/api/analytics/quality') {
      return <String, dynamic>{
        'recordsChecked': 2, 'flaggedVisits': 0,
        'serviceDateMismatch': 0, 'negativeWait': 0,
        'negativeConsultation': 0, 'completedMissingTimestamps': 0,
      };
    }
    if (path == '/api/analytics/export.csv') {
      return 'service_date,doctor_id,check_ins\r\n2026-10-25,1,2\r\n';
    }
    throw ApiException('Unexpected test endpoint: $path');
  }

  @override
  Future<List<Map<String, dynamic>>> list(String path,
      {Map<String, String>? query}) async {
    if (path == '/api/doctors') {
      return <Map<String, dynamic>>[
        {'id': 1, 'name': 'Dr. Test', 'department': 'Diagnostics'},
      ];
    }
    if (path == '/api/analytics/daily') {
      return <Map<String, dynamic>>[
        {'date': '2026-10-25', 'checkins': 2, 'completed': 1, 'noShows': 0},
      ];
    }
    if (path == '/api/analytics/doctors') {
      return <Map<String, dynamic>>[
        {
          'doctorId': 1, 'doctorName': 'Dr. Test', 'department': 'Diagnostics',
          'checkins': 2, 'completed': 1, 'noShows': 0, 'waiting': 1,
          'avgWaitMinutes': 12.4,
        },
      ];
    }
    if (path == '/api/analytics/hourly') {
      return <Map<String, dynamic>>[
        for (var hour = 0; hour < 24; hour++)
          {'hour': hour, 'checkins': hour == 14 ? 2 : 0},
      ];
    }
    throw ApiException('Unexpected test endpoint: $path');
  }
}

void main() {
  testWidgets('Insights uses actual aggregate API response values', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: InsightsScreen(api: FakeAnalyticsApi())),
    ));
    await tester.pumpAndSettle();

    expect(find.text('Operations overview'), findsOneWidget);
    expect(find.text('Check-ins'), findsWidgets);
    expect(find.text('50.0%'), findsOneWidget);
    expect(find.text('12.4 min'), findsWidgets);
    await tester.scrollUntilVisible(find.text('Previous-period comparison'), 400, scrollable: find.byType(Scrollable).first);
    expect(find.text('Previous-period comparison'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Data quality checks'), 400, scrollable: find.byType(Scrollable).first);
    expect(find.text('Data quality checks'), findsOneWidget);
  });
}
