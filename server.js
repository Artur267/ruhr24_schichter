require('dotenv').config();
const express = require('express');
const expressLayouts = require('express-ejs-layouts');
const bodyParser = require('body-parser');
const axios = require('axios');
const path = require('path');
const fs = require('fs');
const { promises: fsPromises } = fs;
const csv = require('csv-parser');
const multer = require('multer');
const app = express();
const PORT = 3000;
const { exec } = require('child_process');
const RULES_PATH = path.join(__dirname, 'data', 'regelwerk.json');
const EMPLOYEE_PATH = path.join(__dirname, 'data', 'mitarbeiter.json');
const CALENDAR_CSV_PATH = path.join(__dirname, 'java', 'optaplanner', 'results', 'output.csv');
console.log(`[NODE.JS BACKEND] Konfigurierter CSV-Lesepfad: ${CALENDAR_CSV_PATH}`); // NEU
const UPLOADS_PATH = path.join(__dirname, 'uploads');
const SETTINGS_PATH = path.join(__dirname, 'data', 'settings.json');
const PROMPT_PATH = path.join(__dirname, 'data', 'prompts.txt');

app.use(expressLayouts);
app.set('layout', 'layout');
app.set('view engine', 'ejs');
app.use(express.json({limit: '50mb'})); // Erhöht das Limit für JSON-Daten
app.use(express.static('public'));
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' })); // Erhöht das Limit für URL-kodierte Daten

// Multer Konfiguration
const storage = multer.diskStorage({
    destination: async (req, file, cb) => {
        try {
            await fsPromises.mkdir(UPLOADS_PATH, { recursive: true });
            cb(null, UPLOADS_PATH);
        } catch (err) {
            cb(err);
        }
    },
    filename: (req, file, cb) => {
        cb(null, file.originalname);
    }
});

const upload = multer({ storage: storage });

// Funktionen
function loadEmployees() {
    try {
        const data = fs.readFileSync(EMPLOYEE_PATH, 'utf8');
        return JSON.parse(data);
    } catch (err) {
        console.error("Fehler beim Laden der Mitarbeiterdaten:", err);
        return [];
    }
}

function saveEmployees(data) {
    try {
        fs.writeFileSync(EMPLOYEE_PATH, JSON.stringify(data, null, 2), 'utf8');
    } catch (err) {
        console.error("Fehler beim Speichern der Mitarbeiterdaten:", err);
        throw err;
    }
}

