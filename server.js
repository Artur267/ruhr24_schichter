import 'dotenv/config'; // Direkter Import, der dotenv konfiguriert
import express from 'express';
import expressLayouts from 'express-ejs-layouts';
import bodyParser from 'body-parser';
import axios from 'axios';
import path, { dirname } from 'path';
import { fileURLToPath } from 'url';
import fs, { promises as fsPromises } from 'fs';
import csv from 'csv-parser';
import multer from 'multer';
import { JSONFilePreset } from 'lowdb/node';

// --- WICHTIG: __dirname in ES-Modulen nachbauen ---
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// --- Datenbank-Initialisierung ---
const defaultData = { plans: [] };
const db = await JSONFilePreset(path.join(__dirname, 'db.json'), defaultData);

const app = express();
const PORT = 3000;

// --- Konstanten ---
const EMPLOYEE_PATH = path.join(__dirname, 'data', 'mitarbeiter.json');
const CALENDAR_CSV_PATH = path.join(__dirname, 'java', 'optaplanner', 'results', 'output.csv');
const WUNSCH_PATH = path.join(__dirname, 'data', 'wuensche.json');

// --- Middleware ---
app.use(expressLayouts);
app.set('layout', 'layout');
app.set('view engine', 'ejs');
app.use(express.json({ limit: '50mb' }));
app.use(express.static('public'));
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' }));

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

async function loadWishes() {
    try {
        // Erstelle die Datei, falls sie nicht existiert
        if (!fs.existsSync(WUNSCH_PATH)) {
            await fs.promises.writeFile(WUNSCH_PATH, JSON.stringify([], null, 2), 'utf8');
            return [];
        }
        const data = await fs.promises.readFile(WUNSCH_PATH, 'utf8');
        return JSON.parse(data);
    } catch (err) {
        console.error("Fehler beim Laden der Wunschschichten:", err);
        return [];
    }
}

async function saveWishes(data) {
    try {
        await fs.promises.writeFile(WUNSCH_PATH, JSON.stringify(data, null, 2), 'utf8');
    } catch (err) {
        console.error("Fehler beim Speichern der Wunschschichten:", err);
        throw err;
    }
}

