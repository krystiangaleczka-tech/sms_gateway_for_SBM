package com.smsgateway.app

import android.content.Context
import com.smsgateway.app.database.SmsRepository
import com.smsgateway.app.routes.smsRoutes
import com.smsgateway.app.workers.WorkManagerService
import com.smsgateway.app.plugins.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.event.Level
import org.slf4j.LoggerFactory

class KtorServer(private val context: Context, private val smsRepository: SmsRepository) {
    
    private val workManagerService = WorkManagerService(context)
    
    private var server: NettyApplicationEngine? = null
    
    fun start() {
        // Uruchomienie okresowego schedulera SMS
        workManagerService.startPeriodicScheduler()
        
        CoroutineScope(Dispatchers.IO).launch {
            server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                // Pluginy Ktor
                install(ContentNegotiation) {
                    json()
                }
                
                // Konfiguracja pluginów
                configureStatusPages()
                configureRequestValidation()
                configureAuthentication()
                configureCORS()
                
                routing {
                    // Root endpoint
                    get("/") {
                        call.respondText(
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <title>SMS Gateway</title>
                                <meta charset="UTF-8">
                            </head>
                            <body>
                                <h1>🚀 SMS Gateway działa!</h1>
                                <p>Serwer Ktor uruchomiony poprawnie</p>
                                <p>Status: <strong style="color: green;">ONLINE</strong></p>
                                <p>API Endpoints:</p>
                                <ul>
                                    <li>POST /api/v1/sms/queue - Kolejkowanie SMS (wymaga autentykacji)</li>
                                    <li>GET /api/v1/sms/status/{id} - Status SMS (wymaga autentykacji)</li>
                                    <li>GET /api/v1/sms/history - Historia SMS (wymaga autentykacji)</li>
                                    <li>DELETE /api/v1/sms/cancel/{id} - Anulowanie SMS (wymaga autentykacji)</li>
                                </ul>
                                <p>Autentykacja: Bearer Token</p>
                                <p>Przykład: Authorization: Bearer smsgateway-api-token-2024-secure</p>
                            </body>
                            </html>
                            """.trimIndent(),
                            ContentType.Text.Html
                        )
                    }
                    
                    // Status endpoint (publiczny, bez autentykacji)
                    get("/api/v1/status") {
                        call.respond(
                            mapOf(
                                "status" to "OK",
                                "message" to "Server running",
                                "version" to "2.1.0",
                                "features" to mapOf(
                                    "authentication" to "Bearer Token",
                                    "pagination" to "Supported",
                                    "validation" to "Automatic",
                                    "compression" to "gzip/deflate",
                                    "cors" to "Enabled"
                                ),
                                "endpoints" to mapOf(
                                    "queue" to "POST /api/v1/sms/queue",
                                    "status" to "GET /api/v1/sms/status/{id}",
                                    "history" to "GET /api/v1/sms/history?page=1&limit=50",
                                    "cancel" to "DELETE /api/v1/sms/cancel/{id}"
                                )
                            )
                        )
                    }
                    
                    // SMS routes
                    smsRoutes(smsRepository, workManagerService)
                }
            }.start(wait = false)
        }
    }
    
    fun stop() {
        server?.stop(1000, 2000)
    }
}