async function parseCSV(filePath) {
    const results = [];
    let dates = []; // Speichert die Datum-Header (z.B. "28.04.")
    let actualHeaders = []; // Speichert die echten Header-Namen der ersten Zeile

    return new Promise((resolve, reject) => {
        fs.createReadStream(filePath, { encoding: 'utf8' }) // UTF-8 Encoding hinzufügen!
            .pipe(csv({ separator: ';', headers: false, ignoreEmpty: true })) // headers: false beibehalten
            .on('data', (row) => {
                const rowValues = Object.values(row);

                // Erste Zeile ist der Header
                if (results.length === 0 && rowValues[0] === 'NutzerID') { // Erkennen der Header-Zeile
                    actualHeaders = rowValues.map(value => String(value).trim());

                    // Datums-Header extrahieren
                    // Beginne ab dem ersten Datum, z.B. "28.04. Von"
                    const firstDateIndex = actualHeaders.findIndex(header => header.match(/^\d{2}\.\d{2}\. Von$/));
                    if (firstDateIndex !== -1) {
                         dates = actualHeaders
                            .slice(firstDateIndex) // Schneide ab dem ersten Datum-Header ab
                            .filter((_, index) => index % 2 === 0) // Nur die "Von"-Header behalten
                            .map(value => String(value).trim().replace(' Von', '')) // " Von" entfernen, nur Datum behalten
                            .filter(value => value !== '');
                        // Sortiere die Daten chronologisch, falls sie nicht schon sortiert sind
                        dates.sort((a, b) => {
                            const [dayA, monthA] = a.split('.').map(Number);
                            const [dayB, monthB] = b.split('.').map(Number);
                            if (monthA !== monthB) return monthA - monthB;
                            return dayA - dayB;
                        });
                    }
                    // Diese Zeile ist der Header, also nicht als Mitarbeiter hinzufügen
                    return;
                }

                // Datenzeilen verarbeiten
                if (rowValues[0] !== 'NutzerID') { // Überspringe die Header-Zeile, wenn sie nicht die erste ist
                    const mitarbeiter = {
                        Arbeitszeiten: {}
                    };

                    // Annahme: Die Reihenfolge der Daten in rowValues entspricht den Spalten in actualHeaders
                    // ACHTUNG: Die Indizes MÜSSEN zu deiner CSV-Datei passen!
                    // Wenn die CSV-Datei 1:1 die gewünschte Struktur hat:
                    mitarbeiter.id = String(rowValues[actualHeaders.indexOf('NutzerID')]).trim() || ''; // Oder rowValues[0] wenn "NutzerID" immer an erster Stelle steht
                    mitarbeiter.nachname = String(rowValues[actualHeaders.indexOf('Nachname')]).trim() || '';
                    mitarbeiter.vorname = String(rowValues[actualHeaders.indexOf('Vorname')]).trim() || '';
                    mitarbeiter.email = String(rowValues[actualHeaders.indexOf('E-Mail')]).trim() || ''; // Richtig!
                    mitarbeiter.stellenbezeichnung = String(rowValues[actualHeaders.indexOf('Stellenbezeichnung')]).trim() || ''; // Richtig!
                    mitarbeiter.ressort = String(rowValues[actualHeaders.indexOf('Ressort')]).trim() || ''; // Richtig!
                    mitarbeiter.cvd = (String(rowValues[actualHeaders.indexOf('CVD')]).trim().toLowerCase() === 'true' || String(rowValues[actualHeaders.indexOf('CVD')]).trim().toLowerCase() === 'ja'); // Richtig, als Boolean!
                    // Rollen und Qualifikationen sowie Teams werden als kommaseparierter String erwartet
                    mitarbeiter.rollenUndQualifikationen = String(rowValues[actualHeaders.indexOf('Qualifikationen')]).trim().split(',').map(s => s.trim()).filter(s => s !== '') || []; // Richtig, als Array!
                    mitarbeiter.teamsUndZugehoerigkeiten = String(rowValues[actualHeaders.indexOf('Teams')]).trim().split(',').map(s => s.trim()).filter(s => s !== '') || []; // Richtig, als Array!
                    mitarbeiter.notizen = String(rowValues[actualHeaders.indexOf('Notizen')]).trim() || '';
                    mitarbeiter.wochenstunden = parseInt(String(rowValues[actualHeaders.indexOf('Wochenstunden')]).trim(), 10) || 0; // Richtig, als Zahl!
                    mitarbeiter.monatsSumme = String(rowValues[actualHeaders.indexOf('MonatsSumme')]).trim() || '0,00';
                    mitarbeiter.delta = String(rowValues[actualHeaders.indexOf('Delta')]).trim() || '0,00';
                    // Die Felder MonatsSumme und Delta aus deinem neuen JSON-Snippet sind hier nicht aufgeführt.
                    // Wenn sie in der CSV als separate Spalten existieren, füge sie hier hinzu:
                    // mitarbeiter.monatsSumme = parseFloat(String(rowValues[actualHeaders.indexOf('MonatsSumme')]).replace(',', '.')).trim()) || 0;
                    // mitarbeiter.delta = parseFloat(String(rowValues[actualHeaders.indexOf('Delta')]).replace(',', '.')).trim()) || 0;

                    // Wenn "Wunschschichten" und "urlaubtageSet" auch in der CSV sind, füge sie hinzu
                    mitarbeiter.wunschschichten = []; // Annahme: nicht direkt in CSV, sonst aus CSV lesen
                    mitarbeiter.urlaubtageSet = []; // Annahme: nicht direkt in CSV, sonst aus CSV lesen


                    // Verarbeite Arbeitszeiten basierend auf den dynamisch gefundenen Datums-Headern
                    dates.forEach(dateKey => { // dateKey ist z.B. "28.04."
                        const vonHeader = `${dateKey} Von`;
                        const bisHeader = `${dateKey} Bis`;

                        const vonIndex = actualHeaders.indexOf(vonHeader);
                        const bisIndex = actualHeaders.indexOf(bisHeader);

                        const vonValue = vonIndex !== -1 && rowValues[vonIndex] ? String(rowValues[vonIndex]).trim() : '';
                        const bisValue = bisIndex !== -1 && rowValues[bisIndex] ? String(rowValues[bisIndex]).trim() : '';

                        mitarbeiter.Arbeitszeiten[dateKey] = {
                            Von: vonValue,
                            Bis: bisValue
                        };
                    });
                    results.push(mitarbeiter);
                }
            })
            .on('end', () => resolve({ dates, mitarbeiterDaten: results }))
            .on('error', reject);
    });
}

