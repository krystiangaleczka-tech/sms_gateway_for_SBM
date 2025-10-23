# SMS Gateway - Security API Documentation

## Overview

This document describes the Security API endpoints for SMS Gateway, including authentication, authorization, rate limiting, and audit functionality.

## Base URL

```
http://localhost:8080/api/security
```

## Authentication

All API requests must include an API token in the Authorization header:

```
Authorization: Bearer YOUR_TOKEN_HERE
```

## Response Format

All responses follow this format:

```json
{
    "success": true,
    "data": {},
    "message": "Operation completed successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

Error responses:

```json
{
    "success": false,
    "error": {
        "code": "ERROR_CODE",
        "message": "Error description",
        "details": {}
    },
    "timestamp": "2023-01-01T12:00:00Z"
}
```

## API Endpoints

### Token Management

#### Create Token

Creates a new API token with specified permissions.

**Endpoint:** `POST /tokens`

**Request Body:**
```json
{
    "name": "Production API Token",
    "permissions": ["sms:send", "sms:view"],
    "expirationDays": 90
}
```

**Response:**
```json
{
    "success": true,
    "data": {
        "id": "token-uuid",
        "name": "Production API Token",
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "permissions": ["sms:send", "sms:view"],
        "expiresAt": "2023-04-01T12:00:00Z",
        "createdAt": "2023-01-01T12:00:00Z"
    },
    "message": "Token created successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `INVALID_PERMISSIONS`: One or more permissions are invalid
- `INVALID_EXPIRATION`: Expiration days must be between 1 and 365
- `TOKEN_LIMIT_EXCEEDED`: Maximum number of tokens reached

#### Get All Tokens

Retrieves all API tokens for the authenticated user.

**Endpoint:** `GET /tokens`

**Query Parameters:**
- `page` (optional): Page number (default: 1)
- `limit` (optional): Items per page (default: 20, max: 100)
- `status` (optional): Filter by status (ACTIVE, EXPIRED, REVOKED)

**Response:**
```json
{
    "success": true,
    "data": {
        "tokens": [
            {
                "id": "token-uuid",
                "name": "Production API Token",
                "permissions": ["sms:send", "sms:view"],
                "status": "ACTIVE",
                "expiresAt": "2023-04-01T12:00:00Z",
                "lastUsedAt": "2023-01-01T12:00:00Z",
                "createdAt": "2023-01-01T12:00:00Z"
            }
        ],
        "pagination": {
            "page": 1,
            "limit": 20,
            "total": 1,
            "totalPages": 1
        }
    },
    "message": "Tokens retrieved successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

#### Get Token by ID

Retrieves a specific API token by ID.

**Endpoint:** `GET /tokens/{tokenId}`

**Response:**
```json
{
    "success": true,
    "data": {
        "id": "token-uuid",
        "name": "Production API Token",
        "permissions": ["sms:send", "sms:view"],
        "status": "ACTIVE",
        "expiresAt": "2023-04-01T12:00:00Z",
        "lastUsedAt": "2023-01-01T12:00:00Z",
        "createdAt": "2023-01-01T12:00:00Z"
    },
    "message": "Token retrieved successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `TOKEN_NOT_FOUND`: Token with specified ID does not exist

#### Revoke Token

Revokes an API token, making it invalid.

**Endpoint:** `DELETE /tokens/{tokenId}`

**Response:**
```json
{
    "success": true,
    "data": null,
    "message": "Token revoked successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `TOKEN_NOT_FOUND`: Token with specified ID does not exist
- `TOKEN_ALREADY_REVOKED`: Token is already revoked

### Tunnel Management

#### Create Tunnel

Creates a new Cloudflare tunnel configuration.

**Endpoint:** `POST /tunnels`

**Request Body:**
```json
{
    "name": "Production Tunnel",
    "tunnelType": "HTTPS",
    "localPort": 8080,
    "subdomain": "api"
}
```

**Response:**
```json
{
    "success": true,
    "data": {
        "id": "tunnel-uuid",
        "name": "Production Tunnel",
        "tunnelType": "HTTPS",
        "localPort": 8080,
        "subdomain": "api",
        "status": "INACTIVE",
        "url": "https://api.yourdomain.com",
        "createdAt": "2023-01-01T12:00:00Z"
    },
    "message": "Tunnel created successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `INVALID_TUNNEL_TYPE`: Tunnel type must be HTTP or HTTPS
- `INVALID_PORT`: Port must be between 1 and 65535
- `SUBDOMAIN_IN_USE`: Subdomain is already in use

#### Get All Tunnels

Retrieves all tunnel configurations.

**Endpoint:** `GET /tunnels`

**Query Parameters:**
- `page` (optional): Page number (default: 1)
- `limit` (optional): Items per page (default: 20, max: 100)
- `status` (optional): Filter by status (ACTIVE, INACTIVE, ERROR)

**Response:**
```json
{
    "success": true,
    "data": {
        "tunnels": [
            {
                "id": "tunnel-uuid",
                "name": "Production Tunnel",
                "tunnelType": "HTTPS",
                "localPort": 8080,
                "subdomain": "api",
                "status": "ACTIVE",
                "url": "https://api.yourdomain.com",
                "createdAt": "2023-01-01T12:00:00Z"
            }
        ],
        "pagination": {
            "page": 1,
            "limit": 20,
            "total": 1,
            "totalPages": 1
        }
    },
    "message": "Tunnels retrieved successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

#### Start Tunnel

Starts a tunnel.

**Endpoint:** `POST /tunnels/{tunnelId}/start`

**Response:**
```json
{
    "success": true,
    "data": {
        "id": "tunnel-uuid",
        "status": "ACTIVE",
        "url": "https://api.yourdomain.com",
        "startedAt": "2023-01-01T12:00:00Z"
    },
    "message": "Tunnel started successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `TUNNEL_NOT_FOUND`: Tunnel with specified ID does not exist
- `TUNNEL_ALREADY_ACTIVE`: Tunnel is already active
- `TUNNEL_START_FAILED`: Failed to start tunnel

#### Stop Tunnel

Stops a tunnel.

**Endpoint:** `POST /tunnels/{tunnelId}/stop`

**Response:**
```json
{
    "success": true,
    "data": {
        "id": "tunnel-uuid",
        "status": "INACTIVE",
        "stoppedAt": "2023-01-01T12:00:00Z"
    },
    "message": "Tunnel stopped successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `TUNNEL_NOT_FOUND`: Tunnel with specified ID does not exist
- `TUNNEL_ALREADY_INACTIVE`: Tunnel is already inactive
- `TUNNEL_STOP_FAILED`: Failed to stop tunnel

#### Delete Tunnel

Deletes a tunnel configuration.

**Endpoint:** `DELETE /tunnels/{tunnelId}`

**Response:**
```json
{
    "success": true,
    "data": null,
    "message": "Tunnel deleted successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `TUNNEL_NOT_FOUND`: Tunnel with specified ID does not exist
- `TUNNEL_ACTIVE`: Cannot delete an active tunnel

### Rate Limiting

#### Get Rate Limits

Retrieves current rate limit settings.

**Endpoint:** `GET /rate-limits`

**Query Parameters:**
- `identifier` (optional): Filter by identifier (user ID, IP address, etc.)
- `limitType` (optional): Filter by limit type (API_REQUESTS, SMS_SEND, etc.)

**Response:**
```json
{
    "success": true,
    "data": {
        "rateLimits": [
            {
                "id": "rate-limit-uuid",
                "identifier": "user123",
                "limitType": "API_REQUESTS",
                "windowSizeMinutes": 60,
                "maxRequests": 100,
                "currentRequests": 45,
                "resetTime": "2023-01-01T13:00:00Z",
                "createdAt": "2023-01-01T12:00:00Z"
            }
        ]
    },
    "message": "Rate limits retrieved successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

#### Create/Update Rate Limit

Creates or updates a rate limit.

**Endpoint:** `POST /rate-limits`

**Request Body:**
```json
{
    "identifier": "user123",
    "limitType": "API_REQUESTS",
    "windowSizeMinutes": 60,
    "maxRequests": 200
}
```

**Response:**
```json
{
    "success": true,
    "data": {
        "id": "rate-limit-uuid",
        "identifier": "user123",
        "limitType": "API_REQUESTS",
        "windowSizeMinutes": 60,
        "maxRequests": 200,
        "currentRequests": 0,
        "resetTime": "2023-01-01T13:00:00Z",
        "createdAt": "2023-01-01T12:00:00Z"
    },
    "message": "Rate limit created/updated successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `INVALID_LIMIT_TYPE`: Limit type is not valid
- `INVALID_WINDOW_SIZE`: Window size must be between 1 and 1440 minutes
- `INVALID_MAX_REQUESTS`: Max requests must be between 1 and 10000

#### Delete Rate Limit

Deletes a rate limit.

**Endpoint:** `DELETE /rate-limits/{rateLimitId}`

**Response:**
```json
{
    "success": true,
    "data": null,
    "message": "Rate limit deleted successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `RATE_LIMIT_NOT_FOUND`: Rate limit with specified ID does not exist

### Security Events

#### Get Security Events

Retrieves security audit events.

**Endpoint:** `GET /events`

**Query Parameters:**
- `userId` (optional): Filter by user ID
- `ipAddress` (optional): Filter by IP address
- `eventType` (optional): Filter by event type
- `startTime` (optional): Filter by start time (ISO 8601)
- `endTime` (optional): Filter by end time (ISO 8601)
- `page` (optional): Page number (default: 1)
- `limit` (optional): Items per page (default: 50, max: 500)

**Response:**
```json
{
    "success": true,
    "data": {
        "events": [
            {
                "id": "event-uuid",
                "userId": "user123",
                "eventType": "LOGIN_SUCCESS",
                "ipAddress": "192.168.1.1",
                "userAgent": "Mozilla/5.0...",
                "details": {
                    "tokenId": "token-uuid"
                },
                "timestamp": "2023-01-01T12:00:00Z"
            }
        ],
        "pagination": {
            "page": 1,
            "limit": 50,
            "total": 1,
            "totalPages": 1
        }
    },
    "message": "Security events retrieved successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

#### Get Event by ID

Retrieves a specific security event by ID.

**Endpoint:** `GET /events/{eventId}`

**Response:**
```json
{
    "success": true,
    "data": {
        "id": "event-uuid",
        "userId": "user123",
        "eventType": "LOGIN_SUCCESS",
        "ipAddress": "192.168.1.1",
        "userAgent": "Mozilla/5.0...",
        "details": {
            "tokenId": "token-uuid"
        },
        "timestamp": "2023-01-01T12:00:00Z"
    },
    "message": "Security event retrieved successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `EVENT_NOT_FOUND`: Event with specified ID does not exist

#### Cleanup Old Events

Deletes security events older than specified number of days.

**Endpoint:** `DELETE /events/cleanup`

**Query Parameters:**
- `olderThanDays` (required): Delete events older than this many days

**Response:**
```json
{
    "success": true,
    "data": {
        "deletedCount": 150
    },
    "message": "Old security events cleaned up successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

**Error Codes:**
- `INVALID_DAYS`: Days must be between 1 and 365

### Security Status

#### Get Security Status

Retrieves overall security status and statistics.

**Endpoint:** `GET /status`

**Response:**
```json
{
    "success": true,
    "data": {
        "tokens": {
            "total": 5,
            "active": 3,
            "expired": 1,
            "revoked": 1
        },
        "tunnels": {
            "total": 2,
            "active": 1,
            "inactive": 1
        },
        "rateLimits": {
            "total": 10,
            "active": 8
        },
        "events": {
            "last24Hours": 150,
            "last7Days": 1050,
            "last30Days": 4500
        },
        "securityScore": 85
    },
    "message": "Security status retrieved successfully",
    "timestamp": "2023-01-01T12:00:00Z"
}
```

## Error Codes Reference

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `UNAUTHORIZED` | 401 | Invalid or missing authentication token |
| `FORBIDDEN` | 403 | Insufficient permissions for the requested operation |
| `TOKEN_NOT_FOUND` | 404 | Token with specified ID does not exist |
| `TUNNEL_NOT_FOUND` | 404 | Tunnel with specified ID does not exist |
| `RATE_LIMIT_NOT_FOUND` | 404 | Rate limit with specified ID does not exist |
| `EVENT_NOT_FOUND` | 404 | Event with specified ID does not exist |
| `INVALID_PERMISSIONS` | 400 | One or more permissions are invalid |
| `INVALID_EXPIRATION` | 400 | Expiration days must be between 1 and 365 |
| `INVALID_TUNNEL_TYPE` | 400 | Tunnel type must be HTTP or HTTPS |
| `INVALID_PORT` | 400 | Port must be between 1 and 65535 |
| `INVALID_LIMIT_TYPE` | 400 | Limit type is not valid |
| `INVALID_WINDOW_SIZE` | 400 | Window size must be between 1 and 1440 minutes |
| `INVALID_MAX_REQUESTS` | 400 | Max requests must be between 1 and 10000 |
| `INVALID_DAYS` | 400 | Days must be between 1 and 365 |
| `TOKEN_LIMIT_EXCEEDED` | 429 | Maximum number of tokens reached |
| `SUBDOMAIN_IN_USE` | 409 | Subdomain is already in use |
| `TOKEN_ALREADY_REVOKED` | 409 | Token is already revoked |
| `TUNNEL_ALREADY_ACTIVE` | 409 | Tunnel is already active |
| `TUNNEL_ALREADY_INACTIVE` | 409 | Tunnel is already inactive |
| `TUNNEL_ACTIVE` | 409 | Cannot delete an active tunnel |
| `TUNNEL_START_FAILED` | 500 | Failed to start tunnel |
| `TUNNEL_STOP_FAILED` | 500 | Failed to stop tunnel |
| `INTERNAL_ERROR` | 500 | Internal server error |

## Rate Limiting

The Security API is subject to rate limiting:

| Endpoint | Limit | Window |
|----------|-------|--------|
| Token operations | 10 requests | 1 minute |
| Tunnel operations | 5 requests | 1 minute |
| Rate limit operations | 20 requests | 1 minute |
| Event queries | 50 requests | 1 minute |
| Status queries | 30 requests | 1 minute |

When rate limits are exceeded, the API returns HTTP 429 with these headers:

```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1640995200
X-RateLimit-Retry-After: 60
```

## SDK Examples

### Kotlin

```kotlin
// Create token
val tokenRequest = CreateTokenRequest(
    name = "Production API Token",
    permissions = listOf("sms:send", "sms:view"),
    expirationDays = 90
)

val response = apiService.createToken(tokenRequest)
val token = response.data.token

// Use token for API calls
val smsResponse = smsApiService.getSmsHistory(
    authorization = "Bearer $token"
)
```

### JavaScript

```javascript
// Create token
const tokenRequest = {
    name: "Production API Token",
    permissions: ["sms:send", "sms:view"],
    expirationDays: 90
};

const response = await fetch('/api/security/tokens', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${existingToken}`
    },
    body: JSON.stringify(tokenRequest)
});

const { data } = await response.json();
const token = data.token;

// Use token for API calls
const smsResponse = await fetch('/api/sms', {
    headers: {
        'Authorization': `Bearer ${token}`
    }
});
```

### Python

```python
import requests

# Create token
token_request = {
    "name": "Production API Token",
    "permissions": ["sms:send", "sms:view"],
    "expirationDays": 90
}

response = requests.post(
    'http://localhost:8080/api/security/tokens',
    json=token_request,
    headers={'Authorization': f'Bearer {existing_token}'}
)

data = response.json()
token = data['data']['token']

# Use token for API calls
sms_response = requests.get(
    'http://localhost:8080/api/sms',
    headers={'Authorization': f'Bearer {token}'}
)
```

## WebSocket API

For real-time security event notifications, you can connect to the WebSocket endpoint:

```
ws://localhost:8080/api/security/events/ws
```

### Authentication

WebSocket connections must include the token in the query parameter:

```
ws://localhost:8080/api/security/events/ws?token=YOUR_TOKEN_HERE
```

### Message Format

```json
{
    "type": "SECURITY_EVENT",
    "data": {
        "id": "event-uuid",
        "userId": "user123",
        "eventType": "LOGIN_SUCCESS",
        "ipAddress": "192.168.1.1",
        "timestamp": "2023-01-01T12:00:00Z"
    }
}
```

## Changelog

### Version 1.0.0
- Initial release
- Token management API
- Tunnel management API
- Rate limiting API
- Security events API
- WebSocket notifications