
import React, { useState, useEffect, useMemo, useCallback} from 'react';
import FullCalendar from '@fullcalendar/react';
import resourceTimelinePlugin from '@fullcalendar/resource-timeline';
import interactionPlugin from "@fullcalendar/interaction"; 
import de from '@fullcalendar/core/locales/de';
import 'react-big-calendar/lib/css/react-big-calendar.css';
import styles from './PlanBearbeiten.module.css'; 
import { useDisclosure } from '@mantine/hooks';
import { Modal, Button, TextInput, Group,Select, Box } from '@mantine/core';
import { TimeInput, DatePicker } from '@mantine/dates'; 
import { SCHICHT_TYPEN, transformDataForCalendar, formatTime } from '../../utils.js';

function PlanBearbeiten() {
    const [events, setEvents] = useState([]);
    const [resources, setResources] = useState([]);
    const [ladeStatus, setLadeStatus] = useState('Lade Plandaten...');
    const [modalOpened, { open: openModal, close: closeModal }] = useDisclosure(false);
    const [editData, setEditData] = useState({
        id: null,
        resourceId: '',
        datum: new Date(),
        von: '08:00', // Standardzeit - Wird nicht benutzt, ohne gehts aber auch nicht
        bis: '16:30',
        typ: 'default'
    });

    const fetchData = async () => {
        try {
            const response = await fetch('/api/schichtplan');
            if (!response.ok) throw new Error('Daten konnten nicht geladen werden');
            const data = await response.json();
            const { events, resources } = transformDataForCalendar(data.mitarbeiterDaten);
            setEvents(events);
            setResources(resources);
        } catch (error) {
            console.error(error);
        } finally {
            setLadeStatus('');
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const handleEventClick = (clickInfo) => {
    const von  = clickInfo.event.startStr.substring(11, 16);
    const bis  = clickInfo.event.endStr.substring(11, 16);
    const key  = `${von}-${bis}`;

    setEditData({
        id: clickInfo.event.id,
        resourceId: clickInfo.event.getResources()[0].id,
        datum: clickInfo.event.start,
        von,
        bis,
        typ: SCHICHT_TYPEN[key] ? key : 'default'
    });

    openModal();
    };


    const handleSelect = (selectionInfo) => {
        const start     = new Date(selectionInfo.start);
        const end   = new Date(start.getTime() + 8.5 * 60 * 60 * 1000);  

        setEditData({
            id: null,
            resourceId: selectionInfo.resource.id,
            datum: start,
            von: start.toTimeString().substring(0, 5),
            bis: end.toTimeString().substring(0, 5),
            typ: 'default'
        });
        openModal();
    };

    const handleSave = async () => {
        if (!editData) return;
        let { von, bis, typ, datum, id, resourceId } = editData;
        if (typ !== 'default') {
            [von, bis] = typ.split('-');
        }

        const neuesDatum = datum.toISOString().split('T')[0];
        const isUpdate = Boolean(id);
        const url      = isUpdate ? `/api/schicht/${id}` : '/api/schicht';
        const method   = isUpdate ? 'PUT'            : 'POST';

        try {
            await fetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                neuesDatum,
                von,
                bis,
                resourceId
            })
            });

            closeModal();
            await fetchData();
        } catch (err) {
            console.error('Speichern fehlgeschlagen', err);
            alert('Speichern fehlgeschlagen!');
        }
    };

    const handleDelete = async () => {
        if (!editData?.id) return;
        try {
            await fetch(`/api/schicht/${editData.id}`, { method: 'DELETE' });
            closeModal();
            await fetchData();
        } catch (error) {
            console.error("Fehler beim Löschen:", error);
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
        } catch (error) {
            alert("Speichern fehlgeschlagen!");
            info.revert();
        }
    };

    if (ladeStatus) return <div>{ladeStatus}</div>;

    return (
        <div className={styles.container}>
            <div className={styles.calendarWrapper}>
                <FullCalendar
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
                    weekends={false}
                    slotDuration={'00:15:00'} 
                    snapDuration={'00:15:00'}
                />
            </div>

            <Modal
                opened={modalOpened}
                onClose={closeModal}
                title={editData.id ? 'Schicht bearbeiten' : 'Neue Schicht erstellen'}
            >
                {editData && (
                    <Box>
                    <Select
                        label="Schicht-Typ"
                        value={editData.typ}
                        onChange={neuerTyp =>
                        setEditData(prev => ({ ...prev, typ: neuerTyp }))
                        }
                        data={Object.entries(SCHICHT_TYPEN).map(
                        ([key, val]) => ({ value: key, label: val.title })
                        )}
                    />
                    {editData.typ === 'default' && (
                        <>
                        <Group grow mt="md">
                            <Select
                                label="Von (Stunde)"
                                value={editData.von.split(':')[0]}
                                onChange={(stunde) => setEditData(prev => ({ ...prev, von: `${stunde}:${prev.von.split(':')[1] || '00'}` }))}
                                data={Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))}
                            />
                            <Select
                                label="Minute"
                                value={editData.von.split(':')[1]}
                                onChange={(minute) => setEditData(prev => ({ ...prev, von: `${prev.von.split(':')[0] || '00'}:${minute}` }))}
                                data={['00', '15', '30', '45']}
                            />
                        </Group>
                        <Group grow mt="sm">
                            <Select
                                label="Bis (Stunde)"
                                value={editData.bis.split(':')[0]}
                                onChange={(stunde) => setEditData(prev => ({ ...prev, bis: `${stunde}:${prev.bis.split(':')[1] || '00'}` }))}
                                data={Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0'))}
                            />
                            <Select
                                label="Minute"
                                value={editData.bis.split(':')[1]}
                                onChange={(minute) => setEditData(prev => ({ ...prev, bis: `${prev.bis.split(':')[0] || '00'}:${minute}` }))}
                                data={['00', '15', '30', '45']}
                            />
                        </Group>
                        </>
                    )}

                    <Group mt="xl" position="apart">
                        {editData.id && (
                        <Button color="red" onClick={handleDelete}>
                            Löschen
                        </Button>
                        )}
                        <Button variant="default" onClick={closeModal}>
                        Abbrechen
                        </Button>
                        <Button color="blue" onClick={handleSave}>
                        {editData.id ? 'Speichern' : 'Hinzufügen'}
                        </Button>
                    </Group>
                    </Box>
                )}
            </Modal>
        </div>
    );
}

export default PlanBearbeiten;