// Routen zu Links erstellen
app.get('/prompt', async (req, res) => {
  try {
    const data = await fsPromises.readFile(PROMPT_PATH, 'utf8');
    res.json(data);
  } catch (err) {
    console.error(err);
    res.status(500).send('Fehler beim Lesen der Prompt-Datei.');
  }
});

// Neu: Endpunkt für settings.json
app.get('/settings', async (req, res) => {
  try {
    const data = await fsPromises.readFile(SETTINGS_PATH, 'utf8');
    res.json(JSON.parse(data));
  } catch (err) {
    console.error(err);
    res.status(500).send('Fehler beim Lesen der Settings-Datei.');
  }
});

app.get('/mitarbeiter-daten', (req, res) => {
    try{
        res.json(loadEmployees());
    } catch(error){
        res.status(500).json({error: "Fehler beim Laden der Mitarbeiterdaten"})
    }
});

app.get('/schichtplan', async (req, res) => {
    try {
        const csvDaten = await parseCSV(CALENDAR_CSV_PATH);
        console.log("CSV-Daten erfolgreich geladen:", csvDaten);
        console.log("Mitarbeiter Daten:", csvDaten.mitarbeiterDaten);
        console.log("Datumsdaten:", csvDaten.dates);
        res.render('schichtplan', {
            title: 'Mitarbeiter Schichtplan',
            dates: csvDaten.dates,
            mitarbeiterDaten: csvDaten.mitarbeiterDaten
        });
    } catch (error) {
        console.error('Fehler beim Lesen der CSV-Datei:', error);
        res.status(500).send('Fehler beim Anzeigen der Mitarbeiter Schichtplandaten.');
    }
});

app.get('/bearbeiten', async (req, res) => {
    try {
        const csvDaten = await parseCSV(CALENDAR_CSV_PATH);
        console.log("CSV-Daten für Bearbeiten geladen:", csvDaten);
        res.render('bearbeiten', {
            title: 'Mitarbeiter Schichtplan bearbeiten',
            dates: csvDaten.dates,
            mitarbeiterDaten: csvDaten.mitarbeiterDaten
        });
    } catch (error) {
        console.error('Fehler beim Laden der Daten für die Bearbeitungsseite:', error);
        res.status(500).send('Fehler beim Anzeigen der Bearbeitungsseite.');
    }
});

app.get('/richtlinien', (req, res) => {
    fs.readFile(RULES_PATH, 'utf-8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Fehler beim Lesen der Regeln.' });
        res.json(JSON.parse(data));
    });
});

app.get('/planungs-ergebnis/:problemId', async (req, res) => {
    const problemId = req.params.problemId;
    try {
        console.log(`[NODE.JS PROXY] Empfange Anfrage für /planungs-ergebnis/${problemId}.`);
        const javaBackendResponse = await fetch(`http://localhost:8080/api/planungs-ergebnis/${problemId}`); // Stelle sicher, dass die URL korrekt ist

        // Den Status und den Body des Java-Backends direkt an das Frontend weiterleiten
        // Hier sollte Java JSON zurückgeben (entweder 200 OK mit Lösung oder 202 Accepted mit null Body)
        if (javaBackendResponse.status === 202) {
            console.log(`[NODE.JS PROXY] Java-Backend antwortet 202 für ${problemId} (noch in Arbeit).`);
            res.status(202).end(); // Sende 202 ohne Body, da das Frontend das so erwartet
        } else if (javaBackendResponse.status === 200) {
            const jsonBody = await javaBackendResponse.json();
            console.log(`[NODE.JS PROXY] Java-Backend antwortet 200 OK für ${problemId} (Lösung gefunden).`);
            res.status(200).json(jsonBody); // Sende JSON-Lösung zurück
        } else {
            // Für 404 Not Found oder andere Fehler
            const errorBody = await javaBackendResponse.text();
            console.error(`[NODE.JS PROXY] Java-Backend antwortet ${javaBackendResponse.status} für ${problemId}: ${errorBody}`);
            res.status(javaBackendResponse.status).send(errorBody); // Leite den Fehler weiter
        }
    } catch (error) {
        console.error(`[NODE.JS PROXY] Interner Serverfehler im /planungs-ergebnis/${problemId} Endpunkt:`, error);
        res.status(500).json({ error: 'Interner Serverfehler beim Abrufen des Planungsergebnisses.' });
    }
});

