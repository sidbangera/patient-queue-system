import 'package:flutter/material.dart';
import 'api.dart';
import 'ui.dart';

export 'insights_screen.dart';

String value(dynamic x) => x?.toString() ?? '—';
String statusOf(Map<String,dynamic> row) => value(row['status']);

class QueueScreen extends StatefulWidget {
  final ApiClient api;
  const QueueScreen({super.key, required this.api});
  @override State<QueueScreen> createState() => _QueueScreenState();
}
class _QueueScreenState extends State<QueueScreen> {
  List<Map<String,dynamic>> doctors=[];
  List<Map<String,dynamic>> queue=[];
  int? doctorId;
  String? error;bool busy=true;bool actionRunning=false;int requestId=0;
  @override void initState(){super.initState();load();}
  Future<void> load() async {
    if (!mounted) return;
    final currentRequest = ++requestId;
    setState(() {busy=true;error=null;});
    try {
      final d=await widget.api.list('/api/doctors');
      final chosen=doctorId!=null && d.any((e)=>e['id']==doctorId) ? doctorId : (d.isEmpty?null:d.first['id'] as int);
      final q=chosen==null?<Map<String,dynamic>>[]:await widget.api.list('/api/queue/doctor/$chosen');
      if(mounted && currentRequest == requestId)setState(() {doctors=d;doctorId=chosen;queue=q;});
    } catch(e){if(mounted && currentRequest == requestId)setState(()=>error=e.toString());}
    finally{if(mounted && currentRequest == requestId)setState(()=>busy=false);}
  }
  Future<void> change(int id,String action) async {
    if (actionRunning || busy) return;
    setState(()=>actionRunning=true);
    try {
      await widget.api.send('POST','/api/queue/$id/$action');
      await load();
    } catch(e){if(mounted)showProblem(context,e);}
    finally{if(mounted)setState(()=>actionRunning=false);}
  }
  bool get canAdvance=>['DOCTOR','ADMIN'].contains(widget.api.role);
  @override Widget build(BuildContext context)=>RefreshIndicator(onRefresh:load,child:ListView(
    padding:const EdgeInsets.all(20),children:[
      sectionHeading('Doctor queue',caption:'Active visits for the clinic service date'),
      Align(alignment: Alignment.centerRight, child: TextButton.icon(
        onPressed: busy ? null : load, icon: const Icon(Icons.refresh), label: const Text('Refresh queue'))),
      const SizedBox(height:16),
      if(doctors.isNotEmpty)DropdownButtonFormField<int>(
        key:ValueKey(doctorId),initialValue:doctorId,isExpanded:true,
        decoration:const InputDecoration(labelText:'Choose doctor',prefixIcon:Icon(Icons.medical_services_outlined)),
        items:[for(final d in doctors)DropdownMenuItem(value:d['id'] as int,child:Text('${d['name']} · ${d['department']}'))],
        onChanged:actionRunning?null:(id){setState((){doctorId=id;queue=[];});load();}),
      const SizedBox(height:18),
      Row(children:[
        Expanded(child:infoCard(label:'Waiting',value:'${queue.where((q)=>statusOf(q)=='WAITING').length}',icon:Icons.hourglass_empty_rounded)),
        const SizedBox(width:12),
        Expanded(child:infoCard(label:'In consultation',value:'${queue.where((q)=>statusOf(q)=='IN_CONSULTATION').length}',icon:Icons.medical_services_outlined)),
      ]),
      const SizedBox(height:18),
      if(busy)const Center(child:CircularProgressIndicator()),
      if(error!=null)errorBanner(error!,load),
      if(!busy && doctors.isEmpty)emptyNotice('No doctors yet. An administrator must register doctors before queue management.'),
      if(!busy && error==null && doctorId!=null && queue.isEmpty)emptyNotice('No active check-ins today for this doctor.'),
      for(final entry in queue)Padding(padding:const EdgeInsets.only(bottom:12),child:Card(child:Padding(
        padding:const EdgeInsets.all(16),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
          Row(children:[
            Container(padding:const EdgeInsets.all(12),decoration:BoxDecoration(color:mist,borderRadius:BorderRadius.circular(14)),
                child:Text('#${entry['tokenNumber']}',style:const TextStyle(fontWeight:FontWeight.w900,fontSize:23,color:teal))),
            const SizedBox(width:14),
            Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
              Text(value((entry['patient'] as Map?)?['name']),style:const TextStyle(fontSize:17,fontWeight:FontWeight.w700,color:ink)),
              Text('Patient ID ${value((entry['patient'] as Map?)?['id'])}  •  ${value(entry['checkInTime']).replaceFirst('T',' ')}',style:const TextStyle(fontSize:12,color:muted)),
            ])),
            Chip(label:Text(statusOf(entry).replaceAll('_',' '))),
          ]),
          if(canAdvance)Padding(padding:const EdgeInsets.only(top:12),child:Wrap(spacing:8,runSpacing:8,children:[
            if(statusOf(entry)=='WAITING')FilledButton.tonalIcon(
              onPressed:actionRunning||busy?null:()=>change(entry['id'] as int,'call-next'),
              icon:const Icon(Icons.play_arrow),label:const Text('Call patient')),
            if(statusOf(entry)=='WAITING')OutlinedButton(
              onPressed:actionRunning||busy?null:()=>change(entry['id'] as int,'no-show'),child:const Text('No-show')),
            if(statusOf(entry)=='IN_CONSULTATION')FilledButton.icon(
              onPressed:actionRunning||busy?null:()=>change(entry['id'] as int,'complete'),
              icon:const Icon(Icons.check),label:const Text('Complete visit')),
          ])),
        ]),
      ))),
      const SizedBox(height:60),
    ],
  ));
}

