import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';

import 'api_client.dart';

/// WebRTC service for managing WebRTC connections
class WebRTCService extends ChangeNotifier {
  final ApiClient _apiClient = ApiClient();
  
  // WebRTC connection state
  RTCPeerConnection? _peerConnection;
  MediaStream? _localStream;
  MediaStream? _remoteStream;
  String? _connectionId;
  String? _receiverId;
  bool _isInitiator = false;
  bool _isConnected = false;
  bool _isLoading = false;
  String? _error;
  
  // Connection settings
  bool _audioEnabled = true;
  bool _videoEnabled = true;
  String _quality = 'medium';
  
  // Status polling timer
  Timer? _statusTimer;
  
  // Connection status
  String _connectionStatus = 'disconnected';
  
  /// Get local stream
  MediaStream? get localStream => _localStream;
  
  /// Get remote stream
  MediaStream? get remoteStream => _remoteStream;
  
  /// Check if connection is established
  bool get isConnected => _isConnected;
  
  /// Check if service is loading
  bool get isLoading => _isLoading;
  
  /// Get error message
  String? get error => _error;
  
  /// Get connection status
  String get connectionStatus => _connectionStatus;
  
  /// Check if audio is enabled
  bool get audioEnabled => _audioEnabled;
  
  /// Check if video is enabled
  bool get videoEnabled => _videoEnabled;
  
  /// Get quality setting
  String get quality => _quality;
  
