import 'dotenv/config'; 
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
import xlsx from 'xlsx';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const defaultData = { plans: [], abwesenheiten: [] }; 
const db = await JSONFilePreset(path.join(__dirname, 'db.json'), defaultData);
const app = express();
const PORT = 3000;
const EMPLOYEE_PATH = path.join(__dirname, 'data', 'mitarbeiter.json');
const CALENDAR_CSV_PATH = path.join(__dirname, 'java', 'optaplanner', 'results', 'output.csv');
const WUNSCH_PATH = path.join(__dirname, 'data', 'wuensche.json');

app.use(expressLayouts);
app.set('layout', 'layout');
app.set('view engine', 'ejs');
app.use(express.json({ limit: '50mb' }));
app.use(express.static('public'));
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' }));

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

const memoryUpload = multer({ storage: multer.memoryStorage() });

app.post('/api/import/absences', memoryUpload.single('absenceFile'), async (req, res) => {
    console.log('[API] Abwesenheits-Import gestartet...');
    if (!req.file) {
        return res.status(400).json({ message: 'Keine Datei hochgeladen.' });
    }

    try {
        const mitarbeiterListe = loadEmployees(); // Lade deine Mitarbeiter-Stammdaten
        const workbook = xlsx.read(req.file.buffer, { type: 'buffer' });
        const sheetName = workbook.SheetNames[0];
        const worksheet = workbook.Sheets[sheetName];
        const jsonData = xlsx.utils.sheet_to_json(worksheet, { header: 1 });

        // --- HIER PASSIERT DIE "ENTSCHLÜSSELUNG" ---
        const { abwesenheiten, monat, jahr } = parseFactorialExport(jsonData, mitarbeiterListe);
        
        console.log(`[API] ${abwesenheiten.length} Abwesenheiten für ${monat} ${jahr} gefunden.`);

        // --- Abwesenheiten in die Datenbank MERGEN ---
        await db.read();
        if (!db.data.abwesenheiten) {
            db.data.abwesenheiten = [];
        }
        
        // Entferne alte Einträge für den importierten Monat, um Duplikate zu vermeiden
        const andereAbwesenheiten = db.data.abwesenheiten.filter(abw => !abw.von.startsWith(`${jahr}-${monat}`));
        db.data.abwesenheiten = [...andereAbwesenheiten, ...abwesenheiten];
        
        await db.write();

        res.status(200).json({ message: `Import erfolgreich! ${abwesenheiten.length} Abwesenheiten für ${monat}/${jahr} importiert.` });

    } catch (error) {
        console.error('Fehler beim Import der Abwesenheiten:', error);
        res.status(500).json({ message: `Import fehlgeschlagen: ${error.message}` });
    }
});

/**
 * Diese Funktion "entschlüsselt" den Factorial-Export.
 */
