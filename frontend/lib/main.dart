import 'package:flutter/material.dart';
import 'src/api.dart';
import 'src/ui.dart';
import 'src/screens.dart';

void main() => runApp(const PatientQueueApp());

class PatientQueueApp extends StatefulWidget {
  const PatientQueueApp({super.key});
  @override State<PatientQueueApp> createState() => _PatientQueueAppState();
}

class _PatientQueueAppState extends State<PatientQueueApp> {
  late final ApiClient api;
  final navigatorKey = GlobalKey<NavigatorState>();
  @override void initState() {
    super.initState();
    api = ApiClient(onUnauthorized: () {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        navigatorKey.currentState?.popUntil((route) => route.isFirst);
        setState(() {});
      });
    });
  }
  @override void dispose() { api.close(); super.dispose(); }
  @override Widget build(BuildContext context) => MaterialApp(
    navigatorKey: navigatorKey,
    debugShowCheckedModeBanner: false, title: 'PQueue', theme: appTheme(),
    home: api.token == null
        ? LoginScreen(api:api,onLoggedIn:()=>setState((){}))
        : WorkspaceScreen(api:api,onLogout:(){api.logout();setState((){});}),
  );
}

class LoginScreen extends StatefulWidget {
  final ApiClient api;
  final VoidCallback onLoggedIn;
  const LoginScreen({super.key,required this.api,required this.onLoggedIn});
  @override State<LoginScreen> createState()=>_LoginScreenState();
}
class _LoginScreenState extends State<LoginScreen> {
  final username=TextEditingController(), password=TextEditingController();
  bool busy=false; String? error;
  @override void dispose(){username.dispose();password.dispose();super.dispose();}
  Future<void> login() async {
    if (busy) return;
    setState(() {busy=true;error=null;});
    try { await widget.api.login(username.text.trim(),password.text);if(mounted) widget.onLoggedIn(); }
    catch(e){if(mounted)setState(()=>error=e.toString());}
    finally{if(mounted)setState(()=>busy=false);}
  }
  @override Widget build(BuildContext context)=>Scaffold(
    body:SafeArea(child:Center(child:ConstrainedBox(
      constraints:const BoxConstraints(maxWidth:440),
      child:SingleChildScrollView(padding:const EdgeInsets.all(24),child:Card(
        child:Padding(padding:const EdgeInsets.all(28),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
          const Icon(Icons.medical_services_outlined,color:teal,size:52),
          const SizedBox(height:14),
          const Text('PQueue',textAlign:TextAlign.center,style:TextStyle(fontSize:29,fontWeight:FontWeight.w800,color:ink)),
          const SizedBox(height:5),
          const Text('Doctor-first outpatient queue management',textAlign:TextAlign.center,style:TextStyle(color:muted)),
          const SizedBox(height:28),
          TextField(controller:username,decoration:const InputDecoration(labelText:'Staff username',prefixIcon:Icon(Icons.person_outline)),textInputAction:TextInputAction.next),
          const SizedBox(height:14),
          TextField(controller:password,obscureText:true,onSubmitted:(_)=>login(),decoration:const InputDecoration(labelText:'Password',prefixIcon:Icon(Icons.lock_outline))),
          if(error!=null)Padding(padding:const EdgeInsets.only(top:10),child:Text(error!,style:const TextStyle(color:Colors.red))),
          const SizedBox(height:20),
          FilledButton.icon(onPressed:busy?null:login,icon:const Icon(Icons.login),label:Text(busy?'Signing in...':'Sign in')),
          const SizedBox(height:20),
          const Text('Demo use only · synthetic patient data',textAlign:TextAlign.center,style:TextStyle(color:muted,fontSize:12)),
        ])),
      )),
    ))),
  );
}

class WorkspaceScreen extends StatefulWidget {
  final ApiClient api; final VoidCallback onLogout;
  const WorkspaceScreen({super.key,required this.api,required this.onLogout});
  @override State<WorkspaceScreen> createState()=>_WorkspaceScreenState();
}
class _WorkspaceScreenState extends State<WorkspaceScreen> {
  int selected=0;
  List<_WorkspaceTab> get tabs => [
    const _WorkspaceTab('Queues',Icons.view_list_rounded),
    const _WorkspaceTab('Patients',Icons.people_alt_outlined),
    if(['RECEPTIONIST','ADMIN'].contains(widget.api.role)) const _WorkspaceTab('Check in',Icons.add_circle_outline),
    if(widget.api.role=='ADMIN') const _WorkspaceTab('Insights',Icons.bar_chart_rounded),
  ];
  Widget page(String title) {
    switch(title){
      case 'Queues':return QueueScreen(api:widget.api);
      case 'Patients':return PatientsScreen(api:widget.api);
      case 'Check in':return CheckinScreen(api:widget.api);
      case 'Insights':return InsightsScreen(api:widget.api);
    }
    return const SizedBox.shrink();
  }
  @override Widget build(BuildContext context){
    final menu=tabs;
    if(selected>=menu.length)selected=0;
    final wide=MediaQuery.sizeOf(context).width>=800;
    final content=page(menu[selected].title);
    return Scaffold(
      appBar:AppBar(title:const Text('PQueue',style:TextStyle(fontWeight:FontWeight.w800,color:ink)),backgroundColor:Colors.white,
        actions:[Center(child:Text('${widget.api.username} · ${widget.api.role}',style:const TextStyle(color:muted,fontSize:12))),
          IconButton(tooltip:'Log out',onPressed:widget.onLogout,icon:const Icon(Icons.logout_rounded))]),
      body:Row(children:[
        if(wide)NavigationRail(
          extended:MediaQuery.sizeOf(context).width>=1100,
          backgroundColor:Colors.white,
          selectedIndex:selected,onDestinationSelected:(i)=>setState(()=>selected=i),
          destinations:[for(final tab in menu)NavigationRailDestination(icon:Icon(tab.icon),label:Text(tab.title))]),
        Expanded(child:content),
      ]),
      bottomNavigationBar:wide?null:NavigationBar(
        selectedIndex:selected,onDestinationSelected:(i)=>setState(()=>selected=i),
        destinations:[for(final tab in menu)NavigationDestination(icon:Icon(tab.icon),label:tab.title)]),
    );
  }
}
class _WorkspaceTab {
  final String title;final IconData icon;
  const _WorkspaceTab(this.title,this.icon);
}
