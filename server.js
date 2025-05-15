require('dotenv').config();
const express = require('express');
const expressLayouts = require('express-ejs-layouts');
const bodyParser = require('body-parser');
const axios = require('axios');
const path = require('path');
const fs = require('fs');
const { promises: fsPromises } = fs;
const csv = require('csv-parser');
const multer = require('multer'); // Für Datei-Uploads hinzufügen
const app = express();
const PORT = 3000;
const { exec } = require('child_process');
const RULES_PATH = path.join(__dirname, 'data', 'regelwerk.json');
const EMPLOYEE_PATH = path.join(__dirname, 'data', 'mitarbeiter.json');
const CALENDAR_CSV_PATH = path.join(__dirname, 'results', 'output.csv');
const UPLOADS_PATH = path.join(__dirname, 'uploads'); // Pfad für hochgeladene Dateien

app.use(expressLayouts);
app.set('layout', 'layout');
app.set('view engine', 'ejs');
app.use(express.json());
app.use(express.static('public'));
app.use(bodyParser.urlencoded({ extended: true })); // Für Formular-Daten

// Multer Konfiguration für Datei-Uploads
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        // Sicherstellen, dass der Uploads-Ordner existiert
        fsPromises.mkdir(UPLOADS_PATH, { recursive: true })
            .then(() => cb(null, UPLOADS_PATH))
            .catch(err => cb(err));
    },
    filename: function (req, file, cb) {
        cb(null, file.originalname);
    }
});

const upload = multer({ storage: storage });

// Funktionen
function loadEmployees() {
    try {
        return JSON.parse(fs.readFileSync(EMPLOYEE_PATH, 'utf8'));
    } catch (err) {
        return [];
    }
}

function saveEmployees(data) {
    fs.writeFileSync(EMPLOYEE_PATH, JSON.stringify(data, null, 2));
}

async function parseCSV(filePath) {
    return new Promise((resolve, reject) => {
        const results = [];
        let dates = [];

        fs.createReadStream(filePath)
            .pipe(csv({ separator: ',', headers: false, ignoreEmpty: true }))
            .on('data', (row) => {
                const rowValues = Object.values(row);

                if (dates.length === 0) {
                    // Erste Zeile: Datumsangaben ab dem 10. Element (Index 9), jeden zweiten Wert
                    dates = rowValues
                        .slice(9)
                        .filter((_, index) => index % 2 === 0)
                        .map(value => value ? value.trim() : '')
                        .filter(value => value !== '');
                } else if (rowValues[0] !== 'NutzerID') { // Skip the second header row
                    const mitarbeiter = {
                        Arbeitszeiten: {}
                    };

                    // Allgemeine Mitarbeiterdaten (Element 1 bis 9)
                    mitarbeiter.NutzerID = rowValues[0] ? rowValues[0].trim() : '';
                    mitarbeiter.Nachname = rowValues[1] ? rowValues[1].trim() : '';
                    mitarbeiter.Vorname = rowValues[2] ? rowValues[2].trim() : '';
                    mitarbeiter.Ressort = rowValues[3] ? rowValues[3].trim() : '';
                    mitarbeiter.CVD = rowValues[4] ? rowValues[4].trim() : '';
                    mitarbeiter.Notizen = rowValues[5] ? rowValues[5].trim() : '';
                    mitarbeiter.Wochenstunden = rowValues[6] ? rowValues[6].trim() : '';
                    mitarbeiter.MonatsSumme = rowValues[7] ? rowValues[7].trim() : '';
                    mitarbeiter.Delta = rowValues[8] ? rowValues[8].trim() : '';

                    // Arbeitszeiten (ab Element 10)
                    for (let i = 0; i < dates.length; i++) {
                        const vonIndex = 9 + i * 2;
                        const bisIndex = 10 + i * 2;

                        if (rowValues[vonIndex] !== undefined && rowValues[bisIndex] !== undefined) {
                            mitarbeiter.Arbeitszeiten[dates[i]] = {
                                Von: rowValues[vonIndex] ? rowValues[vonIndex].trim() : '',
                                Bis: rowValues[bisIndex] ? rowValues[bisIndex].trim() : ''
                            };
                        }
                    }
                    results.push(mitarbeiter);
                }
            })
            .on('end', () => {
                resolve({ dates, mitarbeiterDaten: results });
            })
            .on('error', (error) => reject(error));
    });
}