function processSolutionForDatabase(solution) {
    if (!solution || !Array.isArray(solution.mitarbeiterList)) {
        return solution; // Gib unverändert zurück, wenn die Datenstruktur unerwartet ist
    }

    // Erstelle eine Map für schnellen Zugriff auf Mitarbeiter
    const mitarbeiterMap = new Map(
        solution.mitarbeiterList.map(m => [m.id, { ...m, Arbeitszeiten: {} }])
    );

    // Gehe durch alle Arbeitsmuster und ordne die Schichten zu
    if (Array.isArray(solution.arbeitsmusterList)) {
        solution.arbeitsmusterList.forEach(muster => {
            if (muster.mitarbeiter && Array.isArray(muster.schichten)) {
                const mitarbeiter = mitarbeiterMap.get(muster.mitarbeiter.id);
                if (mitarbeiter) {
                    muster.schichten.forEach(schicht => {
                        if (schicht.datum && schicht.startZeit && schicht.endZeit) {
                             // Füge die Schicht zum Arbeitszeiten-Objekt des Mitarbeiters hinzu
                            mitarbeiter.Arbeitszeiten[schicht.datum] = {
                                Von: schicht.startZeit.substring(0, 5),
                                Bis: schicht.endZeit.substring(0, 5)
                            };
                        }
                    });
                }
            }
        });
    }

    // Erstelle eine neue Lösung mit der angereicherten Mitarbeiterliste
    return { ...solution, mitarbeiterList: Array.from(mitarbeiterMap.values()) };
}


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
    return new Promise((resolve, reject) => {
        const results = [];
        fs.createReadStream(filePath)
            .pipe(csv({ 
                separator: ';',
                mapHeaders: ({ header }) => header.trim().replace(/"/g, '') 
            }))
            .on('data', (data) => results.push(data))
            .on('end', () => {
                try {
                    if (results.length === 0) {
                        return resolve({ mitarbeiterDaten: [], dates: [], datesISO: [] });
                    }

                    const headers = Object.keys(results[0]);
                    const dateHeaders = headers.filter(h => h.endsWith(' Von')).map(h => h.replace(' Von', ''));
                    const year = new Date().getFullYear();
                    const datesISO = dateHeaders.map(dateStr => {
                        const [day, month] = dateStr.split('.');
                        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                    });

                    const mitarbeiterDaten = results.map(row => {
                        const arbeitszeiten = {};
                        
                        // KORREKTUR HIER: Wir benutzen das ISO-Datum als Schlüssel
                        datesISO.forEach((isoDate, index) => {
                            const displayDate = dateHeaders[index];
                            arbeitszeiten[isoDate] = { // Der Schlüssel ist jetzt z.B. "2025-08-04"
                                Von: row[`${displayDate} Von`] || '',
                                Bis: row[`${displayDate} Bis`] || ''
                            };
                        });

                        return {
                            NutzerID: row.NutzerID,
                            Nachname: row.Nachname,
                            Vorname: row.Vorname,
                            Email: row['E-Mail'],
                            Stellenbezeichnung: row.Stellenbezeichnung,
                            Ressort: row.Ressort,
                            CVD: row.CVD === 'true',
                            Qualifikationen: row.Qualifikationen ? row.Qualifikationen.split(',').map(r => r.trim()) : [],
                            Teams: row.Teams ? row.Teams.split(',').map(t => t.trim()) : [],
                            Notizen: row.Notizen,
                            Wochenstunden: parseInt(row.Wochenstunden, 10) || 0,
                            MonatsSumme: row.MonatsSumme,
                            Delta: row.Delta,
                            Arbeitszeiten: arbeitszeiten
                        };
                    });

                    resolve({
                        mitarbeiterDaten: mitarbeiterDaten,
                        dates: dateHeaders, // Das bleibt für die Anzeige im Header
                        datesISO: datesISO  // Das wird jetzt für den Datenzugriff genutzt
                    });
                } catch (parseError) {
                    reject(parseError);
                }
            })
            .on('error', (error) => reject(error));
    });
}
/*
app.get('/api/schichtplan', async (req, res) => {
    // Lese alle Pläne aus der Datenbank
    const allPlans = db.data.plans;

    // Fasse die Daten aus allen Plänen zusammen (vereinfachtes Beispiel)
    const combinedMitarbeiterDaten = []; // Hier müsstest du die Daten aller Pläne mergen
    // Fürs Erste geben wir nur den letzten Plan zurück, um die Logik zu zeigen
    const lastPlan = allPlans[allPlans.length - 1]; 

    if (lastPlan) {
        // Deine `transformDataForFullCalendar` Logik bleibt dieselbe
        res.json(transformDataForFullCalendar(lastPlan.mitarbeiterDaten));
    } else {
        res.json({ events: [], resources: [] });
    }
});
*/
// in server.js

app.get('/api/schichtplan', async (req, res) => {
    try {
        await db.read(); // Lese den aktuellen Stand der Datenbank
        const allPlans = db.data.plans;

        if (!allPlans || allPlans.length === 0) {
            return res.json({ mitarbeiterDaten: [] }); // Sende leere Daten, wenn keine Pläne existieren
        }

        // KORREKTUR: Wir führen jetzt alle Pläne zusammen
        const mitarbeiterMap = new Map();

        allPlans.forEach(plan => {
            if (plan && Array.isArray(plan.mitarbeiterList)) {
                plan.mitarbeiterList.forEach(mitarbeiter => {
                    if (mitarbeiter && mitarbeiter.id) {
                        // Wenn wir diesen Mitarbeiter schon kennen, fügen wir nur die neuen Arbeitszeiten hinzu
                        if (mitarbeiterMap.has(mitarbeiter.id)) {
                            const existingMitarbeiter = mitarbeiterMap.get(mitarbeiter.id);
                            Object.assign(existingMitarbeiter.Arbeitszeiten, mitarbeiter.Arbeitszeiten);
                        } else {
                            // Ansonsten fügen wir den kompletten Mitarbeiter neu hinzu
                            mitarbeiterMap.set(mitarbeiter.id, { ...mitarbeiter });
                        }
                    }
                });
            }
        });
        
        // Wandle die Map zurück in ein Array
        const combinedMitarbeiterDaten = Array.from(mitarbeiterMap.values());

        // Sende die kombinierte Liste an das Frontend
        res.json({ mitarbeiterDaten: combinedMitarbeiterDaten });

    } catch (error) {
        console.error('Fehler in /api/schichtplan:', error);
        res.status(500).json({ error: 'Fehler beim Laden der Plandaten aus der DB.' });
    }
});

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