Widget errorBanner(String error,VoidCallback retry)=>Card(child:Padding(padding:const EdgeInsets.all(18),child:Column(
  children:[Text(error,style:const TextStyle(color:Colors.red)),TextButton(onPressed:retry,child:const Text('Try again'))])));

class PatientsScreen extends StatefulWidget {
  final ApiClient api;
  const PatientsScreen({super.key,required this.api});
  @override State<PatientsScreen> createState()=>_PatientsScreenState();
}
class _PatientsScreenState extends State<PatientsScreen> {
  List<Map<String,dynamic>> patients=[];String? error;bool loading=true;bool creating=false;String filter='';
  @override void initState(){super.initState();load();}
  Future<void> load()async{
    setState(() {loading=true;error=null;});
    try {final list=await widget.api.list('/api/patients');if(mounted)setState(()=>patients=list);}
    catch(e){if(mounted)setState(()=>error=e.toString());}
    finally{if(mounted)setState(()=>loading=false);}
  }
  Future<void> addPatient() async {
    if (creating) return;
    setState(() => creating = true);
    try {
      final data = await showDialog<Map<String, dynamic>>(
        context: context,
        builder: (_) => const _RegisterPatientDialog(),
      );
      if (!mounted || data == null) return;
      await widget.api.send('POST', '/api/patients', body: data);
      if (mounted) await load();
    } catch (e) {
      if (mounted) showProblem(context, e);
    } finally {
      if (mounted) setState(() => creating = false);
    }
  }
  @override Widget build(BuildContext context){
    final visible=patients.where((p)=>value(p['name']).toLowerCase().contains(filter.toLowerCase())||value(p['id']).contains(filter)).toList();
    return RefreshIndicator(onRefresh:load,child:ListView(padding:const EdgeInsets.all(20),children:[
      sectionHeading('Patients',caption:'Patient directory and past visits'),
      const SizedBox(height:14),
      if(['RECEPTIONIST','ADMIN'].contains(widget.api.role))Align(alignment:Alignment.centerLeft,child:FilledButton.icon(
        onPressed:creating?null:addPatient,icon:const Icon(Icons.person_add_alt_1_outlined),label:const Text('Register patient'))),
      const SizedBox(height:14),
      TextField(onChanged:(s)=>setState(()=>filter=s),decoration:const InputDecoration(
        labelText:'Search by patient name or ID',prefixIcon:Icon(Icons.search))),
      const SizedBox(height:15),
      if(loading)const Center(child:CircularProgressIndicator()),
      if(error!=null)errorBanner(error!,load),
      if(!loading && visible.isEmpty)emptyNotice('No matching patients found.'),
      for(final patient in visible)Padding(padding:const EdgeInsets.only(bottom:8),child:Card(child:ListTile(
        leading:CircleAvatar(backgroundColor:mist,child:Text((value(patient['name']).isEmpty ? '?' : value(patient['name'])[0].toUpperCase()))),
        title:Text(value(patient['name']),style:const TextStyle(fontWeight:FontWeight.w700)),
        subtitle:Text('ID ${value(patient['id'])} · ${value(patient['age'])} years'),
        trailing:const Icon(Icons.chevron_right),
        onTap:()=>Navigator.of(context).push(MaterialPageRoute(builder:(_)=>PatientDetailScreen(api:widget.api,patient:patient))),
      ))),
      const SizedBox(height:55),
    ]));
  }
}

