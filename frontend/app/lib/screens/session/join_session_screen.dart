import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../api/session_service.dart';
import '../../config/theme.dart';
import '../../widgets/app_button.dart';
import '../../widgets/app_text_field.dart';
import '../streaming/streaming_screen.dart';

/// Screen for joining an existing session
class JoinSessionScreen extends StatefulWidget {
  /// Constructor
  const JoinSessionScreen({Key? key}) : super(key: key);

  @override
  State<JoinSessionScreen> createState() => _JoinSessionScreenState();
}

class _JoinSessionScreenState extends State<JoinSessionScreen> {
  final TextEditingController _codeController = TextEditingController();
  final FocusNode _codeFocus = FocusNode();
  bool _audioEnabled = true;
  bool _videoEnabled = true;
  String _quality = 'medium';
  bool _isJoining = false;
  String? _error;

  @override
  void dispose() {
    _codeController.dispose();
    _codeFocus.dispose();
    super.dispose();
  }

  /// Join a session
  Future<void> _joinSession() async {
    final sessionCode = _codeController.text.trim();
    
    if (sessionCode.isEmpty) {
      setState(() {
        _error = 'Please enter a session code';
      });
      return;
    }

    setState(() {
      _isJoining = true;
      _error = null;
    });

    try {
      final sessionService = Provider.of<SessionService>(context, listen: false);
      
      final session = await sessionService.joinSession(
        sessionCode,
        audioEnabled: _audioEnabled,
        videoEnabled: _videoEnabled,
        quality: _quality,
      );

      if (session != null && mounted) {
        // Navigate to streaming screen
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (context) => const StreamingScreen()),
        );
      } else {
        setState(() {
          _error = sessionService.error ?? 'Failed to join session';
        });
      }
    } catch (e) {
      setState(() {
        _error = e.toString();
      });
    } finally {
      setState(() {
        _isJoining = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDarkMode ? AppTheme.brandBlack : AppTheme.lightBackground,
      appBar: AppBar(
        title: const Text('Join Session'),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppTheme.spacingM),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Instructions
              Text(
                'Enter the 6-digit session code provided by the admin',
                style: AppTheme.bodyMedium.copyWith(
                  color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
                ),
              ),
              const SizedBox(height: AppTheme.spacingM),
              
              // Session code field
              AppTextField(
                controller: _codeController,
                focusNode: _codeFocus,
                label: 'Session Code',
                hint: 'Enter 6-digit code',
                prefixIcon: Icons.vpn_key,
                keyboardType: TextInputType.number,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _joinSession(),
              ),
              const SizedBox(height: AppTheme.spacingM),
              
              // Session settings
              Card(
                color: isDarkMode ? AppTheme.brandGray : AppTheme.brandWhite,
                elevation: 0,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(4.0),
                  side: BorderSide(
                    color: isDarkMode
                        ? AppTheme.brandWhite.withOpacity(0.1)
                        : AppTheme.brandGray.withOpacity(0.3),
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(AppTheme.spacingS),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Session Settings',
                        style: AppTheme.headingSmall.copyWith(
                          color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
                        ),
                      ),
                      const Divider(height: AppTheme.spacingM),
                      
                      // Audio
                      _buildSettingRow(
                        'Audio',
                        Switch(
                          value: _audioEnabled,
                          onChanged: (value) {
                            setState(() {
                              _audioEnabled = value;
                            });
                          },
                          activeColor: AppTheme.actionBlue,
                        ),
                      ),
                      
                      // Video
                      _buildSettingRow(
                        'Video',
                        Switch(
                          value: _videoEnabled,
                          onChanged: (value) {
                            setState(() {
                              _videoEnabled = value;
                            });
                          },
                          activeColor: AppTheme.actionBlue,
                        ),
                      ),
                      
                      // Quality
                      _buildSettingRow(
                        'Quality',
                        DropdownButton<String>(
                          value: _quality,
                          isDense: true,
                          underline: const SizedBox(),
                          items: [
                            DropdownMenuItem(value: 'low', child: Text('Low')),
                            DropdownMenuItem(value: 'medium', child: Text('Medium')),
                            DropdownMenuItem(value: 'high', child: Text('High')),
                          ],
                          onChanged: (value) {
                            setState(() {
                              _quality = value!;
                            });
                          },
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppTheme.spacingM),
              
              // Error message
              if (_error != null)
                Container(
                  padding: const EdgeInsets.all(AppTheme.spacingXs),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(4.0),
                  ),
                  child: Text(
                    _error!,
                    style: AppTheme.bodySmall.copyWith(
                      color: AppTheme.errorRed,
                    ),
                  ),
                ),
              
              const Spacer(),
              
              // Join button
              AppButton(
                text: 'Join Session',
                icon: Icons.login,
                isLoading: _isJoining,
                onPressed: _joinSession,
              ),
            ],
          ),
        ),
      ),
    );
  }
  
  /// Build a setting row with label and control
  Widget _buildSettingRow(String label, Widget control) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;
    
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTheme.spacingXs),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: AppTheme.bodyMedium.copyWith(
              color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
            ),
          ),
          control,
        ],
      ),
    );
  }
} 