document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("schedule-form");
  const output = document.getElementById("shift-output");
  const statusMessage = document.getElementById("statusMessage");
  const csvExportButton = document.getElementById("csv-export-button"); // Dieser Button kann immer noch für einen manuellen Save genutzt werden

  let pollingIntervalId;
  let countdownIntervalId;
  let solverTimeoutMillis = 0;
  let startTime;
  let finalSchichtPlanSolution = null; // Zum Speichern der finalen Lösung

  // Funktion zum Starten und Aktualisieren des Countdowns
  function startCountdown() {
    startTime = Date.now();
    countdownIntervalId = setInterval(() => {
      const elapsedTime = Date.now() - startTime;
      const remainingTimeMillis = solverTimeoutMillis - elapsedTime;

      if (remainingTimeMillis <= 0) {
        clearInterval(countdownIntervalId);
        statusMessage.textContent = 'Planung läuft noch... (Suche nach besserer Lösung)';
      } else {
        const remainingSeconds = Math.ceil(remainingTimeMillis / 1000);
        const minutes = Math.floor(remainingSeconds / 60);
        const seconds = remainingSeconds % 60;
        statusMessage.textContent = `Planung läuft... Verbleibende Zeit: ${minutes}m ${seconds}s`;
      }
    }, 1000);
  }

  // Funktion zum Stoppen aller Intervalle
  function stopAllIntervals() {
    if (pollingIntervalId) {
      clearInterval(pollingIntervalId);
      pollingIntervalId = null;
    }
    if (countdownIntervalId) {
      clearInterval(countdownIntervalId);
      countdownIntervalId = null;
    }
  }

  // NEU: Funktion zum Speichern der Schichtplan-Lösung als CSV auf dem Server
  async function saveSchichtplanToCSV(solution) {
      statusMessage.textContent = "Speichere Planung als CSV...";
      try {
          const response = await fetch('/save-schichtplan-csv', { // Sende an den Node.js Backend-Endpunkt
              method: 'POST',
              headers: {
                  'Content-Type': 'application/json',
              },
              body: JSON.stringify(solution), // Sende die gesamte Lösung als JSON
          });

          if (response.ok) {
              const result = await response.json();
              console.log("[FRONTEND] CSV-Speicherung erfolgreich:", result);
              statusMessage.textContent = "Planung als CSV auf dem Server gespeichert!";
              alert(result.message || "CSV erfolgreich gespeichert!"); // Optional: Benachrichtigung anzeigen
          } else {
              const errorText = await response.text();
              console.error("[FRONTEND] Fehler beim Speichern der CSV:", response.status, errorText);
              statusMessage.textContent = `Fehler beim Speichern der CSV: ${response.status}`;
              alert(`Fehler beim Speichern der CSV: ${errorText}`); // Optional: Benachrichtigung anzeigen
          }
      } catch (error) {
          console.error("[FRONTEND] Netzwerkfehler CSV-Speicherung:", error);
          statusMessage.textContent = `Fehler beim Speichern der CSV: ${error.message}`;
          alert(`Netzwerkfehler beim Speichern der CSV: ${error.message}`); // Optional: Benachrichtigung anzeigen
      }
  }

  // Event Listener für den CSV Export Button (optional: für manuellen Save)
  if (csvExportButton) {
      csvExportButton.addEventListener("click", async () => {
          if (finalSchichtPlanSolution) {
              await saveSchichtplanToCSV(finalSchichtPlanSolution); // Manuelles Speichern
          } else {
              alert("Keine finale Lösung zum Exportieren verfügbar. Bitte starten Sie zuerst eine Planung.");
          }
      });
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    statusMessage.textContent = 'Planung wird gestartet...';
    if (output) {
        output.innerHTML = 'Warte auf Ergebnis...';
    }
    if (csvExportButton) {
        csvExportButton.disabled = true; // Button deaktivieren, bis eine neue Lösung da ist
    }
    stopAllIntervals();
    finalSchichtPlanSolution = null; // Alte Lösung zurücksetzen

    const von = document.getElementById("von").value;
    const bis = document.getElementById("bis").value;
    const ressortSelect = document.getElementById("ressort");
    const selectedRessort = ressortSelect ? ressortSelect.value : '';

    console.log("[DEBUG] Planung angefordert für Zeitraum:", von, "bis", bis);
    console.log("[DEBUG] Ausgewähltes Ressort:", selectedRessort);

    if (!von || !bis || new Date(bis) < new Date(von)) {
      alert("Bitte wähle einen gültigen Zeitraum (Startdatum darf nicht nach Enddatum liegen).");
      statusMessage.textContent = 'Fehler: Ungültiger Zeitraum.';
      return;
    }

    try {
      console.log("[DEBUG] Lade Mitarbeiterdaten...");
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
          wochenstunden: mitarbeiter.wochenstunden != null ? parseInt(mitarbeiter.wochenstunden, 10) : 0,
          cvd: mitarbeiter.cvd ?? false,
          email: mitarbeiter.email ?? null,
          stellenbezeichnung: mitarbeiter.stellenbezeichnung ?? null,
          notizen: mitarbeiter.notizen ?? null,
          rollenUndQualifikationen: mitarbeiter.rollenUndQualifikationen ?? [],
          teamsUndZugehoerigkeiten: mitarbeiter.teamsUndZugehoerigkeiten ?? [],
          wunschschichten: mitarbeiter.wunschschichten ?? [],
          urlaubtageSet: mitarbeiter.urlaubtageSet ?? []
        }))
      };

      console.log("[DEBUG] Sende Planungsanfrage an das Java-Backend (über Node.js Proxy)...");

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

      const startResult = await response.json();
      const problemId = startResult.problemId;
      solverTimeoutMillis = startResult.solverTimeoutMillis || (15 * 60 * 1000);

      if (!problemId) {
          throw new Error('Fehler: Keine Problem-ID vom Server erhalten.');
      }
      
      startCountdown();

      pollingIntervalId = setInterval(async () => {
        try {
            const resultResponse = await fetch(`/planungs-ergebnis/${problemId}`);

            if (resultResponse.status === 202) {
                const contentType = resultResponse.headers.get("content-type");
                let currentSolution = null;
                if (contentType && contentType.includes("application/json")) {
                    try {
                        currentSolution = await resultResponse.json();
                    } catch (e) {
                        console.warn("[FRONTEND] Fehler beim Parsen der 202 JSON-Antwort (möglicherweise leer oder ungültig):", e);
                    }
                }
                
                if (output && currentSolution && currentSolution.score) {
                     output.innerHTML = `Planung läuft... Aktueller Score: ${currentSolution.score ? JSON.stringify(currentSolution.score) : 'N/A'}`;
                }
            } else if (resultResponse.status === 200) {
                stopAllIntervals();
                const finalSolution = await resultResponse.json();
                finalSchichtPlanSolution = finalSolution; // Finale Lösung speichern

                if (csvExportButton) {
                    csvExportButton.disabled = false; // Export-Button aktivieren
                }

                statusMessage.textContent = `Planung (ID: ${problemId}) abgeschlossen!`;
                
                if (output) {
                    let outputHtml = `<h3>Planung abgeschlossen!</h3>
                                      <p>Score: <strong>${finalSolution.score ? JSON.stringify(finalSolution.score) : 'N/A'}</strong></p>
                                      <h4>Generierte Zuteilungen:</h4>`;
                    
                    if (finalSolution.schichtBlockList && finalSolution.schichtBlockList.length > 0) {
                        const assignedBlocks = finalSolution.schichtBlockList.filter(block => block.mitarbeiter);
                        const unassignedBlocks = finalSolution.schichtBlockList.filter(block => !block.mitarbeiter);

                        outputHtml += `<h5>Zugewiesene Schichtblöcke (${assignedBlocks.length}):</h5><ul>`;
                        assignedBlocks.forEach(block => {
                            const blockInfo = block.blockTyp || 'Unbekannter Block';
                            const mitarbeiterName = block.mitarbeiter ? `${block.mitarbeiter.vorname} ${block.mitarbeiter.nachname}` : 'N/A';
                            const schichtDetails = (block.schichtenImBlock || []).map(s => `${s.datum} ${s.startZeit}-${s.endZeit}`).join('; ');
                            const datumDisplay = block.blockStartDatum && block.blockEndDatum ? `(${block.blockStartDatum} bis ${block.blockEndDatum})` : '';
                            outputHtml += `<li><strong>${blockInfo} ${datumDisplay}</strong> zugewiesen an: ${mitarbeiterName}<br>Schichten: ${schichtDetails}</li>`;
                        });
                        outputHtml += `</ul>`;

                        if (unassignedBlocks.length > 0) {
                            outputHtml += `<h5>Unbesetzte Schichtblöcke (${unassignedBlocks.length}):</h5><ul>`;
                            unassignedBlocks.forEach(block => {
                                const blockInfo = block.blockTyp || 'Unbekannter Block';
                                const schichtDetails = (block.schichtenImBlock || []).map(s => `${s.datum} ${s.startZeit}-${s.endZeit}`).join('; ');
                                const datumDisplay = block.blockStartDatum && block.blockEndDatum ? `(${block.blockStartDatum} bis ${block.blockEndDatum})` : '';
                                outputHtml += `<li><strong>${blockInfo} ${datumDisplay}</strong>: UNBESETZT<br>Schichten: ${schichtDetails}</li>`;
                            });
                            outputHtml += `</ul>`;
                            outputHtml += `<p style="color: red;">Es sind noch ${unassignedBlocks.length} Schichtblöcke unbesetzt (Hard-Constraint-Verletzungen).</p>`;
                        }

                    } else {
                        outputHtml += `<p>Keine Schichtblöcke in der Lösung gefunden.</p>`;
                    }
                    output.innerHTML = outputHtml;
                }
                
                alert("Schichtplanung erfolgreich abgeschlossen!");
                console.log('Finale Lösung:', finalSolution);

                // --- HIER WIRD DIE CSV AUF DEM SERVER AUTOMATISCH GESPEICHERT ---
                await saveSchichtplanToCSV(finalSolution);

            } else if (resultResponse.status === 404) {
                stopAllIntervals();
                const errorData = await resultResponse.json();
                statusMessage.textContent = `Fehler: ${errorData.error || 'Planung mit ID nicht gefunden.'}`;
                if (output) output.textContent = 'Ergebnis nicht verfügbar.';
                alert(errorData.error || 'Planung nicht gefunden. Eventuell abgelaufen oder falsche ID.');
            } else {
                stopAllIntervals();
                const errorData = await resultResponse.json();
                statusMessage.textContent = `Fehler beim Abrufen der Planung: ${resultResponse.status}, ${errorData.error || 'Unbekannter Fehler'}`;
                if (output) output.textContent = 'Fehler beim Laden des Ergebnisses.';
                console.error('Fehler beim Abrufen der Planung:', resultResponse.status, errorData);
            }
        } catch (pollingError) {
            stopAllIntervals();
            statusMessage.textContent = `Ein Fehler beim Polling ist aufgetreten: ${pollingError.message}`;
            if (output) output.textContent = 'Fehler beim Laden des Ergebnisses.';
            console.error('Polling-Fehler:', pollingError);
        }
      }, 3000);
    } catch (err) {
      stopAllIntervals();
      console.error("Fehler bei der Planung:", err);
      statusMessage.textContent = `Ein Fehler ist aufgetreten: ${err.message}`;
      if (output) output.textContent = `Fehler bei der Planung. Details siehe Konsole: ${err.message}`;
      alert(`Ein Fehler ist aufgetreten: ${err.message}`);
    }
  });
});