  /// Initialize WebRTC service
  Future<void> initialize() async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    
    try {
      // Nothing to do here yet
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
  
  /// Initialize local stream
  Future<MediaStream?> initLocalStream({
    bool audio = true,
    bool video = true,
  }) async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    
    try {
      // Stop any existing stream
      await stopLocalStream();
      
      // Update settings
      _audioEnabled = audio;
      _videoEnabled = video;
      
      // Get WebRTC configuration
      final config = await _apiClient.getWebRTCConfig();
      final mediaConstraints = config['mediaConstraints'] as Map<String, dynamic>;
      
      // Create media constraints
      final constraints = {
        'audio': audio ? mediaConstraints['audio'] : false,
        'video': video ? mediaConstraints['video'] : false,
      };
      
      // Get user media
      _localStream = await navigator.mediaDevices.getUserMedia(constraints);
      
      notifyListeners();
      return _localStream;
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      return null;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
  
  /// Stop local stream
  Future<void> stopLocalStream() async {
    if (_localStream != null) {
      await _localStream!.dispose();
      _localStream = null;
    }
    notifyListeners();
  }
  
  /// Initialize WebRTC connection as initiator
  Future<bool> initConnection(String receiverId) async {
    _isLoading = true;
    _error = null;
    _isInitiator = true;
    _receiverId = receiverId;
    notifyListeners();
    
    try {
      // Initialize local stream if not already done
      if (_localStream == null) {
        await initLocalStream(
          audio: _audioEnabled,
          video: _videoEnabled,
        );
      }
      
      // Initialize WebRTC connection
      final response = await _apiClient.initializeWebRTCConnection(
        receiverId,
        audioEnabled: _audioEnabled,
        videoEnabled: _videoEnabled,
        quality: _quality,
      );
      
      if (response != null) {
        _connectionId = response['connectionId'] as String;
        final config = response['config'] as Map<String, dynamic>;
        
        // Create peer connection
        await _createPeerConnection(config);
        
        // Create offer
        await _createOffer();
        
        // Start polling for connection status
        _startStatusPolling();
        
        return true;
      } else {
        _error = 'Failed to initialize WebRTC connection';
        return false;
      }
    } catch (e) {
      _error = e.toString();
      return false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
  
  /// Accept WebRTC connection as receiver
  Future<bool> acceptConnection(String connectionId, String senderId) async {
    _isLoading = true;
    _error = null;
    _isInitiator = false;
    _connectionId = connectionId;
    _receiverId = senderId;
    notifyListeners();
    
    try {
      // Initialize local stream if not already done
      if (_localStream == null) {
        await initLocalStream(
          audio: _audioEnabled,
          video: _videoEnabled,
        );
      }
      
      // Get WebRTC configuration
      final config = await _apiClient.getWebRTCConfig();
      
      // Create peer connection
      await _createPeerConnection(config);
      
      // Start polling for connection status
      _startStatusPolling();
      
      return true;
    } catch (e) {
      _error = e.toString();
      return false;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
  
  /// Create peer connection
  Future<void> _createPeerConnection(Map<String, dynamic> config) async {
    // Create RTCPeerConnection
    _peerConnection = await createPeerConnection({
      'iceServers': config['iceServers'],
      'iceTransportPolicy': config['iceTransportPolicy'] ?? 'all',
      'bundlePolicy': config['bundlePolicy'] ?? 'balanced',
    });
    
    // Add local stream tracks to peer connection
    if (_localStream != null) {
      for (final track in _localStream!.getTracks()) {
        await _peerConnection!.addTrack(track, _localStream!);
      }
    }
    
    // Set up event listeners
    _peerConnection!.onIceCandidate = _onIceCandidate;
    _peerConnection!.onTrack = _onTrack;
    _peerConnection!.onConnectionState = _onConnectionState;
    _peerConnection!.onIceConnectionState = _onIceConnectionState;
    
    // Create remote stream
    _remoteStream = await createLocalMediaStream('remote-stream');
  }
  
  /// Create and send offer
  Future<void> _createOffer() async {
    if (_peerConnection == null) return;
    
    try {
      // Create offer
      final offer = await _peerConnection!.createOffer();
      await _peerConnection!.setLocalDescription(offer);
      
      // Send offer to server
      await _apiClient.sendSessionDescription(_connectionId!, {
        'type': 'offer',
        'sdp': offer.sdp,
      });
    } catch (e) {
      _error = 'Error creating offer: $e';
      notifyListeners();
    }
  }
  
  /// Process received offer
  Future<void> processOffer(Map<String, dynamic> offerData) async {
    if (_peerConnection == null) return;
    
    try {
      // Set remote description
      final offer = RTCSessionDescription(
        offerData['sdp'] as String,
        'offer',
      );
      await _peerConnection!.setRemoteDescription(offer);
      
      // Create answer
      final answer = await _peerConnection!.createAnswer();
      await _peerConnection!.setLocalDescription(answer);
      
      // Send answer to server
      await _apiClient.sendSessionDescription(_connectionId!, {
        'type': 'answer',
        'sdp': answer.sdp,
      });
    } catch (e) {
      _error = 'Error processing offer: $e';
      notifyListeners();
    }
  }
  
  /// Process received answer
  Future<void> processAnswer(Map<String, dynamic> answerData) async {
    if (_peerConnection == null) return;
    
    try {
      // Set remote description
      final answer = RTCSessionDescription(
        answerData['sdp'] as String,
        'answer',
      );
      await _peerConnection!.setRemoteDescription(answer);
    } catch (e) {
      _error = 'Error processing answer: $e';
      notifyListeners();
    }
  }
  
  /// Process received ICE candidate
  Future<void> processIceCandidate(Map<String, dynamic> candidateData) async {
    if (_peerConnection == null) return;
    
    try {
      final candidate = RTCIceCandidate(
        candidateData['candidate'] as String,
        candidateData['sdpMid'] as String,
        candidateData['sdpMLineIndex'] as int,
      );
      await _peerConnection!.addCandidate(candidate);
    } catch (e) {
      _error = 'Error processing ICE candidate: $e';
      notifyListeners();
    }
  }
  
  /// Handle ICE candidate event
  void _onIceCandidate(RTCIceCandidate candidate) {
    if (_connectionId == null) return;
    
    _apiClient.sendIceCandidates(_connectionId!, {
      'candidate': candidate.candidate,
      'sdpMid': candidate.sdpMid,
      'sdpMLineIndex': candidate.sdpMLineIndex,
    });
  }
  
  /// Handle track event
  void _onTrack(RTCTrackEvent event) {
    if (_remoteStream != null) {
      _remoteStream!.addTrack(event.track);
      notifyListeners();
    }
  }
  
  /// Handle connection state change
  void _onConnectionState(RTCPeerConnectionState state) {
    debugPrint('Connection state: $state');
    
    switch (state) {
      case RTCPeerConnectionState.RTCPeerConnectionStateConnected:
        _isConnected = true;
        _connectionStatus = 'connected';
        break;
      case RTCPeerConnectionState.RTCPeerConnectionStateFailed:
        _isConnected = false;
        _connectionStatus = 'failed';
        _error = 'Connection failed';
        break;
      case RTCPeerConnectionState.RTCPeerConnectionStateClosed:
        _isConnected = false;
        _connectionStatus = 'closed';
        break;
      default:
        _isConnected = false;
        _connectionStatus = state.toString().split('.').last.toLowerCase();
    }
    
    notifyListeners();
  }
  
  /// Handle ICE connection state change
  void _onIceConnectionState(RTCIceConnectionState state) {
    debugPrint('ICE connection state: $state');
  }
  
  /// Start polling for connection status
  void _startStatusPolling() {
    _statusTimer?.cancel();
    _statusTimer = Timer.periodic(const Duration(seconds: 5), (timer) async {
      if (_connectionId == null) {
        timer.cancel();
        return;
      }
      
      try {
        final status = await _apiClient.getConnectionStatus(_connectionId!);
        if (status != null) {
          _connectionStatus = status['status'] as String;
          notifyListeners();
        }
      } catch (e) {
        debugPrint('Error polling connection status: $e');
      }
    });
  }
  
  /// Toggle audio
  Future<void> toggleAudio() async {
    if (_localStream == null) return;
    
    _audioEnabled = !_audioEnabled;
    
    for (final track in _localStream!.getAudioTracks()) {
      track.enabled = _audioEnabled;
    }
    
    notifyListeners();
  }
  
  /// Toggle video
  Future<void> toggleVideo() async {
    if (_localStream == null) return;
    
    _videoEnabled = !_videoEnabled;
    
    for (final track in _localStream!.getVideoTracks()) {
      track.enabled = _videoEnabled;
    }
    
    notifyListeners();
  }
  
  /// End WebRTC connection
  Future<void> endConnection() async {
    _isLoading = true;
    notifyListeners();
    
    try {
      // Stop status polling
      _statusTimer?.cancel();
      _statusTimer = null;
      
      // End WebRTC connection on server
      if (_connectionId != null) {
        await _apiClient.endWebRTCConnection(_connectionId!);
      }
      
      // Close peer connection
      if (_peerConnection != null) {
        await _peerConnection!.close();
        _peerConnection = null;
      }
      
      // Stop local stream
      await stopLocalStream();
      
      // Dispose remote stream
      if (_remoteStream != null) {
        await _remoteStream!.dispose();
        _remoteStream = null;
      }
      
      // Reset state
      _isConnected = false;
      _connectionId = null;
      _receiverId = null;
      _isInitiator = false;
      _connectionStatus = 'disconnected';
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
  
  /// Clear error
  void clearError() {
    _error = null;
    notifyListeners();
  }
  
  /// Dispose resources
  @override
  void dispose() {
    endConnection();
    super.dispose();
  }
} 