# CamCheck Mobile API Reference

This document provides a reference for all the mobile API endpoints available in the CamCheck application.

## Base URL

All API endpoints are available at:

```
https://api.camcheck.com
```

Replace this with your actual API domain.

## API Versioning

All mobile endpoints use the `v2` API version:

```
/api/v2/...
```

## Authentication

### Login

Authenticates a user and returns JWT tokens.

**Endpoint:** `POST /api/v2/auth/login`

**Request:**
```json
{
  "username": "string",
  "password": "string",
  "deviceId": "string",
  "deviceName": "string",
  "deviceType": "string", // "ANDROID", "IOS", "WEB"
  "fcmToken": "string"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Authentication successful",
  "data": {
    "auth": {
      "accessToken": "string",
      "refreshToken": "string",
      "tokenType": "Bearer",
      "expiresIn": 3600,
      "username": "string",
      "roles": ["USER", "ADMIN"],
      "userData": {},
      "requiresPasswordChange": false,
      "appConfig": {
        "videoQuality": "medium",
        "backgroundModeEnabled": false
      }
    }
  }
}
```

### Refresh Token

Refreshes an expired JWT token.

**Endpoint:** `POST /api/v2/auth/refresh`

**Request:**
```json
{
  "refreshToken": "string",
  "deviceId": "string"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "string",
    "refreshToken": "string",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

### Logout

Invalidates user tokens.

**Endpoint:** `POST /api/v2/auth/logout`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Logout successful"
}
```

### Validate Token

Validates a JWT token.

**Endpoint:** `GET /api/v2/auth/validate`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Token is valid",
  "data": {
    "username": "string",
    "roles": ["USER", "ADMIN"],
    "valid": true
  }
}
```

## Device Management

### Register Device

Registers a device with the user account.

**Endpoint:** `POST /api/v2/devices/register`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "deviceId": "string",
  "deviceName": "string",
  "deviceType": "string", // "ANDROID", "IOS", "WEB"
  "osVersion": "string",
  "appVersion": "string",
  "fcmToken": "string",
  "pushNotificationsEnabled": true,
  "timezone": "string",
  "deviceSettings": {}
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Device registered successfully"
}
```

### Get Registered Devices

Returns a list of registered devices for the current user.

**Endpoint:** `GET /api/v2/devices`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Devices retrieved",
  "data": {
    "devices": [
      {
        "deviceId": "string",
        "deviceName": "string",
        "deviceType": "string",
        "osVersion": "string",
        "appVersion": "string",
        "lastSeen": "2023-06-15T14:30:00.000Z",
        "pushNotificationsEnabled": true
      }
    ],
    "count": 1
  }
}
```

### Update Device

Updates device information.

**Endpoint:** `PUT /api/v2/devices/{deviceId}`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "deviceName": "string",
  "osVersion": "string",
  "appVersion": "string",
  "pushNotificationsEnabled": true,
  "deviceSettings": {}
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Device updated successfully"
}
```

### Update FCM Token

Updates the Firebase Cloud Messaging token for a device.

**Endpoint:** `PUT /api/v2/devices/{deviceId}/fcm-token`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "fcmToken": "string"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "FCM token updated successfully"
}
```

## Mobile Configuration

### Get Configuration

Gets mobile-specific configuration.

**Endpoint:** `GET /api/v2/config`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Configuration retrieved",
  "data": {
    "apiVersion": "v2",
    "webRtcEnabled": true,
    "maxSessions": 1,
    "backgroundModeEnabled": false,
    "videoQuality": "medium",
    "audioQuality": "medium",
    "pushNotificationsEnabled": true
  }
}
```

### Update Preferences

Updates user preferences.

**Endpoint:** `POST /api/v2/config/preferences`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "videoQuality": "high",
  "audioQuality": "medium",
  "pushNotificationsEnabled": true,
  "dataUsageLimit": true,
  "automaticRecording": false
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Preferences updated",
  "data": {
    // Updated configuration
  }
}
```

### Get Quality Options

Gets available quality options for video and audio.

**Endpoint:** `GET /api/v2/config/quality-options`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Quality options retrieved",
  "data": {
    "video": {
      "low": {
        "resolution": "320x240",
        "frameRate": 15,
        "bitrate": 250000,
        "codec": "h264"
      },
      "medium": {
        "resolution": "640x480",
        "frameRate": 25,
        "bitrate": 800000,
        "codec": "h264"
      },
      "high": {
        "resolution": "1280x720",
        "frameRate": 30,
        "bitrate": 1500000,
        "codec": "h264"
      }
    },
    "audio": {
      "low": {
        "sampleRate": 8000,
        "channels": 1,
        "bitrate": 16000,
        "codec": "opus"
      },
      "medium": {
        "sampleRate": 22050,
        "channels": 1,
        "bitrate": 32000,
        "codec": "opus"
      },
      "high": {
        "sampleRate": 44100,
        "channels": 2,
        "bitrate": 64000,
        "codec": "opus"
      }
    }
  }
}
```

## Session Management

### Create Session

Creates a new camera viewing session.

