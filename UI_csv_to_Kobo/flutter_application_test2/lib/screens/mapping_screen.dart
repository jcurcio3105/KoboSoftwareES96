import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class MappingScreen extends StatefulWidget {
  final Map<String, dynamic> credentialsAndFields;

  const MappingScreen({Key? key, required this.credentialsAndFields}) : super(key: key);

  @override
  _MappingScreenState createState() => _MappingScreenState();
}

class _MappingScreenState extends State<MappingScreen> {
  final Map<String, String> _fieldMapping = {};
  final TextEditingController _outputController = TextEditingController();
  bool _uploading = false;

  static const List<String> buttonChoices = [
    "(Unused)",
    "button1",
    "button2",
    "button3",
    "button4",
    "button5"
  ];

  void _startUpload() async {
    // Validate uniqueness
    final chosen = _fieldMapping.values.where((v) => v != "(Unused)").toList();
    if (chosen.length != chosen.toSet().length) {
      _showError("Each CSV button can be assigned to at most one form field.");
      return;
    }

    setState(() {
      _uploading = true;
      _outputController.text = "Starting upload...\n\n";
    });

    final url = Uri.parse('http://localhost:5001/upload');
    final body = jsonEncode({
      "username": widget.credentialsAndFields['username'],
      "password": widget.credentialsAndFields['password'],
      "survey_link": widget.credentialsAndFields['survey_link'],
      "csv_path": widget.credentialsAndFields['csv_path'],
      "output_root": widget.credentialsAndFields['output_root'],
      "mapping": _fieldMapping,
    });

    try {
      final response = await http.post(url,
          headers: {"Content-Type": "application/json"}, body: body);

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        _outputController.text += data['result'] ?? "Upload completed.\n";
        _showInfo("Upload process completed. See details below.");
      } else {
        final err = jsonDecode(response.body)['error'] ?? 'Unknown error';
        _showError(err);
      }
    } catch (e) {
      _showError(e.toString());
    } finally {
      setState(() => _uploading = false);
    }
  }

  void _showError(String message) {
    showDialog(
        context: context,
        builder: (_) => AlertDialog(
              title: const Text("Error"),
              content: Text(message),
              actions: [
                TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text("OK"))
              ],
            ));
  }

  void _showInfo(String message) {
    showDialog(
        context: context,
        builder: (_) => AlertDialog(
              title: const Text("Info"),
              content: Text(message),
              actions: [
                TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text("OK"))
              ],
            ));
  }

  @override
  Widget build(BuildContext context) {
    final fields = widget.credentialsAndFields['fields'] as List<dynamic>? ?? [];

    return Scaffold(
      appBar: AppBar(title: const Text("Map CSV columns to form fields")),
      body: Padding(
        padding: const EdgeInsets.all(18.0),
        child: Column(
          children: [
            const Text(
              "Assign CSV columns to your custom form fields",
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 10),
            Expanded(
              child: ListView.builder(
                itemCount: fields.length,
                itemBuilder: (context, index) {
                  final field = fields[index];
                  final name = field['name'] ?? '';
                  final label = field['label'] ?? name;
                  return Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4.0),
                    child: Row(
                      children: [
                        Expanded(flex: 2, child: Text(label)),
                        Expanded(flex: 1, child: Text(name, style: const TextStyle(color: Colors.grey))),
                        Expanded(
                          flex: 1,
                          child: DropdownButton<String>(
                            value: _fieldMapping[name] ?? "(Unused)",
                            items: buttonChoices
                                .map((btn) => DropdownMenuItem(
                                      value: btn,
                                      child: Text(btn),
                                    ))
                                .toList(),
                            onChanged: (val) {
                              setState(() {
                                if (val != null) _fieldMapping[name] = val;
                              });
                            },
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 10),
            if (fields.isEmpty)
              const Text(
                "No custom fields detected in this form. You can still upload; only timestamps/meta will be submitted.",
                style: TextStyle(color: Colors.grey),
              ),
            const SizedBox(height: 10),
            const Text(
              "Note: 'Time' column is fixed and used for start/end timestamps.",
              style: TextStyle(color: Colors.grey),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: _outputController,
              maxLines: 6,
              readOnly: true,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                hintText: "Upload output console",
              ),
            ),
            const SizedBox(height: 10),
            _uploading
                ? const CircularProgressIndicator()
                : Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      ElevatedButton(
                        onPressed: _startUpload,
                        child: const Text("Upload"),
                      ),
                      ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        child: const Text("Close"),
                      ),
                    ],
                  ),
          ],
        ),
      ),
    );
  }
}
