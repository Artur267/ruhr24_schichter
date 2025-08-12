// client/src/pages/PlanungErstellen/PlanungErstellen.jsx

import React, { useState, useEffect, useRef } from 'react';
import styles from './PlanungErstellen.module.css'; // Wir erstellen gleich das CSS
import { Button, TextInput, NumberInput } from '@mantine/core';
import { IconRocket } from '@tabler/icons-react';
import { startOfISOWeek, setISOWeek } from 'date-fns';


function transformLiveSolutionForDisplay(liveSolution) {
    if (!liveSolution || !liveSolution.mitarbeiterList || !liveSolution.arbeitsmusterList) {
        return { mitarbeiterList: [] }; // Leere Daten zurückgeben, wenn die Struktur unerwartet ist
    }

    // 1. Erstelle eine Map aller Mitarbeiter für schnellen Zugriff
    const mitarbeiterMap = new Map();
    liveSolution.mitarbeiterList.forEach(m => {
        mitarbeiterMap.set(m.id, { ...m, Arbeitszeiten: {} });
    });

    // 2. Gehe durch alle Arbeitsmuster und verteile die Schichten
    liveSolution.arbeitsmusterList.forEach(muster => {
        // Nimm nur die Muster, die bereits einem Mitarbeiter zugewiesen sind
        if (muster.mitarbeiter && muster.mitarbeiter.id) {
            const mitarbeiter = mitarbeiterMap.get(muster.mitarbeiter.id);
            if (mitarbeiter && muster.schichten) {
                muster.schichten.forEach(schicht => {
                    // Füge die Schicht zu den Arbeitszeiten des Mitarbeiters hinzu
                    mitarbeiter.Arbeitszeiten[schicht.datum] = {
                        Von: schicht.startZeit.substring(0, 5),
                        Bis: schicht.endZeit.substring(0, 5)
                    };
                });
            }
        }
    });

    // 3. Gib die angereicherte Mitarbeiterliste zurück
    return { 
        ...liveSolution, // Behalte andere Infos wie den Score bei
        mitarbeiterList: Array.from(mitarbeiterMap.values()) 
    };
}


function getMondayFromISOWeek(weekString) {
    if (!weekString) return null;
    const [year, week] = weekString.split('-W').map(Number);
    if (isNaN(year) || isNaN(week)) return null;
    
    // 1. Finde den 4. Januar des Jahres (ist laut ISO 8601 immer in der ersten Woche)
    const jan4 = new Date(Date.UTC(year, 0, 4));
    
    // 2. Finde den Montag dieser ersten Woche
    const jan4Day = jan4.getUTCDay() || 7; // Mache Sonntag (0) zu 7
    const mondayOfFirstWeek = new Date(jan4.valueOf());
    mondayOfFirstWeek.setUTCDate(jan4.getUTCDate() - jan4Day + 1);

    // 3. Füge die Anzahl der Wochen hinzu (abzüglich der ersten Woche)
    const targetMonday = new Date(mondayOfFirstWeek.valueOf());
    targetMonday.setUTCDate(mondayOfFirstWeek.getUTCDate() + (week - 1) * 7);
    
    return targetMonday;
}


