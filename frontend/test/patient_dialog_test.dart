import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:patient_queue_flutter/src/api.dart';
import 'package:patient_queue_flutter/src/screens.dart';
class PatientApi extends ApiClient {
  final patients=<Map<String,dynamic>>[];
  PatientApi(){profile={'role':'ADMIN'};}
  @override Future<List<Map<String,dynamic>>> list(String path,{Map<String,String>? query})async=>patients;
  @override Future<dynamic> send(String method,String path,{Object? body,Map<String,String>? query})async{
    final row=Map<String,dynamic>.from(body as Map);row['id']=1;patients.add(row);return row;
  }
}
void main(){
  testWidgets('registration dialog saves and unmounts without disposing controllers early',(tester)async{
    final api=PatientApi();
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:PatientsScreen(api:api))));await tester.pumpAndSettle();
    await tester.tap(find.text('Register patient'));await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField).at(0),'Synthetic Patient');
    await tester.enterText(find.byType(TextFormField).at(1),'29');
    await tester.enterText(find.byType(TextFormField).at(2),'0000000000');
    await tester.tap(find.text('Save'));await tester.pumpAndSettle();
    expect(tester.takeException(),isNull);expect(find.text('Synthetic Patient'),findsOneWidget);
    await tester.tap(find.text('Register patient'));await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));await tester.pumpAndSettle();expect(tester.takeException(),isNull);
    api.close();
  });
}