// Neuer Endpoint für den CSV-Upload
app.post('/upload', upload.single('csvFile'), async (req, res) => {
    if (!req.file) {
        return res.status(400).send('Keine Datei hochgeladen.');
    }

    const filePath = path.join(UPLOADS_PATH, req.file.originalname);

    try {
        const parsedData = await parseCSV(filePath);
        console.log('CSV-Daten erfolgreich geladen:', parsedData);
        res.json(parsedData); // Sende die Daten als JSON
    } catch (error) {
        console.error('Fehler beim Parsen der CSV-Datei:', error);
        res.status(500).send('Fehler beim Verarbeiten der CSV-Datei.');
    } finally {
        // Optional: Datei nach der Verarbeitung löschen
        await fsPromises.unlink(filePath);
        console.log('Datei gelöscht:', filePath);
    }
});

// Routen zu Links erstellen
app.get('/mitarbeiter-daten', (req, res) => {
    res.json(loadEmployees());
});

app.post('/mitarbeiter', (req, res) => {
    const employees = loadEmployees();
    const newEmployee = {
        id: Date.now().toString(),
        ...req.body
    };
    employees.push(newEmployee);
    saveEmployees(employees);
    res.json({ success: true });
});

app.put('/mitarbeiter/:id', (req, res) => {
    const employees = loadEmployees();
    const index = employees.findIndex(e => e.id === req.params.id);
    if (index === -1) return res.status(404).json({ error: 'Nicht gefunden' });
    employees[index] = { id: req.params.id, ...req.body };
    saveEmployees(employees);
    res.json({ success: true });
});