function parseFactorialExport(data, mitarbeiterListe) {
    const abwesenheiten = [];
    
    // Finde Jahr und Monat aus dem Titel (z.B. "Zwischen August 1, 2025...")
    const titelZeile = data[0].join(' ');
    const yearMatch = titelZeile.match(/(\d{4})/);
    const monthMatch = titelZeile.match(/Zwischen (\w+)/);
    if (!yearMatch || !monthMatch) throw new Error("Konnte Jahr und Monat nicht aus dem Dateititel extrahieren.");
    
    const jahr = yearMatch[1];
    const monatsNamen = { "januar": "01", "februar": "02", "märz": "03", "april": "04", "mai": "05", "juni": "06", "juli": "07", "august": "08", "september": "09", "oktober": "10", "november": "11", "dezember": "12" };
    const monat = monatsNamen[monthMatch[1].toLowerCase()];
    if (!monat) throw new Error(`Unbekannter Monatsname: ${monthMatch[1]}`);

    // Finde die Zeile mit den Datums-Headern (z.B. "Fr 1", "Sa 2")
    const headerRowIndex = data.findIndex(row => row[1] === 'Fr 1' || row[1] === 'Sa 1' || row[1] === 'So 1'); // Finde den ersten Tag
    if (headerRowIndex === -1) throw new Error("Datums-Header-Zeile nicht gefunden.");
    
    const dateHeaders = data[headerRowIndex];
    const dataRows = data.slice(headerRowIndex + 1);

    for (const row of dataRows) {
        const fullName = row[0];
        if (!fullName) continue;

        const mitarbeiter = mitarbeiterListe.find(m => `${m.vorname} ${m.nachname}` === fullName);
        if (!mitarbeiter) {
            console.warn(`Mitarbeiter "${fullName}" aus Excel nicht in der Datenbank gefunden. Wird übersprungen.`);
            continue;
        }

        for (let i = 1; i < dateHeaders.length; i++) {
            const cellValue = row[i];
            if (cellValue && isNaN(cellValue) && !cellValue.includes(':')) { // Prüft, ob es Text ohne Uhrzeit ist
                const day = dateHeaders[i].split(' ')[1];
                const isoDate = `${jahr}-${monat}-${String(day).padStart(2, '0')}`;
                
                let typ = 'SONSTIGES_FREI';
                if (cellValue.toLowerCase().includes('urlaub')) typ = 'URLAUB';
                if (cellValue.toLowerCase().includes('elternzeit')) typ = 'ELTERNZEIT';

                abwesenheiten.push({
                    id: `imp_${mitarbeiter.id}_${isoDate}`,
                    mitarbeiterId: mitarbeiter.id,
                    von: isoDate,
                    bis: isoDate,
                    typ: typ,
                    notiz: `Importiert aus Factorial: ${cellValue}`
                });
            }
        }
    }
    
    // Optional: Hier könnte man noch Logik einbauen, um aufeinanderfolgende Tage zu einem einzigen Eintrag zusammenzufassen.
    return { abwesenheiten, monat, jahr };
}


async function loadWishes() {
    const WUNSCH_PATH = path.join(__dirname, 'data', 'wuensche.json');
    try {
        if (!fs.existsSync(WUNSCH_PATH)) return [];
        const data = await fs.promises.readFile(WUNSCH_PATH, 'utf8');
        return JSON.parse(data);
    } catch (err) { return []; }
}

