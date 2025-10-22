package com.smsgateway.app.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.response.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

/**
 * Konfiguruje autentykację Bearer Token dla API
 * 
 * W środowisku produkcyjnym token powinien być przechowywany bezpiecznie
 * i mieć ograniczony czas ważności.
 */
object ApiToken {
    // W środowisku produkcyjnym ten token powinien pochodzić z bezpiecznego źródła
    // np. zmiennych środowiskowych, Android Keystore lub remote config
    const val TOKEN = "smsgateway-api-token-2024-secure"
}

/**
 * Konfiguruje autentykację JWT z Bearer Token
 */
fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "SMS Gateway API"
            verifier(
                JWT
                    .require(Algorithm.HMAC256("secret"))
                    .withAudience("sms-gateway-api")
                    .withIssuer("sms-gateway-server")
                    .build()
            )
            validate { credential ->
                // Prosta walidacja tokena - w środowisku produkcyjnym powinna być bardziej zaawansowana
                if (credential.payload.audience.contains("sms-gateway-api")) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf(
                        "error" to "Unauthorized",
                        "message" to "Token is not valid or has expired"
                    )
                )
            }
        }
        
        // Alternatywna prosta autentykacja Bearer Token (bez JWT)
        bearer("auth-bearer") {
            realm = "SMS Gateway API"
            authenticate { token ->
                if (token.token == ApiToken.TOKEN) {
                    UserIdPrincipal("api-user")
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Generuje prosty token JWT dla testów
 * W środowisku produkcyjnym nie powinno być dostępne
 */
fun generateTestToken(): String {
    return JWT.create()
        .withAudience("sms-gateway-api")
        .withIssuer("sms-gateway-server")
        .withClaim("userId", "api-user")
        .withExpiresAt(Date(System.currentTimeMillis() + 3600000)) // 1 godzina
        .sign(Algorithm.HMAC256("secret"))
}