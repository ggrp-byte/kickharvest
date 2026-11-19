# Kick Miner Drop — aplikacje natywne

Dwie proste aplikacje do automatycznego nabijania czasu na dropy Kick (kampania GTA 6 „Sunset Drop", do 1.01.2027):

## Komputer (Windows)

**Plik: `KickMinerDrop.exe`** (w głównym folderze) — kliknij dwa razy i gotowe. Nie wymaga żadnej instalacji ani żadnych programów. (Alternatywa dla ciekawskich: `desktop/start.bat` uruchamia tę samą aplikację z kodu źródłowego.)

Co daje aplikacja na komputerze:
- Zwykłe okno ze znanym interfejsem (kanały, cele, START).
- Tryb „osobne okno" działa w **ukrytym oknie aplikacji**: stream leci bez obrazu i bez dźwięku, wymuszona jakość 160p (mało internetu i procesora), automatyczne wyciszanie.
- Kliknięcie **X nie wyłącza aplikacji** — pracuje dalej w tle, ikona przy zegarze. Zakończ przez prawy klik na ikonę → „Zakończ".
- Logowanie: raz wciśnij w aplikacji przycisk, żeby otworzyć kick.com i się zalogować (aplikacja zapamięta).
- Na noc: kliknij START, zamknij okno (X), wyłącz monitor. Liczy się całą noc.

## Telefon (Android)

**Plik: `KickMinerDrop.apk`** — skopiuj go na telefon (WhatsApp, mail, Dysk albo kabel USB), otwórz plik i zezwól na instalację z nieznanych źródeł (Android sam zapyta).

Co daje aplikacja na telefonie:
- Ikona na ekranie głównym, działa jak zwykła aplikacja — bez przeglądarki.
- Ekran nie gaśnie, gdy aplikacja jest otwarta (natywny tryb „nie wygaszaj").
- Wbudowany tryb „w tej karcie" z pełną stroną kick.com — możesz się w niej zalogować bezpośrednio w aplikacji.
- Kategorie kanałów są pokazywane na kartach (szukaj „Grand Theft Auto VI").
- **Uwaga (ograniczenie Androida, nie aplikacji):** przy wyłączonym ekranie stream się nie liczy. Do nabijania nocnego służy komputer.

## Nowe funkcje (wersja 1.1)

- **Inteligentne przełączanie** — bez ręcznego ustawiania czasu. Aplikacja sama wykrywa, że streamer zszedł z anteny (dwa potwierdzenia co ~20 s) i przeskakuje na następny kanał. Po wpisaniu „Kategorii dropu" (np. `GTA`) przeskakuje też, gdy streamer zmieni grę na inną — i podświetla kanały plakietką **DROP**.
- **Postęp dropów w aplikacji** (tylko Windows) — panel u góry: przycisk **Zaloguj się na kick.com** (otworzy okno logowania — wystarczy raz) i **Odśwież postęp dropów**: pokazuje kampanie, pasek postępu każdej nagrody i status „odebrano". Przy każdej kampanii przycisk **„Dodaj aktywne streamy z tej gry"** — sam znajdzie kanały, które teraz grają w daną grę.
- **Zweryfikowane na Twoim koncie:** kampania GTA 6 „Sunset Drop" wymaga **250 godzin** oglądania (nagroda „SunsetK Badge", do 1.01.2027). Twój postęp widać na kick.com/drops — i w aplikacji po zalogowaniu.

## Zasada działania (ta sama w obu)

1. Dodaj kanał (np. `xqc` albo link `kick.com/xqc`) i kliknij START.
2. Aplikacja sama sprawdza, kto jest LIVE (zielona kropka), ogląda stream wyciszony i zmienia kanał co ustawione minuty.
3. Postęp dropów sprawdzasz na kick.com — tam się nalicza na Twoje konto.
4. Drop GTA 6 liczy się z każdego streamu w kategorii GTA 6 — możesz ustawić „Cel łączny" (suma ze wszystkich kanałów) zamiast osobnych celów.
