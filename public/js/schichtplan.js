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
                row.insertCell().textContent = mitarbeiter.NutzerID;
                row.insertCell().textContent = mitarbeiter.Nachname;
                row.insertCell().textContent = mitarbeiter.Vorname;
                row.insertCell().textContent = mitarbeiter.Ressort;
                row.insertCell().textContent = mitarbeiter.CVD;
                row.insertCell().textContent = mitarbeiter.Notizen;
                row.insertCell().textContent = mitarbeiter.Wochenstunden;
                row.insertCell().textContent = mitarbeiter.MonatsSumme;
                row.insertCell().textContent = mitarbeiter.Delta;

                const dates = Object.keys(mitarbeiter.Arbeitszeiten).sort(); // Annahme: Dates sind Keys im Arbeitszeiten-Objekt
                dates.forEach(date => {
                    const arbeitszeit = mitarbeiter.Arbeitszeiten[date] || { Von: '', Bis: '' };
                    row.insertCell().textContent = arbeitszeit.Von;
                    row.insertCell().textContent = arbeitszeit.Bis;
                });
            });
        } else {
            const row = mitarbeiterKalenderTbody.insertRow();
            const cell = row.insertCell();
            cell.colSpan = 100;
            cell.textContent = 'Keine Mitarbeiterdaten gefunden.';
        }
    }

    // Funktion zum Filtern der Mitarbeiterdaten
    function filterMitarbeiter() {
            const selectedRessort = ressortFilter.value;
            const selectedCvd = cvdFilter.value;
            const searchTerm = mitarbeiterSuche.value.toLowerCase();

            const gefilterteDaten = initialMitarbeiterDaten.filter(mitarbeiter => {
                const ressortMatch = !selectedRessort || mitarbeiter.Ressort === selectedRessort;
                let cvdMatch = true; // Standardmäßig wird alles angezeigt, wenn kein Filter ausgewählt ist

                if (selectedCvd === 'true') {
                    cvdMatch = mitarbeiter.CVD === true || mitarbeiter.CVD === 'True';
                } else if (selectedCvd === 'false') {
                    cvdMatch = mitarbeiter.CVD === false || mitarbeiter.CVD === 'False';
                }

                const sucheMatch = !searchTerm ||
                                    mitarbeiter.Nachname.toLowerCase().includes(searchTerm) ||
                                    mitarbeiter.Vorname.toLowerCase().includes(searchTerm) ||
                                    String(mitarbeiter.NutzerID).includes(searchTerm);

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
    const alleRessorts = [...new Set(initialMitarbeiterDaten.map(m => m.Ressort))].sort();
    alleRessorts.forEach(ressort => {
        const option = document.createElement('option');
        option.value = ressort;
        option.textContent = ressort;
        ressortFilter.appendChild(option);
    });

    // Initiales Anzeigen aller Daten
    updateTable(initialMitarbeiterDaten);
});