app.delete('/mitarbeiter/:id', (req, res) => {
    const employees = loadEmployees();
    const filtered = employees.filter(e => e.id !== req.params.id);
    if (filtered.length === employees.length) return res.status(404).json({ error: 'Nicht gefunden' });
    saveEmployees(filtered);
    res.json({ success: true });
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

            fs.writeFile(CALENDAR_CSV_PATH, updatedCSV, 'utf8', (writeErr) => {
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

if (!process.env.GOOGLE_API_KEY) {
    console.error("Fehler: GOOGLE_API_KEY nicht in der .env-Datei gefunden.");
    process.exit(1);
}
const API_KEY = process.env.GOOGLE_API_KEY;
const API_URL = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent';

app.get('/', (req, res) => res.render('index'));
app.get('/erstellen', (req, res) => res.render('erstellen'));
app.get('/bearbeiten', (req, res) => res.render('bearbeiten'));
app.get('/limitationen', (req, res) => res.render('limitationen'));
app.get('/mitarbeiter', (req, res) => res.render('mitarbeiter'));
// app.get('/schichtplan', (req, res) => res.render('schichtplan')); // Bereits weiter oben definiert

app.post('/ask-gemini', async (req, res) => {
    try {
        const prompt = req.body.prompt;
        const article = req.body.article;

        if (!prompt || !article) {
            return res.status(400).send("Fehler: 'prompt' und 'article' Parameter im Request Body fehlen.");
        }

        const combinedPrompt = `${prompt}\n\nArtikel:\n${article}`;

        const requestBody = {
            contents: [{
                parts: [{ text: combinedPrompt }]
            }]
        };

        const response = await axios.post(
            `${API_URL}?key=${API_KEY}`,
            requestBody,
            {
                headers: {
                    'Content-Type': 'application/json'
                }
            }
        );

        const data = response.data;

        if (data && data.candidates && data.candidates.length > 0 && data.candidates[0].content && data.candidates[0].content.parts && data.candidates[0].content.parts.length > 0) {
            const answer = data.candidates[0].content.parts[0].text;
            res.json({ answer: answer });
        } else {
            console.error("Unerwartete Antwortstruktur:", data);
            res.status(500).send("Fehler: Unerwartete Antwort vom Gemini-Modell.");
        }

    } catch (error) {
        console.error("Fehler beim Abrufen der Gemini-Antwort:", error.response ? error.response.data : error.message);
        res.status(500).send("Fehler beim Abrufen der Antwort vom Gemini-Modell.");
    }
});

app.post("/starte-scheduler", async (req, res) => {
    const von = req.body.von;
    const bis = req.body.bis;
    const mitarbeiterDatenPfad = path.join(__dirname, "data", "mitarbeiter.json");
    const schedulerPath = path.join(__dirname, "py", "scheduler.py");
    const outputPath = path.join(__dirname, "results", "output.csv");

    try {
        const mitarbeiterDatenBuffer = await fsPromises.readFile(mitarbeiterDatenPfad, 'utf8');
        const mitarbeiterListe = JSON.parse(mitarbeiterDatenBuffer);
        let gesamtCsvOutput = "";
        const totalMitarbeiter = mitarbeiterListe.length;
        let processedMitarbeiter = 0;
        let dataRows = [];
        let completeHeaderOutput = "";

        // Header generieren und beide Zeilen in completeHeaderOutput speichern
        const headerResult = await new Promise((resolve, reject) => {
            exec(`python3 "${schedulerPath}" "${von}" "${bis}" "header"`, (error, stdout, stderr) => {
                if (error) {
                    console.error("[SERVER ERROR] Fehler beim Abrufen des Headers:", error);
                    reject(error);
                    return;
                }
                if (stderr) {
                    console.error("[SERVER ERROR] STDERR beim Abrufen des Headers:", stderr);
                    reject(new Error(stderr));
                    return;
                }
                const headerZeilen = stdout.trim().split('\n');
                if (headerZeilen.length === 2) {
                    const ersteZeileRoh = headerZeilen[0].trim();
                    const zweiteZeile = headerZeilen[1].trim();

                    // Führende Kommas für die erste Zeile bis zur Spalte I (Index 8) hinzufügen
                    const spaltenBisDatum = 8; // Korrigierter Wert
                    const kommas = Array(spaltenBisDatum).fill('').join(',');
                    const ersteZeileFormatiert = `${kommas}${ersteZeileRoh}`;

                    completeHeaderOutput = `${ersteZeileFormatiert}\n${zweiteZeile}`;
                    resolve(completeHeaderOutput);
                } else {
                    reject(new Error("Unerwartete Anzahl an Header-Zeilen"));
                }
            });
        });
        const headerResultOutput = await headerResult;
        dataRows.push(headerResultOutput);
        console.log("[SERVER DEBUG] Inhalt von dataRows NACH Header:", dataRows);

        for (const mitarbeiter of mitarbeiterListe) {
            processedMitarbeiter++;
            const mitarbeiterId = mitarbeiter.id;
            console.log(`[SERVER LOG] Verarbeite Mitarbeiter ${processedMitarbeiter}/${totalMitarbeiter} (ID: ${mitarbeiterId})`);
            const mitarbeiterResultPromise = new Promise((resolve, reject) => {
                exec(`python3 "${schedulerPath}" "${von}" "${bis}" "${mitarbeiterId}"`, (error, stdout, stderr) => {
                    if (error) {
                        console.error(`[SERVER ERROR] Fehler bei Mitarbeiter ${mitarbeiterId}:`, error);
                        reject(error);
                        return;
                    }
                    if (stderr) {
                        console.error(`[SERVER ERROR] STDERR von Python für Mitarbeiter ${mitarbeiterId}:\n`, stderr);
                        reject(new Error(stderr));
                        return;
                    }
                    console.log(`[SERVER DEBUG] STDOUT von Python für Mitarbeiter ${mitarbeiterId}:\n`, stdout);

                    const dataRow = stdout.split('\n').find(line => line.startsWith(mitarbeiterId));

                    if (dataRow) {
                        const trimmedDataRow = dataRow.trim();
                        console.log(`[SERVER DEBUG] Extrahierte Datenzeile für Mitarbeiter ${mitarbeiterId}:`, trimmedDataRow);
                        resolve(trimmedDataRow);
                    } else {
                        console.warn(`[SERVER WARNUNG] Keine Datenzeile für Mitarbeiter ${mitarbeiterId} in STDOUT gefunden.`);
                        resolve("");
                    }
                });
            });
            const mitarbeiterResult = await mitarbeiterResultPromise;
            console.log("[SERVER DEBUG] Wert von mitarbeiterResult:", mitarbeiterResult);
            if (mitarbeiterResult) {
                dataRows = [...dataRows, mitarbeiterResult];
            }
        }
        console.log("[SERVER DEBUG] Inhalt von dataRows VOR dem Join:", dataRows);
        gesamtCsvOutput = dataRows.filter(row => row).join('\n');
        try {
            await fsPromises.writeFile(outputPath, gesamtCsvOutput, 'utf8');
            console.log(`[SERVER LOG] Schichtplan erfolgreich gespeichert unter: ${outputPath}`);
            res.json({ message: "MVP Plan für alle Mitarbeiter erstellt und gespeichert", output: gesamtCsvOutput });
        } catch (writeError) {
            console.error("[SERVER ERROR] Fehler beim Schreiben der CSV-Datei:", writeError);
            res.status(500).json({ error: "Fehler beim Schreiben der CSV-Datei" });
            return;
        }
        //res.json({ message: "MVP Plan für alle Mitarbeiter erstellt", output: gesamtCsvOutput }); // Entfernt, da die Antwort bereits im Speicher-Block gesendet wird
    } catch (error) {
        console.error("Fehler bei der Massenplanung:", error);
        res.status(500).json({ error: "Fehler bei der Planung für alle Mitarbeiter" });
    } finally {
        console.log("[SERVER DEBUG] Schichtplanerstellung abgeschlossen (mit oder ohne Fehler).");
    }
});

app.get('/richtlinien', (req, res) => {
    fs.readFile(RULES_PATH, 'utf-8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Fehler beim Lesen der Regeln.' });
        res.json(JSON.parse(data));
    });
});

app.delete('/richtlinie/:id', (req, res) => {
    const ruleId = req.params.id;

    fs.readFile(RULES_PATH, 'utf-8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Fehler beim Lesen der Datei.' });

        let rules = JSON.parse(data);
        const updatedRules = rules.filter(rule => rule.id !== ruleId);

        if (rules.length === updatedRules.length) {
            return res.status(404).json({ error: 'Regel nicht gefunden.' });
        }

        fs.writeFile(RULES_PATH, JSON.stringify(updatedRules, null, 2), 'utf-8', (err) => {
            if (err) return res.status(500).json({ error: 'Fehler beim Speichern der Datei.' });
            res.json({ success: true });
        });
    });
});

app.post('/richtlinie', (req, res) => {
    const { text } = req.body;
    if (!text) return res.status(400).json({ error: 'Text fehlt.' });

    fs.readFile(RULES_PATH, 'utf-8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Fehler beim Lesen der Datei.' });

        const rules = JSON.parse(data);
        const newRule = {
            id: Date.now().toString(),
            text
        };

        rules.push(newRule);

        fs.writeFile(RULES_PATH, JSON.stringify(rules, null, 2), 'utf-8', (err) => {
            if (err) return res.status(500).json({ error: 'Fehler beim Schreiben.' });
            res.json({ success: true });
        });
    });
});

app.listen(PORT, () => {
    console.log(`Server läuft auf http://localhost:${PORT}`);
});