async function loadPreflights() {
    const PREFLIGHT_PATH = path.join(__dirname, 'data', 'preflight.json');
    try {
        if (!fs.existsSync(PREFLIGHT_PATH)) return [];
        const data = await fs.promises.readFile(PREFLIGHT_PATH, 'utf8');
        return JSON.parse(data);
    } catch (err) { return []; }
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


app.get('/api/abwesenheiten', async (req, res) => {
    try {
        await db.read(); // Lese den aktuellen Stand der db.json
        const abwesenheiten = db.data.abwesenheiten || [];
        res.status(200).json(abwesenheiten);
    } catch (error) {
        console.error('Fehler beim Laden der Abwesenheiten:', error);
        res.status(500).json({ message: 'Server-Fehler beim Laden der Abwesenheiten.' });
    }
});

// POST /api/abwesenheiten
// Erstellt eine neue Abwesenheit und speichert sie in der Datenbank.
app.post('/api/abwesenheiten', async (req, res) => {
    const { mitarbeiterId, von, bis, typ, notiz } = req.body;

    // Einfache Validierung
    if (!mitarbeiterId || !von || !bis || !typ) {
        return res.status(400).json({ message: 'Fehlende Daten. Mitarbeiter, Typ, Start- und Enddatum sind erforderlich.' });
    }

    try {
        await db.read();
        
        // Initialisiere das Array, falls es nicht existiert
        if (!db.data.abwesenheiten) {
            db.data.abwesenheiten = [];
        }

        const neueAbwesenheit = {
            id: `abw_${Date.now()}`, // Eindeutige ID generieren
            mitarbeiterId,
            von,
            bis,
            typ,
            notiz: notiz || ''
        };

        db.data.abwesenheiten.push(neueAbwesenheit);
        await db.write(); // Speichere die Änderungen

        res.status(201).json(neueAbwesenheit); // Sende die erstellte Abwesenheit zurück
    } catch (error) {
        console.error('Fehler beim Speichern der Abwesenheit:', error);
        res.status(500).json({ message: 'Server-Fehler beim Speichern der Abwesenheit.' });
    }
});

// DELETE /api/abwesenheiten/:id
// Löscht eine spezifische Abwesenheit aus der Datenbank.
app.delete('/api/abwesenheiten/:id', async (req, res) => {
    const { id } = req.params;

    try {
        await db.read();

        const anzahlVorher = db.data.abwesenheiten?.length || 0;
        // Filtere das Array, um den Eintrag mit der passenden ID zu entfernen
        db.data.abwesenheiten = db.data.abwesenheiten?.filter(abw => abw.id !== id) || [];
        
        if (db.data.abwesenheiten.length === anzahlVorher) {
            return res.status(404).json({ message: 'Abwesenheit mit dieser ID nicht gefunden.' });
        }

        await db.write();
        res.status(200).json({ message: 'Abwesenheit erfolgreich gelöscht.' });
    } catch (error) {
        console.error('Fehler beim Löschen der Abwesenheit:', error);
        res.status(500).json({ message: 'Server-Fehler beim Löschen der Abwesenheit.' });
    }
});


app.get('/api/mitarbeiter-daten', (req, res) => {
    try{
        res.json(loadEmployees());
    } catch(error){
        res.status(500).json({error: "Fehler beim Laden der Mitarbeiterdaten"})
    }
});

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

app.delete('/api/schichtplan', async (req, res) => {
    const { von, bis } = req.query; // z.B. ?von=2025-08-04&bis=2025-08-10

    if (!von || !bis) {
        return res.status(400).json({ message: 'Start- und Enddatum sind erforderlich.' });
    }
    
    console.log(`[API] Lösche Pläne im Zeitraum: ${von} bis ${bis}`);

    try {
        await db.read();

        db.data.plans.forEach(plan => {
            if (plan.mitarbeiterList) {
                plan.mitarbeiterList.forEach(mitarbeiter => {
                    if (mitarbeiter.Arbeitszeiten) {
                        for (const isoDate in mitarbeiter.Arbeitszeiten) {
                            // Der String-Vergleich funktioniert hier zuverlässig für das ISO-Format
                            if (isoDate >= von && isoDate <= bis) {
                                delete mitarbeiter.Arbeitszeiten[isoDate];
                            }
                        }
                    }
                });
            }
        });

        await db.write();
        res.status(200).json({ message: `Alle Schichten vom ${von} bis ${bis} wurden gelöscht.` });

    } catch (error) {
        console.error('Fehler beim Löschen der Plandaten:', error);
        res.status(500).json({ message: 'Server-Fehler beim Löschen.' });
    }
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

// =======================================================================
//   PREFLIGHT API-ENDPUNKTE (NEU)
// =======================================================================

/**
 * GET /api/preflight/:employeeId/:month
 * Holt ein spezifisches PreFlight-Muster für einen Mitarbeiter und Monat.
 * Nutzt deine bestehende `loadWishes`-Funktion.
 */
app.get('/api/preflight/:employeeId/:month', async (req, res) => {
    const { employeeId, month } = req.params;
    console.log(`[API] Suche PreFlight-Wunsch für MA ${employeeId} im Monat ${month}`);

    try {
        const allWishes = await loadWishes(); // Deine Helfer-Funktion!

        // Finde den passenden PREFLIGHT-Wunsch in der Liste
        const preflightWunsch = allWishes.find(w =>
            w.mitarbeiterId === employeeId &&
            w.typ === 'PREFLIGHT' &&
            w.datum && w.datum.startsWith(month) // Vergleicht "2025-08-01" mit "2025-08"
        );

        if (preflightWunsch) {
            console.log(`[API] PreFlight-Wunsch gefunden.`);
            // Das Frontend erwartet das 'details'-Objekt, also senden wir es direkt
            res.json({ details: preflightWunsch.details || {} });
        } else {
            console.log(`[API] Keinen passenden PreFlight-Wunsch gefunden.`);
            res.status(404).json({ details: {} }); // Sende leeres Objekt, wenn nichts gefunden wird
        }
    } catch (error) {
        console.error('[API PREFLIGHT GET] Fehler:', error);
        res.status(500).json({ message: 'Server-Fehler beim Laden der Wünsche.' });
    }
});

/**
 * POST /api/preflight
 * Speichert oder aktualisiert ein PreFlight-Muster.
 * Nutzt deine `loadWishes` und `saveWishes`-Funktionen.
 */
app.post('/api/preflight', async (req, res) => {
    const newWish = req.body;
    console.log(`[API] Speichere PreFlight-Wunsch für MA: ${newWish.mitarbeiterId}`);

    // Validierung der ankommenden Daten
    if (!newWish.mitarbeiterId || !newWish.datum || newWish.typ !== 'PREFLIGHT' || !newWish.details) {
        return res.status(400).json({ message: 'Ungültige Daten für PreFlight-Wunsch.' });
    }

    try {
        let allWishes = await loadWishes(); // Deine Helfer-Funktion!

        const month = newWish.datum.slice(0, 7); // z.B. "2025-08"

        // Prüfen, ob schon ein PreFlight-Wunsch für diesen MA/Monat existiert
        const existingWishIndex = allWishes.findIndex(w =>
            w.mitarbeiterId === newWish.mitarbeiterId &&
            w.typ === 'PREFLIGHT' &&
            w.datum && w.datum.startsWith(month)
        );

        if (Object.keys(newWish.details).length === 0) {
            // Wenn der Nutzer alle Wünsche gelöscht hat, entfernen wir den Eintrag
            if (existingWishIndex !== -1) {
                console.log('[API] Leeres Muster empfangen, lösche bestehenden Wunsch.');
                allWishes.splice(existingWishIndex, 1);
            }
        } else {
            // Ansonsten: Aktualisieren oder Hinzufügen
            if (existingWishIndex !== -1) {
                console.log('[API] Aktualisiere bestehenden PreFlight-Wunsch.');
                allWishes[existingWishIndex] = newWish;
            } else {
                console.log('[API] Füge neuen PreFlight-Wunsch hinzu.');
                allWishes.push(newWish);
            }
        }

        await saveWishes(allWishes); // Deine Helfer-Funktion!
        
        res.status(200).json({ message: 'PreFlight-Muster erfolgreich gespeichert.' });

    } catch (error) {
        console.error('[API PREFLIGHT POST] Fehler:', error);
        res.status(500).json({ message: 'Server-Fehler beim Speichern des Wunsches.' });
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


app.post("/api/starte-scheduler", async (req, res) => {
    const { von, bis, mitarbeiterList } = req.body;

    console.log("[NODE.JS PROXY] Empfange Anfrage für /starte-scheduler.");
    console.log("[NODE.JS PROXY] Empfangene Daten (von, bis):", von, bis);
    console.log("[NODE.JS PROXY] Anzahl Mitarbeiter:", mitarbeiterList ? mitarbeiterList.length : 0);

    // Sicherheitsabfrage: Stelle sicher, dass die Mitarbeiterliste ein Array ist.
    if (!Array.isArray(mitarbeiterList)) {
        const errorMsg = "Fehler: Die Mitarbeiterliste wurde nicht korrekt übermittelt.";
        console.error(`[NODE.JS PROXY] ${errorMsg}`);
        return res.status(400).json({ error: errorMsg });
    }

    try {
        const alleWuensche = await loadWishes();
        const allePreflights = await loadPreflights();
        await db.read();
        const alleAbwesenheiten = db.data.abwesenheiten || [];
        
        const planungsDaten = { 
            von, 
            bis, 
            mitarbeiterList, 
            wuensche: alleWuensche, 
            abwesenheiten: alleAbwesenheiten, // NEU
            preflights: allePreflights // NEU
        };

        console.log("[NODE.JS PROXY] Sende Planungsdaten an Java-Backend...");
        const javaBackendResponse = await axios.post('http://localhost:8080/api/solve', planungsDaten);

        res.status(202).json(javaBackendResponse.data);

    } catch (error) {
        console.error("[NODE.JS PROXY] Fehler beim Starten der OptaPlanner-Planung:", error.message);
        if (axios.isAxiosError(error) && error.response) {
            res.status(error.response.status).json({ error: `Java-Backend-Fehler: ${JSON.stringify(error.response.data)}` });
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


function generateCsvFromPlans(plans, month) {
    if (!plans || plans.length === 0) return "";

    const mitarbeiterMap = new Map();
    const allDatesInMonth = new Set();

    // 1. Sammle alle Mitarbeiter und ihre Schichten für den gewählten Monat
    plans.forEach(plan => {
        if (Array.isArray(plan.mitarbeiterList)) {
            plan.mitarbeiterList.forEach(mitarbeiter => {
                const existingMitarbeiter = mitarbeiterMap.get(mitarbeiter.id) || { ...mitarbeiter, Arbeitszeiten: {} };
                
                if (mitarbeiter.Arbeitszeiten) {
                    for (const [isoDate, zeiten] of Object.entries(mitarbeiter.Arbeitszeiten)) {
                        if (isoDate.startsWith(month)) { // Filter nach Monat
                            existingMitarbeiter.Arbeitszeiten[isoDate] = zeiten;
                            allDatesInMonth.add(isoDate);
                        }
                    }
                }
                mitarbeiterMap.set(mitarbeiter.id, existingMitarbeiter);
            });
        }
    });

    const sortedDates = Array.from(allDatesInMonth).sort();

    // 2. Erstelle den CSV-Header
    let csvHeaders = ["NutzerID", "Nachname", "Vorname", "Ressort"];
    sortedDates.forEach(date => {
        const [y, m, d] = date.split('-');
        csvHeaders.push(`${d}.${m}. Von`);
        csvHeaders.push(`${d}.${m}. Bis`);
    });
    let csvContent = csvHeaders.join(";") + "\n";

    // 3. Erstelle die Zeilen für jeden Mitarbeiter
    for (const mitarbeiter of mitarbeiterMap.values()) {
        let row = [
            `"${mitarbeiter.id || ''}"`,
            `"${mitarbeiter.nachname || ''}"`,
            `"${mitarbeiter.vorname || ''}"`,
            `"${mitarbeiter.ressort || ''}"`
        ];
        
        sortedDates.forEach(date => {
            const schicht = mitarbeiter.Arbeitszeiten[date];
            if (schicht && schicht.Von) {
                row.push(`"${schicht.Von}"`);
                row.push(`"${schicht.Bis}"`);
            } else {
                row.push('""');
                row.push('""');
            }
        });
        csvContent += row.join(";") + "\n";
    }

    return csvContent;
}

app.get('/api/export-csv', async (req, res) => {
    const { month } = req.query;
    if (!month || !/^\d{4}-\d{2}$/.test(month)) {
        return res.status(400).send('Bitte gib einen Monat im Format YYYY-MM an.');
    }
    try {
        await db.read();
        const csvData = generateCsvFromPlans(db.data.plans, month);
        const fileName = `schichtplan_${month}.csv`;
        res.setHeader('Content-Type', 'text/csv; charset=utf-8');
        res.setHeader('Content-Disposition', `attachment; filename="${fileName}"`);
        res.status(200).send('\uFEFF' + csvData); // BOM für Excel-Kompatibilität
    } catch (error) {
        console.error("Fehler beim CSV-Export:", error);
        res.status(500).send("Fehler beim Erstellen der CSV-Datei.");
    }
});

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

app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, 'client', 'dist', 'index.html'));
});


app.listen(PORT, () => {
    console.log(`Server läuft auf http://localhost:${PORT}`);
});