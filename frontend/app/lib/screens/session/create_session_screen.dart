import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../api/session_service.dart';
import '../../config/theme.dart';
import '../../models/session.dart';
import '../../widgets/app_button.dart';
import '../streaming/streaming_screen.dart';

/// Screen for creating a new session
class CreateSessionScreen extends StatefulWidget {
  /// Constructor
  const CreateSessionScreen({Key? key}) : super(key: key);

  @override
  State<CreateSessionScreen> createState() => _CreateSessionScreenState();
}

class _CreateSessionScreenState extends State<CreateSessionScreen> {
  int _expirationMinutes = 30;
  bool _audioEnabled = true;
  bool _videoEnabled = true;
  String _quality = 'medium';
  String _recordingMode = 'none';
  bool _isCreating = false;
  String? _error;

  /// Create a new session
  Future<void> _createSession() async {
    setState(() {
      _isCreating = true;
      _error = null;
    });

    try {
      final sessionService = Provider.of<SessionService>(context, listen: false);
      
      final session = await sessionService.createSession(
        expirationMinutes: _expirationMinutes,
        audioEnabled: _audioEnabled,
        videoEnabled: _videoEnabled,
        quality: _quality,
        recordingMode: _recordingMode,
      );

      if (session != null && mounted) {
        // Navigate to streaming screen
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (context) => const StreamingScreen()),
        );
      } else {
        setState(() {
          _error = sessionService.error ?? 'Failed to create session';
        });
      }
    } catch (e) {
      setState(() {
        _error = e.toString();
      });
    } finally {
      setState(() {
        _isCreating = false;
      });
    }
  }

  /// Share session code
  void _shareSessionCode(Session session) {
    // Copy to clipboard
    Clipboard.setData(ClipboardData(text: session.sessionCode));
    
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Session code copied to clipboard'),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDarkMode = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDarkMode ? AppTheme.brandBlack : AppTheme.lightBackground,
      appBar: AppBar(
        title: const Text('Create Session'),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppTheme.spacingM),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Instructions
              Text(
                'Create a new session for remote camera viewing',
                style: AppTheme.bodyMedium.copyWith(
                  color: isDarkMode ? AppTheme.brandWhite : AppTheme.brandBlack,
                ),
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
                      
                      // Expiration time
                      _buildSettingRow(
                        'Expiration',
                        DropdownButton<int>(
                          value: _expirationMinutes,
                          isDense: true,
                          underline: const SizedBox(),
                          items: [
                            DropdownMenuItem(value: 10, child: Text('10 minutes')),
                            DropdownMenuItem(value: 30, child: Text('30 minutes')),
                            DropdownMenuItem(value: 60, child: Text('1 hour')),
                            DropdownMenuItem(value: 120, child: Text('2 hours')),
                          ],
                          onChanged: (value) {
                            setState(() {
                              _expirationMinutes = value!;
                            });
                          },
                        ),
                      ),
                      
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
                      
                      // Recording mode
                      _buildSettingRow(
                        'Recording',
                        DropdownButton<String>(
                          value: _recordingMode,
                          isDense: true,
                          underline: const SizedBox(),
                          items: [
                            DropdownMenuItem(value: 'none', child: Text('None')),
                            DropdownMenuItem(value: 'auto', child: Text('Auto')),
                            DropdownMenuItem(value: 'manual', child: Text('Manual')),
                          ],
                          onChanged: (value) {
                            setState(() {
                              _recordingMode = value!;
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
              
              // Create button
              AppButton(
                text: 'Create Session',
                icon: Icons.add,
                isLoading: _isCreating,
                onPressed: _createSession,
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