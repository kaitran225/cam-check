import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../api/session_service.dart';
import '../../config/theme.dart';
import '../../config/app_config.dart';

/// Screen for creating a new camera viewing session
class CreateSessionScreen extends StatefulWidget {
  const CreateSessionScreen({super.key});

  @override
  State<CreateSessionScreen> createState() => _CreateSessionScreenState();
}

class _CreateSessionScreenState extends State<CreateSessionScreen> {
  int _expirationMinutes = AppConfig.defaultSessionExpirationMinutes;
  bool _audioEnabled = AppConfig.defaultAudioEnabled;
  bool _videoEnabled = AppConfig.defaultVideoEnabled;
  String _quality = AppConfig.defaultVideoQuality;
  String _recordingMode = 'none';
  bool _isCreatingSession = false;
  
  final List<int> _expirationOptions = [5, 10, 15, 30, 60];
  final List<String> _qualityOptions = ['low', 'medium', 'high'];
  final List<String> _recordingOptions = ['none', 'audio', 'video', 'both'];
  
  Future<void> _createSession() async {
    if (_isCreatingSession) return;
    
    setState(() {
      _isCreatingSession = true;
    });
    
    final sessionService = Provider.of<SessionService>(context, listen: false);
    final session = await sessionService.createSession(
      expirationMinutes: _expirationMinutes,
      audioEnabled: _audioEnabled,
      videoEnabled: _videoEnabled,
      quality: _quality,
      recordingMode: _recordingMode,
    );
    
    setState(() {
      _isCreatingSession = false;
    });
    
    if (session != null && mounted) {
      // Show success dialog with session code
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => AlertDialog(
          title: const Text('Session Created'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Share this code with the user you want to connect with:'),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppTheme.brandGray,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: AppTheme.actionBlue),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      session.sessionCode,
                      style: AppTheme.headingLarge.copyWith(
                        letterSpacing: 2,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.copy),
                      onPressed: () {
                        // Copy to clipboard
                        // In a real app, you would use Clipboard.setData
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Session code copied to clipboard'),
                            duration: Duration(seconds: 2),
                          ),
                        );
                      },
                      tooltip: 'Copy to clipboard',
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Session expires in ${session.formattedTimeRemaining}',
                style: AppTheme.bodySmall,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                Navigator.pop(context, session);
              },
              child: const Text('DONE'),
            ),
          ],
        ),
      );
    } else if (mounted) {
      // Show error message
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(sessionService.errorMessage ?? 'Failed to create session'),
          backgroundColor: AppTheme.errorRed,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }
  
  @override
  Widget build(BuildContext context) {
    final sessionService = Provider.of<SessionService>(context);
    
    return Scaffold(
      backgroundColor: AppTheme.brandBlack,
      appBar: AppBar(
        title: const Text('Create Session'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Session duration
            const Text(
              'Session Duration',
              style: AppTheme.headingSmall,
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<int>(
              value: _expirationMinutes,
              decoration: const InputDecoration(
                labelText: 'Expiration Time',
                prefixIcon: Icon(Icons.timer),
              ),
              items: _expirationOptions.map((minutes) {
                return DropdownMenuItem<int>(
                  value: minutes,
                  child: Text('$minutes minutes'),
                );
              }).toList(),
              onChanged: (value) {
                if (value != null) {
                  setState(() {
                    _expirationMinutes = value;
                  });
                }
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
            const SizedBox(height: 16),
            
            // Recording options
            const Text(
              'Recording Options',
              style: AppTheme.headingSmall,
            ),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: _recordingMode,
              decoration: const InputDecoration(
                labelText: 'Recording Mode',
                prefixIcon: Icon(Icons.fiber_manual_record),
              ),
              items: _recordingOptions.map((mode) {
                String label;
                switch (mode) {
                  case 'none':
                    label = 'No Recording';
                    break;
                  case 'audio':
                    label = 'Audio Only';
                    break;
                  case 'video':
                    label = 'Video Only';
                    break;
                  case 'both':
                    label = 'Audio & Video';
                    break;
                  default:
                    label = mode.toUpperCase();
                }
                
                return DropdownMenuItem<String>(
                  value: mode,
                  child: Text(label),
                );
              }).toList(),
              onChanged: (value) {
                if (value != null) {
                  setState(() {
                    _recordingMode = value;
                  });
                }
              },
            ),
            const SizedBox(height: 32),
            
            // Create session button
            ElevatedButton(
              onPressed: _isCreatingSession ? null : _createSession,
              child: _isCreatingSession
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: AppTheme.brandWhite,
                      ),
                    )
                  : const Text('CREATE SESSION'),
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
    );
  }
} 