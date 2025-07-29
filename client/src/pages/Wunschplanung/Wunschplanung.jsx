// client/src/pages/Wunschplanung/Wunschplanung.jsx

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Textarea, Button, Group, Box, Text, Select, ActionIcon, SimpleGrid, TextInput, Accordion, Badge } from '@mantine/core';
import { DatePickerInput } from '@mantine/dates';
import { IconX, IconSparkles, IconDeviceFloppy } from '@tabler/icons-react';
import 'dayjs/locale/de';
import styles from './Wunschplanung.module.css';
import { WUNSCH_TYPEN } from '../../utils.js';

function Wunschplanung() {
    const [mitarbeiterList, setMitarbeiterList] = useState([]);
    const [selectedMitarbeiterId, setSelectedMitarbeiterId] = useState(null);
    const [wuensche, setWuensche] = useState([]); // Wünsche des ausgewählten Mitarbeiters
    const [ladeStatus, setLadeStatus] = useState('Lade Daten...');
    const [textWunsch, setTextWunsch] = useState('');
    const [isParsing, setIsParsing] = useState(false);
    
    // State für die Übersicht aller Wünsche von allen Mitarbeitern
    const [allWishes, setAllWishes] = useState([]);

    // Lade alle initialen Daten (Mitarbeiter und alle Wünsche)
    const fetchInitialData = useCallback(async () => {
        try {
            setLadeStatus('Lade Daten...');
            const mitarbeiterResponse = await fetch('/api/mitarbeiter-daten');
            if (!mitarbeiterResponse.ok) throw new Error('Mitarbeiter konnten nicht geladen werden.');
            const mitarbeiterData = await mitarbeiterResponse.json();
            setMitarbeiterList(mitarbeiterData);

            const wishesResponse = await fetch('/api/wuensche');
            if (!wishesResponse.ok) throw new Error('Alle Wünsche konnten nicht geladen werden.');
            const allWishesData = await wishesResponse.json();
            setAllWishes(allWishesData);

        } catch (error) {
            console.error(error);
        } finally {
            setLadeStatus('');
        }
    }, []);
    
    useEffect(() => {
        fetchInitialData();
    }, [fetchInitialData]);

    // Lade die spezifischen Wünsche, wenn ein Mitarbeiter ausgewählt wird
    useEffect(() => {
        if (selectedMitarbeiterId) {
            const mitarbeiterWishes = allWishes.filter(w => w.mitarbeiterId === selectedMitarbeiterId);
            const formatierteWuensche = mitarbeiterWishes.map(w => ({
                ...w,
                datum: new Date(w.datum)
            }));
            setWuensche(formatierteWuensche);
        } else {
            setWuensche([]);
        }
    }, [selectedMitarbeiterId, allWishes]);

    // Bereitet die Daten für die Übersichts-Accordion vor
    const wishesByEmployee = useMemo(() => {
        const grouped = {};
        allWishes.forEach(wish => {
            const mitarbeiter = mitarbeiterList.find(m => m.id === wish.mitarbeiterId);
            if (mitarbeiter) {
                if (!grouped[wish.mitarbeiterId]) {
                    grouped[wish.mitarbeiterId] = {
                        name: `${mitarbeiter.vorname} ${mitarbeiter.nachname}`,
                        wishes: []
                    };
                }
                grouped[wish.mitarbeiterId].wishes.push(wish);
            }
        });
        return Object.values(grouped).sort((a,b) => a.name.localeCompare(b.name));
    }, [allWishes, mitarbeiterList]);

    // KI-Parsing (simuliert)
    const handleParseWishes = async () => {
        setIsParsing(true);
        await new Promise(resolve => setTimeout(resolve, 1500));
        const kiAntwort = [
            { datum: new Date(), typ: WUNSCH_TYPEN.MUSS, von: '08:00', bis: '16:30' },
            { datum: new Date(new Date().setDate(new Date().getDate() + 1)), typ: WUNSCH_TYPEN.FREI },
        ];
        setWuensche(prev => [...prev, ...kiAntwort]);
        setIsParsing(false);
        setTextWunsch('');
    };
    
    const addEmptyWish = () => {
        setWuensche(prev => [...prev, { datum: new Date(), typ: WUNSCH_TYPEN.FREI, von: '', bis: '' }]);
    };
    
    const removeWish = (index) => {
        setWuensche(prev => prev.filter((_, i) => i !== index));
    };

    const updateWish = (index, field, value) => {
        setWuensche(prev => prev.map((wish, i) => 
            i === index ? { ...wish, [field]: value } : wish
        ));
    };
    
    const handleSaveAllWishes = async () => {
        if (!selectedMitarbeiterId) return alert("Bitte zuerst einen Mitarbeiter auswählen.");
        try {
            const response = await fetch(`/api/wuensche/${selectedMitarbeiterId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(wuensche)
            });
            if (!response.ok) throw new Error('Fehler beim Speichern.');
            alert("Wünsche erfolgreich gespeichert!");
            
            // KORREKTUR: Aktualisiere den lokalen State direkt, anstatt alles neu zu laden.
            // 1. Entferne die alten Wünsche des aktuellen Mitarbeiters aus der Gesamtliste.
            const otherWishes = allWishes.filter(w => w.mitarbeiterId !== selectedMitarbeiterId);
            // 2. Füge die neuen, gespeicherten Wünsche hinzu.
            const newAllWishes = [...otherWishes, ...wuensche];
            // 3. Setze den State, was die Übersicht automatisch aktualisiert.
            setAllWishes(newAllWishes);

        } catch (error) {
            console.error("Fehler beim Speichern der Wünsche:", error);
            alert("Speichern fehlgeschlagen.");
        }
    };

    if (ladeStatus) {
        return <div>{ladeStatus}</div>;
    }

    return (
        <div className={styles.container}>
            <h1>Wunschplanung</h1>
            
            <Select
                label="Mitarbeiter auswählen"
                placeholder="Wähle einen Mitarbeiter aus, um Wünsche zu bearbeiten"
                value={selectedMitarbeiterId}
                onChange={setSelectedMitarbeiterId}
                data={mitarbeiterList.map(m => ({ value: m.id, label: `${m.vorname} ${m.nachname}` }))}
                searchable
                mb="xl"
            />

            {selectedMitarbeiterId && (
                <>
                    <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="xl">
                        <Box className={styles.box}>
                            <Textarea
                                label="Deine Wünsche als Text"
                                placeholder="z.B. Nächsten Montag möchte ich Frühschicht, am 25. brauche ich frei..."
                                value={textWunsch}
                                onChange={(event) => setTextWunsch(event.currentTarget.value)}
                                autosize
                                minRows={3}
                            />
                            <Button 
                                onClick={handleParseWishes} 
                                leftSection={<IconSparkles size={16} />} 
                                mt="md"
                                loading={isParsing}
                            >
                                Wünsche magisch umwandeln
                            </Button>
                        </Box>

                        <Box className={styles.box}>
                            <h2>Strukturierte Wünsche für {mitarbeiterList.find(m => m.id === selectedMitarbeiterId)?.vorname}</h2>
                            <div className={styles.wishList}>
                                {wuensche.map((wish, index) => (
                                    <Group key={index} className={styles.wishItem} grow>
                                        <DatePickerInput
                                            className={styles.dateInput}
                                            locale="de"
                                            placeholder="Datum"
                                            value={wish.datum}
                                            onChange={(date) => updateWish(index, 'datum', date)}
                                            valueFormat="DD.MM.YYYY"
                                        />
                                        <Select
                                            value={wish.typ}
                                            onChange={(value) => updateWish(index, 'typ', value)}
                                            data={Object.values(WUNSCH_TYPEN)}
                                            allowDeselect={false}
                                        />
                                        {wish.typ !== WUNSCH_TYPEN.FREI && (
                                            <TextInput
                                                placeholder="HH:mm-HH:mm"
                                                defaultValue={`${wish.von || '08:00'}-${wish.bis || '16:30'}`}
                                                onChange={(e) => {
                                                    const [von, bis] = e.currentTarget.value.split('-');
                                                    updateWish(index, 'von', von || '');
                                                    updateWish(index, 'bis', bis || '');
                                                }}
                                            />
                                        )}
                                        <ActionIcon color="red" variant="subtle" onClick={() => removeWish(index)}>
                                            <IconX size={18} />
                                        </ActionIcon>
                                    </Group>
                                ))}
                            </div>
                            <Button onClick={addEmptyWish} variant="light" mt="md">
                                Wunsch manuell hinzufügen
                            </Button>
                        </Box>
                    </SimpleGrid>

                    <Group justify="flex-end" mt="xl">
                        <Button 
                            size="lg" 
                            color="green" 
                            onClick={handleSaveAllWishes}
                            leftSection={<IconDeviceFloppy size={20} />}
                        >
                            Wünsche für diesen Mitarbeiter speichern
                        </Button>
                    </Group>
                </>
            )}

            <Box mt="xl" pt="xl" style={{ borderTop: '1px solid var(--mantine-color-gray-2)' }}>
                <h2>Übersicht aller eingetragenen Wünsche</h2>
                <Accordion variant="separated">
                    {wishesByEmployee.map(employee => (
                        <Accordion.Item key={employee.name} value={employee.name}>
                            <Accordion.Control>
                                <Text fw={500}>{employee.name} <Badge>{employee.wishes.length} Wünsche</Badge></Text>
                            </Accordion.Control>
                            <Accordion.Panel>
                                {employee.wishes.sort((a, b) => new Date(a.datum) - new Date(b.datum)).map((wish, index) => (
                                    <Group key={index} className={styles.wishItem} justify="space-between">
                                        <Text>{new Date(wish.datum).toLocaleDateString('de-DE', { weekday: 'short', day: '2-digit', month: '2-digit' })}</Text>
                                        <Badge color="blue">{wish.typ}</Badge>
                                        {wish.von && <Text c="dimmed">{wish.von} - {wish.bis}</Text>}
                                    </Group>
                                ))}
                            </Accordion.Panel>
                        </Accordion.Item>
                    ))}
                </Accordion>
            </Box>
        </div>
    );
}

export default Wunschplanung;