app.get('/api/mitarbeiter-daten', (req, res) => {
    try{
        res.json(loadEmployees());
    } catch(error){
        res.status(500).json({error: "Fehler beim Laden der Mitarbeiterdaten"})
    }
});

app.get('/mitarbeiter-daten', (req, res) => {
    try{
        res.json(loadEmployees());
    } catch(error){
        res.status(500).json({error: "Fehler beim Laden der Mitarbeiterdaten"})
    }
});

/*
app.get('/api/schichtplan', async (req, res) => {
    try {
        const outputCsvPath = path.join(__dirname, 'java', 'optaplanner', 'results', 'output.csv');
        
        // Prüfen, ob die Datei existiert
        if (!fs.existsSync(outputCsvPath)) {
            console.error(`[API] Plandatei nicht gefunden unter: ${outputCsvPath}`);
            return res.status(404).json({ message: 'Keine Plandatei (output.csv) gefunden.' });
        }
        
        // Die parseCSV-Funktion aufrufen
        const csvDaten = await parseCSV(outputCsvPath);
        
        // Die Daten als JSON an React zurücksenden
        res.json(csvDaten);

    } catch (error) {
        console.error('Fehler in der /api/schichtplan Route:', error);
        res.status(500).json({ error: 'Fehler beim Laden der Plandaten.' });
    }
});
*/

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
        const outputCsvPath = path.join(__dirname,'java','optaplanner', 'results', 'output.csv');
        const csvDaten = await parseCSV(outputCsvPath);

        res.render('bearbeiten', {
            title: 'Mitarbeiter Schichtplan bearbeiten',
            dates: csvDaten.dates,
            datesISO: csvDaten.datesISO,
            mitarbeiterDaten: csvDaten.mitarbeiterDaten
        });
    } catch (error) {
        console.error('Fehler beim Laden der CSV-Daten für die Bearbeitungsseite:', error);
        res.status(500).send('Fehler beim Laden der Plandaten.');
    }
});

// Hilfsfunktion, um die Datenaufbereitung zu kapseln
async function parseCSVAndCreateDates(filePath) {
    const csvDaten = await parseCSV(filePath); // Deine bestehende Funktion
    const dates = csvDaten.dates; // Annahme: ['30.06.', ...]
    const year = new Date().getFullYear();

    const datesISO = dates.map(dateStr => {
        const [day, month] = dateStr.replace('.', '').split('.');
        return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
    });

    return { mitarbeiterDaten: csvDaten.mitarbeiterDaten, dates, datesISO };
}

app.get('/richtlinien', (req, res) => {
    fs.readFile(RULES_PATH, 'utf-8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Fehler beim Lesen der Regeln.' });
        res.json(JSON.parse(data));
    });
});

