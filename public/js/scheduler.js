document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("schedule-form");
  const output = document.getElementById("shift-output");
  const statusMessage = document.getElementById("statusMessage");

  form.addEventListener("submit", async (e) => {
    e.preventDefault(); // Standard-Formular-Absendeverhalten verhindern

    statusMessage.textContent = 'Planung wird gestartet...'; // Initialisiere Statusmeldung
    output.textContent = 'Warte auf Ergebnis...'; // Initialisiere Ergebnisbereich

    // Eingabewerte aus den HTML-Elementen abrufen
    const von = document.getElementById("von").value;
    const bis = document.getElementById("bis").value;
    // Sicherstellen, dass das Element existiert, bevor .value aufgerufen wird
    const selectedRessort = document.getElementById("ressort") ? document.getElementById("ressort").value : '';

    console.log("[DEBUG] Planung angefordert für Zeitraum:", von, "bis", bis);
    console.log("[DEBUG] Ausgewähltes Ressort:", selectedRessort);

    // Grundlegende Validierung der Eingabedaten
    if (!von || !bis || new Date(bis) < new Date(von)) {
      alert("Bitte wähle einen gültigen Zeitraum (Startdatum darf nicht nach Enddatum liegen).");
      statusMessage.textContent = 'Fehler: Ungültiger Zeitraum.';
      return;
    }

    try {
      console.log("[DEBUG] Lade Mitarbeiterdaten...");
      // Sicherstellen, dass der /mitarbeiter-daten Endpunkt im Node.js Backend existiert und korrekte Daten zurückgibt
      const employees = await fetch('/mitarbeiter-daten').then(res => {
          if (!res.ok) {
              throw new Error(`Fehler beim Laden der Mitarbeiterdaten: ${res.status} ${res.statusText}`);
          }
          return res.json();
      });
      console.log("[DEBUG] Mitarbeiterdaten geladen:", employees);

      const requestBody = {
        von: von,
        bis: bis,
        ressort: selectedRessort,
        mitarbeiterList: employees.map(mitarbeiter => ({
          id: mitarbeiter.id,
          nachname: mitarbeiter.nachname,
          vorname: mitarbeiter.vorname,
          ressort: mitarbeiter.ressort,
          // ACHTUNG: Stelle sicher, dass mitarbeiter.wochenstunden existiert und richtig benannt ist!
          // Oder passe es an, wenn dein /mitarbeiter-daten Endpoint es anders liefert (z.B. als 'stunden')
          wochenstunden: mitarbeiter.wochenstunden != null ? parseInt(mitarbeiter.wochenstunden, 10) : 0,
          cvd: mitarbeiter.cvd ?? false, // Immer noch gut, einen Default zu haben

          // ***************************************************************
          // HIER DIE NEUEN FELDER HINZUFÜGEN, GENAU WIE IM JAVA-DTO!
          // ABER: Stelle sicher, dass sie auch vom /mitarbeiter-daten Endpoint kommen!
          // ***************************************************************
          email: mitarbeiter.email ?? null, // Oder "" falls String erwartet
          stellenbezeichnung: mitarbeiter.stellenbezeichnung ?? null, // Oder ""
          notizen: mitarbeiter.notizen ?? null, // Oder ""
          rollenUndQualifikationen: mitarbeiter.rollenUndQualifikationen ?? [], // Sollte ein Array sein
          teamsUndZugehoerigkeiten: mitarbeiter.teamsUndZugehoerigkeiten ?? [], // Sollte ein Array sein
          wunschschichten: mitarbeiter.wunschschichten ?? [], // Falls vorhanden
          urlaubstageSet: mitarbeiter.urlaubstageSet ?? [] // Falls vorhanden, und Korrektur des Namens
        }))
      };

      console.log("[DEBUG] Sende Planungsanfrage an das Java-Backend (über Node.js Proxy)...");

      // 1. POST-Request zum Starten der Planung an DEIN NODE.JS ENDPUNKT `/starte-scheduler`
      // Node.js leitet diesen dann an dein Java-Backend weiter.
      const response = await fetch('/starte-scheduler', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody),
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP-Fehler beim Starten der Planung! Status: ${response.status}, Nachricht: ${errorText}`);
      }

      // Der Node.js Endpunkt /starte-scheduler MUSS jetzt die problemId vom Java-Backend weitergeben.
      // Annahme: Node.js gibt ein JSON-Objekt zurück wie `{ message: "Planung gestartet mit ID ...", problemId: "..." }`
      const startResult = await response.json(); // Muss jetzt JSON sein!
      const problemId = startResult.problemId; // Extrahieren der ID

      if (!problemId) {
          throw new Error('Fehler: Keine Problem-ID vom Server erhalten.');
      }
      
      statusMessage.textContent = `Planung (ID: ${problemId}) wurde gestartet. Bitte warten...`;

      // 2. Polling für das Ergebnis über den neuen GET-Endpunkt des Java-Backends (via Node.js Proxy)
      const intervalId = setInterval(async () => {
        try {
            const resultResponse = await fetch(`/get-planungs-ergebnis/${problemId}`); // Neuer Node.js Proxy Endpunkt!

            if (resultResponse.status === 202) {
                statusMessage.textContent += '.'; // Zeige dem Benutzer, dass noch gewartet wird
            } else if (resultResponse.status === 200) {
                clearInterval(intervalId); // Polling stoppen
                const finalSolution = await resultResponse.json();
                statusMessage.textContent = `Planung (ID: ${problemId}) abgeschlossen!`;
                // Zeige nur relevante Daten aus zuteilungList an
                output.textContent = `Planung erfolgreich! Score: ${finalSolution.score || 'N/A'}\n\nGenerierte Zuteilungen:\n${JSON.stringify(finalSolution.zuteilungList.map(z => ({
                    mitarbeiter: z.mitarbeiter ? `${z.mitarbeiter.vorname} ${z.mitarbeiter.nachname}` : 'UNBESETZT',
                    schicht: z.schicht ? `${z.schicht.datum} ${z.schicht.startZeit}-${z.schicht.endZeit} (${z.schicht.ressortBedarf})` : 'Keine Schicht zugewiesen'
                })), null, 2)}`;
                
                alert("Schichtplanung erfolgreich abgeschlossen!");
                console.log('Finale Lösung:', finalSolution);
                // Hier kannst du die finale Lösung in einer Tabelle oder ähnlichem anzeigen
            } else if (resultResponse.status === 404) {
                clearInterval(intervalId); // Polling stoppen
                statusMessage.textContent = `Fehler: Planung mit ID ${problemId} nicht gefunden.`;
                output.textContent = 'Ergebnis nicht verfügbar.';
                alert('Planung nicht gefunden. Eventuell abgelaufen oder falsche ID.');
            } else {
                clearInterval(intervalId); // Polling stoppen
                const errorText = await resultResponse.text();
                statusMessage.textContent = `Fehler beim Abrufen der Planung: ${resultResponse.status}, ${errorText}`;
                output.textContent = 'Fehler beim Laden des Ergebnisses.';
                console.error('Fehler beim Abrufen der Planung:', resultResponse.status, errorText);
            }
        } catch (pollingError) {
            clearInterval(intervalId); // Polling stoppen
            statusMessage.textContent = `Ein Fehler beim Polling ist aufgetreten: ${pollingError.message}`;
            output.textContent = 'Fehler beim Laden des Ergebnisses.';
            console.error('Polling-Fehler:', pollingError);
        }
      }, 3000); // Alle 3 Sekunden nachfragen

    } catch (err) {
      console.error("Fehler bei der Planung:", err);
      statusMessage.textContent = `Ein Fehler ist aufgetreten: ${err.message}`;
      output.textContent = `Fehler bei der Planung. Details siehe Konsole: ${err.message}`;
      alert(`Ein Fehler ist aufgetreten: ${err.message}`);
    }
  });
});