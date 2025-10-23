# SMS Gateway - Przewodnik Bezpieczeństwa

## Spis treści

1. [Wprowadzenie](#wprowadzenie)
2. [Architektura Bezpieczeństwa](#architektura-bezpieczeństwa)
3. [Konfiguracja Dostępu Zewnętrznego](#konfiguracja-dostępu-zewnętrznego)
4. [Zarządzanie Tokenami API](#zarządzanie-tokenami-api)
5. [Limitowanie Żądań](#limitowanie-żądań)
6. [Cloudflare Tunnel](#cloudflare-tunnel)
7. [Audyt Bezpieczeństwa](#audyt-bezpieczeństwa)
8. [Najlepsze Praktyki](#najlepsze-praktyki)
9. [Troubleshooting](#troubleshooting)

## Wprowadzenie

Ten dokument opisuje funkcje bezpieczeństwa zaimplementowane w SMS Gateway, w tym mechanizmy uwierzytelniania, autoryzacji, limitowania żądań i audytu bezpieczeństwa.

### Cele Bezpieczeństwa

- **Poufność**: Ochrona danych przed nieautoryzowanym dostępem
- **Integralność**: Zapewnienie, że dane nie zostały zmodyfikowane w nieautoryzowany sposób
- **Dostępność**: Gwarancja, że autoryzowani użytkownicy mają dostęp do zasobów
- **Audytowalność**: Możliwość śledzenia i rejestrowania działań w systemie

## Architektura Bezpieczeństwa

### Warstwy Bezpieczeństwa

```
┌─────────────────────────────────────────────────────────────┐
│                    Cloudflare Tunnel                        │
│                 (DDoS Protection, SSL)                     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  Authentication Layer                       │
│              (API Tokens, Rate Limiting)                   │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Authorization Layer                       │
│              (Permission-based Access)                     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│                 (Business Logic, Validation)                │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                             │
│              (Encrypted Storage, Audit Logs)               │
└─────────────────────────────────────────────────────────────┘
```

### Komponenty Bezpieczeństwa

1. **TokenManagerService** - Zarządzanie tokenami API
2. **RateLimitService** - Limitowanie żądań
3. **CloudflareTunnelService** - Zarządzanie tunelami Cloudflare
4. **SecurityAuditService** - Audyt zdarzeń bezpieczeństwa
5. **Middleware** - Filtry żądań (uwierzytelnianie, limitowanie, audyt)

## Konfiguracja Dostępu Zewnętrznego

### Cloudflare Tunnel

Cloudflare Tunnel zapewnia bezpieczny dostęp zewnętrzny bez konieczności otwierania portów w firewall.

#### Konfiguracja

1. **Instalacja Cloudflared**

```bash
# Linux/macOS
curl -L https://pkg.cloudflare.com/pubkey.gpg | sudo gpg --dearmor -o /usr/share/keyrings/cloudflare-archive-keyring.gpg
echo 'deb [signed-by=/usr/share/keyrings/cloudflare-archive-keyring.gpg] https://pkg.cloudflare.com/ cloudflare-main' | sudo tee /etc/apt/sources.list.d/cloudflare.list
sudo apt-get update && sudo apt-get install cloudflared

# Windows
# Pobierz z https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/
```

2. **Autentykacja Cloudflare**

```bash
cloudflared tunnel login
```

3. **Utworzenie Tunelu**

```bash
cloudflared tunnel create sms-gateway
```

4. **Konfiguracja Pliku**

```yaml
# ~/.cloudflared/config.yml
tunnel: sms-gateway
credentials-file: ~/.cloudflared/<tunnel-id>.json

ingress:
  - hostname: sms-gateway.yourdomain.com
    service: http://localhost:8080
  - service: http_status:404
```

5. **Uruchomienie Tunelu**

```bash
cloudflared tunnel run sms-gateway
```

### Konfiguracja Aplikacji

Dodaj do pliku `application.properties`:

```properties
# Cloudflare Tunnel Configuration
cloudflare.tunnel.enabled=true
cloudflare.tunnel.subdomain=sms-gateway
cloudflare.tunnel.domain=yourdomain.com
cloudflare.tunnel.local-port=8080
```

## Zarządzanie Tokenami API

### Tworzenie Tokenu

```bash
curl -X POST http://localhost:8080/api/security/tokens \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Production API Token",
    "permissions": ["sms:send", "sms:view"],
    "expirationDays": 90
  }'
```

### Używanie Tokenu

```bash
curl -X GET http://localhost:8080/api/sms \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Odwołanie Tokenu

```bash
curl -X DELETE http://localhost:8080/api/security/tokens/TOKEN_ID \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Typy Uprawnień

| Uprawnienie | Opis |
|-------------|------|
| `sms:send` | Wysyłanie SMS-ów |
| `sms:view` | Przeglądanie historii SMS-ów |
| `sms:delete` | Usuwanie SMS-ów |
| `queue:manage` | Zarządzanie kolejką |
| `security:manage` | Zarządzanie tokenami i tunelami |
| `audit:view` | Przeglądanie logów audytu |

## Limitowanie Żądań

### Konfiguracja Limitów

Domyślne limity są zdefiniowane w pliku `application.properties`:

```properties
# Rate Limiting Configuration
rate.limit.api.requests.per.minute=100
rate.limit.sms.send.per.hour=50
rate.limit.ip.requests.per.minute=200
```

### Dostosowywanie Limitów

Możesz dostosować limity dla różnych typów żądań:

```bash
curl -X POST http://localhost:8080/api/security/rate-limits \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "identifier": "user123",
    "limitType": "API_REQUESTS",
    "windowSizeMinutes": 60,
    "maxRequests": 200
  }'
```

### Obsługa Przekroczenia Limitów

Gdy limit zostanie przekroczony, API zwróci kod HTTP 429 z nagłówkiem:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1640995200
```

## Cloudflare Tunnel

### Zarządzanie Tunelami przez API

#### Tworzenie Tunelu

```bash
curl -X POST http://localhost:8080/api/security/tunnels \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "name": "Production Tunnel",
    "tunnelType": "HTTPS",
    "localPort": 8080,
    "subdomain": "api"
  }'
```

#### Uruchomienie Tunelu

```bash
curl -X POST http://localhost:8080/api/security/tunnels/TUNNEL_ID/start \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

#### Zatrzymanie Tunelu

```bash
curl -X POST http://localhost:8080/api/security/tunnels/TUNNEL_ID/stop \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

#### Usunięcie Tunelu

```bash
curl -X DELETE http://localhost:8080/api/security/tunnels/TUNNEL_ID \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Monitorowanie Tunelu

Status tunelu można sprawdzić przez API:

```bash
curl -X GET http://localhost:8080/api/security/tunnels/TUNNEL_ID/status \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## Audyt Bezpieczeństwa

### Przeglądanie Zdarzeń Bezpieczeństwa

```bash
curl -X GET "http://localhost:8080/api/security/events?userId=user123&limit=50" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Typy Zdarzeń Audytu

| Typ Zdarzenia | Opis |
|---------------|------|
| `LOGIN_SUCCESS` | Pomyślne logowanie |
| `LOGIN_FAILURE` | Nieudane logowanie |
| `TOKEN_CREATED` | Utworzenie tokenu API |
| `TOKEN_REVOKED` | Odwołanie tokenu API |
| `API_ACCESS` | Dostęp do API |
| `RATE_LIMIT_EXCEEDED` | Przekroczenie limitu żądań |
| `SUSPICIOUS_ACTIVITY` | Podejrzana aktywność |

### Filtrowanie Zdarzeń

```bash
# Zdarzenia dla konkretnego użytkownika
curl -X GET "http://localhost:8080/api/security/events?userId=user123" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

# Zdarzenia z określonego adresu IP
curl -X GET "http://localhost:8080/api/security/events?ipAddress=192.168.1.1" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

# Zdarzenia z określonego zakresu czasowego
curl -X GET "http://localhost:8080/api/security/events?startTime=2023-01-01T00:00:00Z&endTime=2023-01-31T23:59:59Z" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Czyszczenie Starych Zdarzeń

```bash
curl -X DELETE "http://localhost:8080/api/security/events/cleanup?olderThanDays=90" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## Najlepsze Praktyki

### Zarządzanie Tokenami

1. **Minimalne Uprawnienia**: Przyznawaj tylko niezbędne uprawnienia
2. **Krótki Czas Ważności**: Ustawiaj krótki czas ważności tokenów (30-90 dni)
3. **Regularna Rotacja**: Regularnie odnawiaj tokeny
4. **Unikalne Tokeny**: Używaj oddzielnych tokenów dla różnych aplikacji
5. **Monitorowanie**: Regularnie przeglądaj użycie tokenów

### Bezpieczeństwo Sieciowe

1. **Cloudflare Tunnel**: Zawsze używaj Cloudflare Tunnel dla dostępu zewnętrznego
2. **HTTPS**: Wymuszaj szyfrowanie SSL/TLS
3. **Firewall**: Konfiguruj firewall na serwerze
4. **VPN**: Używaj VPN dla dostępu administracyjnego

### Monitorowanie i Reagowanie

1. **Logi**: Przechowuj logi bezpieczeństwa przez co najmniej 90 dni
2. **Alerty**: Skonfiguruj alerty dla podejrzanej aktywności
3. **Regularne Przeglądy**: Regularnie przeglądaj logi bezpieczeństwa
4. **Plan Reagowania**: Przygotuj plan reagowania na incydenty

## Troubleshooting

### Problemy z Tokenami

#### Token nie działa

1. Sprawdź, czy token nie wygasł
2. Upewnij się, że token ma odpowiednie uprawnienia
3. Sprawdź, czy token nie został odwołany

```bash
curl -X GET http://localhost:8080/api/security/tokens/TOKEN_ID \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

#### Błąd 401 Unauthorized

1. Sprawdź poprawność tokenu
2. Upewnij się, że nagłówek Authorization jest poprawnie sformatowany

```bash
# Poprawny format
Authorization: Bearer YOUR_TOKEN_HERE
```

### Problemy z Limitowaniem

#### Błąd 429 Too Many Requests

1. Sprawdź aktualne limity
2. Zwiększ limity, jeśli to konieczne
3. Zaimplementuj ponawianie z wykładniczym opóźnieniem

```bash
curl -X GET "http://localhost:8080/api/security/rate-limits?identifier=user123" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Problemy z Cloudflare Tunnel

#### Tunnel nie łączy się

1. Sprawdź konfigurację pliku `~/.cloudflared/config.yml`
2. Upewnij się, że usługa lokalna działa na poprawnym porcie
3. Sprawdź logi cloudflared

```bash
cloudflared tunnel info sms-gateway
```

#### Błąd 502 Bad Gateway

1. Sprawdź, czy aplikacja działa lokalnie
2. Upewnij się, że port jest poprawnie skonfigurowany
3. Sprawdź logi aplikacji

### Problemy z Audytem

#### Brak zdarzeń w logach

1. Sprawdź, czy audyt jest włączony
2. Upewnij się, że middleware audytu jest poprawnie skonfigurowany
3. Sprawdź uprawnienia dostępu do logów

```bash
curl -X GET "http://localhost:8080/api/security/events?limit=10" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## Podsumowanie

Implementacja tych funkcji bezpieczeństwa zapewnia kompleksową ochronę SMS Gateway przed nieautoryzowanym dostępem i atakami. Regularne monitorowanie i aktualizacja konfiguracji bezpieczeństwa są kluczowe dla utrzymania wysokiego poziomu ochrony.

W przypadku pytań lub problemów związanych z bezpieczeństwem, skontaktuj się z administratorem systemu.