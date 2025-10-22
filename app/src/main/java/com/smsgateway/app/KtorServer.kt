package com.smsgateway.app

import android.content.Context
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KtorServer(private val context: Context) {
    
    private var server: NettyApplicationEngine? = null
    
    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
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
                            </body>
                            </html>
                            """.trimIndent(),
                            ContentType.Text.Html
                        )
                    }
                    
                    // Status endpoint
                    get("/api/v1/status") {
                        call.respondText(
                            """{"status":"OK","message":"Server running"}""",
                            ContentType.Application.Json
                        )
                    }
                    
                    // SMS send endpoint (do zrobienia później)
                    post("/api/v1/sms/send") {
                        call.respondText(
                            """{"status":"not_implemented","message":"SMS sending coming soon"}""",
                            ContentType.Application.Json
                        )
                    }
                }
            }.start(wait = false)
        }
    }
    
    fun stop() {
        server?.stop(1000, 2000)
    }
}