app.delete('/mitarbeiter/:id', (req, res) => {
    try{
        const employees = loadEmployees();
        const filtered = employees.filter(e => e.id !== req.params.id);
        if (filtered.length === employees.length) return res.status(404).json({ error: 'Nicht gefunden' });
        saveEmployees(filtered);
        res.json({ success: true });
    } catch(error){
        res.status(500).json({error: "Fehler beim Löschen des Mitarbeiters"})
    }
});

app.delete('/richtlinie/:id', (req, res) => {
    const ruleId = req.params.id;

    fs.readFile(RULES_PATH, 'utf-8', async (err, data) => {
        if (err) return res.status(500).json({ error: 'Fehler beim Lesen der Datei.' });

        let rules = JSON.parse(data);
        const updatedRules = rules.filter(rule => rule.id !== ruleId);

        if (rules.length === updatedRules.length) {
            return res.status(404).json({ error: 'Regel nicht gefunden.' });
        }

        try {
            await fsPromises.writeFile(RULES_PATH, JSON.stringify(updatedRules, null, 2), 'utf-8');
            res.json({ success: true });
        } catch (err) {
            return res.status(500).json({ error: 'Fehler beim Speichern der Datei.' });
        }
    });
});

app.put('/mitarbeiter/:id', (req, res) => {
    try{
        const employees = loadEmployees();
        const index = employees.findIndex(e => e.id === req.params.id);
        if (index === -1) return res.status(404).json({ error: 'Nicht gefunden' });
        employees[index] = { id: req.params.id, ...req.body };
        saveEmployees(employees);
        res.json({ success: true });
    } catch(error){
        res.status(500).json({error: "Fehler beim Aktualisieren des Mitarbeiters"})
    }
});

// Neuer Endpunkt für den CSV-Upload
app.post('/upload', upload.single('csvFile'), async (req, res) => {
    if (!req.file) {
        return res.status(400).send('Keine Datei hochgeladen.');
    }

    const filePath = path.join(UPLOADS_PATH, req.file.originalname);

    try {
        const parsedData = await parseCSV(filePath);
        console.log('CSV-Daten erfolgreich geladen:', parsedData);
        res.json(parsedData);
    } catch (error) {
        console.error('Fehler beim Parsen der CSV-Datei:', error);
        res.status(500).send('Fehler beim Verarbeiten der CSV-Datei.');
    } finally {
        try {
            await fsPromises.unlink(filePath);
            console.log('Datei gelöscht:', filePath);
        } catch (unlinkErr) {
                console.error("Fehler beim Löschen der Datei:", unlinkErr);
            }
        }
    });

app.post('/mitarbeiter', (req, res) => {
    try{
        const employees = loadEmployees();
        const newEmployee = {
            id: Date.now().toString(),
            ...req.body
        };
        employees.push(newEmployee);
        saveEmployees(employees);
        res.json({ success: true });
    } catch(error){
        res.status(500).json({error: "Fehler beim Erstellen des Mitarbeiters"})
    }
});

