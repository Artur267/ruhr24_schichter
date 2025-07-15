// client/src/pages/PlanBearbeiten/PlanBearbeiten.jsx

import React, { useState, useEffect } from 'react';
import FullCalendar from '@fullcalendar/react';
import resourceTimelinePlugin from '@fullcalendar/resource-timeline';
import interactionPlugin from "@fullcalendar/interaction"; // Wichtig für Drag & Drop
import de from '@fullcalendar/core/locales/de';
import 'react-big-calendar/lib/css/react-big-calendar.css';
import styles from './PlanBearbeiten.module.css'; // Benutze das neue CSS

// Diese Hilfsfunktion ist identisch zur Ansicht-Seite
function transformDataForCalendar(mitarbeiterDaten) {
    const events = [];
    const resources = [];
    if (!mitarbeiterDaten) return { events, resources };

    mitarbeiterDaten.forEach(mitarbeiter => {
        resources.push({
            id: mitarbeiter.id,
            title: `${mitarbeiter.vorname} ${mitarbeiter.nachname}`
        });

        if (mitarbeiter.Arbeitszeiten && typeof mitarbeiter.Arbeitszeiten === 'object') {
            Object.entries(mitarbeiter.Arbeitszeiten).forEach(([isoDate, zeiten]) => {
                if (zeiten.Von && zeiten.Bis) {
                    events.push({
                        id: `${mitarbeiter.id}_${isoDate}`,
                        resourceId: mitarbeiter.id,
                        title: `${zeiten.Von} - ${zeiten.Bis}`,
                        start: `${isoDate}T${zeiten.Von}`,
                        end: `${isoDate}T${zeiten.Bis}`,
                        // Farben werden hier nicht mehr gebraucht, da FullCalendar sie selbst verwaltet
                    });
                }
            });
        }
    });
    return { events, resources };
}


function PlanBearbeiten() {
    const [events, setEvents] = useState([]);
    const [resources, setResources] = useState([]);
    const [ladeStatus, setLadeStatus] = useState('Lade Plandaten...');

    useEffect(() => {
        async function fetchData() {
            try {
                const response = await fetch('/api/schichtplan');
                if (!response.ok) throw new Error('Daten konnten nicht geladen werden');
                
                const data = await response.json();
                const { events, resources } = transformDataForCalendar(data.mitarbeiterDaten);
                
                setEvents(events);
                setResources(resources);
                setLadeStatus('');
            } catch (error) {
                setLadeStatus(error.message);
                console.error("Fehler beim Laden:", error);
            }
        }
        fetchData();
    }, []);

    const handleEventDrop = async (info) => {
        const { event } = info;
        const newResourceId = event.getResources()[0].id;
        
        // KORREKTUR: Formatiere die Zeiten manuell, um Zeitzonenfehler zu vermeiden
        const newStart = event.start;
        const newEnd = event.end;

        // Extrahiere die Zeit als HH:mm String
        const von = `${String(newStart.getHours()).padStart(2, '0')}:${String(newStart.getMinutes()).padStart(2, '0')}`;
        const bis = `${String(newEnd.getHours()).padStart(2, '0')}:${String(newEnd.getMinutes()).padStart(2, '0')}`;
        
        // Extrahiere das Datum als YYYY-MM-DD String
        const datum = newStart.toISOString().split('T')[0];

        console.log(`Verschiebe Schicht ${event.id} zu Mitarbeiter ${newResourceId} auf Datum ${datum} von ${von} bis ${bis}`);

        try {
            const response = await fetch(`/api/schicht/${event.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    neuesDatum: datum,
                    von: von,
                    bis: bis,
                    resourceId: newResourceId
                })
            });
            if (!response.ok) throw new Error("Fehler beim Speichern der Änderung.");
        } catch (error) {
            alert("Speichern fehlgeschlagen! Die Änderung wird zurückgesetzt.");
            info.revert();
        }
    };
    
    if (ladeStatus) {
        return <div>{ladeStatus}</div>;
    }

    return (
        <div className={styles.container}>
            <div className={styles.calendarWrapper}>
                <FullCalendar
                    plugins={[resourceTimelinePlugin, interactionPlugin]}
                    initialView="resourceTimelineWeek" // Wochenansicht ist oft besser zum Bearbeiten
                    schedulerLicenseKey="GPL-My-Project-Is-Open-Source"
                    locale={de}
                    headerToolbar={{
                        left: 'prev,next today',
                        center: 'title',
                        right: 'resourceTimelineDay,resourceTimelineWeek,resourceTimelineMonth'
                    }}
                    resourceAreaHeaderContent="Mitarbeiter"
                    resources={resources}
                    events={events}
                    height="85vh"
                    editable={true}
                    eventDrop={handleEventDrop}
                    slotDuration={'00:30:00'}
                    snapDuration={'00:30:00'}
                />
            </div>
        </div>
    );
}

export default PlanBearbeiten;