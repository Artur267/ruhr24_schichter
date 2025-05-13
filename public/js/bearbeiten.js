document.addEventListener('DOMContentLoaded', () => {
    const ressortFilter = document.getElementById('ressortFilter');
    const cvdFilter = document.getElementById('cvdFilter');
    const mitarbeiterSuche = document.getElementById('mitarbeiterSuche');
    const mitarbeiterKalenderTbody = document.getElementById('mitarbeiterKalenderTbody');
    const saveAllBtn = document.getElementById('save-all-btn');

    // Daten vom data-Attribut des tbody holen
    const initialMitarbeiterDaten = JSON.parse(mitarbeiterKalenderTbody.dataset.mitarbeiterDaten || '[]');
    let aktuelleMitarbeiterDaten = [...initialMitarbeiterDaten]; // Kopie für Filterung

    // Funktion zum Aktualisieren der Tabelle (ähnlich schichtplan.js, aber ohne Klick-Events für Sortierung)
    function updateTable(mitarbeiterDaten) {
        console.log('Aktualisiere Tabelle mit Daten:', mitarbeiterDaten);
        mitarbeiterKalenderTbody.innerHTML = '';
        if (mitarbeiterDaten && mitarbeiterDaten.length > 0) {
            mitarbeiterDaten.forEach(mitarbeiter => {
                const row = mitarbeiterKalenderTbody.insertRow();
                row.dataset.nutzerId = mitarbeiter.NutzerID; // Nutzer-ID im data-Attribut der Zeile speichern
                row.innerHTML = `
                    <td>${mitarbeiter.NutzerID}</td>
                    <td>${mitarbeiter.Nachname}</td>
                    <td>${mitarbeiter.Vorname}</td>
                    <td>${mitarbeiter.Ressort}</td>
                    <td>${mitarbeiter.CVD}</td>
                    <td class="notizen-spalte">${mitarbeiter.Notizen}</td>
                    <td>${mitarbeiter.Wochenstunden}</td>
                    <td>${mitarbeiter.MonatsSumme}</td>
                    <td>${mitarbeiter.Delta}</td>
                    ${Object.keys(mitarbeiter.Arbeitszeiten || {}).sort().map(datum => `
                        <td><input type="time" class="arbeitszeit-von" value="${mitarbeiter.Arbeitszeiten[datum]?.Von || ''}"></td>
                        <td><input type="time" class="arbeitszeit-bis" value="${mitarbeiter.Arbeitszeiten[datum]?.Bis || ''}"></td>
                    `).join('')}
                    <td><button class="save-row-btn">Speichern</button></td>
                `;
            });
        } else {
            const row = mitarbeiterKalenderTbody.insertRow();
            const cell = row.insertCell();
            cell.colSpan = 100;
            cell.textContent = 'Keine Mitarbeiterdaten gefunden.';
        }
        // **HIER WIRD DIE FUNKTION JEDES MAL AUFGERUFEN, WENN DIE TABELLE NEU GERENDERT WIRD**
        addSaveRowEventListeners();
        const testButtons = mitarbeiterKalenderTbody.querySelectorAll('.save-row-btn');
        testButtons.forEach(button => {
            button.addEventListener('click', () => {
                console.log('Test-Button wurde geklickt!');
            });
        });
    }

    // Funktion zum Filtern der Mitarbeiterdaten (identisch zu schichtplan.js)
    function filterMitarbeiter() {
        console.log('Filter anwenden');
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


    // Event Listener für die Filter (identisch zu schichtplan.js)
    ressortFilter.addEventListener('change', filterMitarbeiter);
    cvdFilter.addEventListener('change', filterMitarbeiter);
    mitarbeiterSuche.addEventListener('input', filterMitarbeiter);

    // Dynamisches Füllen des Ressort-Filters (identisch zu schichtplan.js)
    const alleRessorts = [...new Set(initialMitarbeiterDaten.map(m => m.Ressort))].sort();
    alleRessorts.forEach(ressort => {
        const option = document.createElement('option');
        option.value = ressort;
        option.textContent = ressort;
        ressortFilter.appendChild(option);
    });

    // Funktion zum Speichern der Änderungen für eine einzelne Zeile
    function saveRow(row) {
        console.log('Speichern für Zeile:', row);
        const nutzerId = row.dataset.nutzerId;
        const vonZeiten = row.querySelectorAll('.arbeitszeit-von');
        const bisZeiten = row.querySelectorAll('.arbeitszeit-bis');
        const geaenderteArbeitszeiten = {};

        const mitarbeiterIndex = aktuelleMitarbeiterDaten.findIndex(m => String(m.NutzerID) === nutzerId);
        if (mitarbeiterIndex !== -1) {
            const mitarbeiter = aktuelleMitarbeiterDaten[mitarbeiterIndex];
            Object.keys(mitarbeiter.Arbeitszeiten || {}).sort().forEach((datum, index) => {
                geaenderteArbeitszeiten[datum] = {
                    Von: vonZeiten[index]?.value || '',
                    Bis: bisZeiten[index]?.value || ''
                };
            });

            fetch(`/api/mitarbeiter/${nutzerId}/arbeitszeiten`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(geaenderteArbeitszeiten)
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(err => {
                        throw new Error(`Fehler beim Speichern: ${err.error || response.statusText}`);
                    });
                }
                return response.json();
            })
            .then(data => {
                console.log('Antwort vom Server:', data);
                // Hier kannst du eine visuelle Rückmeldung an den Benutzer geben, z.B.
                // eine kurze Erfolgsmeldung neben dem Button oder in der Zeile.
                alert('Änderungen gespeichert!'); // Einfache Benachrichtigung
            })
            .catch(error => {
                console.error('Fehler beim Senden der Daten zum Server:', error);
                alert(`Fehler beim Speichern: ${error.message}`); // Einfache Fehlermeldung
            });
        } else {
            console.error(`Mitarbeiter mit ID ${nutzerId} nicht in den aktuellen Daten gefunden.`);
            alert(`Fehler: Mitarbeiter mit ID ${nutzerId} nicht gefunden.`);
        }
    }

    // Event Listener für die "Speichern" Buttons in jeder Zeile
    function addSaveRowEventListeners() {
        const saveButtons = document.querySelectorAll('.save-row-btn');
        saveButtons.forEach(button => {
            button.addEventListener('click', function() {
                const row = this.closest('tr');
                saveRow(row);
            });
        });
    }

    // Event Listener für den "Alle Änderungen speichern" Button
    saveAllBtn.addEventListener('click', () => {
        const rows = mitarbeiterKalenderTbody.querySelectorAll('tr');
        const alleAenderungen = [];
        rows.forEach(row => {
            const nutzerId = row.dataset.nutzerId;
            const vonZeiten = row.querySelectorAll('.arbeitszeit-von');
            const bisZeiten = row.querySelectorAll('.arbeitszeit-bis');
            const geaenderteArbeitszeiten = {};

            const mitarbeiter = aktuelleMitarbeiterDaten.find(m => String(m.NutzerID) === nutzerId);
            if (mitarbeiter) {
                Object.keys(mitarbeiter.Arbeitszeiten || {}).sort().forEach((datum, index) => {
                    geaenderteArbeitszeiten[datum] = {
                        Von: vonZeiten[index]?.value || '',
                        Bis: bisZeiten[index]?.value || ''
                    };
                });
                alleAenderungen.push({ nutzerId: nutzerId, arbeitszeiten: geaenderteArbeitszeiten });
            }
        });

        // Hier müsstest du eine AJAX-Anfrage an deinen Server senden,
        // um alle Änderungen (alleAenderungen) zu speichern.
        console.log('Alle Änderungen speichern:', alleAenderungen);
        // Nach erfolgreicher Speicherung könntest du eine globale Rückmeldung geben.
    });

    // Initiales Anzeigen aller Daten
    updateTable(initialMitarbeiterDaten);
});