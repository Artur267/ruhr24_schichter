@echo off
REM Setzt den Titel des Hauptfensters
TITLE Ruhr24 Schichter - Development Launcher

ECHO ===================================================
ECHO  Starting Development Environment...
ECHO ===================================================
ECHO.

REM Schritt 1: Starte das Java Spring Boot Backend
ECHO [1/3] Starting Java Backend (mvn spring-boot:run)...
REM Wechselt in den optaplanner-Ordner und startet den Maven-Befehl in einem neuen Fenster
start "Java Backend" cmd /k "cd java\optaplanner && mvnw.cmd spring-boot:run"

ECHO.
ECHO Waiting for Java Backend to initialize (15 seconds)...
timeout /t 15 >nul

REM Schritt 2: Starte den Node.js Server
ECHO [2/3] Starting Node.js Server (node server.js)...
REM Startet den Node-Server im Hauptverzeichnis in einem neuen Fenster
start "Node.js Server" cmd /k "node server.js"

ECHO.
ECHO Waiting for Node.js Server to initialize (5 seconds)...
timeout /t 5 >nul

REM Schritt 3: Starte den React Vite Dev-Server
ECHO [3/3] Starting React Frontend (npm run dev)...
REM Wechselt in den client-Ordner und startet den Vite-Server in einem neuen Fenster
start "React Frontend" cmd /k "cd client && npm run dev"

ECHO.
ECHO ===================================================
ECHO  All services launched!
ECHO  Your browser should open automatically.
ECHO ===================================================

REM Kurze Pause, bevor das Hauptfenster sich schließt
timeout /t 5 >nul
