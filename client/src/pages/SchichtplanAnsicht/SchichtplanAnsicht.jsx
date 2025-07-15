import React, { useState, useEffect, useMemo, useCallback} from 'react';
import FullCalendar from '@fullcalendar/react';
import resourceTimelinePlugin from '@fullcalendar/resource-timeline';
import de from '@fullcalendar/core/locales/de'; // Korrekter Import für die Sprache
import { TextInput } from '@mantine/core';
import styles from './SchichtplanAnsicht.module.css';
import 'react-big-calendar/lib/css/react-big-calendar.css'; // Behalte das für Basis-Styles
import { isHoliday, getHolidays } from 'feiertagejs'; // NEU: Importiere die Funktionen


// NEU: Definiere deine Schicht-Typen an einer zentralen Stelle
const SCHICHT_TYPEN = {
    '06:00-14:30': { title: 'CvD Früh', color: '#2ecc71' }, // Blau
    '08:00-16:30': { title: 'Kerndienst', color: '#3498db' }, // Grün
    '14:30-23:00': { title: 'CvD Spät', color: '#f39c12' }, // Gelb/Orange
    'default':     { title: 'Andere', color: '#95a5a6' }  // Grau für den Rest
};


function transformDataForCalendar(mitarbeiterDaten) {
    const events = [];
    const resources = [];
    if (!mitarbeiterDaten) return { events, resources };

    mitarbeiterDaten.forEach(mitarbeiter => {
        // KORREKTUR: Greife auf 'id', 'vorname' und 'nachname' (kleingeschrieben) zu
        resources.push({
            id: mitarbeiter.id,
            title: `${mitarbeiter.vorname} ${mitarbeiter.nachname}`
        });

        if (mitarbeiter.Arbeitszeiten && typeof mitarbeiter.Arbeitszeiten === 'object') {
            Object.entries(mitarbeiter.Arbeitszeiten).forEach(([isoDate, zeiten]) => {
                if (zeiten.Von && zeiten.Bis) {
                    const schichtKey = `${zeiten.Von}-${zeiten.Bis}`;
                    const schichtInfo = SCHICHT_TYPEN[schichtKey] || { 
                        title: `${zeiten.Von}-${zeiten.Bis}`, 
                        color: SCHICHT_TYPEN['default'].color 
                    };

                    events.push({
                        // KORREKTUR: 'id' statt 'NutzerID'
                        resourceId: mitarbeiter.id,
                        title: schichtInfo.title,
                        start: `${isoDate}T${zeiten.Von}`,
                        end: `${isoDate}T${zeiten.Bis}`,
                        backgroundColor: schichtInfo.color,
                        borderColor: schichtInfo.color
                    });
                }
            });
        }
    });
    return { events, resources };
}