app.post('/save-schichtplan-csv', async (req, res) => {
    const solution = req.body; // Die gesamte SchichtPlan-Lösung wird im Body erwartet

    if (!solution) {
        console.error("[NODE.JS PROXY] Fehler: Keine Lösung im Request Body für CSV-Speicherung erhalten.");
        return res.status(400).json({ error: "Keine Schichtplan-Lösung zum Speichern erhalten." });
    }

    try {
        // Die generateSchichtplanCSV-Funktion muss auch in dieser server.js-Datei definiert sein.
        // Siehe die vollständige server.js-Datei, die ich in früheren Antworten gesendet habe.
        const csvString = generateSchichtplanCSV(solution); 

        // Schreibe die CSV-Daten in die Datei
        await fsPromises.writeFile(CALENDAR_CSV_PATH, csvString, 'utf8');
        console.log(`[NODE.JS PROXY] Schichtplan-Lösung erfolgreich als CSV gespeichert unter: ${CALENDAR_CSV_PATH}`);
        res.status(200).json({ message: "Schichtplan erfolgreich als CSV gespeichert." });
    } catch (error) {
        console.error("[NODE.JS PROXY] Fehler beim Speichern der Schichtplan-Lösung als CSV:", error);
        res.status(500).json({ error: `Fehler beim Speichern der CSV: ${error.message}` });
    }
});

app.post('/api/mitarbeiter/:nutzerId/arbeitszeiten', express.json(), (req, res) => {
    const nutzerId = req.params.nutzerId;
    const arbeitszeiten = req.body;

    console.log(`[SERVER] Speichern angefordert für Nutzer: ${nutzerId}`, arbeitszeiten);

    fs.readFile(CALENDAR_CSV_PATH, 'utf8', (err, data) => {
        if (err) {
            console.error('[SERVER] Fehler beim Lesen der CSV-Datei:', err);
            return res.status(500).json({ error: 'Fehler beim Lesen der CSV-Datei.' });
        }

        const rows = data.trim().split('\n');
        const header = rows[0].split(',');
        const dataRows = rows.slice(1).map(row => row.split(','));

        const mitarbeiterIndex = dataRows.findIndex(row => row[0] === nutzerId);

        if (mitarbeiterIndex !== -1) {
            const mitarbeiterRow = dataRows[mitarbeiterIndex];
            const datesInHeader = header.slice(9).filter((_, index) => index % 2 === 0);

            for (const datum in arbeitszeiten) {
                const dateIndex = datesInHeader.indexOf(datum);
                if (dateIndex !== -1) {
                    const vonIndex = 9 + dateIndex * 2;
                    const bisIndex = 10 + dateIndex * 2;
                    mitarbeiterRow[vonIndex] = arbeitszeiten[datum].Von || '';
                    mitarbeiterRow[bisIndex] = arbeitszeiten[datum].Bis || '';
                }
            }

            const updatedCSV = [header.join(','), ...dataRows.map(row => row.join(','))].join('\n');

            fsPromises.writeFile(CALENDAR_CSV_PATH, updatedCSV, 'utf8', (writeErr) => {
                if (writeErr) {
                    console.error('[SERVER] Fehler beim Schreiben der CSV-Datei:', writeErr);
                    return res.status(500).json({ error: 'Fehler beim Speichern der Daten.' });
                }
                console.log(`[SERVER] Daten für Nutzer ${nutzerId} erfolgreich gespeichert.`);
                res.json({ success: true, message: 'Daten erfolgreich gespeichert.' });
            });
        } else {
            res.status(404).json({ error: `Mitarbeiter mit ID ${nutzerId} nicht gefunden.` });
        }
    });
});

