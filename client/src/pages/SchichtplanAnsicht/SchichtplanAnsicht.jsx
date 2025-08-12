import React, { useState, useEffect, useCallback, useRef } from 'react';
import FullCalendar from '@fullcalendar/react';
import resourceTimelinePlugin from '@fullcalendar/resource-timeline';
import interactionPlugin from "@fullcalendar/interaction";
import de from '@fullcalendar/core/locales/de';
import { TextInput, Group, Button, Skeleton, Modal, NumberInput, Paper, Text, Progress, FileInput, Box, Select } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconDownload, IconTrash, IconRocket, IconUpload } from '@tabler/icons-react';
import DateRangeSelector from '../../components/DateRangeSelector/DateRangeSelector';
import styles from './SchichtplanAnsicht.module.css';
import { MonthPickerInput } from '@mantine/dates';

// =======================================================================
// HELFER-FUNKTIONEN
// =======================================================================

const SCHICHT_TYPEN = {
    '06:00-14:30': { title: 'CvD Früh', color: '#228be6' },
    '08:00-16:30': { title: 'Kerndienst', color: '#40c057' },
    '14:30-23:00': { title: 'CvD Spät', color: '#fab005' },
    'PREFLIGHT_ON': { title: 'Wunsch', color: '#be4bdb' },
    'default':     { title: 'Andere', color: '#868e96' }
};

function transformDataForCalendar(mitarbeiterDaten, abwesenheiten) {
    if (!mitarbeiterDaten) {
        return { resources: [], events: [] };
    }
    const resources = mitarbeiterDaten.map(m => ({
        id: m.id,
        title: `${m.vorname} ${m.nachname}`
    }));
    const events = [];
    mitarbeiterDaten.forEach(mitarbeiter => {
        if (mitarbeiter.Arbeitszeiten) {
            Object.entries(mitarbeiter.Arbeitszeiten).forEach(([isoDate, zeiten]) => {
                if (zeiten.Von && zeiten.Bis) {
                    const schichtKey = `${zeiten.Von}-${zeiten.Bis}`;
                    const schichtInfo = SCHICHT_TYPEN[schichtKey] || SCHICHT_TYPEN['default'];
                    events.push({
                        id: `${mitarbeiter.id}_${isoDate}`,
                        title: schichtInfo.title,
                        start: `${isoDate}T${zeiten.Von}:00`,
                        end: `${isoDate}T${zeiten.Bis}:00`,
                        resourceId: mitarbeiter.id,
                        backgroundColor: schichtInfo.color,
                        borderColor: schichtInfo.color
                    });
                }
            });
        }
    });
    if (abwesenheiten) {
        abwesenheiten.forEach(abw => {
            events.push({
                id: `abw_${abw.id}`,
                title: abw.typ,
                start: abw.von,
                end: abw.bis,
                resourceId: abw.mitarbeiterId,
                allDay: true,
                backgroundColor: "#e67e22",
                borderColor: "#e67e22",
                classNames: [styles.absenceEvent],
                editable: false // Abwesenheiten nicht verschiebbar machen
            });
        });
    }
    return { resources, events };
}

function transformLiveSolutionForDisplay(liveSolution) {
    if (!liveSolution || !liveSolution.mitarbeiterList || !liveSolution.arbeitsmusterList) {
        return { mitarbeiterList: [], score: liveSolution?.score };
    }
    const mitarbeiterMap = new Map();
    liveSolution.mitarbeiterList.forEach(m => {
        mitarbeiterMap.set(m.id, { ...m, Arbeitszeiten: {} });
    });
    liveSolution.arbeitsmusterList.forEach(muster => {
        if (muster.mitarbeiter && muster.mitarbeiter.id) {
            const mitarbeiter = mitarbeiterMap.get(muster.mitarbeiter.id);
            if (mitarbeiter && muster.schichten) {
                muster.schichten.forEach(schicht => {
                    mitarbeiter.Arbeitszeiten[schicht.datum] = {
                        Von: schicht.startZeit.substring(0, 5),
                        Bis: schicht.endZeit.substring(0, 5)
                    };
                });
            }
        }
    });
    return { 
        ...liveSolution,
        mitarbeiterList: Array.from(mitarbeiterMap.values()) 
    };
}

