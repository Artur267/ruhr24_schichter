document.addEventListener('DOMContentLoaded', () => {

    // ==============
    //  1. SETUP
    // ==============

    // Referenzen auf alle wichtigen HTML-Elemente
    const ressortFilter = document.getElementById('ressortFilter');
    const cvdFilter = document.getElementById('cvdFilter');
    const mitarbeiterSuche = document.getElementById('mitarbeiterSuche');
    const mitarbeiterKalenderTbody = document.getElementById('mitarbeiterKalenderTbody');
    const modal = document.getElementById('edit-modal');
    const modalDatumSpan = document.getElementById('modal-datum');
    const modalVonInput = document.getElementById('modal-von-zeit');
    const modalBisInput = document.getElementById('modal-bis-zeit');
    const modalSpeichernBtn = document.getElementById('modal-speichern-btn');
    const modalAbbrechenBtn = document.getElementById('modal-abbrechen-btn');
    const modalLeerenBtn = document.getElementById('modal-leeren-btn');

    // Globale Variable, die sich merkt, welche Zelle gerade bearbeitet wird
    let aktiveZelle = null;

    // ==============
    //  2. LOGIK
    // ==============

    // Öffnet das Pop-up und füllt es mit den Daten der angeklickten Zelle
    function openEditModal(zelle) {
        aktiveZelle = zelle; // Merken, welche Zelle wir bearbeiten

        const datum = zelle.dataset.datum;
        const von = zelle.dataset.von;
        const bis = zelle.dataset.bis;

        modalDatumSpan.textContent = datum; // Datum im Modal-Titel anzeigen
        modalVonInput.value = von;
        modalBisInput.value = bis;

        modal.style.display = 'flex'; // Modal anzeigen
        modalVonInput.focus(); // Cursor direkt ins "Von"-Feld setzen
    }

    // Schließt das Pop-up und setzt die aktive Zelle zurück
    function closeModal() {
        aktiveZelle = null;
        modal.style.display = 'none';
    }

    // Speichert die Änderungen aus dem Modal in die Tabellenzelle (noch nicht auf dem Server!)
    function saveModalChanges() {
        if (!aktiveZelle) return;

        const neueVonZeit = modalVonInput.value;
        const neueBisZeit = modalBisInput.value;

        // Aktualisiere die data-Attribute der Zelle (hier speichern wir die neuen Werte zwischen)
        aktiveZelle.dataset.von = neueVonZeit;
        aktiveZelle.dataset.bis = neueBisZeit;

        // Aktualisiere den sichtbaren Text in der Zelle
        aktiveZelle.querySelector('span').textContent = neueVonZeit ? `${neueVonZeit} - ${neueBisZeit}` : '-';

        closeModal();
    }

    // Speichert die Daten einer kompletten Zeile auf dem Server
    function saveRow(row) {
        const nutzerId = row.dataset.nutzerId;
        const geaenderteArbeitszeiten = {};

        // Lese die zwischengespeicherten Werte aus den data-Attributen jeder Zelle
        row.querySelectorAll('.zeit-zelle').forEach(zelle => {
            geaenderteArbeitszeiten[zelle.dataset.datum] = {
                Von: zelle.dataset.von,
                Bis: zelle.dataset.bis
            };
        });

        console.log(`Speichere für Nutzer ${nutzerId}:`, geaenderteArbeitszeiten);

        // Sende die Daten an das Java-Backend
        fetch(`/api/mitarbeiter/${nutzerId}/arbeitszeiten`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(geaenderteArbeitszeiten)
        })
        .then(res => {
            if (!res.ok) return res.json().then(err => Promise.reject(err));
            return res.json();
        })
        .then(data => {
            alert(`Änderungen für Nutzer ${nutzerId} gespeichert!`);
            // Optional: Zeile kurz grün färben als visuelles Feedback
            row.style.backgroundColor = '#d4edda';
            setTimeout(() => { row.style.backgroundColor = ''; }, 2000);
        })
        .catch(error => {
            console.error('Fehler beim Speichern:', error);
            alert(`Fehler beim Speichern: ${error.message || 'Serverfehler'}`);
        });
    }
    
    // Filtert die Tabelle, indem Zeilen ein- und ausgeblendet werden
    function filterTable() {
        const selectedRessort = ressortFilter.value;
        const selectedCvd = cvdFilter.value;
        const searchTerm = mitarbeiterSuche.value.toLowerCase();

        mitarbeiterKalenderTbody.querySelectorAll('tr').forEach(row => {
            const ressortMatch = !selectedRessort || row.dataset.ressort === selectedRessort;
            const cvdMatch = !selectedCvd || row.dataset.cvd === selectedCvd;
            const sucheMatch = !searchTerm || row.dataset.name.includes(searchTerm) || row.dataset.nutzerId.includes(searchTerm);
            
            row.style.display = (ressortMatch && cvdMatch && sucheMatch) ? '' : 'none';
        });
    }

    // Füllt den Ressort-Filter mit den vorhandenen Optionen
    function populateRessortFilter() {
        const ressorts = new Set();
        document.querySelectorAll('#mitarbeiterKalenderTbody tr[data-ressort]').forEach(row => {
            if (row.dataset.ressort) ressorts.add(row.dataset.ressort);
        });
        [...ressorts].sort().forEach(r => ressortFilter.appendChild(new Option(r, r)));
    }


    // ==============
    //  3. EVENT LISTENERS
    // ==============
    
    // Filter-Events
    ressortFilter.addEventListener('change', filterTable);
    cvdFilter.addEventListener('change', filterTable);
    mitarbeiterSuche.addEventListener('input', filterTable);

    // Effiziente Event Listener für die ganze Tabelle
    mitarbeiterKalenderTbody.addEventListener('click', (event) => {
        // Klick auf eine Zeit-Zelle? -> Modal öffnen
        const zelle = event.target.closest('.zeit-zelle');
        if (zelle) {
            openEditModal(zelle);
        }
        // Klick auf einen Speicher-Button? -> Zeile speichern
        if (event.target.classList.contains('save-row-btn')) {
            saveRow(event.target.closest('tr'));
        }
    });

    // Modal-Button-Events
    modalAbbrechenBtn.addEventListener('click', closeModal);
    modalLeerenBtn.addEventListener('click', () => {
        modalVonInput.value = '';
        modalBisInput.value = '';
    });
    modalSpeichernBtn.addEventListener('click', saveModalChanges);
    
    // Klick auf den Hintergrund schließt das Modal
    modal.addEventListener('click', (event) => {
        if (event.target === modal) {
            closeModal();
        }
    });

    // ==============
    //  4. START
    // ==============
    
    // Fülle den Ressort-Filter beim ersten Laden der Seite
    populateRessortFilter();
});