function generateSchichtplanCSV(solution) {
    // 1. Sicherheitsabfrage für die neue Datenstruktur
    if (!solution || !solution.mitarbeiterList || !solution.schichtList) {
        console.error("[CSV_GENERATOR] Ungültige Lösung für CSV-Export: Listen fehlen.", solution);
        return ""; // Gibt einen leeren String zurück, um eine leere Datei zu erzeugen
    }

    // 2. Sammle alle einzigartigen Daten aus der neuen schichtList
    const allDates = new Set();
    solution.schichtList.forEach(schicht => {
        if (schicht.datum) {
            allDates.add(schicht.datum); // Datum im Format YYYY-MM-DD
        }
    });
    const sortedDates = Array.from(allDates).sort();

    // 3. Erstelle den kompletten Header, so wie dein Frontend ihn erwartet
    let csvHeaders = [
        "NutzerID", "Nachname", "Vorname", "E-Mail", "Stellenbezeichnung",
        "Ressort", "CVD", "Qualifikationen", "Teams", "Notizen",
        "Wochenstunden", "MonatsSumme", "Delta"
    ];
    sortedDates.forEach(date => {
        const [year, month, day] = date.split('-');
        const displayDate = `${day}.${month}.`; // Format: DD.MM.
        csvHeaders.push(`${displayDate} Von`);
        csvHeaders.push(`${displayDate} Bis`);
    });
    let csvContent = csvHeaders.join(";") + "\n";

    // 4. Gruppiere die Schichten pro Mitarbeiter für einfache Verarbeitung
    const schichtenProMitarbeiter = {};
    solution.schichtList.forEach(schicht => {
        if (schicht.mitarbeiter && schicht.mitarbeiter.id) {
            const mitarbeiterId = schicht.mitarbeiter.id;
            if (!schichtenProMitarbeiter[mitarbeiterId]) {
                schichtenProMitarbeiter[mitarbeiterId] = [];
            }
            schichtenProMitarbeiter[mitarbeiterId].push(schicht);
        }
    });
    
    // 5. Erstelle eine Zeile für jeden Mitarbeiter
    solution.mitarbeiterList.forEach(mitarbeiter => {
        const zugewieseneSchichten = schichtenProMitarbeiter[mitarbeiter.id] || [];
        
        // Berechne MonatsSumme und Delta
        const monatsSummeMinuten = zugewieseneSchichten.reduce((sum, schicht) => sum + (schicht.arbeitszeitInMinuten || 0), 0);
        const monatsSummeStunden = monatsSummeMinuten / 60.0;
        // Annahme: 4 Wochen Planungszeitraum für Delta-Berechnung
        const sollStunden = mitarbeiter.wochenstunden * 4.0;
        const delta = monatsSummeStunden - sollStunden;

        const rowData = [
            `"${mitarbeiter.id || ''}"`,
            `"${mitarbeiter.nachname || ''}"`,
            `"${mitarbeiter.vorname || ''}"`,
            `"${mitarbeiter.email || ''}"`,
            `"${mitarbeiter.stellenbezeichnung || ''}"`,
            `"${mitarbeiter.ressort || ''}"`,
            `"${mitarbeiter.cvd ? 'true' : 'false'}"`,
            `"${(mitarbeiter.rollenUndQualifikationen || []).join(', ')}"`,
            `"${(mitarbeiter.teamsUndZugehoerigkeiten || []).join(', ')}"`,
            `"${(mitarbeiter.notizen || '').replace(/"/g, '""')}"`,
            `"${mitarbeiter.wochenstunden || 0}"`,
            `"${monatsSummeStunden.toFixed(2).replace('.', ',')}"`,
            `"${delta.toFixed(2).replace('.', ',')}"`
        ];

        const tagesSchichtMap = new Map();
        zugewieseneSchichten.forEach(schicht => {
            tagesSchichtMap.set(schicht.datum, schicht);
        });

        sortedDates.forEach(date => {
            const schicht = tagesSchichtMap.get(date);
            if (schicht && schicht.startZeit && schicht.endZeit) {
                rowData.push(`"${schicht.startZeit.substring(0, 5)}"`);
                rowData.push(`"${schicht.endZeit.substring(0, 5)}"`);
            } else {
                rowData.push('""');
                rowData.push('""');
            }
        });
        csvContent += rowData.join(";") + "\n";
    });

    return csvContent;
}

