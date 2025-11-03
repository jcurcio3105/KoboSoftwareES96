import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

class CredentialsScreen extends StatefulWidget {
  final Function(Map<String, dynamic>) onFieldsFetched;

  const CredentialsScreen({Key? key, required this.onFieldsFetched}) : super(key: key);

  @override
  State<CredentialsScreen> createState() => _CredentialsScreenState();
}

class _CredentialsScreenState extends State<CredentialsScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  bool _isPasswordVisible = false;
  bool _loading = false;

  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _surveyLinkController = TextEditingController();
  final _csvPathController = TextEditingController();
  final _outputController = TextEditingController(text: "instances");

  Future<void> _fetchFields() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() => _loading = true);

    final url = Uri.parse('http://localhost:5001/get_fields');
    final body = jsonEncode({
      "username": _usernameController.text.trim(),
      "password": _passwordController.text.trim(),
      "survey_link": _surveyLinkController.text.trim(),
    });

    try {
      final response = await http.post(
        url,
        headers: {"Content-Type": "application/json"},
        body: body,
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final fields = data['fields'] ?? [];
        widget.onFieldsFetched({
          "username": _usernameController.text.trim(),
          "password": _passwordController.text.trim(),
          "survey_link": _surveyLinkController.text.trim(),
          "csv_path": _csvPathController.text.trim(),
          "output_root": _outputController.text.trim(),
          "fields": fields,
        });
      } else {
        final err = jsonDecode(response.body)['error'] ?? 'Unknown error';
        _showError(err);
      }
    } catch (e) {
      _showError(e.toString());
    } finally {
      setState(() => _loading = false);
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
            child: const Text("OK"),
          ),
        ],
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    bool obscure = false,
    String? Function(String?)? validator,
    Widget? suffixIcon,
  }) {
    return TextFormField(
      controller: controller,
      obscureText: obscure,
      validator: validator,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
        border: const OutlineInputBorder(),
        suffixIcon: suffixIcon,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white, // Page background
      body: Center(
        child: Card(
          color: Colors.white, // Form card background
          elevation: 4,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
          child: Container(
            padding: const EdgeInsets.all(32.0),
            constraints: const BoxConstraints(maxWidth: 400),
            child: SingleChildScrollView(
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Image.asset(
                      'assets/images/KoboToolbox_log.jpg',
                      height: 100,
                    ),
                    const SizedBox(height: 16),

                    // Headline
                    Text(
                      "Upload to KoboToolbox",
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                    ),
                    const SizedBox(height: 24),

                    _buildTextField(
                      controller: _usernameController,
                      label: "Username",
                      icon: Icons.person,
                      validator: (v) => v == null || v.isEmpty ? 'Enter username' : null,
                    ),
                    const SizedBox(height: 16),

                    _buildTextField(
                      controller: _passwordController,
                      label: "Password",
                      icon: Icons.lock,
                      obscure: !_isPasswordVisible,
                      validator: (v) => v == null || v.isEmpty ? 'Enter password' : null,
                      suffixIcon: IconButton(
                        icon: Icon(_isPasswordVisible
                            ? Icons.visibility_off
                            : Icons.visibility),
                        onPressed: () {
                          setState(() {
                            _isPasswordVisible = !_isPasswordVisible;
                          });
                        },
                      ),
                    ),
                    const SizedBox(height: 16),

                    _buildTextField(
                      controller: _surveyLinkController,
                      label: "Survey Link",
                      icon: Icons.link,
                      validator: (v) => v == null || v.isEmpty ? 'Enter survey link' : null,
                    ),
                    const SizedBox(height: 16),

                    _buildTextField(
                      controller: _csvPathController,
                      label: "CSV Path (optional)",
                      icon: Icons.folder_open,
                    ),
                    const SizedBox(height: 16),

                    _buildTextField(
                      controller: _outputController,
                      label: "Output Root",
                      icon: Icons.save,
                    ),
                    const SizedBox(height: 24),

                    _loading
                        ? const CircularProgressIndicator()
                        : SizedBox(
                            width: double.infinity,
                            child: ElevatedButton(
                              onPressed: _fetchFields,
                              style: ElevatedButton.styleFrom(
                                backgroundColor: Color(0xFF0090F2),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(4),
                                ),
                              ),
                              child: const Padding(
                                padding: EdgeInsets.symmetric(vertical: 12),
                                child: Text(
                                  "Next",
                                  style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.black,
                                  ),
                                ),
                              ),
                            ),
                          ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
