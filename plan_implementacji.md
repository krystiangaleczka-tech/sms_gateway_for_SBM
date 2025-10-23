PLAN IMPLEMENTACJI SMS GATEWAY
Dokument Strategiczny - Co Trzeba Zrobić
1. PRZEGLĄD WYKONAWCZY
Aktualny Stan
15% projektu ukończone
UI i podstawowy serwer działają
Brak całej logiki biznesowej backend
Brak zabezpieczeń
Brak integracji z systemem Android SMS
Cel
Stworzyć w pełni funkcjonalną aplikację Android SMS Gateway z bezpiecznym API, systemem kolejkowania i zewnętrznym dostępem przez Cloudflare Tunnel.
2. FAZA 1: FUNDAMENT BEZPIECZEŃSTWA [PRIORYTET KRYTYCZNY]
2.1 Implementacja Autentykacji Bearer Token
Dlaczego najpierw: Otwarte API to ogromne zagrożenie bezpieczeństwa. Każdy może wysyłać SMS z twojego urządzenia.​
Co zrobić:
Stworzyć system generowania tokenów API
Dodać middleware do Ktor, który sprawdza nagłówek Authorization
Zaimplementować walidację tokenu przy każdym żądaniu do API
Dodać mechanizm rotacji tokenów
Przygotować obsługę błędów HTTP 401 Unauthorized
Stworzyć interfejs w aplikacji do zarządzania tokenami (generowanie, usuwanie, lista aktywnych)
Wymagania funkcjonalne:
Token musi być unikalny dla każdego urządzenia
Token powinien być długi (min. 32 znaki) i losowy
Wszystkie endpointy API (oprócz / i /api/v1/status) wymagają tokenu
Token przekazywany jako: Authorization: Bearer {token}
Kryteria ukończenia:
Żądanie bez tokenu zwraca 401
Żądanie z nieprawidłowym tokenem zwraca 401
Żądanie z prawidłowym tokenem działa poprawnie
UI pozwala wygenerować nowy token
3. FAZA 2: WARSTWA PERSYSTENCJI DANYCH
3.1 Implementacja Bazy Danych Room
Dlaczego: Aplikacja musi przechowywać historię wiadomości, statusy wysyłki i kolejkę zadań.​
Co zrobić:
Stworzyć encję SmsMessage z polami: id, phoneNumber, message, status, scheduledTime, sentTime, errorMessage, retryCount, createdAt
Zdefiniować enum SmsStatus z wartościami: QUEUED, SCHEDULED, SENDING, SENT, FAILED, CANCELLED
Utworzyć interfejs DAO z metodami CRUD: insert, update, delete, getById, getAll, getByStatus, getScheduledBefore
Skonfigurować klasę AppDatabase z wersjonowaniem i strategią migracji
Dodać indeksy na kolumny często używane w zapytaniach (status, scheduledTime)
Architektura alternatywna:
Rozważyć użycie Exposed zamiast Room, ponieważ serwer Ktor działa jako standardowa aplikacja JVM, nie jako typowy komponent Android.​
Wymagania funkcjonalne:
Baza musi przechowywać min. 10,000 wiadomości
Zapytania muszą być wydajne (indeksowanie)
Automatyczne czyszczenie starych wiadomości (opcjonalnie)
Obsługa transakcji dla atomowych operacji
Kryteria ukończenia:
Wiadomości są zapisywane do bazy przy każdym żądaniu API
Historia wiadomości jest dostępna przez API
Aplikacja nie traci danych po restarcie
4. FAZA 3: RDZEŃ FUNKCJONALNOŚCI SMS
4.1 Integracja z Android SMS Manager API
Dlaczego: Bez tego aplikacja nie może wysyłać SMS.​
Co zrobić:
Dodać uprawnienia SEND_SMS i READ_SMS w AndroidManifest.xml
Stworzyć klasę SmsService jako wrapper dla SmsManager
Zaimplementować wysyłanie pojedynczej wiadomości SMS
Dodać obsługę długich wiadomości (multipart SMS) - automatyczne dzielenie wiadomości dłuższych niż 160 znaków​
Zaimplementować callback dla statusu wysyłki (wysłane, dostarczone, błąd)
Obsłużyć różne typy błędów (brak sieci, nieprawidłowy numer, limit operatora)
Dodać obsługę wielu kart SIM (jeśli urządzenie obsługuje)
Wymagania funkcjonalne:
Sprawdzanie dostępności uprawnień przed wysłaniem
Automatyczne dzielenie długich wiadomości
Zwracanie szczegółowych informacji o błędach
Śledzenie statusu dostarczenia
Kryteria ukończenia:
Aplikacja może wysłać SMS programowo
Długie wiadomości są dzielone automatycznie
Status wysyłki jest raportowany do bazy danych
Błędy są logowane z odpowiednimi kodami
4.2 Implementacja Endpointów REST API
Co zrobić:
POST /api/v1/sms/queue - Kolejkowanie nowej wiadomości:
Przyjąć JSON z polami: phoneNumber, message, scheduledTime (opcjonalnie)
Walidować numer telefonu (format międzynarodowy)
Walidować treść wiadomości (nie pusta, max długość)
Zapisać do bazy ze statusem QUEUED lub SCHEDULED
Zwrócić ID wiadomości i status 201 Created
GET /api/v1/sms/status/{id} - Sprawdzanie statusu wiadomości:
Pobrać wiadomość z bazy po ID
Zwrócić pełne informacje: status, czas zaplanowania, czas wysłania, błąd (jeśli wystąpił)
Obsłużyć 404 jeśli wiadomość nie istnieje
GET /api/v1/sms/history - Historia wiadomości:
Obsługiwać parametry query: limit, offset, status, dateFrom, dateTo
Zwrócić listę wiadomości z paginacją
Sortować od najnowszych
DELETE /api/v1/sms/cancel/{id} - Anulowanie wiadomości:
Sprawdzić czy wiadomość istnieje
Sprawdzić czy można anulować (status QUEUED lub SCHEDULED)
Zmienić status na CANCELLED
Anulować zaplanowane zadanie WorkManager
Zwrócić 200 OK lub 400 Bad Request z powodem
Wymagania funkcjonalne:
Wszystkie endpointy wymagają autentykacji
Walidacja danych wejściowych z odpowiednimi komunikatami błędów
Standardowe kody HTTP (200, 201, 400, 401, 404, 500)
Odpowiedzi w formacie JSON
Kryteria ukończenia:
Wszystkie 4 endpointy są funkcjonalne
Walidacja danych działa poprawnie
Dokumentacja API jest dostępna
5. FAZA 4: SYSTEM KOLEJKOWANIA I PLANOWANIA
5.1 Implementacja WorkManager
Dlaczego: WorkManager zapewnia niezawodne wykonywanie zadań w tle nawet po restarcie urządzenia.​​
Co zrobić:
SmsSchedulerWorker - Planowanie wysyłki (mniej niz 28h przed wizytą):
Przyjąć jako parametr ID wiadomości z bazy
Pobrać szczegóły wiadomości
Sprawdzić czy status to SCHEDULED
Zaplanować SmsSenderWorker na właściwy czas (mniej niz 24h przed wizytą)
Zaktualizować status w bazie
Obsłużyć sytuację gdy termin już minął
SmsSenderWorker - Faktyczna wysyłka SMS (mniej niz 24h przed wizytą):
Przyjąć jako parametr ID wiadomości
Pobrać szczegóły wiadomości z bazy
Sprawdzić uprawnienia i połączenie sieciowe
Wywołać SmsService do wysłania SMS
Zaktualizować status (SENDING → SENT lub FAILED)
Zapisać szczegóły błędu jeśli wystąpił
Mechanizm Retry:
Skonfigurować ExponentialBackoff dla ponownych prób
Maksymalnie 3 próby wysyłki
Opóźnienie: 5 min, 15 min, 30 min
Po 3 nieudanych próbach status FAILED
Wymagania funkcjonalne:
Zadania muszą przetrwać restart urządzenia
Kolejność wysyłki musi być zachowana
Worker musi sprawdzać warunki (sieć, bateria)
Logi wszystkich operacji
Kryteria ukończenia:
Wiadomości są wysyłane o zaplanowanym czasie
Retry działa automatycznie przy błędach
Zadania przetrwają restart aplikacji/urządzenia
Status jest aktualizowany w czasie rzeczywistym
6. FAZA 5: ZEWNĘTRZNY DOSTĘP I BEZPIECZEŃSTWO
6.1 Konfiguracja Cloudflare Tunnel
Dlaczego: Bezpieczny dostęp do API z zewnątrz bez otwierania portów na routerze.​
Co zrobić:
Utworzyć konto Cloudflare (jeśli nie istnieje)
Zainstalować cloudflared daemon na urządzeniu Android (przez Termux lub podobne)
Utworzyć tunel w panelu Cloudflare Zero Trust
Skonfigurować tunel do przekazywania ruchu na localhost:8080
Ustawić własną subdomenę (np. sms-gateway.twojadomena.com)
Skonfigurować polityki dostępu Cloudflare Access
Opcje bezpieczeństwa:
Rate limiting - maksymalna liczba żądań na minutę
IP whitelisting - dostęp tylko z określonych adresów IP
Geo-blocking - blokowanie określonych krajów
WAF rules - ochrona przed atakami
Alternatywne rozwiązania:
Tailscale VPN - prostsze niż Cloudflare, dobra prywatność
Ngrok - szybki setup, ale płatne subdomeny
WireGuard - własny VPN, maksymalna kontrola
Wymagania funkcjonalne:
HTTPS szyfrowanie ruchu
Autentykacja dostępu (dodatkowo do Bearer Token)
Monitoring połączeń
Automatyczne restarty tunelu
Kryteria ukończenia:
API jest dostępne przez publiczny URL
HTTPS działa poprawnie
Połączenie jest stabilne przez min. 24h
Dokumentacja konfiguracji tunelu
7. FAZA 6: POŁĄCZENIE UI Z BACKENDEM
7.1 Integracja Dashboard z API
Co zrobić:
Połączyć statystyki na dashboardzie z rzeczywistymi danymi z bazy
Zamiast hardcoded wartości, pobrać z API: liczbę SMS w kolejce, wysłanych dzisiaj, błędów, status systemu
Zaimplementować odświeżanie danych co 30 sekund
Dodać pull-to-refresh w tabeli historii
Połączyć przyciski szybkich akcji z faktycznymi funkcjami
7.2 Ekran History
Co zrobić:
Pobrać listę wiadomości z API /api/v1/sms/history
Wyświetlić w tabeli z paginacją
Dodać filtry: status, zakres dat
Dodać możliwość wyszukiwania po numerze telefonu
Opcja eksportu do CSV
7.3 Ekran Send SMS
Co zrobić:
Formularz z polami: numer telefonu, treść wiadomości, data/czas wysyłki
Walidacja numeru (format międzynarodowy)
Licznik znaków z informacją o ilości części SMS
Podgląd wiadomości przed wysłaniem
Wywołanie API POST /api/v1/sms/queue po wysłaniu
Potwierdzenie wysłania lub komunikat błędu
7.4 Ekran Settings
Co zrobić:
Zarządzanie tokenem API (generowanie, kopiowanie, usuwanie)
Ustawienia retry (ilość prób, opóźnienia)
Wybór karty SIM (jeśli urządzenie ma wiele)
Limit wysyłanych SMS na godzinę/dzień
Włączanie/wyłączanie automatycznego czyszczenia historii
Status uprawnień (SMS, notyfikacje)
Informacje o połączeniu Cloudflare Tunnel
Kryteria ukończenia:
Wszystkie ekrany wyświetlają rzeczywiste dane
Formularz wysyłania SMS działa
Ustawienia są zapisywane i działają
8. FAZA 7: MONITORING I OBSŁUGA BŁĘDÓW
8.1 System Logowania
Co zrobić:
Zaimplementować strukturalne logowanie (Logback lub podobne)
Poziomy logów: DEBUG, INFO, WARNING, ERROR
Logi zapisywane do pliku + wyświetlane w konsoli
Rotacja logów (max 7 dni lub 50MB)
Osobne logi dla: API requests, SMS sending, WorkManager, Errors
8.2 Obsługa Błędów
Co zrobić:
Szczegółowe kody błędów dla różnych sytuacji:
SMS_PERMISSION_DENIED
INVALID_PHONE_NUMBER
MESSAGE_TOO_LONG
NETWORK_ERROR
CARRIER_LIMIT_EXCEEDED
UNKNOWN_ERROR
Przyjazne komunikaty użytkownika w UI
Automatyczne raportowanie krytycznych błędów
Mechanizm powiadomień push przy poważnych problemach
8.3 Health Check Endpoint
Co zrobić:
Endpoint GET /api/v1/health zwracający:
Status serwera (up/down)
Status połączenia sieciowego
Dostępność uprawnień SMS
Liczba wiadomości w kolejce
Ostatni błąd (jeśli wystąpił)
Wersja aplikacji
Wykorzystanie pamięci
Kryteria ukończenia:
Logi zawierają wszystkie istotne operacje
Błędy są czytelne i możliwe do debugowania
Health check pokazuje rzeczywisty stan systemu
9. FAZA 8: TESTY I WALIDACJA
9.1 Testy Jednostkowe
Co przetestować:
Walidacja numeru telefonu (różne formaty)
Dzielenie długich wiadomości na części
Logika planowania (obliczanie czasu 18h/24h przed)
Autentykacja tokenu
Parsowanie JSON w API
Narzędzia:
JUnit 5 dla testów jednostkowych
MockK dla mockowania zależności
Truth dla asercji
9.2 Testy Integracyjne
Co przetestować:
Pełny flow: API request → Baza → WorkManager → SMS wysłany
Mechanizm retry przy błędach
Anulowanie zaplanowanej wiadomości
Synchronizacja między UI a backendem
9.3 Testy Manualne
Scenariusze:
Wysłanie SMS natychmiast
Zaplanowanie SMS na przyszłość
Anulowanie zaplanowanego SMS
Restart aplikacji z wiadomościami w kolejce
Brak połączenia sieciowego podczas wysyłki
Limit operatora (symulacja)
Długa wiadomość (>160 znaków)
Nieprawidłowy numer telefonu
Kryteria ukończenia:
Min. 70% code coverage testami jednostkowymi
Wszystkie kluczowe scenariusze mają testy integracyjne
Dokumentacja testów manualnych
10. POTENCJALNE PROBLEMY I ROZWIĄZANIA
Problem 1: Room w serwerze Ktor
Problem: Room jest zaprojektowany dla Android, nie dla JVM serwera.​
Rozwiązanie:
Opcja A: Użyć Exposed ORM zamiast Room (bardziej naturalne dla Ktor)
Opcja B: Oddzielić warstwę danych (Room dla UI, Exposed dla serwera)
Opcja C: Pozostać przy Room ale pamiętać o ograniczeniach
Problem 2: Android może zabić proces w tle
Problem: System Android agresywnie zarządza pamięcią.
Rozwiązanie:
Uruchomić serwer jako Foreground Service z trwałą notyfikacją
Użyć WorkManager z ConstraintLayout dla gwarantowanego wykonania
Dodać opcję "Battery optimization exclusion"
Problem 3: Limity operatora SMS
Problem: Operatorzy mogą blokować zbyt częste wysyłanie SMS.​
Rozwiązanie:
Implementacja rate limiting (np. max 30 SMS/godzinę)
Kolejkowanie z opóźnieniami między wiadomościami
Konfigurowalny interwał między SMS (domyślnie 2 sekundy)
Problem 4: Bezpieczeństwo tokenu
Problem: Token może wyciec w logach lub przechwyceniu ruchu.
Rozwiązanie:
Używać tylko HTTPS (Cloudflare Tunnel)
Nigdy nie logować pełnego tokenu
Opcja rotacji tokenu co X dni
Rozważyć dodanie IP whitelisting
Problem 5: Zużycie baterii
Problem: Serwer działający 24/7 rozładowuje baterię.
Rozwiązanie:
Dedykowane urządzenie podłączone do ładowarki
Doze mode exclusion dla krytycznych operacji
Optymalizacja zapytań do bazy (indeksy, cache)
Opcjonalnie: sleep mode gdy brak zadań
11. HARMONOGRAM WDROŻENIA
Tydzień 1: Bezpieczeństwo i Fundament
Dzień 1-2: Autentykacja Bearer Token
Dzień 3-5: Baza danych Room/Exposed
Dzień 6-7: Integracja Android SMS Manager
Tydzień 2: Funkcjonalność Core
Dzień 1-3: Wszystkie endpointy REST API
Dzień 4-7: WorkManager (Scheduler + Sender)
Tydzień 3: Integracja i UI
Dzień 1-3: Połączenie UI z backendem
Dzień 4-5: Ekrany History i Send SMS
Dzień 6-7: Ekran Settings
Tydzień 4: Zewnętrzny Dostęp i Testy
Dzień 1-3: Cloudflare Tunnel
Dzień 4-5: System logowania i błędów
Dzień 6-7: Testy jednostkowe i integracyjne
Tydzień 5: Finalizacja
Dzień 1-3: Testy manualne wszystkich scenariuszy
Dzień 4-5: Poprawki błędów i optymalizacja
Dzień 6-7: Dokumentacja użytkownika i developerska
12. KRYTERIA SUKCESU PROJEKTU
Funkcjonalne
✅ Aplikacja może wysyłać SMS programowo przez API
✅ Wiadomości mogą być zaplanowane na przyszłość (18h przed → wysyłka 24h przed)
✅ System retry działa przy błędach
✅ Historia wszystkich wiadomości jest dostępna
✅ API jest zabezpieczone autentykacją
✅ Zewnętrzny dostęp przez HTTPS działa
Niefunkcjonalne
✅ Aplikacja działa stabilnie przez min. 7 dni bez restartów
✅ Czas odpowiedzi API < 500ms
✅ Baza przechowuje min. 10,000 wiadomości bez problemów
✅ Zużycie baterii < 5% na godzinę (urządzenie na ładowarce)
✅ Code coverage testami > 70%
Bezpieczeństwo
✅ Wszystkie endpointy wymagają autentykacji
✅ Ruch szyfrowany przez HTTPS
✅ Brak wrażliwych danych w logach
✅ Rate limiting chroni przed nadużyciami
13. DOKUMENTACJA DO STWORZENIA
Dla Użytkowników
Quick Start Guide - jak zainstalować i uruchomić w 5 minut
API Documentation - wszystkie endpointy z przykładami curl
Troubleshooting Guide - najczęstsze problemy i rozwiązania
FAQ - odpowiedzi na typowe pytania
Dla Deweloperów
Architecture Overview - diagram architektury systemu
Database Schema - struktura tabel i relacje
API Contract - szczegółowa specyfikacja wszystkich endpointów (OpenAPI/Swagger)
Deployment Guide - jak wdrożyć w produkcji
Contributing Guidelines - jak dodawać nowe funkcje