/// Owns form controllers for the entire lifetime of the dialog route.
/// The caller receives the completed values; no controllers cross route boundaries.
class _RegisterPatientDialog extends StatefulWidget {
  const _RegisterPatientDialog();

  @override
  State<_RegisterPatientDialog> createState() => _RegisterPatientDialogState();
}

class _RegisterPatientDialogState extends State<_RegisterPatientDialog> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _age = TextEditingController();
  final _contact = TextEditingController();

  @override
  void dispose() {
    _name.dispose();
    _age.dispose();
    _contact.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    Navigator.of(context).pop(<String, dynamic>{
      'name': _name.text.trim(),
      'age': int.parse(_age.text.trim()),
      'contact': _contact.text.trim(),
    });
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Register patient'),
    content: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 400),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(labelText: 'Full name'),
              validator: (v) => v == null || v.trim().isEmpty ? 'Required' : null,
            ),
            const SizedBox(height: 10),
            TextFormField(
              controller: _age,
              decoration: const InputDecoration(labelText: 'Age'),
              keyboardType: TextInputType.number,
              validator: (v) {
                final age = int.tryParse(v?.trim() ?? '');
                return age == null || age < 0 || age > 130 ? 'Enter a valid age' : null;
              },
            ),
            const SizedBox(height: 10),
            TextFormField(
              controller: _contact,
              decoration: const InputDecoration(labelText: 'Contact'),
              onFieldSubmitted: (_) => _submit(),
              validator: (v) => v == null || v.trim().isEmpty ? 'Required' : null,
            ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
      FilledButton(onPressed: _submit, child: const Text('Save')),
    ],
  );
}

class PatientDetailScreen extends StatefulWidget {
  final ApiClient api;final Map<String,dynamic> patient;
  const PatientDetailScreen({super.key,required this.api,required this.patient});
  @override State<PatientDetailScreen> createState()=>_PatientDetailScreenState();
}
class _PatientDetailScreenState extends State<PatientDetailScreen> {
  List<Map<String,dynamic>> visits=[];String? error;bool loading=true;
  @override void initState(){super.initState();load();}
  Future<void> load() async {
    if(widget.api.role=='RECEPTIONIST'){setState(()=>loading=false);return;}
    try {final v=await widget.api.list('/api/queue/patient/${widget.patient['id']}');if(mounted)setState(()=>visits=v);}
    catch(e){if(mounted)setState(()=>error=e.toString());}
    finally{if(mounted)setState(()=>loading=false);}
  }
  @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:Text(value(widget.patient['name']))),
    body:ListView(padding:const EdgeInsets.all(20),children:[
      sectionHeading('Patient summary',caption:'Synthetic/demo use only'),const SizedBox(height:12),
      Card(child:Padding(padding:const EdgeInsets.all(18),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        Text(value(widget.patient['name']),style:const TextStyle(fontSize:22,fontWeight:FontWeight.w700)),
        const SizedBox(height:8),Text('Patient #${widget.patient['id']}  •  Age ${widget.patient['age']}'),
        Text('Contact: ${value(widget.patient['contact'])}'),
      ]))),
      const SizedBox(height:24),sectionHeading('Visit history'),const SizedBox(height:10),
      if(widget.api.role=='RECEPTIONIST')emptyNotice('Visit history requires doctor, nurse or admin access.'),
      if(widget.api.role!='RECEPTIONIST' && loading)const Center(child:CircularProgressIndicator()),
      if(error!=null && widget.api.role!='RECEPTIONIST')Text(error!,style:const TextStyle(color:Colors.red)),
      if(!loading && error==null && visits.isEmpty && widget.api.role!='RECEPTIONIST')emptyNotice('No visits recorded yet.'),
      for(final visit in visits)Card(child:ListTile(
        leading:const Icon(Icons.event_note,color:teal),
        title:Text('${value((visit['doctor'] as Map?)?['name'])} • Token #${visit['tokenNumber']}'),
        subtitle:Text('${value(visit['serviceDate'])} · ${statusOf(visit).replaceAll('_',' ')}'),
      )),
      const SizedBox(height:24),sectionHeading('Clinical documents & recordings'),
      const SizedBox(height:10),
      const Card(child:Padding(padding:EdgeInsets.all(18),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
        Text('Planned module · not available in this version',style:TextStyle(fontWeight:FontWeight.w700,color:ink)),
        SizedBox(height:8),
        Text('X-rays, reports, secure file uploads, voice notes and transcription require an audited storage/consent workflow. No real patient files should be entered in this demo.',style:TextStyle(color:muted)),
      ]))),
    ]));
}

