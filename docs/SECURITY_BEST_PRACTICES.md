# SMS Gateway - Security Best Practices Guide

## Overview

This guide provides comprehensive security best practices for SMS Gateway, covering authentication, authorization, network security, data protection, and operational security.

## Table of Contents

1. [Authentication & Authorization](#authentication--authorization)
2. [Network Security](#network-security)
3. [Data Protection](#data-protection)
4. [API Security](#api-security)
5. [Operational Security](#operational-security)
6. [Incident Response](#incident-response)
7. [Compliance](#compliance)
8. [Security Checklist](#security-checklist)

## Authentication & Authorization

### API Token Management

#### Principle of Least Privilege

```kotlin
// ❌ BAD: Granting excessive permissions
val token = ApiToken(
    permissions = listOf("sms:*", "security:*", "system:*")
)

// ✅ GOOD: Granting minimal required permissions
val token = ApiToken(
    permissions = listOf("sms:send", "sms:view")
)
```

#### Token Lifecycle Management

1. **Short Expiration Times**
   ```kotlin
   // Production tokens: 30-90 days
   // Development tokens: 7 days
   // Emergency tokens: 24 hours
   ```

2. **Regular Rotation**
   ```bash
   # Automated rotation script
   #!/bin/bash
   # Rotate tokens older than 60 days
   curl -X POST "https://api.example.com/security/tokens/rotate" \
        -H "Authorization: Bearer $ADMIN_TOKEN"
   ```

3. **Secure Storage**
   ```kotlin
   // Store tokens in Android Keystore
   val masterKey = MasterKey.Builder(context)
       .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
       .build()
   
   val encryptedPrefs = EncryptedSharedPreferences.create(
       context,
       "secure_prefs",
       masterKey,
       EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
       EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
   )
   ```

### Multi-Factor Authentication (MFA)

#### Implementing TOTP

```kotlin
class TotpService {
    fun generateSecret(): String {
        return GoogleAuthenticator.generateSecret()
    }
    
    fun verifyCode(secret: String, code: String): Boolean {
        val authenticator = GoogleAuthenticator()
        return authenticator.authorize(secret, code)
    }
}
```

#### Backup Codes

```kotlin
data class BackupCodes(
    val codes: List<String>,
    val generatedAt: Instant,
    val usedCodes: Set<String> = emptySet()
)

fun generateBackupCodes(count: Int = 10): BackupCodes {
    val codes = (1..count).map { 
        RandomStringUtils.randomNumeric(8) 
    }
    return BackupCodes(codes, Instant.now())
}
```

## Network Security

### Cloudflare Tunnel Configuration

#### Access Policies

```yaml
# Cloudflare Zero Trust configuration
access_policies:
  - name: "Corporate Access"
    include:
      - email_domain: "company.com"
      - ip: "192.168.1.0/24"
    require:
      - mfa: true
      
  - name: "Emergency Access"
    include:
      - email: "admin@company.com"
    require:
      - mfa: true
      - hardware_key: true
```

#### IP Restrictions

```kotlin
// IP whitelist configuration
class IpWhitelist {
    private val allowedRanges = listOf(
        "192.168.1.0/24",
        "10.0.0.0/8",
        "203.0.113.0/24"
    )
    
    fun isAllowed(ip: String): Boolean {
        return allowedRanges.any { range ->
            IpAddressMatcher(range).matches(ip)
        }
    }
}
```

### SSL/TLS Configuration

#### Certificate Management

```kotlin
// SSL configuration for Ktor
install(Https) {
    keyStore = KeyStore.getInstance("PKCS12").apply {
        load(File("keystore.p12").inputStream(), "password".toCharArray())
    }
    keyAlias = "sms-gateway"
    keyStorePassword = "password"
    privateKeyPassword = "password"
}
```

#### HSTS Headers

```kotlin
install(StatusPages) {
    install(HSTS) {
        includeSubDomains = true
        maxAgeInSeconds = 31536000 // 1 year
        preload = true
    }
}
```

## Data Protection

### Encryption at Rest

#### Database Encryption

```kotlin
// Room database encryption
val database = Room.databaseBuilder(
    context,
    AppDatabase::class.java,
    "sms-gateway"
).openHelperFactory(
    SupportFactory(
        SQLiteDatabase.getBytes("passphrase".toCharArray())
    )
).build()
```

#### File Encryption

```kotlin
class FileEncryptionService {
    fun encryptFile(inputFile: File, outputFile: File, key: SecretKey) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(inputFile.readBytes())
        
        // Write IV + encrypted data
        outputFile.writeBytes(iv + encryptedData)
    }
}
```

### Encryption in Transit

#### API Communication

```kotlin
// Enforce HTTPS
val client = HttpClient {
    install(JsonFeature) {
        serializer = KotlinxSerializer()
    }
    
    defaultRequest {
        url.protocol = URLProtocol.HTTPS
    }
}
```

#### End-to-End Encryption

```kotlin
class MessageEncryption {
    private val keyPair = generateKeyPair()
    
    fun encryptMessage(message: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance("RSA/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.encodeToString(cipher.doFinal(message.toByteArray()), Base64.DEFAULT)
    }
    
    fun decryptMessage(encryptedMessage: String): String {
        val cipher = Cipher.getInstance("RSA/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        val decryptedBytes = cipher.doFinal(Base64.decode(encryptedMessage, Base64.DEFAULT))
        return String(decryptedBytes)
    }
}
```

## API Security

### Rate Limiting

#### Adaptive Rate Limiting

```kotlin
class AdaptiveRateLimiter {
    private val baseLimits = mapOf(
        "sms:send" to RateLimit(50, Duration.ofHours(1)),
        "sms:view" to RateLimit(1000, Duration.ofHours(1)),
        "security:manage" to RateLimit(10, Duration.ofHours(1))
    )
    
    fun checkLimit(userId: String, action: String): Boolean {
        val baseLimit = baseLimits[action] ?: return false
        
        // Adjust based on user trust score
        val trustScore = calculateTrustScore(userId)
        val adjustedLimit = (baseLimit.maxRequests * trustScore).toInt()
        
        return currentUsage(userId, action) < adjustedLimit
    }
    
    private fun calculateTrustScore(userId: String): Double {
        // Calculate based on:
        // - Account age
        // - Historical behavior
        // - Verification status
        // - Security incidents
        return when {
            isVerifiedUser(userId) -> 1.0
            isTrustedLocation(userId) -> 0.8
            isNewUser(userId) -> 0.5
            else -> 0.3
        }
    }
}
```

### Input Validation

#### Request Validation

```kotlin
class SmsRequestValidator {
    fun validate(request: SmsRequest): ValidationResult {
        return when {
            request.phoneNumber.isBlank() -> 
                ValidationResult.Error("Phone number is required")
            !isValidPhoneNumber(request.phoneNumber) -> 
                ValidationResult.Error("Invalid phone number format")
            request.message.isBlank() -> 
                ValidationResult.Error("Message is required")
            request.message.length > 160 -> 
                ValidationResult.Error("Message too long")
            containsSuspiciousContent(request.message) -> 
                ValidationResult.Error("Message contains suspicious content")
            else -> ValidationResult.Success
        }
    }
    
    private fun containsSuspiciousContent(message: String): Boolean {
        val suspiciousPatterns = listOf(
            Regex(?i)(click here|urgent|verify now|winner|congratulations)),
            Regex(?i)(\b\d{4}\s*\d{4}\s*\d{4}\s*\d{4}\b)), // Credit card pattern
            Regex(?i)(http[s]?://\S+)) // URL pattern
        )
        
        return suspiciousPatterns.any { it.containsMatchIn(message) }
    }
}
```

### SQL Injection Prevention

#### Parameterized Queries

```kotlin
// ❌ BAD: String concatenation
fun getSmsByPhoneUnsafe(phone: String): List<SmsMessage> {
    val query = "SELECT * FROM sms_messages WHERE phone = '$phone'"
    return database.rawQuery(query, null).use { cursor ->
        // Parse results
    }
}

// ✅ GOOD: Parameterized query
fun getSmsByPhoneSafe(phone: String): List<SmsMessage> {
    return smsDao.findByPhone(phone)
}
```

## Operational Security

### Logging and Monitoring

#### Security Event Logging

```kotlin
class SecurityLogger {
    fun logSecurityEvent(
        userId: String,
        eventType: SecurityEventType,
        details: Map<String, Any> = emptyMap()
    ) {
        val event = SecurityEvent(
            id = UUID.randomUUID().toString(),
            userId = userId,
            eventType = eventType,
            ipAddress = getCurrentIpAddress(),
            userAgent = getCurrentUserAgent(),
            details = details,
            timestamp = Instant.now()
        )
        
        // Log to secure storage
        securityEventRepository.save(event)
        
        // Check for suspicious patterns
        if (isSuspiciousEvent(event)) {
            alertingService.sendAlert(event)
        }
    }
    
    private fun isSuspiciousEvent(event: SecurityEvent): Boolean {
        return when (event.eventType) {
            SecurityEventType.LOGIN_FAILURE -> {
                val recentFailures = getRecentFailures(event.userId, Duration.ofMinutes(5))
                recentFailures.size >= 3
            }
            SecurityEventType.RATE_LIMIT_EXCEEDED -> true
            SecurityEventType.UNUSUAL_LOCATION -> true
            else -> false
        }
    }
}
```

#### Alerting

```kotlin
class AlertingService {
    fun sendAlert(event: SecurityEvent) {
        when (event.eventType) {
            SecurityEventType.MULTIPLE_LOGIN_FAILURES -> {
                sendSmsAlert(
                    "+1234567890",
                    "Multiple login failures detected for user ${event.userId}"
                )
                sendEmailAlert(
                    "security@company.com",
                    "Security Alert: Multiple Login Failures",
                    generateAlertEmail(event)
                )
            }
            SecurityEventType.SUSPICIOUS_API_USAGE -> {
                createSecurityTicket(event)
                blockTemporary(event.userId, Duration.ofMinutes(15))
            }
        }
    }
}
```

### Backup and Recovery

#### Encrypted Backups

```kotlin
class BackupService {
    fun createEncryptedBackup(): BackupResult {
        try {
            // Export database
            val dbFile = File(context.getDatabasePath("sms-gateway").path)
            val backupFile = File(backupDir, "backup_${Instant.now().epochSecond}.db")
            
            // Encrypt backup
            val encryptionKey = generateBackupKey()
            encryptFile(dbFile, backupFile, encryptionKey)
            
            // Upload to secure storage
            val backupId = uploadToSecureStorage(backupFile)
            
            // Store backup metadata
            backupRepository.save(
                BackupMetadata(
                    id = backupId,
                    fileName = backupFile.name,
                    encrypted = true,
                    createdAt = Instant.now(),
                    size = backupFile.length()
                )
            )
            
            return BackupResult.Success(backupId)
        } catch (e: Exception) {
            logger.error("Backup failed", e)
            return BackupResult.Error(e.message ?: "Unknown error")
        }
    }
}
```

## Incident Response

### Security Incident Classification

```kotlin
enum class IncidentSeverity {
    LOW,      // Minor issue, limited impact
    MEDIUM,   // Significant issue, partial service impact
    HIGH,     // Major issue, significant service impact
    CRITICAL  // Emergency, complete service disruption
}

data class SecurityIncident(
    val id: String,
    val severity: IncidentSeverity,
    val type: IncidentType,
    val description: String,
    val affectedUsers: List<String>,
    val detectedAt: Instant,
    val resolvedAt: Instant? = null,
    val resolution: String? = null
)
```

### Incident Response Playbook

#### 1. Detection

```kotlin
class IncidentDetection {
    fun detectAnomalies(): List<SecurityIncident> {
        val incidents = mutableListOf<SecurityIncident>()
        
        // Check for unusual login patterns
        val unusualLogins = detectUnusualLogins()
        incidents.addAll(unusualLogins.map { 
            SecurityIncident(
                id = UUID.randomUUID().toString(),
                severity = IncidentSeverity.MEDIUM,
                type = IncidentType.UNUSUAL_LOGIN_PATTERN,
                description = "Unusual login pattern detected for user ${it.userId}",
                affectedUsers = listOf(it.userId),
                detectedAt = Instant.now()
            )
        })
        
        // Check for API abuse
        val apiAbuse = detectApiAbuse()
        incidents.addAll(apiAbuse.map {
            SecurityIncident(
                id = UUID.randomUUID().toString(),
                severity = IncidentSeverity.HIGH,
                type = IncidentType.API_ABUSE,
                description = "API abuse detected from IP ${it.ipAddress}",
                affectedUsers = it.affectedUsers,
                detectedAt = Instant.now()
            )
        })
        
        return incidents
    }
}
```

#### 2. Containment

```kotlin
class IncidentContainment {
    fun containIncident(incident: SecurityIncident) {
        when (incident.type) {
            IncidentType.UNAUTHORIZED_ACCESS -> {
                // Revoke all tokens for affected users
                incident.affectedUsers.forEach { userId ->
                    tokenManager.revokeAllTokensForUser(userId)
                }
                
                // Force password reset
                incident.affectedUsers.forEach { userId ->
                    userService.forcePasswordReset(userId)
                }
            }
            
            IncidentType.API_ABUSE -> {
                // Block malicious IP addresses
                val maliciousIps = getMaliciousIps(incident)
                firewallService.blockIps(maliciousIps)
                
                // Increase rate limits temporarily
                rateLimitService.increaseLimits(Duration.ofHours(1))
            }
            
            IncidentType.DATA_BREACH -> {
                // Take service offline
                maintenanceService.enableMaintenanceMode()
                
                // Preserve evidence
                forensicsService.preserveEvidence(incident)
            }
        }
    }
}
```

#### 3. Eradication

```kotlin
class IncidentEradication {
    fun eradicateThreat(incident: SecurityIncident) {
        when (incident.type) {
            IncidentType.MALWARE_DETECTED -> {
                // Scan and clean affected systems
                malwareScanner.scanAndClean()
                
                // Update security signatures
                securitySignatureService.updateSignatures()
            }
            
            IncidentType.CONFIGURATION_VULNERABILITY -> {
                // Apply security patches
                patchService.applySecurityPatches()
                
                // Update configuration
                configurationService.applySecureConfiguration()
            }
        }
    }
}
```

#### 4. Recovery

```kotlin
class IncidentRecovery {
    fun recoverFromIncident(incident: SecurityIncident) {
        when (incident.type) {
            IncidentType.SERVICE_OUTAGE -> {
                // Restore from backup
                backupService.restoreFromLatestValid()
                
                // Verify service integrity
                healthCheckService.performComprehensiveCheck()
                
                // Gradually restore service
                trafficManager.graduallyRestoreTraffic()
            }
            
            IncidentType.DATA_CORRUPTION -> {
                // Restore data from backup
                dataRecoveryService.restoreFromBackup(incident.detectedAt)
                
                // Verify data integrity
                dataIntegrityService.verifyAllData()
            }
        }
    }
}
```

## Compliance

### GDPR Compliance

```kotlin
class GdprCompliance {
    fun exportUserData(userId: String): UserDataExport {
        return UserDataExport(
            personalData = userService.getUserData(userId),
            smsHistory = smsService.getSmsHistory(userId),
            apiTokens = tokenService.getUserTokens(userId),
            auditLogs = auditService.getUserAuditLogs(userId),
            exportDate = Instant.now()
        )
    }
    
    fun deleteUserData(userId: String): Boolean {
        return try {
            // Anonymize personal data
            userService.anonymizeUserData(userId)
            
            // Delete SMS messages
            smsService.deleteUserSmsMessages(userId)
            
            // Revoke API tokens
            tokenService.revokeAllTokensForUser(userId)
            
            // Create audit record
            auditService.recordDataDeletion(userId)
            
            true
        } catch (e: Exception) {
            logger.error("Failed to delete user data", e)
            false
        }
    }
}
```

### SOC 2 Type II Compliance

```kotlin
class Soc2Compliance {
    fun generateComplianceReport(): ComplianceReport {
        return ComplianceReport(
            securityControls = listOf(
                SecurityControl(
                    name = "Access Control",
                    status = ControlStatus.COMPLIANT,
                    evidence = listOf(
                        "Access logs for all users",
                        "Regular access reviews",
                        "MFA enforcement"
                    )
                ),
                SecurityControl(
                    name = "Data Encryption",
                    status = ControlStatus.COMPLIANT,
                    evidence = listOf(
                        "Database encryption at rest",
                        "TLS 1.3 for all communications",
                        "Key rotation logs"
                    )
                )
            ),
            auditDate = Instant.now(),
            nextReviewDate = Instant.now().plus(Duration.ofDays(90))
        )
    }
}
```

## Security Checklist

### Daily Checks

- [ ] Review security logs for anomalies
- [ ] Check for failed login attempts
- [ ] Verify SSL certificate validity
- [ ] Monitor API rate limits
- [ ] Check for unusual API usage patterns

### Weekly Checks

- [ ] Review and rotate API tokens
- [ ] Update security signatures
- [ ] Check for security patches
- [ ] Review access logs
- [ ] Verify backup integrity

### Monthly Checks

- [ ] Conduct security audit
- [ ] Review user permissions
- [ ] Update security policies
- [ ] Perform penetration testing
- [ ] Review incident response procedures

### Quarterly Checks

- [ ] Update security documentation
- [ ] Conduct security training
- [ ] Review compliance requirements
- [ ] Perform risk assessment
- [ ] Update disaster recovery plan

### Annual Checks

- [ ] Complete security audit
- [ ] Update security architecture
- [ ] Review and update policies
- [ ] Conduct third-party security assessment
- [ ] Update business continuity plan

## Conclusion

Security is an ongoing process, not a one-time implementation. By following these best practices and regularly reviewing and updating your security measures, you can maintain a strong security posture for your SMS Gateway.

Remember:

1. **Defense in Depth**: Implement multiple layers of security
2. **Principle of Least Privilege**: Grant minimum necessary permissions
3. **Continuous Monitoring**: Regularly monitor and audit security controls
4. **Incident Preparedness**: Have a plan for security incidents
5. **Regular Updates**: Keep systems and security measures up to date

For additional security resources:

- [OWASP Mobile Security Testing Guide](https://owasp.org/www-project-mobile-security-testing-guide/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
- [Cloudflare Security Best Practices](https://www.cloudflare.com/learning/security/)