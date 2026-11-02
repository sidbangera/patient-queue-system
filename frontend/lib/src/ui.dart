import 'package:flutter/material.dart';

const ink = Color(0xFF18333A);
const teal = Color(0xFF137D74);
const mist = Color(0xFFF4F8F6);
const muted = Color(0xFF667E82);

ThemeData appTheme() => ThemeData(
  useMaterial3: true,
  scaffoldBackgroundColor: mist,
  colorScheme: ColorScheme.fromSeed(seedColor: teal, primary: teal, surface: Colors.white),
  textTheme: const TextTheme(
    headlineMedium: TextStyle(color: ink, fontSize: 28, fontWeight: FontWeight.w800),
    titleLarge: TextStyle(color: ink, fontWeight: FontWeight.w700),
    bodyMedium: TextStyle(color: ink),
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true, fillColor: Colors.white,
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
    contentPadding: const EdgeInsets.symmetric(horizontal: 15, vertical: 14),
  ),
  cardTheme: CardThemeData(
    color: Colors.white, elevation: 0,
    margin: EdgeInsets.zero,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: const BorderSide(color: Color(0xFFE2ECE8))),
  ),
);

Widget sectionHeading(String title, {String? caption}) => Column(
  crossAxisAlignment: CrossAxisAlignment.start,
  children: [
    Text(title, style: const TextStyle(fontSize: 22, color: ink, fontWeight: FontWeight.w800)),
    if (caption != null) Padding(padding: const EdgeInsets.only(top: 4), child: Text(caption, style: const TextStyle(color: muted))),
  ],
);

Widget infoCard({required String label, required String value, IconData? icon}) => Card(
  child: Padding(padding: const EdgeInsets.all(18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    if (icon != null) Icon(icon, color: teal),
    const SizedBox(height: 8),
    Text(value, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: ink)),
    const SizedBox(height: 4),
    Text(label, style: const TextStyle(color: muted)),
  ])),
);

void showProblem(BuildContext context, Object error) {
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString()), backgroundColor: const Color(0xFFAD4F3F)));
}

Widget emptyNotice(String text) => Padding(
  padding: const EdgeInsets.symmetric(vertical: 38, horizontal: 12),
  child: Center(child: Text(text, textAlign: TextAlign.center, style: const TextStyle(color: muted))),
);