class CheckinScreen extends StatefulWidget {
  final ApiClient api;
  const CheckinScreen({super.key,required this.api});
  @override State<CheckinScreen> createState()=>_CheckinScreenState();
}
class _CheckinScreenState extends State<CheckinScreen> {
  List<Map<String,dynamic>> patients=[],doctors=[];
  int? patientId,doctorId;bool submitting=false,loading=true;String? result,error;
  @override void initState(){super.initState();load();}
  Future<void> load()async{
    try {
      final p=await widget.api.list('/api/patients');final d=await widget.api.list('/api/doctors');
      if(mounted)setState(() {patients=p;doctors=d;});
    }catch(e){if(mounted)setState(()=>error=e.toString());}
    finally{if(mounted)setState(()=>loading=false);}
  }
  Future<void> checkIn() async {
    if(submitting)return;
    if(patientId==null||doctorId==null){showProblem(context,'Choose both a patient and a doctor');return;}
    setState(() {submitting=true;result=null;error=null;});
    try {
      final r=await widget.api.send('POST','/api/queue/checkin',body:{'patientId':patientId,'doctorId':doctorId}) as Map;
      if(mounted)setState(()=>result='Check-in successful · Token #${r['tokenNumber']}');
    }catch(e){if(mounted)setState(()=>error=e.toString());}
    finally{if(mounted)setState(()=>submitting=false);}
  }
  @override Widget build(BuildContext context)=>ListView(padding:const EdgeInsets.all(20),children:[
    sectionHeading('Patient check-in',caption:'Reception · allocate a unique daily token'),
    const SizedBox(height:20),
    if(loading)const Center(child:CircularProgressIndicator()),
    if(!loading)Card(child:Padding(padding:const EdgeInsets.all(20),child:Column(children:[
      DropdownButtonFormField<int>(isExpanded:true,decoration:const InputDecoration(labelText:'Patient'),
        items:[for(final p in patients)DropdownMenuItem(value:p['id'] as int,child:Text('${p['name']} (#${p['id']})'))],
        onChanged:(v)=>setState(()=>patientId=v)),
      const SizedBox(height:16),
      DropdownButtonFormField<int>(isExpanded:true,decoration:const InputDecoration(labelText:'Doctor'),
        items:[for(final d in doctors)DropdownMenuItem(value:d['id'] as int,child:Text('${d['name']} · ${d['department']}'))],
        onChanged:(v)=>setState(()=>doctorId=v)),
      const SizedBox(height:20),
      FilledButton.icon(onPressed:submitting?null:checkIn,icon:const Icon(Icons.add_task),label:Text(submitting?'Checking in...':'Confirm check-in')),
      if(result!=null)Padding(padding:const EdgeInsets.only(top:15),child:Text(result!,style:const TextStyle(color:teal,fontWeight:FontWeight.w800))),
      if(error!=null)Padding(padding:const EdgeInsets.only(top:15),child:Text(error!,style:const TextStyle(color:Colors.red))),
    ]))),
  ]);
}
