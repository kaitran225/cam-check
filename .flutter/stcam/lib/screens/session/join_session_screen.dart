import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../api/session_service.dart';
import '../../config/theme.dart';
import '../../config/app_config.dart';

/// Screen for joining an existing camera viewing session
class JoinSessionScreen extends StatefulWidget {
  const JoinSessionScreen({super.key});

  @override
  State<JoinSessionScreen> createState() => _JoinSessionScreenState();
}

class _JoinSessionScreenState extends State<JoinSessionScreen> {
  final _formKey = GlobalKey<FormState>();
  final _sessionCodeController = TextEditingController();
  bool _audioEnabled = AppConfig.defaultAudioEnabled;
  bool _videoEnabled = AppConfig.defaultVideoEnabled;
  String _quality = AppConfig.defaultVideoQuality;
  bool _isJoiningSession = false;
  
  final List<String> _qualityOptions = ['low', 'medium', 'high'];
  
  @override
  void dispose() {
    _sessionCodeController.dispose();
    super.dispose();
  }
  
  Future<void> _joinSession() async {
    if (_isJoiningSession) return;
    
    if (_formKey.currentState!.validate()) {
      setState(() {
        _isJoiningSession = true;
      });
      
      final sessionCode = _sessionCodeController.text.trim();
      final sessionService = Provider.of<SessionService>(context, listen: false);
      final session = await sessionService.joinSession(
        sessionCode,
        audioEnabled: _audioEnabled,
        videoEnabled: _videoEnabled,
        quality: _quality,
      );
      
      setState(() {
        _isJoiningSession = false;
      });
      
      if (session != null && mounted) {
        // Navigate back with the session
        Navigator.pop(context, session);
      } else if (mounted) {
        // Show error message
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(sessionService.errorMessage ?? 'Failed to join session'),
            backgroundColor: AppTheme.errorRed,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }
  
  @override
  Widget build(BuildContext context) {
    final sessionService = Provider.of<SessionService>(context);
    
    return Scaffold(
      backgroundColor: AppTheme.brandBlack,
      appBar: AppBar(
        title: const Text('Join Session'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Session code input
              const Text(
                'Session Code',
                style: AppTheme.headingSmall,
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _sessionCodeController,
                decoration: const InputDecoration(
                  labelText: 'Enter 6-digit code',
                  prefixIcon: Icon(Icons.vpn_key),
                  hintText: 'e.g. 123456',
                ),
                style: AppTheme.bodyLarge,
                textInputAction: TextInputAction.next,
                keyboardType: TextInputType.number,
                maxLength: 6,
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return 'Please enter a session code';
                  }
                  if (value.length != 6 || int.tryParse(value) == null) {
                    return 'Please enter a valid 6-digit code';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 24),
              
              // Media options
              const Text(
                'Media Options',
                style: AppTheme.headingSmall,
              ),
              const SizedBox(height: 8),
              SwitchListTile(
                title: const Text('Enable Audio'),
                subtitle: const Text('Allow audio streaming'),
                value: _audioEnabled,
                onChanged: (value) {
                  setState(() {
                    _audioEnabled = value;
                  });
                },
                secondary: Icon(
                  _audioEnabled ? Icons.mic : Icons.mic_off,
                  color: _audioEnabled ? AppTheme.actionBlue : AppTheme.lightGray,
                ),
              ),
              SwitchListTile(
                title: const Text('Enable Video'),
                subtitle: const Text('Allow video streaming'),
                value: _videoEnabled,
                onChanged: (value) {
                  setState(() {
                    _videoEnabled = value;
                  });
                },
                secondary: Icon(
                  _videoEnabled ? Icons.videocam : Icons.videocam_off,
                  color: _videoEnabled ? AppTheme.actionBlue : AppTheme.lightGray,
                ),
              ),
              const SizedBox(height: 16),
              
              // Quality options
              const Text(
                'Stream Quality',
                style: AppTheme.headingSmall,
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                value: _quality,
                decoration: const InputDecoration(
                  labelText: 'Video Quality',
                  prefixIcon: Icon(Icons.high_quality),
                ),
                items: _qualityOptions.map((quality) {
                  return DropdownMenuItem<String>(
                    value: quality,
                    child: Text(quality.toUpperCase()),
                  );
                }).toList(),
                onChanged: (value) {
                  if (value != null) {
                    setState(() {
                      _quality = value;
                    });
                  }
                },
              ),
              const SizedBox(height: 32),
              
              // Join session button
              ElevatedButton(
                onPressed: _isJoiningSession ? null : _joinSession,
                child: _isJoiningSession
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: AppTheme.brandWhite,
                        ),
                      )
                    : const Text('JOIN SESSION'),
              ),
              
              if (sessionService.errorMessage != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: AppTheme.errorRed),
                  ),
                  child: Text(
                    sessionService.errorMessage!,
                    style: AppTheme.bodyMedium.copyWith(color: AppTheme.errorRed),
                    textAlign: TextAlign.center,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
} 