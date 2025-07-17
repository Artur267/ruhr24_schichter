#!/bin/bash

# ===================================================================================
#  RUHR24 SCHICHTER - DEVELOPMENT LAUNCHER für macOS
# ===================================================================================
#  Anleitung:
#  1. Speichere diese Datei als "start-dev.command" im Projekt-Hauptordner.
#  2. Öffne ein Terminal, gehe in den Projektordner und mache die Datei
#     einmalig ausführbar mit dem Befehl: chmod +x start-dev.command
#  3. Von nun an kannst du die Anwendung einfach durch einen Doppelklick
#     auf diese Datei starten.
# ===================================================================================

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "==================================================="
echo "  Starting Development Environment..."
echo "  Project Root: $SCRIPT_DIR"
echo "==================================================="
echo ""

echo "[1/3] Starting Java Backend (mvnw spring-boot:run)..."
osascript -e 'tell app "Terminal" to do script "cd \"'$SCRIPT_DIR'/java/optaplanner\" && mvn spring-boot:run"'

echo ""
echo "Waiting for Java Backend to initialize (15 seconds)..."
sleep 15

echo "[2/3] Starting Node.js Server (node server.js)..."
osascript -e 'tell app "Terminal" to do script "cd \"'$SCRIPT_DIR'\" && node server.js"'

echo ""
echo "Waiting for Node.js Server to initialize (5 seconds)..."
sleep 5

echo "[3/3] Starting React Frontend (npm run dev)..."
osascript -e 'tell app "Terminal" to do script "cd \"'$SCRIPT_DIR'/client\" && npm run dev"'

echo ""
echo "==================================================="
echo "  All services launched!"
echo "  Your browser should open automatically."
echo "==================================================="

sleep 5
open -a "Google Chrome" http://localhost:5173

