document.addEventListener('DOMContentLoaded', () => {
    const ressortFilter = document.getElementById('ressortFilter');
    const cvdFilter = document.getElementById('cvdFilter');
    const mitarbeiterSuche = document.getElementById('mitarbeiterSuche');
    const mitarbeiterKalenderTbody = document.getElementById('mitarbeiterKalenderTbody');

    // Daten vom data-Attribut des tbody holen
    const initialMitarbeiterDaten = JSON.parse(mitarbeiterKalenderTbody.dataset.mitarbeiterDaten || '[]');
    let aktuelleMitarbeiterDaten = [...initialMitarbeiterDaten]; // Kopie für Filterung

    // Funktion zum Aktualisieren der Tabelle basierend auf den gefilterten Daten
    function updateTable(mitarbeiterDaten) {
        mitarbeiterKalenderTbody.innerHTML = ''; // Leere den Tabellenkörper

        if (mitarbeiterDaten && mitarbeiterDaten.length > 0) {
            mitarbeiterDaten.forEach(mitarbeiter => {
                const row = mitarbeiterKalenderTbody.insertRow();

                row.insertCell().textContent = mitarbeiter.id || ''; // von NutzerID zu id
                row.insertCell().textContent = mitarbeiter.nachname || ''; // von Nachname zu nachname
                row.insertCell().textContent = mitarbeiter.vorname || ''; // von Vorname zu vorname
                row.insertCell().textContent = mitarbeiter.email || ''; // Jetzt "email"
                row.insertCell().textContent = mitarbeiter.stellenbezeichnung || ''; // Jetzt "stellenbezeichnung"
                row.insertCell().textContent = mitarbeiter.ressort || ''; // Jetzt "ressort"
                row.insertCell().textContent = mitarbeiter.cvd ? "Ja" : "Nein"; // Jetzt "cvd" (Boolean)
                row.insertCell().textContent = (mitarbeiter.rollenUndQualifikationen || []).join(', ') || ''; // Jetzt "rollenUndQualifikationen" (Array)
                row.insertCell().textContent = (mitarbeiter.teamsUndZugehoerigkeiten || []).join(', ') || ''; // Jetzt "teamsUndZugehoerigkeiten" (Array)
                row.insertCell().textContent = mitarbeiter.notizen || ''; // Jetzt "notizen"
                row.insertCell().textContent = mitarbeiter.wochenstunden || ''; // Jetzt "wochenstunden"
                row.insertCell().textContent = mitarbeiter.monatsSumme || ''; // Wenn diese Felder im neuen JSON sind
                row.insertCell().textContent = mitarbeiter.delta || ''; // Wenn diese Felder im neuen JSON sind


                // Arbeitszeiten Teil - Die Schlüssel sollten jetzt nur noch die Daten sein
                const dates = Object.keys(mitarbeiter.Arbeitszeiten || {}).sort((a, b) => {
                    // Sortierung nach Datum im Format "TT.MM."
                    const [dayA, monthA] = a.split('.').map(Number);
                    const [dayB, monthB] = b.split('.').map(Number);
                    if (monthA !== monthB) return monthA - monthB;
                    return dayA - dayB;
                });

                dates.forEach(date => {
                    const arbeitszeit = mitarbeiter.Arbeitszeiten[date] || { Von: '', Bis: '' };
                    row.insertCell().textContent = arbeitszeit.Von || '-';
                    row.insertCell().textContent = arbeitszeit.Bis || '-';
                });
            });
        } else {
            const row = mitarbeiterKalenderTbody.insertRow();
            const cell = row.insertCell();
            const fixedCols = 12; // id, nachname, vorname, email, stellenbezeichnung, ressort, cvd, rollenUndQualifikationen, teamsUndZugehoerigkeiten, notizen, wochenstunden, monatsSumme, delta
            // Schätzt die Spaltenanzahl basierend auf dem ersten Mitarbeiter, falls vorhanden
            const totalCols = fixedCols + (Object.keys(mitarbeiterDaten[0]?.Arbeitszeiten || {}).length * 2);
            cell.colSpan = totalCols > 0 ? totalCols : 100; // Oder einen sinnvollen Standardwert
            cell.textContent = 'Keine Mitarbeiterdaten gefunden.';
        }
    }

    // Funktion zum Filtern der Mitarbeiterdaten
    function filterMitarbeiter() {
            const selectedRessort = ressortFilter.value;
            const selectedCvd = cvdFilter.value;
            const searchTerm = mitarbeiterSuche.value.toLowerCase();

            const gefilterteDaten = initialMitarbeiterDaten.filter(mitarbeiter => {
                const ressortMatch = !selectedRessort || mitarbeiter.ressort === selectedRessort;
                let cvdMatch = true;

                if (selectedCvd === 'true') {
                    cvdMatch = mitarbeiter.cvd === true;
                } else if (selectedCvd === 'false') {
                    cvdMatch = mitarbeiter.cvd === false; 
                }

                const sucheMatch = !searchTerm ||
                                    (mitarbeiter.nachname && mitarbeiter.nachname.toLowerCase().includes(searchTerm)) ||
                                    (mitarbeiter.vorname && mitarbeiter.vorname.toLowerCase().includes(searchTerm)) ||
                                    (mitarbeiter.id && String(mitarbeiter.id).includes(searchTerm)); // id statt NutzerID

                return ressortMatch && cvdMatch && sucheMatch;
            });

            aktuelleMitarbeiterDaten = gefilterteDaten;
            updateTable(aktuelleMitarbeiterDaten);
    }

    // Event Listener für die Filter
    ressortFilter.addEventListener('change', filterMitarbeiter);
    cvdFilter.addEventListener('change', filterMitarbeiter);
    mitarbeiterSuche.addEventListener('input', filterMitarbeiter);

    // Dynamisches Füllen des Ressort-Filters beim Laden der Seite
    const alleRessorts = [...new Set(initialMitarbeiterDaten.map(m => m.ressort))].sort();
    alleRessorts.forEach(ressort => {
        if (ressort) { 
            const option = document.createElement('option');
            option.value = ressort;
            option.textContent = ressort;
            ressortFilter.appendChild(option);
        }
    });

    updateTable(initialMitarbeiterDaten);
});