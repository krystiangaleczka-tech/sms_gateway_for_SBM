package com.smsgateway.app.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

/**
 * Konfiguruje CORS (Cross-Origin Resource Sharing)
 * 
 * Pozwala na dostęp do API z przeglądarek webowych
 * W środowisku produkcyjnym powinien być bardziej restrykcyjny
 */
fun Application.configureCORS() {
    install(CORS) {
        // Dozwolone metody HTTP
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        
        // Dozwolone nagłówki
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Requested-With")
        
        // W środowisku deweloperskim pozwalamy na wszystkie origins
        // W produkcji powinny być tutaj konkretne domeny
        anyHost() // UWAGA: W produkcji zmienić na konkretne domeny!
        
        // Pozwól na credentials (cookies, authorization headers)
        allowCredentials = true
        
        // Maksymalny czas cache preflight request
        maxAgeInSeconds = 3600 // 1 godzina
        
        // Eksponowane nagłówki (dostępne z JavaScript)
        exposeHeader("X-Total-Count")
        exposeHeader("X-Page-Count")
    }
}