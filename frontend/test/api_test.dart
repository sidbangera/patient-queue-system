import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:patient_queue_flutter/src/api.dart';

void main() {
  test('login reads token then fetches the actual profile', () async {
    final api = ApiClient(client: MockClient((request) async {
      if (request.url.path.endsWith('/login')) return http.Response('{"token":"test-token"}',200);
      expect(request.headers['Authorization'],'Bearer test-token');
      return http.Response('{"username":"clinic_admin","role":"ADMIN"}',200);
    }));
    await api.login('clinic_admin','not-a-real-password');
    expect(api.role,'ADMIN'); expect(api.username,'clinic_admin'); api.close();
  });
  test('401 clears the local session and notifies the app', () async {
    var notified=false;
    final api=ApiClient(client:MockClient((_)async=>http.Response('{"error":"Expired"}',401)),onUnauthorized:()=>notified=true);
    api.token='expired';
    await expectLater(api.send('GET','/api/patients'),throwsA(isA<ApiException>()));
    expect(api.token,isNull);expect(notified,isTrue);api.close();
  });
  test('403 preserves a valid session but reports the error', () async {
    final api=ApiClient(client:MockClient((_)async=>http.Response('{"error":"Insufficient permissions"}',403)));
    api.token='valid'; await expectLater(api.send('GET','/api/analytics/summary'),throwsA(isA<ApiException>()));
    expect(api.token,'valid');api.close();
  });
  test('CSV payload is returned unchanged and sent with CSV Accept header', () async {
    final api=ApiClient(client:MockClient((r)async{
      expect(r.headers['Accept'],'text/csv'); return http.Response('date,count\r\n2026-10-31,2\r\n',200);
    }));
    expect(await api.send('GET','/api/analytics/export.csv'),'date,count\r\n2026-10-31,2\r\n');api.close();
  });
  test('login without a token is rejected', () async {
    final api=ApiClient(client:MockClient((_)async=>http.Response('{}',200)));
    await expectLater(api.login('name','password'),throwsA(isA<ApiException>()));api.close();
  });
}