**Endpoint:** `POST /api/v2/sessions`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "expirationMinutes": 10,
  "deviceId": "string",
  "audioEnabled": true,
  "videoEnabled": true,
  "quality": "medium",
  "recordingMode": "none"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Session created successfully",
  "data": {
    "sessionCode": "123456",
    "expiresAt": "2023-06-15T15:30:00.000Z",
    "expiresIn": 600
  }
}
```

### Join Session

Joins an existing camera viewing session.

**Endpoint:** `POST /api/v2/sessions/{sessionCode}/join`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "sessionCode": "123456",
  "deviceId": "string",
  "audioEnabled": true,
  "videoEnabled": true,
  "quality": "medium"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Session joined successfully",
  "data": {
    "sessionId": "123456",
    "connectedTo": "username",
    "audioEnabled": true,
    "videoEnabled": true,
    "quality": "medium",
    "joinedAt": "2023-06-15T14:30:00.000Z"
  }
}
```

### End Session

Ends an active session.

**Endpoint:** `DELETE /api/v2/sessions/{sessionCode}`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Session ended successfully",
  "data": {
    "sessionCode": "123456",
    "sessionDuration": "00:15:30"
  }
}
```

### Get My Session

Gets the current user's active session.

**Endpoint:** `GET /api/v2/sessions/my-session`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Session retrieved",
  "data": {
    "sessionCode": "123456",
    "role": "creator",
    "connectedTo": "username",
    "audioEnabled": true,
    "videoEnabled": true,
    "quality": "medium",
    "joinedAt": "2023-06-15T14:30:00.000Z"
  }
}
```

## WebRTC

### Get WebRTC Configuration

Gets WebRTC configuration optimized for the client's device and network conditions.

**Endpoint:** `GET /api/v2/webrtc/config`

**Headers:**
```
Authorization: Bearer <token>
```

**Query Parameters:**
```
networkType=wifi|cellular|unknown
batteryLevel=100
deviceType=android|ios|other
```

**Response:**
```json
{
  "status": "success",
  "message": "WebRTC configuration retrieved",
  "data": {
    "iceServers": [
      {
        "urls": "stun:stun.l.google.com:19302"
      }
    ],
    "iceTransportPolicy": "all",
    "bundlePolicy": "balanced",
    "mediaConstraints": {
      "video": {
        "enabled": true,
        "maxWidth": 480,
        "maxHeight": 360,
        "maxFrameRate": 15,
        "powerSavingMode": false
      },
      "audio": {
        "enabled": true,
        "echoCancellation": true,
        "noiseSuppression": true,
        "autoGainControl": true
      }
    },
    "networkType": "wifi",
    "batteryLevel": 100,
    "deviceType": "android"
  }
}
```

### Initialize WebRTC Connection

Initializes a WebRTC connection with another user.

**Endpoint:** `POST /api/v2/webrtc/connect/{receiverId}`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "quality": "medium",
  "audioEnabled": true,
  "videoEnabled": true,
  "networkType": "wifi",
  "batteryLevel": 100,
  "deviceId": "string"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "WebRTC connection initialized",
  "data": {
    "connectionId": "uuid-string",
    "config": {
      // WebRTC configuration
    }
  }
}
```

### Send ICE Candidates

Sends ICE candidates for WebRTC connection.

**Endpoint:** `POST /api/v2/webrtc/candidates/{connectionId}`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "candidate": "string",
  "sdpMid": "string",
  "sdpMLineIndex": 0
}
```

**Response:**
```json
{
  "status": "success",
  "message": "ICE candidates sent successfully"
}
```

### Send Session Description

Sends WebRTC session description (offer or answer).

**Endpoint:** `POST /api/v2/webrtc/sdp/{connectionId}`

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "type": "offer", // or "answer"
  "sdp": "string"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Session description sent successfully"
}
```

### Get Connection Status

Gets WebRTC connection status.

**Endpoint:** `GET /api/v2/webrtc/status/{connectionId}`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "Connection status retrieved",
  "data": {
    "connectionId": "uuid-string",
    "status": "CONNECTED", // INITIALIZING, OFFER_CREATED, ANSWER_CREATED, ICE_GATHERING, CONNECTED, FAILED, CLOSED
    "timestamp": 1686839400000
  }
}
```

### End WebRTC Connection

Ends an active WebRTC connection.

**Endpoint:** `DELETE /api/v2/webrtc/{connectionId}`

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "status": "success",
  "message": "WebRTC connection ended successfully"
}
```

## Error Handling

All API endpoints return standard error responses:

```json
{
  "status": "error",
  "message": "Error message",
  "data": {} // Optional additional error data
}
```

HTTP status codes:
- 200: Success
- 400: Bad request
- 401: Unauthorized
- 403: Forbidden
- 404: Not found
- 409: Conflict
- 500: Server error

## Rate Limiting

API requests are rate limited:
- 60 requests per minute for authenticated users
- 10 requests per minute for unauthenticated users

Rate limit headers are included in all responses:
```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 59
X-RateLimit-Reset: 1686840000
``` 