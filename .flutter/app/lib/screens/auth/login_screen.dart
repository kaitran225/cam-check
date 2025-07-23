import 'package:flutter/material.dart';
import '../../widgets/app_button.dart';
import '../../widgets/app_text_field.dart';
import '../../config/theme.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({Key? key}) : super(key: key);

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _isLoading = false;
  String? _errorMessage;
  bool _obscurePassword = true;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _login() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    // Simulate login delay
    await Future.delayed(const Duration(seconds: 2));

    // For demo purposes, just show success
    setState(() {
      _isLoading = false;
      _errorMessage = null;
    });

    // Navigate to main screen after successful login
    if (mounted) {
      // This would normally navigate to the main screen
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Login successful! (Demo mode)'),
          backgroundColor: AppTheme.successGreen,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.all(AppTheme.spacingL),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Logo and Title
                    Icon(
                      Icons.camera_alt,
                      size: 80,
                      color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
                    ),
                    const SizedBox(height: AppTheme.spacingM),
                    Text(
                      'CamCheck',
                      style: Theme.of(context).textTheme.displayMedium,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: AppTheme.spacingXs),
                    Text(
                      'Mobile Security Camera',
                      style: Theme.of(context).textTheme.bodyMedium,
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: AppTheme.spacingXl),

                    // Error Message
                    if (_errorMessage != null)
                      Container(
                        padding: const EdgeInsets.all(AppTheme.spacingS),
                        decoration: BoxDecoration(
                          color: AppTheme.errorRed.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          _errorMessage!,
                          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                color: AppTheme.errorRed,
                              ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    if (_errorMessage != null)
                      const SizedBox(height: AppTheme.spacingM),

                    // Username Field
                    AppTextField(
                      controller: _usernameController,
                      label: 'Username',
                      hint: 'Enter your username',
                      prefixIcon: Icons.person_outline,
                    ),
                    const SizedBox(height: AppTheme.spacingM),

                    // Password Field
                    AppTextField(
                      controller: _passwordController,
                      label: 'Password',
                      hint: 'Enter your password',
                      prefixIcon: Icons.lock_outline,
                      suffixIcon: _obscurePassword ? Icons.visibility : Icons.visibility_off,
                      obscureText: _obscurePassword,
                      onSuffixIconPressed: () {
                        setState(() {
                          _obscurePassword = !_obscurePassword;
                        });
                      },
                    ),
                    const SizedBox(height: AppTheme.spacingL),

                    // Login Button
                    AppButton(
                      text: 'Login',
                      isLoading: _isLoading,
                      onPressed: _login,
                    ),
                    const SizedBox(height: AppTheme.spacingM),

                    // QR Code Scanner Button
                    AppButton(
                      text: 'Scan QR Code',
                      type: ButtonType.secondary,
                      icon: Icons.qr_code_scanner,
                      onPressed: () {
                        // Navigate to QR scanner
                      },
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