function SchichtplanAnsicht() {
    const [allEvents, setAllEvents] = useState([]);
    const [allResources, setAllResources] = useState([]);
    const [ladeStatus, setLadeStatus] = useState('Lade Plandaten...');
    const [searchTerm, setSearchTerm] = useState('');
    const [holidaySet, setHolidaySet] = useState(new Set());
    const [sortConfig, setSortConfig] = useState({ key: null, direction: 'descending' });


    useEffect(() => {
        async function fetchDataAndHolidays() {
            try {
                // Plandaten laden
                const response = await fetch('/api/schichtplan');
                if (!response.ok) throw new Error('Plandaten konnten nicht geladen werden');
                const data = await response.json();
                const { events, resources } = transformDataForCalendar(data.mitarbeiterDaten);
                setAllEvents(events);
                setAllResources(resources);

                // Feiertage laden und im State speichern
                const jahr = new Date().getFullYear();
                const feiertage = getHolidays(jahr, 'NW');
                const holidayDateStrings = new Set(
                feiertage.map(h => h.date.toISOString().split('T')[0])
                );
                
                setHolidaySet(holidayDateStrings);
                
                setLadeStatus('');
            } catch (error) {
                setLadeStatus(error.message);
                console.error("Fehler beim Laden:", error);
            }
        }
        fetchDataAndHolidays();
    }, []); // Läuft nur einmal beim ersten Laden

    
    const handleHeaderClick = (date) => {
        const dateKey = date.toISOString().split('T')[0];
        let direction = 'descending';
        // Wenn schon nach diesem Tag sortiert wird, kehre die Richtung um
        if (sortConfig.key === dateKey && sortConfig.direction === 'descending') {
            direction = 'ascending';
        }
        setSortConfig({ key: dateKey, direction });
    };
    // === 3. Memoized Filter und Callbacks ===
    const sortedAndFilteredResources = useMemo(() => {
        let sortableResources = [...allResources]; // Kopie der Ressourcen

        // 1. Filtern (wie bisher)
        if (searchTerm) {
            sortableResources = sortableResources.filter(resource =>
                resource.title.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }

        // 2. Sortieren (NEU)
        if (sortConfig.key !== null) {
            sortableResources.sort((a, b) => {
                // Finde heraus, ob Mitarbeiter A oder B an dem sortierten Tag einen Event hat
                const eventA = allEvents.find(e => e.resourceId === a.id && e.start.startsWith(sortConfig.key));
                const eventB = allEvents.find(e => e.resourceId === b.id && e.start.startsWith(sortConfig.key));

                // Logik: Wer eine Schicht hat, kommt nach oben
                if (eventA && !eventB) return sortConfig.direction === 'descending' ? -1 : 1;
                if (!eventA && eventB) return sortConfig.direction === 'descending' ? 1 : -1;
                return 0;
            });
        }

        return sortableResources;
    }, [allResources, allEvents, searchTerm, sortConfig]);

    // Diese Funktion greift jetzt auf den `holidaySet` aus dem State zu
    const dayCellClassNamesCallback = useCallback((arg) => {
        const dateString = arg.date.toISOString().split('T')[0];
        if (holidaySet.has(dateString)) {
            return ['feiertag-zelle']; // CSS-Klasse für Feiertage
        }
        return [];
    }, [holidaySet]);

    const handleDayCellMount = (arg) => {
        // Prüfe, ob das Datum ein Feiertag ist
        if (isHoliday(arg.date, 'NW')) {
            // Füge die CSS-Klasse direkt zum DOM-Element hinzu
            arg.el.classList.add('feiertag-zelle');
        }
    };

    if (ladeStatus) {
        return <div>{ladeStatus}</div>;
    }

    return (
        <div className={styles.container}>
            <TextInput
                placeholder="Mitarbeiter suchen..."
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.currentTarget.value)}
                className={styles.searchInput}
            />
            <div className={styles.calendarWrapper}>
                <FullCalendar
                    plugins={[resourceTimelinePlugin]}
                    initialView="resourceTimelineMonth"
                    schedulerLicenseKey="GPL-My-Project-Is-Open-Source"
                    locale={de}
                    headerToolbar={{
                        left: 'prev,next today',
                        center: 'title',
                        right: 'resourceTimelineMonth,resourceTimelineWeek'
                    }}
                    resourceAreaHeaderContent="Mitarbeiter"
                    events={allEvents}
                    height="80vh"
                    firstDay={1}
                    dayCellDidMount={handleDayCellMount}
                    resources={sortedAndFilteredResources} // Benutze die neue sortierte Liste

                    // NEU: Macht die Header zu klickbaren Buttons
                    slotLabelDidMount={(arg) => {
                        // Mache den Header klickbar
                        arg.el.style.cursor = 'pointer';
                        
                        // Füge einen direkten Klick-Listener hinzu, der unsere Sortierfunktion aufruft
                        arg.el.addEventListener('click', () => {
                            handleHeaderClick(arg.date);
                        });
                    }}  

                />
            </div>
        </div>
    );
}

export default SchichtplanAnsicht;