function PlanungErstellen() {
    // State für die Formulardaten
    const [startWoche, setStartWoche] = useState('');
    const [dauer, setDauer] = useState(4);
    
    const [problemId, setProblemId] = useState(null);
    const [statusMessage, setStatusMessage] = useState('Bereit zum Starten.');
    const [liveSolution, setLiveSolution] = useState(null);
    const [isPolling, setIsPolling] = useState(false);
    const pollingIntervalRef = useRef(null);


    const handleSavePlan = async (solution) => {
        setStatusMessage("Speichere Plan in der Datenbank...");
        try {
            const response = await fetch('/api/plan', { // Ruft die neue DB-Route auf
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(solution),
            });
            if (!response.ok) throw new Error('Fehler beim Speichern in der DB.');
            
            const result = await response.json();
            setStatusMessage(result.message || "Plan erfolgreich gespeichert!");
            alert("Plan wurde erfolgreich übernommen!");
        } catch (error) {
            setStatusMessage(`Fehler: ${error.message}`);
            alert(`Fehler: ${error.message}`);
        }
    };

    const saveSchichtplanToCSV = async (solution) => {
        setStatusMessage("Speichere finale Planung als CSV...");
        try {
            // Wichtig: Auch diese Route braucht das /api Präfix
            const response = await fetch('/api/plan', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(solution),
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Fehler beim Speichern: ${errorText}`);
            }
            const result = await response.json();
            setStatusMessage(`Planung als CSV auf dem Server gespeichert! (${result.message})`);
            console.log("[FRONTEND] CSV-Speicherung erfolgreich.");
        } catch (error) {
            console.error("[FRONTEND] Fehler beim Speichern der CSV:", error);
            setStatusMessage(`Fehler beim Speichern der CSV: ${error.message}`);
        }
    };

    useEffect(() => {
        if (!problemId || !isPolling) {
            clearInterval(pollingIntervalRef.current);
            return;
        }

        const pollSolution = async () => {
            try {
                const response = await fetch(`/api/planungs-ergebnis/${problemId}`);

                // WICHTIG: Den Body erst lesen, NACHDEM wir den Status geprüft haben!

                if (response.status === 200) { // Fertig!
                    const finalSolution = await response.json(); // Lese den Body HIER (1. Mal)
                    const transformed = transformLiveSolutionForDisplay(finalSolution);
                    
                    setLiveSolution(transformed);
                    setStatusMessage(`Planung abgeschlossen! Score: ${transformed.score?.softScore || 'N/A'}`);
                    setIsPolling(false);
                    clearInterval(pollingIntervalRef.current);
                    
                } else if (response.status === 202) { // Läuft noch...
                    const intermediateSolution = await response.json(); // ODER HIER (1. Mal)
                    const transformed = transformLiveSolutionForDisplay(intermediateSolution);

                    setLiveSolution(transformed);
                    setStatusMessage(`Planung läuft... Score: ${transformed.score?.softScore || 'wird berechnet'}`);

                } else { // Ein Fehler ist aufgetreten
                    // Im Fehlerfall lesen wir den Body als Text, um eine detailliertere Meldung zu bekommen
                    const errorText = await response.text();
                    throw new Error(`Server-Fehler: ${response.status} - ${errorText}`);
                }
            } catch (error) {
                console.error("Polling-Fehler:", error);
                setStatusMessage(`Fehler bei der Verbindung zum Server.`);
                setIsPolling(false);
                clearInterval(pollingIntervalRef.current); // Wichtig: auch im Fehlerfall stoppen
            }
        };



        pollingIntervalRef.current = setInterval(pollSolution, 3000); // Alle 3 Sekunden nachfragen

        return () => clearInterval(pollingIntervalRef.current); // Aufräumen
    }, [problemId, isPolling]);

    const handleStartPlanung = async (event) => {
        event.preventDefault();
        const vonDatum = getMondayFromISOWeek(startWoche);
        if (!vonDatum) return alert("Bitte eine gültige Startwoche auswählen.");

        setStatusMessage('Berechne Zeitraum und starte Planung...');
        const bisDatum = new Date(vonDatum.valueOf());
        bisDatum.setUTCDate(vonDatum.getUTCDate() + 6 + (dauer - 1) * 7);
        const vonISO = vonDatum.toISOString().split('T')[0];
        const bisISO = bisDatum.toISOString().split('T')[0];

        console.log(`Berechneter Zeitraum: ${vonISO} bis ${bisISO}`);

        try {
            const mitarbeiterResponse = await fetch('/api/mitarbeiter-daten');
            if (!mitarbeiterResponse.ok) throw new Error('Mitarbeiterdaten konnten nicht geladen werden.');
            
            // KORREKTUR: Die Antwort IST bereits das Array, das wir brauchen.
            const mitarbeiterList = await mitarbeiterResponse.json(); 

            // Die Sicherheitsabfrage
            if (!Array.isArray(mitarbeiterList)) {
                throw new Error("Die vom Server empfangenen Mitarbeiterdaten sind kein gültiges Array.");
            }

            // Jetzt wird das korrekte Array an den Server gesendet
            const response = await fetch('/api/starte-scheduler', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ von: vonISO, bis: bisISO, mitarbeiterList: mitarbeiterList })
            });

            if (!response.ok) throw new Error('Fehler beim Starten des Schedulers.');
            
            const result = await response.json();
            setProblemId(result.problemId);
            setIsPolling(true);
        } catch (error) {
            console.error("Fehler beim Starten der Planung:", error);
            setStatusMessage(`Fehler: ${error.message}`);
        }
    };

    return (
        <div className={styles.container}>
            <form onSubmit={handleStartPlanung} className={styles.form}>
                <h2>Neue Planung erstellen</h2>
                
                {/* Ersetze die alten Inputs durch Mantine-Inputs */}
                <TextInput
                  label="Start-Kalenderwoche"
                  type="week"
                  value={startWoche}
                  onChange={(event) => setStartWoche(event.currentTarget.value)}
                  required
                />
                <NumberInput
                  label="Dauer in Wochen"
                  value={dauer}
                  onChange={setDauer}
                  min={1}
                  max={8}
                  required
                />

                {/* Ersetze den alten Button durch einen Mantine-Button */}
                <Button type="submit" loading={isPolling} color="orange" size="md">
                    Planung starten
                </Button>
            </form>

            <div className={styles.status}>
                <strong>Status:</strong> {statusMessage}
            </div>

            {/* Hier ist deine Live-Ansicht! */}
            <div className={styles.liveView}>
                <h3>Live-Planungsfortschritt</h3>
                {liveSolution ? (
                    <SchichtplanTable 
                        mitarbeiter={liveSolution.mitarbeiterList} 
                        dates={liveSolution.dates} 
                        datesISO={liveSolution.datesISO} 
                    />
                ) : (
                    <p>Noch keine Lösung vorhanden. Bitte Planung starten.</p>
                )}
            </div>

            {liveSolution && !isPolling && (
                <Button 
                    onClick={() => handleSavePlan(liveSolution)} 
                    size="lg" 
                    color="green" 
                    mt="xl" 
                    fullWidth
                >
                    Diesen Plan übernehmen und in der Datenbank speichern
                </Button>
            )}
        </div>
    );
}


const SchichtplanTable = ({ mitarbeiter }) => {
    // Sicherheitsabfrage
    if (!Array.isArray(mitarbeiter) || mitarbeiter.length === 0) {
        return <p>Warte auf Plandaten...</p>;
    }

    // KORREKTUR 1: Extrahiere alle einzigartigen und sortierten Daten aus den Schichten
    const allDates = new Set();
    mitarbeiter.forEach(m => {
        if (m.Arbeitszeiten) {
            Object.keys(m.Arbeitszeiten).forEach(date => allDates.add(date));
        }
    });
    const sortedDatesISO = Array.from(allDates).sort();
    
    // Formatiere die Daten für die Anzeige (optional, aber schöner)
    const displayDates = sortedDatesISO.map(iso => {
        const [year, month, day] = iso.split('-');
        return `${day}.${month}.`;
    });

    return (
        <table className={styles.liveTable}>
            <thead>
                <tr>
                    <th>Mitarbeiter</th>
                    {/* Zeige die formatierten Daten im Header an */}
                    {displayDates.map(d => <th key={d}>{d}</th>)}
                </tr>
            </thead>
            <tbody>
                {mitarbeiter.map(m => (
                    // KORREKTUR 2: Benutze m.id als Key
                    <tr key={m.id}>
                        <td>{m.vorname} {m.nachname}</td>
                        {sortedDatesISO.map(iso => {
                            const zeit = m.Arbeitszeiten?.[iso] || {};
                            return <td key={`${m.id}-${iso}`}>{zeit.Von ? `${zeit.Von}-${zeit.Bis}` : '-'}</td>;
                        })}
                    </tr>
                ))}
            </tbody>
        </table>
    );
};


export default PlanungErstellen;