app.post('/richtlinie', (req, res) => {
    const { text } = req.body;
    if (!text) return res.status(400).json({ error: 'Text fehlt.' });

    fs.readFile(RULES_PATH, 'utf-8', async (err, data) => {
        if (err) return res.status(500).json({ error: 'Fehler beim Lesen der Datei.' });

        const rules = JSON.parse(data);
        const newRule = {
            id: Date.now().toString(),
            text
        };

        rules.push(newRule);
        try {
            await fsPromises.writeFile(RULES_PATH, JSON.stringify(rules, null, 2), 'utf-8');
            res.json({ success: true });
        } catch (err) {
            return res.status(500).json({ error: 'Fehler beim Schreiben.' });
        }
    });
});
app.post("/starte-scheduler", async (req, res) => {
    const { von, bis, ressort, mitarbeiterList } = req.body;

    console.log("[NODE.JS PROXY] Empfange Anfrage für /starte-scheduler.");
    console.log("[NODE.JS PROXY] Empfangene Daten (von, bis, ressort):", von, bis, ressort);
    console.log("[NODE.JS PROXY] Anzahl Mitarbeiter:", mitarbeiterList ? mitarbeiterList.length : 0);

    try {
        const planungsDaten = {
            von: von,
            bis: bis,
            ressort: ressort,
            mitarbeiterList: mitarbeiterList.map(mitarbeiter => ({
                id: mitarbeiter.id,
                nachname: mitarbeiter.nachname,
                vorname: mitarbeiter.vorname,
                email: mitarbeiter.email ?? null,
                stellenbezeichnung: mitarbeiter.stellenbezeichnung ?? null,
                ressort: mitarbeiter.ressort,
                wochenstunden: mitarbeiter.wochenstunden != null ? parseInt(mitarbeiter.wochenstunden, 10) : 0,
                cvd: mitarbeiter.cvd ?? false,
                notizen: mitarbeiter.notizen ?? null,
                rollenUndQualifikationen: mitarbeiter.rollenUndQualifikationen ?? [],
                teamsUndZugehoerigkeiten: mitarbeiter.teamsUndZugehoerigkeiten ?? [],
                wunschschichten: mitarbeiter.wunschschichten ?? [],
                urlaubtageSet: mitarbeiter.urlaubtageSet ?? []
            }))
        };
        // URL zum Java-Backend: /api/solve (POST)
        console.log("[NODE.JS PROXY] Sende Planungsdaten an Java-Backend (http://localhost:8080/api/solve)...");

        const javaBackendResponse = await axios.post('http://localhost:8080/api/solve', planungsDaten);

        const { problemId, solverTimeoutMillis, message } = javaBackendResponse.data;

        if (!problemId) {
            console.error('[NODE.JS PROXY] FEHLER: Keine problemId im JSON-Objekt vom Java-Backend erhalten.');
            return res.status(500).json({ error: 'Konnte problemId vom Java-Backend nicht extrahieren.' });
        }
        console.log('[NODE.JS PROXY] Extrahierte Problem-ID:', problemId);
        console.log('[NODE.JS PROXY] Extrahierter Solver Timeout (ms):', solverTimeoutMillis);

        res.status(202).json({
            message: message || "Planung erfolgreich gestartet.",
            problemId: problemId,
            solverTimeoutMillis: solverTimeoutMillis
        });

    } catch (error) {
        console.error("[NODE.JS PROXY] Fehler beim Starten der OptaPlanner-Planung:", error.message);
        if (axios.isAxiosError(error) && error.response) {
            console.error("Axios Response Data (Java-Backend Fehler):", error.response.data);
            console.error("Axios Response Status (Java-Backend Fehler):", error.response.status);
            res.status(error.response.status).json({
                error: `Fehler beim Starten der OptaPlanner-Planung: ${error.response.status} - ${JSON.stringify(error.response.data) || error.message}`
            });
        } else {
            res.status(500).json({ error: "Ein unerwarteter Fehler ist aufgetreten: " + error.message });
        }
    }
});

// Endpunkt zum Abrufen des Planungsstatus oder Ergebnisses
// Dies ist der Endpunkt, den das Frontend aufruft, um den Fortschritt/Ergebnis zu erhalten
app.get("/planungs-ergebnis/:problemId", async (req, res) => { // <--- Dieser Endpunkt ist entscheidend!
    const problemId = req.params.problemId;
    // HIER KORRIGIERTER LOG, um die korrekte empfangene URL anzuzeigen
    console.log(`[NODE.JS PROXY] Empfange Anfrage für /planungs-ergebnis/${problemId}.`);

    try {
        // Interne Weiterleitung an das Java-Backend: /api/planungs-ergebnis/{problemId} (GET)
        const javaBackendResponse = await axios.get(`http://localhost:8080/api/planungs-ergebnis/${problemId}`);
        res.status(javaBackendResponse.status).json(javaBackendResponse.data);
    } catch (error) {
        console.error(`[NODE.JS PROXY] Fehler beim Abrufen des Planungsergebnisses für ID ${problemId}:`, error.message);
        if (axios.isAxiosError(error) && error.response) {
            console.error("Axios Response Data (Java-Backend Fehler):", error.response.data);
            console.error("Axios Response Status (Java-Backend Fehler):", error.response.status);
            res.status(error.response.status).json({
                error: `Fehler beim Abrufen des Planungsergebnisses: ${error.response.status} - ${JSON.stringify(error.response.data) || error.message}`
            });
        } else {
            res.status(500).json({ error: "Ein unerwarteter Fehler ist aufgetreten: " + error.message });
        }
    }
});

