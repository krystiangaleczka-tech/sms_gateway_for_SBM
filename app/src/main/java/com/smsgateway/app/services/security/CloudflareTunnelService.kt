package com.smsgateway.app.services.security

import com.smsgateway.app.models.security.TunnelConfig
import com.smsgateway.app.models.security.TunnelStatus
import com.smsgateway.app.models.security.TunnelType
import com.smsgateway.app.models.security.dto.CreateTunnelRequest
import com.smsgateway.app.models.security.dto.CreateTunnelResponse
import com.smsgateway.app.models.security.dto.TunnelStatusResponse
import com.smsgateway.app.repositories.TunnelConfigRepository
import com.smsgateway.app.utils.TokenGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Serwis zarządzający tunelami Cloudflare
 * Odpowiedzialny za tworzenie, konfigurację i monitoring tuneli
 */
class CloudflareTunnelService(
    private val tunnelConfigRepository: TunnelConfigRepository,
    private val tokenGenerator: TokenGenerator
) {
    
    /**
     * Tworzy nowy tunel Cloudflare
     * @param request Dane do stworzenia tunelu
     * @return Odpowiedź z danymi nowego tunelu
     */
    suspend fun createTunnel(request: CreateTunnelRequest): CreateTunnelResponse {
        logger.info { "Tworzenie nowego tunelu Cloudflare typu: ${request.type}" }
        
        // Sprawdzenie czy istnieje już aktywny tunel tego samego typu
        val existingTunnel = tunnelConfigRepository.findByType(request.type)
            .find { it.status == TunnelStatus.ACTIVE }
        
        if (existingTunnel != null) {
            logger.warn { "Istnieje już aktywny tunel typu ${request.type}: ${existingTunnel.id}" }
            throw IllegalStateException("Istnieje już aktywny tunel tego typu")
        }
        
        // Generowanie unikalnego ID tunelu
        val tunnelId = tokenGenerator.generateTunnelId()
        
        // Pobieranie konfiguracji zależnej od typu
        val (port, hostname) = when (request.type) {
            TunnelType.HTTP -> Pair(8080, "smsgateway-${tunnelId}.trycloudflare.com")
            TunnelType.HTTPS -> Pair(8443, "smsgateway-${tunnelId}.trycloudflare.com")
            TunnelType.TCP -> Pair(2222, "tcp://${tunnelId}.trycloudflare.com")
        }
        
        // Tworzenie encji tunelu
        val tunnelConfig = TunnelConfig(
            id = tunnelId,
            name = request.name,
            type = request.type,
            hostname = hostname,
            port = port,
            secretToken = tokenGenerator.generateSecureToken(),
            status = TunnelStatus.INACTIVE,
            configJson = generateTunnelConfig(tunnelId, hostname, port, request.type),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        // Zapisanie konfiguracji tunelu
        val savedConfig = tunnelConfigRepository.save(tunnelConfig)
        
        // Generowanie pliku konfiguracyjnego cloudflared
        generateCloudflaredConfig(savedConfig)
        
        logger.info { "Utworzono tunel: $tunnelId" }
        
        return CreateTunnelResponse(
            id = savedConfig.id,
            name = savedConfig.name,
            type = savedConfig.type,
            hostname = savedConfig.hostname,
            port = savedConfig.port,
            status = savedConfig.status,
            configJson = savedConfig.configJson
        )
    }
    
    /**
     * Uruchamia tunel
     * @param tunnelId ID tunelu
     * @return Status tunelu
     */
    suspend fun startTunnel(tunnelId: String): TunnelStatusResponse {
        logger.info { "Uruchamianie tunelu: $tunnelId" }
        
        val tunnel = tunnelConfigRepository.findById(tunnelId)
            ?: throw IllegalArgumentException("Tunel nie istnieje: $tunnelId")
        
        if (tunnel.status == TunnelStatus.ACTIVE) {
            logger.warn { "Tunel jest już aktywny: $tunnelId" }
            return TunnelStatusResponse(
                id = tunnel.id,
                status = tunnel.status,
                hostname = tunnel.hostname,
                port = tunnel.port,
                message = "Tunel jest już aktywny"
            )
        }
        
        // Uruchomienie procesu cloudflared
        val process = startCloudflaredProcess(tunnel)
        
        if (process.isAlive) {
            // Aktualizacja statusu tunelu
            val updatedTunnel = tunnelConfigRepository.updateStatus(tunnelId, TunnelStatus.ACTIVE)
            
            logger.info { "Tunel uruchomiony pomyślnie: $tunnelId" }
            
            return TunnelStatusResponse(
                id = updatedTunnel.id,
                status = updatedTunnel.status,
                hostname = updatedTunnel.hostname,
                port = updatedTunnel.port,
                message = "Tunel uruchomiony pomyślnie"
            )
        } else {
            logger.error { "Nie udało się uruchomić tunelu: $tunnelId" }
            
            // Aktualizacja statusu na błąd
            tunnelConfigRepository.updateStatus(tunnelId, TunnelStatus.ERROR)
            
            return TunnelStatusResponse(
                id = tunnel.id,
                status = TunnelStatus.ERROR,
                hostname = tunnel.hostname,
                port = tunnel.port,
                message = "Nie udało się uruchomić tunelu"
            )
        }
    }
    
    /**
     * Zatrzymuje tunel
     * @param tunnelId ID tunelu
     * @return Status tunelu
     */
    suspend fun stopTunnel(tunnelId: String): TunnelStatusResponse {
        logger.info { "Zatrzymywanie tunelu: $tunnelId" }
        
        val tunnel = tunnelConfigRepository.findById(tunnelId)
            ?: throw IllegalArgumentException("Tunel nie istnieje: $tunnelId")
        
        if (tunnel.status == TunnelStatus.INACTIVE) {
            logger.warn { "Tunel jest już nieaktywny: $tunnelId" }
            return TunnelStatusResponse(
                id = tunnel.id,
                status = tunnel.status,
                hostname = tunnel.hostname,
                port = tunnel.port,
                message = "Tunel jest już nieaktywny"
            )
        }
        
        // Zatrzymanie procesu cloudflared
        stopCloudflaredProcess(tunnelId)
        
        // Aktualizacja statusu tunelu
        val updatedTunnel = tunnelConfigRepository.updateStatus(tunnelId, TunnelStatus.INACTIVE)
        
        logger.info { "Tunel zatrzymany: $tunnelId" }
        
        return TunnelStatusResponse(
            id = updatedTunnel.id,
            status = updatedTunnel.status,
            hostname = updatedTunnel.hostname,
            port = updatedTunnel.port,
            message = "Tunel zatrzymany"
        )
    }
    
    /**
     * Pobiera status tunelu
     * @param tunnelId ID tunelu
     * @return Status tunelu
     */
    suspend fun getTunnelStatus(tunnelId: String): TunnelStatusResponse {
        logger.debug { "Pobieranie statusu tunelu: $tunnelId" }
        
        val tunnel = tunnelConfigRepository.findById(tunnelId)
            ?: throw IllegalArgumentException("Tunel nie istnieje: $tunnelId")
        
        // Sprawdzenie czy proces jest aktywny
        val isProcessRunning = isCloudflaredProcessRunning(tunnelId)
        val actualStatus = if (isProcessRunning) {
            if (tunnel.status != TunnelStatus.ACTIVE) {
                tunnelConfigRepository.updateStatus(tunnelId, TunnelStatus.ACTIVE)
            }
            TunnelStatus.ACTIVE
        } else {
            if (tunnel.status == TunnelStatus.ACTIVE) {
                tunnelConfigRepository.updateStatus(tunnelId, TunnelStatus.INACTIVE)
            }
            TunnelStatus.INACTIVE
        }
        
        return TunnelStatusResponse(
            id = tunnel.id,
            status = actualStatus,
            hostname = tunnel.hostname,
            port = tunnel.port,
            message = when (actualStatus) {
                TunnelStatus.ACTIVE -> "Tunel jest aktywny"
                TunnelStatus.INACTIVE -> "Tunel jest nieaktywny"
                TunnelStatus.ERROR -> "Tunel ma błąd"
            }
        )
    }
    
    /**
     * Pobiera wszystkie tunele
     * @return Lista tuneli
     */
    suspend fun getAllTunnels(): List<TunnelConfig> {
        logger.debug { "Pobieranie wszystkich tuneli" }
        return tunnelConfigRepository.findAll()
    }
    
    /**
     * Usuwa tunel
     * @param tunnelId ID tunelu
     */
    suspend fun deleteTunnel(tunnelId: String) {
        logger.info { "Usuwanie tunelu: $tunnelId" }
        
        val tunnel = tunnelConfigRepository.findById(tunnelId)
            ?: throw IllegalArgumentException("Tunel nie istnieje: $tunnelId")
        
        // Zatrzymanie tunelu jeśli jest aktywny
        if (tunnel.status == TunnelStatus.ACTIVE) {
            stopTunnel(tunnelId)
        }
        
        // Usunięcie plików konfiguracyjnych
        deleteCloudflaredConfig(tunnelId)
        
        // Usunięcie tunelu z bazy danych
        tunnelConfigRepository.delete(tunnelId)
        
        logger.info { "Tunel usunięty: $tunnelId" }
    }
    
    /**
     * Generuje konfigurację tunelu w formacie JSON
     * @param tunnelId ID tunelu
     * @param hostname Nazwa hosta
     * @param port Port
     * @param type Typ tunelu
     * @return Konfiguracja w formacie JSON
     */
    private fun generateTunnelConfig(
        tunnelId: String,
        hostname: String,
        port: Int,
        type: TunnelType
    ): String {
        return """
        {
            "tunnel": "$tunnelId",
            "ingress": [
                {
                    "hostname": "$hostname",
                    "service": "http://localhost:$port"
                },
                {
                    "service": "http_status:404"
                }
            ]
        }
        """.trimIndent()
    }
    
    /**
     * Generuje plik konfiguracyjny cloudflared
     * @param tunnel Konfiguracja tunelu
     */
    private fun generateCloudflaredConfig(tunnel: TunnelConfig) {
        val configDir = File("/data/data/com.smsgateway.app/files/cloudflared")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        val configFile = File(configDir, "${tunnel.id}.yml")
        configFile.writeText("""
            tunnel: ${tunnel.id}
            credentials-file: ${configDir.absolutePath}/${tunnel.id}.creds.json
            
            ingress:
              - hostname: ${tunnel.hostname}
                service: http://localhost:${tunnel.port}
              - service: http_status:404
        """.trimIndent())
        
        // Generowanie pliku z danymi uwierzytelniającymi
        val credsFile = File(configDir, "${tunnel.id}.creds.json")
        credsFile.writeText("""
            {
                "AccountTag": "",
                "TunnelID": "${tunnel.id}",
                "TunnelSecret": "${tunnel.secretToken}"
            }
        """.trimIndent())
    }
    
    /**
     * Uruchamia proces cloudflared
     * @param tunnel Konfiguracja tunelu
     * @return Proces cloudflared
     */
    private fun startCloudflaredProcess(tunnel: TunnelConfig): Process {
        val configDir = File("/data/data/com.smsgateway.app/files/cloudflared")
        val configFile = File(configDir, "${tunnel.id}.yml")
        
        val command = listOf(
            "/data/data/com.smsgateway.app/files/cloudflared/cloudflared",
            "tunnel",
            "run",
            "--config",
            configFile.absolutePath
        )
        
        return ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
    }
    
    /**
     * Zatrzymuje proces cloudflared
     * @param tunnelId ID tunelu
     */
    private fun stopCloudflaredProcess(tunnelId: String) {
        try {
            // Znalezienie i zabicie procesu cloudflared dla danego tunelu
            val pgrep = ProcessBuilder("pgrep", "-f", "cloudflared.*$tunnelId").start()
            val reader = BufferedReader(InputStreamReader(pgrep.inputStream))
            
            reader.useLines { lines ->
                lines.forEach { pid ->
                    ProcessBuilder("kill", pid).start()
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Nie udało się zatrzymać procesu cloudflared dla tunelu: $tunnelId" }
        }
    }
    
    /**
     * Sprawdza czy proces cloudflared jest uruchomiony
     * @param tunnelId ID tunelu
     * @return True jeśli proces jest uruchomiony
     */
    private fun isCloudflaredProcessRunning(tunnelId: String): Boolean {
        return try {
            val pgrep = ProcessBuilder("pgrep", "-f", "cloudflared.*$tunnelId").start()
            val exitCode = pgrep.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            logger.warn(e) { "Nie udało się sprawdzić statusu procesu cloudflared dla tunelu: $tunnelId" }
            false
        }
    }
    
    /**
     * Usuwa pliki konfiguracyjne cloudflared
     * @param tunnelId ID tunelu
     */
    private fun deleteCloudflaredConfig(tunnelId: String) {
        try {
            val configDir = File("/data/data/com.smsgateway.app/files/cloudflared")
            File(configDir, "${tunnelId}.yml").delete()
            File(configDir, "${tunnelId}.creds.json").delete()
        } catch (e: Exception) {
            logger.warn(e) { "Nie udało się usunąć plików konfiguracyjnych dla tunelu: $tunnelId" }
        }
    }
}