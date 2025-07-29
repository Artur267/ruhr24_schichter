// client/src/utils.js

export const SCHICHT_TYPEN = {
    '06:00-14:30': { title: 'CvD Früh', color: '#2ecc71' },
    '08:00-16:30': { title: 'Kerndienst', color: '#3498db' },
    '14:30-23:00': { title: 'CvD Spät', color: '#f39c12' },
    'default':     { title: 'Andere', color: '#95a5a6' }
};

export const WUNSCH_TYPEN = {
    MUSS: 'Muss',
    KANN: 'Wunsch',
    FREI: 'Frei (nicht verfügbar)',
};

export function formatTime(date) {
  return date.toLocaleTimeString('de-DE', {
    hour: '2-digit',
    minute: '2-digit'
  });
}

export function transformDataForCalendar(mitarbeiterDaten) {
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
                    const schichtKey = `${zeiten.Von}-${zeiten.Bis}`;
                    const schichtInfo = SCHICHT_TYPEN[schichtKey] || { 
                        title: `${zeiten.Von}-${zeiten.Bis}`, 
                        color: SCHICHT_TYPEN['default'].color 
                    };

                    events.push({
                        id: `${mitarbeiter.id}_${isoDate}`,
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