// Die restlichen Routen und Funktionen bleiben unverändert
app.get('/', (req, res) => res.render('index'));
app.get('/erstellen', (req, res) => res.render('erstellen'));
app.get('/bearbeiten', (req, res) => res.render('bearbeiten'));
app.get('/limitationen', (req, res) => res.render('limitationen'));
app.get('/mitarbeiter', (req, res) => res.render('mitarbeiter'));

function formatDateISOtoDeutsch(isoDatum) {
    const [jahr, monat, tag] = isoDatum.split('-');
    return `${tag}.${monat}.${jahr}`;
}

function updateDataRows(kiAntwort, dataRowsMitPlan) {
    const headerZeile2 = dataRowsMitPlan[0].split('\n')[1].split(',');
    const mitarbeiterZeilenStart = 1;

    const updatedDataRows = [...dataRowsMitPlan];

    for (let i = mitarbeiterZeilenStart; i < updatedDataRows.length; i++) {
        const zeile = updatedDataRows[i].split(',');
        const mitarbeiterId = zeile[0];

        if (kiAntwort[mitarbeiterId]) {
            for (const datumISO in kiAntwort[mitarbeiterId]) {
                const schicht = kiAntwort[mitarbeiterId][datumISO];
                const datumDeutsch = formatDateISOtoDeutsch(datumISO);
                const spaltenIndex = headerZeile2.findIndex(header => header.includes(datumDeutsch));
                if (spaltenIndex !== -1) {
                    zeile[spaltenIndex] = schicht;
                }
            }
            updatedDataRows[i] = zeile.join(',');
        }
    }
    return updatedDataRows;
}

async function createCsvTemplate(von, bis) {
  try {
    const mitarbeiterListe = loadEmployees();

    const startDatum = new Date(von);
    const endDatum = new Date(bis);
    const tage = [];

    for (let aktuellesDatum = startDatum; aktuellesDatum <= endDatum; aktuellesDatum.setDate(aktuellesDatum.getDate() + 1)) {
      const tagString = aktuellesDatum.toISOString().split('T')[0];
      const wochentag = aktuellesDatum.toLocaleDateString('de-DE', { weekday: 'short' });
      tage.push({ tagString, wochentag });
    }

    let headerZeile1 = 'NutzerID,Nachname,Vorname,Ressort,CVD,Stunden';
    let headerZeile2 = 'NutzerID,Nachname,Vorname,Ressort,CVD,Stunden';
    tage.forEach(tag => {
      headerZeile1 += `,${tag.tagString},`;
      headerZeile2 += `,${tag.wochentag} Von,${tag.wochentag} Bis`;
    });
    headerZeile1 += '\n';
    headerZeile2 += '\n';

    const mitarbeiterZeilen = mitarbeiterListe.map(mitarbeiter => {
      let zeile = `${mitarbeiter.id},${mitarbeiter.nachname},${mitarbeiter.vorname},${mitarbeiter.ressort},${mitarbeiter.cvd},${mitarbeiter.stunden}`;
      tage.forEach(() => {
        zeile += ',,';
      });
      return zeile;
    }).join('\n');

    const csvInhalt = headerZeile1 + headerZeile2 + mitarbeiterZeilen;

    await fsPromises.writeFile(CALENDAR_CSV_PATH, csvInhalt, 'utf8');
    console.log(`[SERVER] CSV-Template mit Header und Mitarbeiterdaten erstellt: ${CALENDAR_CSV_PATH}`);
    return { success: true, message: 'CSV-Template erstellt' };
  } catch (error) {
    console.error('[SERVER] Fehler beim Erstellen des CSV-Templates:', error);
    return { success: false, message: `Fehler beim Erstellen des CSV-Templates: ${error.message}` };
  }
}

app.listen(PORT, () => {
    console.log(`Server läuft auf http://localhost:${PORT}`);
});