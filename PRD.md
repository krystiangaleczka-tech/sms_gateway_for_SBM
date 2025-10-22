
Product Overview
SMS Gateway is an Android-based application that enables scheduled SMS messaging through a REST API. The application uses Cloudflare Tunnel to provide secure HTTPS access and implements a queuing system for appointment reminders with precise timing control.
​
Business Objectives
The product aims to provide a reliable, self-hosted SMS gateway solution that allows external systems to schedule SMS messages with precise timing. The system is optimized for appointment reminder use cases where messages need to be sent 24 hours before scheduled appointments.​
Target Users
External systems and applications that require SMS notification capabilities, particularly those managing appointment scheduling and requiring automated reminder functionality.​
Product Scope
In Scope
REST API for SMS scheduling and management
Cloudflare Tunnel integration for secure external access
Message queuing system with precise timing control
SMS status tracking and history
API authentication via Bearer tokens
Automated SMS sending via WorkManager
Database persistence for message queue
Battery optimization considerations
​
Out of Scope
User interface and design elements (already implemented)
Multi-device support
SMS receiving capabilities
Analytics and reporting dashboard
User management system
​
Technical Architecture
Core Components
Server Layer (Ktor):
HTTP server running on port 8080
RESTful API with JSON serialization
Bearer token authentication middleware
Route handlers for SMS operations
​
Database Layer (Room):
SQLite database for message persistence
DAO interface for CRUD operations
Flow-based reactive queries
Entity model for SMS messages
​
Worker Layer (WorkManager):
Scheduled job execution
Delayed task processing
Automatic retry on failure
Battery-optimized background processing
​
Network Layer (Cloudflare Tunnel):
Secure HTTPS access without port forwarding
Zero Trust network access
cloudflared daemon running in Termux
​
Functional Requirements
FR-1: SMS Queue Management
The system must accept SMS scheduling requests via REST API with the following parameters:
Phone number (E.164 format)
Message content (text)
Appointment time (ISO 8601 timestamp)
​
FR-2: Timing Control
The system must implement two-stage timing:
Queue time: 18 hours before appointment
Send time: 24 hours before appointment
WorkManager scheduling at queue time
Actual SMS transmission at send time
​
FR-3: Status Tracking
The system must track message status through the following states:
QUEUED: Initial state after API submission
SCHEDULED: WorkManager job created
SENDING: SMS transmission in progress
SENT: Successfully delivered
FAILED: Transmission error
CANCELLED: Manually cancelled
​
FR-4: API Endpoints
POST /api/v1/sms/queue:
Accepts SmsRequest JSON payload
Returns SmsResponse with message ID
HTTP 201 on success
HTTP 400 on validation error
​
GET /api/v1/sms/status/{id}:
Returns full message object by ID
HTTP 200 with message data
HTTP 404 if not found
​
GET /api/v1/sms/history:
Returns all messages ordered by creation time
HTTP 200 with array of message objects
​
DELETE /api/v1/sms/cancel/{id}:
Updates message status to CANCELLED
Cancels pending WorkManager job
HTTP 200 on success
​
FR-5: Authentication
All API requests must include Bearer token in Authorization header. Invalid or missing tokens must return HTTP 401.​
FR-6: Error Handling
Failed SMS transmission must:
Update message status to FAILED
Store error message in database
Trigger WorkManager retry mechanism
Log error details
​
Non-Functional Requirements
NFR-1: Performance
API response time: < 200ms for queue operations
Database query time: < 50ms
SMS sending latency: < 5 seconds from scheduled time
​
NFR-2: Reliability
Message delivery success rate: > 99%
System uptime: > 99.5%
WorkManager retry attempts: 3 maximum
Database transaction atomicity
​
NFR-3: Scalability
Support for 10,000+ queued messages
Concurrent API requests: up to 50
Database size limit: 500MB
Message retention: 90 days
​
NFR-4: Security
TLS encryption via Cloudflare Tunnel
API key rotation capability
No sensitive data logging
Secure credential storage
​
NFR-5: Battery Optimization
WorkManager instead of foreground service
No continuous background processing
Doze mode compatibility
Battery optimization whitelist support
​
Technical Specifications
Technology Stack
Runtime Environment:
Kotlin 2.0.0
Android SDK 24+ (minimum)
JVM target 17
​
Server Framework:
Ktor 2.3.12 (Netty engine)
Content negotiation plugin
Kotlinx Serialization
​
Database:
Room 2.6.1
SQLite
KSP annotation processing
​
Background Processing:
WorkManager 2.9.0
OneTimeWorkRequest
ExactAlarm scheduling
​
External Tools:
Termux (F-Droid distribution)
cloudflared binary
​
Data Models
SmsMessage Entity:
text
- id: Long (primary key, auto-increment)
- phoneNumber: String
- message: String
- scheduledTime: Long (epoch milliseconds)
- queueTime: Long (epoch milliseconds)
- status: SmsStatus (enum)
- createdAt: Long (epoch milliseconds)
- sentAt: Long? (nullable)
- errorMessage: String? (nullable)
SmsRequest DTO:
text
- phoneNumber: String
- message: String
- appointmentTime: String (ISO 8601)
SmsResponse DTO:
text
- id: Long
- status: String
- message: String
Permissions Required
android.permission.INTERNET
android.permission.SEND_SMS
android.permission.READ_PHONE_STATE
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_DATA_SYNC
android.permission.POST_NOTIFICATIONS (Android 13+)
android.permission.SCHEDULE_EXACT_ALARM (Android 12+)
android.permission.USE_EXACT_ALARM
android.permission.WAKE_LOCK
​
Dependencies and Constraints
External Dependencies
Cloudflare account with Zero Trust access
Domain name for tunnel hostname
F-Droid access for Termux installation
Android device with SMS capability
​
Technical Constraints
Requires Android 7.0 (API 24) minimum
Device must remain powered and connected
Internet connectivity required for tunnel
SIM card with SMS capability required
​
System Constraints
Single Android device deployment
No horizontal scaling support
SMS rate limits from carrier
WorkManager scheduling accuracy depends on Doze mode
​
Assumptions
Android device remains operational 24/7
Stable internet connection availability
Sufficient battery and power management configuration
Cloudflare Tunnel maintains persistent connection
Termux background execution permitted
​
Milestones and Timeline
Phase 1: Core Infrastructure
Database schema implementation
Room DAO and entity setup
Ktor server initialization
Basic routing structure
​
Phase 2: API Implementation
SMS queue endpoint
Status query endpoint
History endpoint
Cancellation endpoint
Authentication middleware
​
Phase 3: Background Processing
WorkManager integration
SMS sending logic
Retry mechanism
Error handling
​
Phase 4: Cloudflare Integration
Tunnel configuration
Termux setup automation
Public hostname configuration
Security hardening
​
Phase 5: Testing and Optimization
Local network testing
Public endpoint testing
Battery optimization verification
Performance profiling
​
Success Metrics
API endpoint availability: > 99.5%
Message delivery success rate: > 99%
Average delivery time accuracy: ± 60 seconds
Battery drain: < 5% per 24 hours
API response time P95: < 300ms
​
Risks and Mitigations
Risk: Android battery optimization kills background processes
Mitigation: Battery whitelist configuration, WorkManager exact alarm scheduling​
Risk: Cloudflare Tunnel disconnection
Mitigation: Automatic reconnection in cloudflared, monitoring endpoint​
Risk: SMS carrier rate limiting
Mitigation: Message queuing with configurable delays, retry logic​
Risk: Database corruption
Mitigation: Room transaction handling, periodic backup capability​
Risk: API key exposure
Mitigation: Secure storage, key rotation support, HTTPS only​
Configuration Requirements
Cloudflare Tunnel Setup
Tunnel name: android-sms-gateway
Public hostname: sms.{domain}
Service target: http://localhost:8080
Token authentication
​
Application Configuration
API Key: User-defined Bearer token
Server port: 8080
Database name: sms_gateway.db
WorkManager constraints: Battery not low, Storage not low
​
Device Configuration
Battery optimization: Disabled for app
Background data: Unrestricted
Autostart: Enabled
Doze mode: Whitelisted
​
APP structure KOTLIN + KTOR
app/
├── src/main/
│   ├── kotlin/
│   │   ├── MainActivity.kt
│   │   ├── server/
│   │   │   ├── KtorServer.kt
│   │   │   ├── routes/
│   │   │   │   ├── SmsRoutes.kt
│   │   │   └── plugins/
│   │   │       └── Routing.kt
│   │   ├── workers/
│   │   │   ├── SmsSchedulerWorker.kt
│   │   │   └── SmsSenderWorker.kt
│   │   ├── database/
│   │   │   ├── AppDatabase.kt
│   │   │   └── SmsDao.kt
│   │   └── models/
│   │       └── SmsMessage.kt
│   └── res/
│       └── layout/
│           └── activity_main.xml