function getMondayFromISOWeek(weekString) {
    if (!weekString) return null;
    const [year, week] = weekString.split('-W').map(Number);
    if (isNaN(year) || isNaN(week)) return null;
    const simple = new Date(Date.UTC(year, 0, 4));
    const dayOfWeek = simple.getUTCDay() || 7;
    simple.setUTCDate(simple.getUTCDate() - dayOfWeek + 1);
    simple.setUTCDate(simple.getUTCDate() + (week - 1) * 7);
    return simple;
}

// =======================================================================
// HAUPTKOMPONENTE
// =======================================================================
function SchichtplanAnsicht() {
    const calendarRef = useRef(null);

    // --- State ---
    const [ladeStatus, setLadeStatus] = useState('Lade Plandaten...');
    const [originalMitarbeiter, setOriginalMitarbeiter] = useState([]);
    const [abwesenheiten, setAbwesenheiten] = useState([]);
    const [events, setEvents] = useState([]);
    const [resources, setResources] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    
    // Modals
    const [editModalOpened, { open: openEditModal, close: closeEditModal }] = useDisclosure(false);
    const [editData, setEditData] = useState(null);
    const [deleteModalOpened, { open: openDeleteModal, close: closeDeleteModal }] = useDisclosure(false);
    const [deleteRange, setDeleteRange] = useState({ start: null, end: null });
    const [createModalOpened, { open: openCreateModal, close: closeCreateModal }] = useDisclosure(false);
    const [importModalOpened, { open: openImportModal, close: closeImportModal }] = useDisclosure(false);
    
    // Planung & Export
    const [startWoche, setStartWoche] = useState('');
    const [dauer, setDauer] = useState(4);
    const [problemId, setProblemId] = useState(null);
    const [statusMessage, setStatusMessage] = useState('');
    const [liveSolution, setLiveSolution] = useState(null);
    const [isPolling, setIsPolling] = useState(false);
    const pollingIntervalRef = useRef(null);
    const [exportMonth, setExportMonth] = useState(new Date());
    const [importFile, setImportFile] = useState(null);
    const [isImporting, setIsImporting] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);

    // --- Daten laden & filtern ---
    const fetchData = useCallback(async (showLoading = true) => {
        if (showLoading) setLadeStatus('Lade Plandaten...');
        try {
            const [planResponse, abwResponse] = await Promise.all([
                fetch('/api/schichtplan'),
                fetch('/api/abwesenheiten')
            ]);
            if (!planResponse.ok || !abwResponse.ok) throw new Error('Daten konnten nicht geladen werden');
            
            const planData = await planResponse.json();
            const abwData = await abwResponse.json();
            
            setOriginalMitarbeiter(planData.mitarbeiterDaten || []);
            setAbwesenheiten(abwData || []);
        } catch (error) {
            setLadeStatus(error.message);
        } finally {
            if(showLoading) setLadeStatus('');
        }
    }, []);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    useEffect(() => {
        const filteredMitarbeiter = searchTerm
            ? originalMitarbeiter.filter(m => 
                `${m.vorname} ${m.nachname}`.toLowerCase().includes(searchTerm.toLowerCase())
              )
            : originalMitarbeiter;
        
        const { resources, events } = transformDataForCalendar(filteredMitarbeiter, abwesenheiten);
        setResources(resources);
        setEvents(events);
    }, [originalMitarbeiter, searchTerm, abwesenheiten]);

    // --- Solver & Polling Logik ---
    const handleStartPlanung = async (event) => {
        event.preventDefault();
        const vonDatum = getMondayFromISOWeek(startWoche);
        if (!vonDatum) return alert("Bitte eine gültige Startwoche auswählen.");

        closeCreateModal();
        setStatusMessage('Starte Planung...');
        setIsPolling(true);
        setLiveSolution(null);

        const bisDatum = new Date(vonDatum.valueOf());
        bisDatum.setUTCDate(vonDatum.getUTCDate() + 6 + (dauer - 1) * 7);
        const vonISO = vonDatum.toISOString().split('T')[0];
        const bisISO = bisDatum.toISOString().split('T')[0];

        try {
            const mitarbeiterResponse = await fetch('/api/mitarbeiter-daten');
            const mitarbeiterList = await mitarbeiterResponse.json();
            const response = await fetch('/api/starte-scheduler', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ von: vonISO, bis: bisISO, mitarbeiterList })
            });
            const result = await response.json();
            setProblemId(result.problemId);
        } catch (error) {
            setStatusMessage(`Fehler: ${error.message}`);
            setIsPolling(false);
        }
    };

    useEffect(() => {
        if (!problemId || !isPolling) {
            clearInterval(pollingIntervalRef.current);
            return;
        }
        const pollSolution = async () => {
            try {
                const response = await fetch(`/api/planungs-ergebnis/${problemId}`);
                const rawSolution = await response.json();

                if (response.status === 200 || response.status === 202) {
                    const transformed = transformLiveSolutionForDisplay(rawSolution);
                    const { resources, events } = transformDataForCalendar(transformed.mitarbeiterList, abwesenheiten);
                    setResources(resources);
                    setEvents(events);
                    setLiveSolution(rawSolution);
                    setStatusMessage(`Planung läuft... Score: ${rawSolution.score?.hardScore || 0} Hard / ${rawSolution.score?.softScore || 0} Soft`);
                }
                
                if (response.status === 200) {
                    setStatusMessage(`Planung abgeschlossen!`);
                    setIsPolling(false);
                }
            } catch (error) {
                console.error("Polling-Fehler:", error);
                setStatusMessage(`Fehler bei der Verbindung zum Server.`);
                setIsPolling(false);
            }
        };
        pollingIntervalRef.current = setInterval(pollSolution, 2000);
        return () => clearInterval(pollingIntervalRef.current);
    }, [problemId, isPolling, abwesenheiten]);

    // --- CRUD & Kalender-Interaktionen ---
    const handleEventClick = (clickInfo) => {
        const { event } = clickInfo;
        if (event.id.startsWith('abw_')) return;

        const von = event.start.toTimeString().substring(0, 5);
        const bis = event.end.toTimeString().substring(0, 5);
        const key = `${von}-${bis}`;

        setEditData({
            id: event.id,
            resourceId: event.getResources()[0].id,
            datum: event.start,
            von,
            bis,
            typ: SCHICHT_TYPEN[key] ? key : 'default'
        });
        openEditModal();
    };

    const handleSelect = (selectionInfo) => {
        setEditData({
            id: null,
            resourceId: selectionInfo.resource.id,
            datum: selectionInfo.start,
            von: '08:00',
            bis: '16:30',
            typ: 'default'
        });
        openEditModal();
    };
    
    const handleSave = async () => {
        if (!editData) return;
        let { von, bis, typ, datum, id, resourceId } = editData;
        if (typ !== 'default') {
            [von, bis] = typ.split('-');
        }

        const neuesDatum = datum.toISOString().split('T')[0];
        const isUpdate = Boolean(id);
        const url = isUpdate ? `/api/schicht/${id}` : '/api/schicht';
        const method = isUpdate ? 'PUT' : 'POST';

        try {
            await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ neuesDatum, von, bis, resourceId })
            });
            closeEditModal();
            await fetchData(false);
        } catch (err) {
            alert('Speichern fehlgeschlagen!');
        }
    };

    const handleEventDrop = async (info) => {
        const { event } = info;
        const newResourceId = event.getResources()[0].id;
        const von = event.start.toTimeString().substring(0, 5);
        const bis = event.end.toTimeString().substring(0, 5);
        const neuesDatum = event.start.toISOString().split('T')[0];

        try {
            await fetch(`/api/schicht/${event.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ neuesDatum, von, bis, resourceId: newResourceId })
            });
            await fetchData(false);
        } catch (error) {
            alert("Verschieben fehlgeschlagen!");
            info.revert();
        }
    };

    const handleDeleteSchicht = async () => {
        if (!editData?.id) return;
        try {
            await fetch(`/api/schicht/${editData.id}`, { method: 'DELETE' });
            closeEditModal();
            await fetchData(false);
        } catch (error) {
            console.error("Fehler beim Löschen:", error);
        }
    };

    // KORREKTUR: Implementierte Handler für die Buttons
    const handleImport = async () => {
        if (!importFile) return alert("Bitte eine Datei auswählen.");
        
        setIsImporting(true);
        const formData = new FormData();
        formData.append('absenceFile', importFile);

        try {
            const response = await fetch('/api/import/absences', {
                method: 'POST',
                body: formData,
            });
            const result = await response.json();
            if (!response.ok) throw new Error(result.message);
            
            alert(result.message);
            closeImportModal();
            await fetchData();
        } catch (error) {
            alert(`Import fehlgeschlagen: ${error.message}`);
        } finally {
            setIsImporting(false);
            setImportFile(null);
        }
    };

    const handleSavePlan = async () => {
        if (!liveSolution) return;
        setStatusMessage("Speichere Plan in der Datenbank...");
        try {
            const response = await fetch('/api/plan', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(liveSolution),
            });
            if (!response.ok) throw new Error('Fehler beim Speichern in der DB.');
            
            setStatusMessage("Plan erfolgreich gespeichert!");
            setLiveSolution(null);
            setProblemId(null);
            await fetchData(false);
        } catch (error) {
            setStatusMessage(`Fehler: ${error.message}`);
        }
    };

    const handleDeleteRange = async () => {
        if (!deleteRange.start || !deleteRange.end) return;
        setIsDeleting(true);
        try {
            const response = await fetch(`/api/schichtplan?von=${deleteRange.start}&bis=${deleteRange.end}`, {
                method: 'DELETE',
            });
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Fehler beim Löschen auf dem Server.');
            }
            closeDeleteModal();
            setDeleteRange({ start: null, end: null });
            await fetchData();
        } catch (error) {
            alert(`Löschen fehlgeschlagen: ${error.message}`);
        } finally {
            setIsDeleting(false);
        }
    };

    const handleExport = () => {
        if (!exportMonth) return alert("Bitte einen Monat für den Export auswählen.");
        const dateObject = new Date(exportMonth);
        if (isNaN(dateObject.getTime())) return alert("Ungültiges Datum für den Export.");
        const monthString = `${dateObject.getFullYear()}-${String(dateObject.getMonth() + 1).padStart(2, '0')}`;
        window.location.href = `/api/export-csv?month=${monthString}`;
    };

    // --- JSX Rendering ---
    if (ladeStatus) {
        return <div className={styles.container}><Skeleton height={500} /></div>;
    }

    return (
        <div className={styles.container}>
            {/* --- Modals --- */}
            <Modal opened={editModalOpened} onClose={closeEditModal} title={editData?.id ? 'Schicht bearbeiten' : 'Neue Schicht erstellen'}>
                {editData && (
                    <Box>
                        <Select
                            label="Schicht-Typ"
                            value={editData.typ}
                            onChange={neuerTyp => setEditData(prev => ({ ...prev, typ: neuerTyp }))}
                            data={Object.entries(SCHICHT_TYPEN).map(([key, val]) => ({ value: key, label: val.title }))}
                        />
                        {editData.typ === 'default' && (
                            <Group grow mt="md">
                                <TextInput label="Von (HH:mm)" value={editData.von} onChange={(e) => setEditData(prev => ({...prev, von: e.currentTarget.value}))} />
                                <TextInput label="Bis (HH:mm)" value={editData.bis} onChange={(e) => setEditData(prev => ({...prev, bis: e.currentTarget.value}))} />
                            </Group>
                        )}
                        <Group mt="xl" position="apart">
                            {editData.id && <Button color="red" onClick={handleDeleteSchicht}>Löschen</Button>}
                            <Button variant="default" onClick={closeEditModal}>Abbrechen</Button>
                            <Button color="blue" onClick={handleSave}>{editData.id ? 'Speichern' : 'Hinzufügen'}</Button>
                        </Group>
                    </Box>
                )}
            </Modal>
            
            <Modal opened={importModalOpened} onClose={closeImportModal} title="Abwesenheiten importieren">
                <Text size="sm" mb="md">Lade hier den Excel-Export (.xlsx) aus Factorial hoch.</Text>
                <FileInput label="Excel-Datei" placeholder="Factorial-Export.xlsx" value={importFile} onChange={setImportFile} accept=".xlsx" />
                <Button fullWidth mt="xl" onClick={handleImport} loading={isImporting} disabled={!importFile}>Jetzt importieren</Button>
            </Modal>
            <Modal opened={deleteModalOpened} onClose={closeDeleteModal} title="Pläne für einen Zeitraum löschen" size="lg">
                <p>Alle Schichten innerhalb dieses Zeitraums werden gelöscht.</p>
                <DateRangeSelector onRangeSelect={setDeleteRange} />
                <Button color="red" fullWidth mt="xl" disabled={!deleteRange.start || !deleteRange.end} onClick={handleDeleteRange} loading={isDeleting}>
                    {deleteRange.start && deleteRange.end ? `Pläne vom ${deleteRange.start} bis ${deleteRange.end} löschen` : "Wähle einen Zeitraum"}
                </Button>
            </Modal>
            <Modal opened={createModalOpened} onClose={closeCreateModal} title="Neue Planung erstellen">
                <form onSubmit={handleStartPlanung}>
                    <TextInput label="Start-Kalenderwoche" type="week" value={startWoche} onChange={(e) => setStartWoche(e.currentTarget.value)} required />
                    <NumberInput label="Dauer in Wochen" value={dauer} onChange={setDauer} min={1} max={8} mt="md" required />
                    <Button type="submit" fullWidth mt="xl" leftSection={<IconRocket size={16} />}>Planung starten</Button>
                </form>
            </Modal>

            {/* --- HAUPT-HEADER --- */}
            <div className={styles.header}>
                <TextInput placeholder="Mitarbeiter suchen..." value={searchTerm} onChange={(e) => setSearchTerm(e.currentTarget.value)} className={styles.searchInput} />
                <Group>
                    <MonthPickerInput label="Monat für Export" placeholder="Monat wählen" value={exportMonth} onChange={setExportMonth} />
                    <Button onClick={openCreateModal} leftSection={<IconRocket size={16} />} mt="25px">Planung erstellen...</Button>
                    <Button onClick={openDeleteModal} color="red" leftSection={<IconTrash size={16} />} mt="25px">Löschen...</Button>
                    <Button onClick={openImportModal} variant="default" leftSection={<IconUpload size={16} />} mt="25px">Importieren...</Button>
                    <Button onClick={handleExport} leftSection={<IconDownload size={16} />} mt="25px">Exportieren</Button>
                </Group>
            </div>

            {/* --- Live-Status-Anzeige --- */}
            {isPolling && (
                <Paper shadow="xs" p="md" withBorder my="md">
                    <Text fw={500}>Live-Planungsfortschritt</Text>
                    <Text c="dimmed" size="sm">{statusMessage}</Text>
                    <Progress value={100} striped animated mt="sm" />
                </Paper>
            )}

            {/* --- DER NEUE FULLCALENDAR --- */}
            <div className={styles.calendarWrapper}>
                <FullCalendar
                    ref={calendarRef}
                    plugins={[resourceTimelinePlugin, interactionPlugin]}
                    initialView="resourceTimelineWeek"
                    schedulerLicenseKey="GPL-My-Project-Is-Open-Source"
                    locale={de}
                    resources={resources}
                    events={events}
                    height="85vh"
                    editable={true}
                    selectable={true}
                    eventDrop={handleEventDrop}
                    eventClick={handleEventClick}
                    select={handleSelect}
                    firstDay={1}
                    slotMinTime="06:00:00"
                    slotMaxTime="23:59:00"
                    headerToolbar={{
                        left: 'today prev,next',
                        center: 'title',
                        right: 'resourceTimelineDay,resourceTimelineWeek,resourceTimelineMonth'
                    }}
                />
            </div>
            
            {!isPolling && liveSolution && (
                <Button onClick={handleSavePlan} color="green" size="lg" mt="md" fullWidth>
                    Diesen finalen Plan übernehmen
                </Button>
            )}
        </div>
    );
}

export default SchichtplanAnsicht;
