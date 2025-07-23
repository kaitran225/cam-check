import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../config/theme.dart';
import '../models/session.dart';
import 'app_button.dart';

/// A widget that displays session information
class SessionCard extends StatelessWidget {
  /// Session to display
  final Session session;
  
  /// Whether to show the session code
  final bool showCode;
  
  /// Callback when end session button is pressed
  final VoidCallback? onEndSession;
  
  /// Callback when share code button is pressed
  final VoidCallback? onShareCode;
  
  /// Constructor
  const SessionCard({
    Key? key,
    required this.session,
    this.showCode = true,
    this.onEndSession,
    this.onShareCode,
  }) : super(key: key);
  
  @override
  Widget build(BuildContext context) {
    final isCreator = session.role == 'creator';
    final timeFormatter = DateFormat('h:mm a');
    final dateFormatter = DateFormat('MMM d, yyyy');
    
    return Card(
      color: Theme.of(context).brightness == Brightness.dark
          ? AppTheme.brandGray
          : AppTheme.brandWhite,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(4.0),
        side: BorderSide(
          color: Theme.of(context).brightness == Brightness.dark
              ? AppTheme.brandWhite.withOpacity(0.1)
              : AppTheme.brandGray.withOpacity(0.3),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spacingS),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Row(
              children: [
                Icon(
                  isCreator ? Icons.videocam : Icons.login,
                  color: isCreator ? AppTheme.actionBlue : AppTheme.successGreen,
                  size: 20,
                ),
                const SizedBox(width: AppTheme.spacingXs),
                Text(
                  isCreator ? 'Session Created' : 'Session Joined',
                  style: AppTheme.headingSmall.copyWith(
                    color: Theme.of(context).brightness == Brightness.dark
                        ? AppTheme.brandWhite
                        : AppTheme.brandBlack,
                  ),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppTheme.spacingXs,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: session.isActive
                        ? AppTheme.successGreen.withOpacity(0.2)
                        : AppTheme.lightGray.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(4.0),
                  ),
                  child: Text(
                    session.isActive ? 'Active' : 'Expired',
                    style: AppTheme.caption.copyWith(
                      color: session.isActive
                          ? AppTheme.successGreen
                          : AppTheme.lightGray,
                    ),
                  ),
                ),
              ],
            ),
            
            const Divider(height: AppTheme.spacingM),
            
            // Session details
            if (showCode && session.sessionCode.isNotEmpty) ...[
              _buildDetailRow(context, 'Session Code', session.sessionCode),
              const SizedBox(height: AppTheme.spacingXs),
            ],
            
            if (session.connectedTo != null) ...[
              _buildDetailRow(context, 'Connected To', session.connectedTo!),
              const SizedBox(height: AppTheme.spacingXs),
            ],
            
            if (session.joinedAt != null) ...[
              _buildDetailRow(
                context,
                'Started',
                '${timeFormatter.format(session.joinedAt!)} on ${dateFormatter.format(session.joinedAt!)}',
              ),
              const SizedBox(height: AppTheme.spacingXs),
            ],
            
            if (session.expiresAt != null) ...[
              _buildDetailRow(
                context,
                'Expires',
                session.isActive
                    ? timeFormatter.format(session.expiresAt!)
                    : 'Expired',
              ),
              const SizedBox(height: AppTheme.spacingXs),
            ],
            
            _buildDetailRow(
              context,
              'Video',
              session.videoEnabled ? 'Enabled' : 'Disabled',
            ),
            
            const SizedBox(height: AppTheme.spacingXs),
            
            _buildDetailRow(
              context,
              'Audio',
              session.audioEnabled ? 'Enabled' : 'Disabled',
            ),
            
            const SizedBox(height: AppTheme.spacingXs),
            
            _buildDetailRow(context, 'Quality', session.quality.toUpperCase()),
            
            if (session.isActive) ...[
              const SizedBox(height: AppTheme.spacingM),
              
              // Actions
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  if (isCreator && showCode && onShareCode != null)
                    AppButton(
                      text: 'Share Code',
                      type: ButtonType.secondary,
                      icon: Icons.share,
                      onPressed: onShareCode,
                    ),
                  const SizedBox(width: AppTheme.spacingXs),
                  if (onEndSession != null)
                    AppButton(
                      text: 'End Session',
                      type: ButtonType.danger,
                      icon: Icons.call_end,
                      onPressed: onEndSession,
                    ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
  
  /// Build a detail row
  Widget _buildDetailRow(BuildContext context, String label, String value) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 100,
          child: Text(
            label,
            style: AppTheme.bodySmall.copyWith(
              color: Theme.of(context).brightness == Brightness.dark
                  ? AppTheme.lightGray
                  : AppTheme.brandGray,
            ),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: AppTheme.bodyMedium.copyWith(
              color: Theme.of(context).brightness == Brightness.dark
                  ? AppTheme.brandWhite
                  : AppTheme.brandBlack,
            ),
          ),
        ),
      ],
    );
  }
} 