app.get("/api/planungs-ergebnis/:problemId", async (req, res) => {
    const { problemId } = req.params;
    console.log(`[NODE.JS PROXY] Status-Anfrage für ${problemId} wird an Java weitergeleitet...`);

    try {
        const javaBackendResponse = await axios.get(`http://localhost:8080/api/planungs-ergebnis/${problemId}`, {
            // Wichtig, damit axios bei 202 nicht abbricht
            validateStatus: status => status < 500 
        });

        // Leite einfach den Status und die Daten vom Java-Backend 1-zu-1 weiter.
        // Das funktioniert für 200 OK, 202 Accepted und alle Fehlercodes.
        res.status(javaBackendResponse.status).json(javaBackendResponse.data);

    } catch (error) {
        console.error(`[NODE.JS PROXY] Fehler beim Abrufen des Planungsergebnisses für ID ${problemId}:`, error.message);
        res.status(500).json({ error: "Proxy-Fehler zum Java-Backend." });
    }
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
            // Leite den Status UND die JSON-Daten vom Java-Backend weiter
            res.status(202).json(javaBackendResponse.data); 
        } else if (javaBackendResponse.status === 200) {
            console.log(`[NODE.JS PROXY] Java-Backend antwortet 200 OK für ${problemId} (Lösung gefunden).`);
            // Leite den Status UND die JSON-Daten vom Java-Backend weiter
            res.status(200).json(javaBackendResponse.data);
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

app.delete('/api/schicht/:eventId', async (req, res) => {
    const { eventId } = req.params;
    const [mitarbeiterId, isoDate] = eventId.split('_');

    try {
        await db.read();
        // Finde den Plan und die Schicht
        for (const plan of db.data.plans) {
            const mitarbeiter = plan.mitarbeiterList.find(m => m.id === mitarbeiterId);
            if (mitarbeiter && mitarbeiter.Arbeitszeiten?.[isoDate]) {
                delete mitarbeiter.Arbeitszeiten[isoDate];
                await db.write();
                return res.json({ message: 'Schicht erfolgreich gelöscht.' });
            }
        }
        return res.status(404).json({ message: 'Zu löschende Schicht nicht gefunden.' });
    } catch (error) {
        res.status(500).json({ error: 'Serverfehler beim Löschen.' });
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

app.get('/api/wuensche/:userId', async (req, res) => {
    const { userId } = req.params;
    try {
        const allWishes = await loadWishes();
        const userWishes = allWishes.filter(w => w.mitarbeiterId === userId);
        res.json(userWishes);
    } catch (error) {
        res.status(500).json({ error: 'Fehler beim Laden der Wünsche.' });
    }
});

// Speichert/Überschreibt alle Wünsche für einen bestimmten Mitarbeiter
app.put('/api/wuensche/:userId', async (req, res) => {
    const { userId } = req.params;
    const newWishesForUser = req.body; // Erwartet ein Array von Wunsch-Objekten

    try {
        const allWishes = await loadWishes();
        // Entferne alle alten Wünsche dieses Mitarbeiters
        const otherWishes = allWishes.filter(w => w.mitarbeiterId !== userId);
        
        // Füge die neuen Wünsche hinzu (jeder Wunsch bekommt die mitarbeiterId)
        const updatedWishes = newWishesForUser.map(w => ({ ...w, mitarbeiterId: userId }));

        await saveWishes([...otherWishes, ...updatedWishes]);
        res.json({ message: 'Wünsche erfolgreich gespeichert.' });
    } catch (error) {
        res.status(500).json({ error: 'Fehler beim Speichern der Wünsche.' });
    }
});

app.put('/api/schicht/:eventId', async (req, res) => {
    const { eventId } = req.params;
    const { neuesDatum, von, bis, resourceId } = req.body;

    try {
        await db.read();
        const [mitarbeiterId, altesDatum] = eventId.split('_');

        for (const plan of db.data.plans) {
            const alterMitarbeiter = plan.mitarbeiterList.find(m => m.id === mitarbeiterId);
            
            if (alterMitarbeiter && alterMitarbeiter.Arbeitszeiten?.[altesDatum]) {
                // Lösche die alte Schicht
                const alteSchicht = alterMitarbeiter.Arbeitszeiten[altesDatum];
                delete alterMitarbeiter.Arbeitszeiten[altesDatum];

                // Finde den neuen Mitarbeiter (kann auch der gleiche sein)
                const neuerMitarbeiter = plan.mitarbeiterList.find(m => m.id === resourceId);
                if (neuerMitarbeiter) {
                    if (!neuerMitarbeiter.Arbeitszeiten) neuerMitarbeiter.Arbeitszeiten = {};
                    // Füge die Schicht am neuen Datum mit den neuen Zeiten hinzu
                    neuerMitarbeiter.Arbeitszeiten[neuesDatum] = { Von: von, Bis: bis };
                }

                await db.write();
                return res.json({ message: 'Schicht erfolgreich aktualisiert.' });
            }
        }
        return res.status(404).json({ message: 'Zu aktualisierende Schicht nicht gefunden.' });
    } catch (error) {
        console.error('Fehler beim Aktualisieren der Schicht:', error);
        res.status(500).json({ error: 'Serverfehler beim Update.' });
    }
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

app.post('/api/schicht', async (req, res) => {
    const { resourceId, neuesDatum, von, bis } = req.body;

    try {
        await db.read();
        
        // Finde den ersten Plan, der den Mitarbeiter enthält
        // In einer komplexeren App müsstest du hier vielleicht den richtigen Plan auswählen
        const plan = db.data.plans.find(p => p.mitarbeiterList.some(m => m.id === resourceId));

        if (!plan) {
            return res.status(404).json({ error: "Kein passender Plan für diesen Mitarbeiter gefunden." });
        }

        const mitarbeiter = plan.mitarbeiterList.find(m => m.id === resourceId);
        if (!mitarbeiter) {
            return res.status(404).json({ error: "Mitarbeiter nicht im Plan gefunden." });
        }

        // Initialisiere das Arbeitszeiten-Objekt, falls es nicht existiert
        if (!mitarbeiter.Arbeitszeiten) {
            mitarbeiter.Arbeitszeiten = {};
        }

        // Füge die neue Schicht hinzu
        mitarbeiter.Arbeitszeiten[neuesDatum] = { Von: von, Bis: bis };

        await db.write(); // Speichere die Änderung in der db.json
        res.status(201).json({ message: 'Schicht erfolgreich erstellt.' });

    } catch (error) {
        console.error("Fehler beim Erstellen der Schicht:", error);
        res.status(500).json({ error: 'Serverfehler beim Erstellen der Schicht.' });
    }
});

app.post('/api/plan', async (req, res) => {
    const solution = req.body;
    if (!solution) {
        return res.status(400).json({ message: "Keine Plandaten erhalten." });
    }

    try {
        // NEU: Bereite die Daten auf, bevor du sie speicherst
        const processedPlan = processSolutionForDatabase(solution);

        await db.read();
        db.data.plans.push(processedPlan); // Speichere den aufbereiteten Plan
        await db.write();

        res.status(201).json({ message: "Plan erfolgreich gespeichert." });
    } catch (error) {
        console.error("[SERVER] Fehler beim Speichern des Plans:", error);
        res.status(500).json({ error: error.message });
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

app.post('/api/save-schichtplan-csv', async (req, res) => {
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

app.post('/api/mitarbeiter/:nutzerId/arbeitszeiten', (req, res) => {
    const nutzerId = req.params.nutzerId;
    const arbeitszeiten = req.body;

    console.log(`[SERVER] Speichern angefordert für Nutzer: ${nutzerId}`);

    try {
        const data = fs.readFileSync(CALENDAR_CSV_PATH, 'utf8');

        const rows = data.trim().split('\n');
        // KORREKTUR: Trennen mit Semikolon
        const header = rows[0].split(';'); 
        const dataRows = rows.slice(1).map(row => row.split(';'));

        const mitarbeiterIndex = dataRows.findIndex(row => {
            // KORREKTUR: Entferne mögliche Anführungszeichen aus der CSV-ID vor dem Vergleich
            const idFromCsv = row[0].replace(/"/g, ''); 
            return idFromCsv === nutzerId;
        });
        if (mitarbeiterIndex !== -1) {
            const mitarbeiterRow = dataRows[mitarbeiterIndex];
            
            // Finde die Indizes der Datumsspalten
            const datesInHeader = header.slice(13).filter((_, index) => index % 2 === 0).map(h => h.replace(' Von', ''));

            for (const datumISO in arbeitszeiten) {
                // Konvertiere YYYY-MM-DD zu DD.MM. für den Abgleich
                const [year, month, day] = datumISO.split('-');
                const datumDeutsch = `${day}.${month}.`;

                const dateIndex = datesInHeader.indexOf(datumDeutsch);
                if (dateIndex !== -1) {
                    const vonIndex = 13 + dateIndex * 2;
                    const bisIndex = vonIndex + 1;
                    mitarbeiterRow[vonIndex] = arbeitszeiten[datumISO].Von || '';
                    mitarbeiterRow[bisIndex] = arbeitszeiten[datumISO].Bis || '';
                }
            }

            // KORREKTUR: Wieder mit Semikolon zusammenfügen
            const updatedCSV = [header.join(';'), ...dataRows.map(row => row.join(';'))].join('\n');

            fs.writeFileSync(CALENDAR_CSV_PATH, updatedCSV, 'utf8');
            console.log(`[SERVER] Daten für Nutzer ${nutzerId} erfolgreich gespeichert.`);
            res.json({ success: true, message: 'Daten erfolgreich gespeichert.' });

        } else {
            res.status(404).json({ error: `Mitarbeiter mit ID ${nutzerId} nicht gefunden.` });
        }
    } catch (err) {
        console.error('[SERVER] Fehler beim Lesen/Schreiben der CSV-Datei:', err);
        return res.status(500).json({ error: 'Fehler beim Verarbeiten der Speicheranfrage.' });
    }
});

function generateSchichtplanCSV(solution) {
    // 1. Sicherheitsabfrage für die neue Datenstruktur
    if (!solution || !solution.mitarbeiterList || !solution.arbeitsmusterList) {
        console.error("[CSV_GENERATOR] Ungültige Lösung für CSV-Export: Listen fehlen.", solution);
        return "";
    }

    // 2. Entpacke alle zugewiesenen Schichten aus den Mustern in eine einzige, flache Liste
    const alleZugewiesenenSchichten = solution.arbeitsmusterList
        .filter(muster => muster.mitarbeiter) // Nimm nur Muster, die einem Mitarbeiter zugewiesen sind
        .flatMap(muster => {
            // Wichtig: Füge den Mitarbeiter des Musters zu jeder Einzelschicht hinzu!
            return muster.schichten.map(schicht => ({
                ...schicht, // Kopiere alle Eigenschaften der Schicht
                mitarbeiter: muster.mitarbeiter // Füge den Mitarbeiter hinzu
            }));
        });
        
    // 3. Sammle alle einzigartigen Daten für den Header
    const allDates = new Set();
    alleZugewiesenenSchichten.forEach(schicht => {
        if (schicht.datum) {
            allDates.add(schicht.datum);
        }
    });
    const sortedDates = Array.from(allDates).sort();

    // 4. Erstelle den kompletten Header
    let csvHeaders = [
        "NutzerID", "Nachname", "Vorname", "E-Mail", "Stellenbezeichnung",
        "Ressort", "CVD", "Qualifikationen", "Teams", "Notizen",
        "Wochenstunden", "MonatsSumme", "Delta"
    ];
    sortedDates.forEach(date => {
        const [year, month, day] = date.split('-');
        const displayDate = `${day}.${month}.`;
        csvHeaders.push(`${displayDate} Von`);
        csvHeaders.push(`${displayDate} Bis`);
    });
    let csvContent = csvHeaders.join(";") + "\n";

    // 5. Gruppiere die Schichten pro Mitarbeiter für die Berechnungen
    const schichtenProMitarbeiter = {};
    alleZugewiesenenSchichten.forEach(schicht => {
        const mitarbeiterId = schicht.mitarbeiter.id;
        if (!schichtenProMitarbeiter[mitarbeiterId]) {
            schichtenProMitarbeiter[mitarbeiterId] = [];
        }
        schichtenProMitarbeiter[mitarbeiterId].push(schicht);
    });

    // 6. Erstelle eine Zeile für jeden Mitarbeiter
    solution.mitarbeiterList.forEach(mitarbeiter => {
        const zugewieseneSchichten = schichtenProMitarbeiter[mitarbeiter.id] || [];
        
        const monatsSummeMinuten = zugewieseneSchichten.reduce((sum, schicht) => sum + (schicht.arbeitszeitInMinuten || 0), 0);
        const monatsSummeStunden = monatsSummeMinuten / 60.0;
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

app.post("/api/starte-scheduler", async (req, res) => {
    const { von, bis, ressort, mitarbeiterList } = req.body;
    const alleWuensche = await loadWishes();
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
            })),
            wuensche: alleWuensche
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

app.get('/api/wuensche', async (req, res) => {
    const allWishes = await loadWishes(); // Deine Funktion zum Lesen der wuensche.json
    res.json(allWishes);
});

app.put('/api/mitarbeiter/:id', async (req, res) => {
    const { id } = req.params;
    // Nimm alle Daten außer den Wunschschichten, um Überschreiben zu verhindern
    const { wunschschichten, ...updatedMitarbeiterData } = req.body;

    try {
        const mitarbeiterData = await fs.promises.readFile(EMPLOYEE_PATH, 'utf8');
        const mitarbeiterList = JSON.parse(mitarbeiterData);

        const index = mitarbeiterList.findIndex(m => m.id === id);
        if (index === -1) {
            return res.status(404).json({ error: 'Mitarbeiter nicht gefunden.' });
        }

        // Führe die alten Daten mit den neuen zusammen, um nichts zu verlieren
        mitarbeiterList[index] = { ...mitarbeiterList[index], ...updatedMitarbeiterData };

        await fs.promises.writeFile(EMPLOYEE_PATH, JSON.stringify(mitarbeiterList, null, 2), 'utf8');
        
        res.json({ message: 'Mitarbeiter erfolgreich aktualisiert.' });
    } catch (error) {
        console.error("Fehler beim Speichern des Mitarbeiters:", error);
        res.status(500).json({ error: 'Serverfehler beim Speichern.' });
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