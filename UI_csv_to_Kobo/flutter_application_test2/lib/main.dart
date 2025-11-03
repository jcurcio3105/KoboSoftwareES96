import 'package:flutter/material.dart';
import 'screens/credentials_screen.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Kobo App',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: CredentialsScreen(
        onFieldsFetched: (fields) {
          // Handle the fetched fields from your Flask backend
          // For now, just print to console
          print('Fields fetched: $fields');

          // You can also navigate to another screen here if needed
          // Navigator.push(context, MaterialPageRoute(builder: (_) => NextScreen(fields: fields)));
        },
      ),
    );
  }
}
