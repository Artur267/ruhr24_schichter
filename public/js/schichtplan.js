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

                row.insertCell().textContent = mitarbeiter.NutzerID || '';
                row.insertCell().textContent = mitarbeiter.Nachname || '';
                row.insertCell().textContent = mitarbeiter.Vorname || '';
                row.insertCell().textContent = mitarbeiter.Email || '';
                row.insertCell().textContent = mitarbeiter.Stellenbezeichnung || '';
                row.insertCell().textContent = mitarbeiter.Ressort || '';
                row.insertCell().textContent = mitarbeiter.CVD ? "Ja" : "Nein";
                row.insertCell().textContent = (mitarbeiter.Qualifikationen || []).join(', ');
                row.insertCell().textContent = (mitarbeiter.Teams || []).join(', ');
                row.insertCell().textContent = mitarbeiter.Notizen || '';
                row.insertCell().textContent = mitarbeiter.Wochenstunden || '';
                row.insertCell().textContent = mitarbeiter.MonatsSumme || '';
                row.insertCell().textContent = mitarbeiter.Delta || '';


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
                    cvdMatch = mitarbeiter.CVD === true;
                } else if (selectedCvd === 'false') {
                    cvdMatch = mitarbeiter.CVD === false; 
                }

                const sucheMatch = !searchTerm ||
                                (mitarbeiter.Nachname && mitarbeiter.Nachname.toLowerCase().includes(searchTerm)) ||
                                (mitarbeiter.Vorname && mitarbeiter.Vorname.toLowerCase().includes(searchTerm)) ||
                                (mitarbeiter.NutzerID && String(mitarbeiter.NutzerID).includes(searchTerm));

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