// client/src/pages/MitarbeiterVerwaltung/MitarbeiterVerwaltung.jsx

import React, { useState, useEffect, useMemo } from 'react';
import { Table, TextInput, Button, Group, Box, Checkbox, Modal, Badge, ActionIcon, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconX, IconPlus, IconTrash, IconDeviceFloppy } from '@tabler/icons-react';
import styles from './MitarbeiterVerwaltung.module.css';

function MitarbeiterVerwaltung() {
    const [mitarbeiter, setMitarbeiter] = useState([]);
    const [ladeStatus, setLadeStatus] = useState('Lade Daten...');
    const [searchTerm, setSearchTerm] = useState('');
    
    // Modal für Wunschschichten
    const [wunschModalOpened, { open: openWunschModal, close: closeWunschModal }] = useDisclosure(false);
    
    // NEU: Modal für die Löschbestätigung
    const [deleteModalOpened, { open: openDeleteModal, close: closeDeleteModal }] = useDisclosure(false);
    
    const [editingMitarbeiter, setEditingMitarbeiter] = useState(null);
    const [newWish, setNewWish] = useState('');
    
    // NEU: State für den zu löschenden Mitarbeiter
    const [mitarbeiterToDelete, setMitarbeiterToDelete] = useState(null);

    // Daten vom Server laden
    const fetchData = async () => {
        try {
            setLadeStatus('Lade Daten...');
            const response = await fetch('/api/mitarbeiter-daten');
            if (!response.ok) throw new Error('Daten konnten nicht geladen werden.');
            const data = await response.json();
            const sanitizedData = data.map(m => ({
                ...m,
                rollenUndQualifikationen: m.rollenUndQualifikationen || [],
                teamsUndZugehoerigkeiten: m.teamsUndZugehoerigkeiten || [],
                wunschschichten: m.wunschschichten || []
            }));
            setMitarbeiter(sanitizedData);
        } catch (error) {
            console.error("Fehler beim Laden:", error);
            setLadeStatus(error.message);
        } finally {
            setLadeStatus('');
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    // Mitarbeiter filtern basierend auf der Suche
    const gefilterteMitarbeiter = useMemo(() =>
        mitarbeiter.filter(m =>
            `${m.vorname} ${m.nachname}`.toLowerCase().includes(searchTerm.toLowerCase())
        ), [mitarbeiter, searchTerm]);

    // Handler für Änderungen in den Textfeldern der Tabelle
    const handleInputChange = (id, field, value) => {
        setMitarbeiter(prev =>
            prev.map(m => (m.id === id ? { ...m, [field]: value } : m))
        );
    };

    // Handler zum Speichern ALLER Änderungen
    const handleSaveAll = async () => {
        try {
            const savePromises = mitarbeiter.map(m => {
                const { isNew, ...dataToSend } = m;
                let url;
                let method;
                let body;

                // Unterscheide zwischen neuen und bestehenden Mitarbeitern
                if (isNew) {
                    url = '/api/mitarbeiter';
                    method = 'POST';
                    // KORREKTUR: Sende neue Mitarbeiter ohne die temporäre ID
                    const { id, ...newEmployeeData } = dataToSend;
                    body = JSON.stringify(newEmployeeData);
                } else {
                    url = `/api/mitarbeiter/${m.id}`;
                    method = 'PUT';
                    body = JSON.stringify(dataToSend);
                }
                
                return fetch(url, {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: body,
                });
            });

            const responses = await Promise.all(savePromises);

            // Prüfe, ob alle Anfragen erfolgreich waren
            for (const res of responses) {
                if (!res.ok) {
                    throw new Error(`Server-Antwort war nicht ok: ${res.status}`);
                }
            }

            console.log('Alle Änderungen erfolgreich gespeichert!');
            fetchData(); // Lade die Daten neu, um konsistente IDs zu haben
        } catch (error) {
            console.error('Fehler beim Speichern aller Änderungen:', error);
        }
    };
    
    // Handler zum Hinzufügen eines neuen Mitarbeiters
    const handleAddNewMitarbeiter = () => {
        const maxId = mitarbeiter.reduce((max, m) => {
            const currentId = parseInt(m.id, 10);
            return isNaN(currentId) ? max : Math.max(max, currentId);
        }, 0);

        const newId = String(maxId + 1).padStart(3, '0');

        const newMitarbeiter = {
            id: newId,
            vorname: '',
            nachname: '',
            email: '',
            stellenbezeichnung: '',
            ressort: '',
            wochenstunden: 40,
            cvd: false,
            notizen: '',
            rollenUndQualifikationen: [],
            teamsUndZugehoerigkeiten: [],
            wunschschichten: [],
            isNew: true // Wichtiger Marker
        };
        setMitarbeiter(prev => [newMitarbeiter, ...prev]);
    };

    // Handler zum Öffnen des Löschen-Modals
    const handleDeleteClick = (mitarbeiter) => {
        if (mitarbeiter.isNew) {
            setMitarbeiter(prev => prev.filter(m => m.id !== mitarbeiter.id));
        } else {
            setMitarbeiterToDelete(mitarbeiter);
            openDeleteModal();
        }
    };

    // Führt das Löschen nach Bestätigung aus
    const confirmDelete = async () => {
        if (!mitarbeiterToDelete) return;
        try {
            await fetch(`/api/mitarbeiter/${mitarbeiterToDelete.id}`, { method: 'DELETE' });
            console.log('Mitarbeiter gelöscht.');
            fetchData();
        } catch (error) {
            console.error('Fehler beim Löschen.', error);
        } finally {
            closeDeleteModal();
            setMitarbeiterToDelete(null);
        }
    };

    // Öffnet das Modal für die Wunschschichten
    const handleOpenWunschModal = (mitarbeiter) => {
        setEditingMitarbeiter({ ...mitarbeiter });
        openWunschModal();
    };

    // Fügt einen neuen Wunsch im Modal hinzu
    const handleAddWish = () => {
        if (newWish && editingMitarbeiter) {
            const updatedWunschschichten = [...editingMitarbeiter.wunschschichten, newWish];
            setEditingMitarbeiter(prev => ({...prev, wunschschichten: updatedWunschschichten}));
        }
    };

    // Entfernt einen Wunsch im Modal
    const handleRemoveWish = (wishToRemove) => {
        if (editingMitarbeiter) {
            const updatedWunschschichten = editingMitarbeiter.wunschschichten.filter(w => w !== wishToRemove);
            setEditingMitarbeiter(prev => ({...prev, wunschschichten: updatedWunschschichten}));
        }
    };

    // Speichert die Änderungen aus dem Wunsch-Modal im lokalen State
    const handleSaveWishes = () => {
        if (!editingMitarbeiter) return;
        handleInputChange(editingMitarbeiter.id, 'wunschschichten', editingMitarbeiter.wunschschichten);
        closeWunschModal();
    };

    if (ladeStatus) {
        return <div>{ladeStatus}</div>;
    }

    return (
        <div className={styles.container}>
            <Group justify="space-between" mb="xl">
                <h1>Mitarbeiter verwalten</h1>
                <Group>
                    <TextInput
                        placeholder="Suchen..."
                        value={searchTerm}
                        onChange={(event) => setSearchTerm(event.currentTarget.value)}
                        className={styles.searchInput}
                    />
                    <Button onClick={handleAddNewMitarbeiter} leftSection={<IconPlus size={16} />}>
                        Mitarbeiter hinzufügen
                    </Button>
                    <Button color="green" onClick={handleSaveAll} leftSection={<IconDeviceFloppy size={16} />}>
                        Alles Speichern
                    </Button>
                </Group>
            </Group>

            <Box className={styles.tableWrapper}>
                <Table striped highlightOnHover withTableBorder withColumnBorders>
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th>Name</Table.Th>
                            <Table.Th>E-Mail</Table.Th>
                            <Table.Th>Stellenbezeichnung</Table.Th>
                            <Table.Th>Ressort</Table.Th>
                            <Table.Th>Std./Woche</Table.Th>
                            <Table.Th>CvD</Table.Th>
                            <Table.Th>Qualifikationen</Table.Th>
                            <Table.Th>Teams</Table.Th>
                            <Table.Th>Wunschschichten</Table.Th>
                            <Table.Th>Aktionen</Table.Th>
                        </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                        {gefilterteMitarbeiter.map((m) => (
                            <Table.Tr key={m.id}>
                                <Table.Td>
                                    <Group>
                                        <TextInput placeholder="Vorname" value={m.vorname} onChange={(e) => handleInputChange(m.id, 'vorname', e.currentTarget.value)} />
                                        <TextInput placeholder="Nachname" value={m.nachname} onChange={(e) => handleInputChange(m.id, 'nachname', e.currentTarget.value)} />
                                    </Group>
                                </Table.Td>
                                <Table.Td><TextInput value={m.email} onChange={(e) => handleInputChange(m.id, 'email', e.currentTarget.value)} /></Table.Td>
                                <Table.Td><TextInput value={m.stellenbezeichnung} onChange={(e) => handleInputChange(m.id, 'stellenbezeichnung', e.currentTarget.value)} /></Table.Td>
                                <Table.Td><TextInput value={m.ressort} onChange={(e) => handleInputChange(m.id, 'ressort', e.currentTarget.value)} /></Table.Td>
                                <Table.Td><TextInput type="number" style={{width: '80px'}} value={m.wochenstunden} onChange={(e) => handleInputChange(m.id, 'wochenstunden', parseInt(e.currentTarget.value, 10) || 0)} /></Table.Td>
                                <Table.Td><Checkbox checked={m.cvd} onChange={(e) => handleInputChange(m.id, 'cvd', e.currentTarget.checked)} /></Table.Td>
                                <Table.Td><TextInput value={m.rollenUndQualifikationen.join(', ')} onChange={(e) => handleInputChange(m.id, 'rollenUndQualifikationen', e.currentTarget.value.split(',').map(s => s.trim()))} /></Table.Td>
                                <Table.Td><TextInput value={m.teamsUndZugehoerigkeiten.join(', ')} onChange={(e) => handleInputChange(m.id, 'teamsUndZugehoerigkeiten', e.currentTarget.value.split(',').map(s => s.trim()))} /></Table.Td>
                                <Table.Td>
                                    <Button size="xs" variant="outline" onClick={() => handleOpenWunschModal(m)}>Wünsche ({m.wunschschichten.length})</Button>
                                </Table.Td>
                                <Table.Td>
                                    <ActionIcon variant="subtle" color="red" onClick={() => handleDeleteClick(m)}>
                                        <IconTrash size={18} />
                                    </ActionIcon>
                                </Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            </Box>

            {/* Modal für Wunschschichten */}
            <Modal opened={wunschModalOpened} onClose={closeWunschModal} title={`Wunschschichten für ${editingMitarbeiter?.vorname || ''}`}>
                {editingMitarbeiter && (
                    <Box>
                        <Text mb="sm">Aktuelle Wünsche:</Text>
                        <Group mb="md" gap="xs">
                            {editingMitarbeiter.wunschschichten.map(wish => (
                                <Badge key={wish} rightSection={
                                    <ActionIcon size="xs" color="blue" radius="xl" variant="transparent" onClick={() => handleRemoveWish(wish)}>
                                        <IconX size={14} />
                                    </ActionIcon>
                                }>
                                    {wish}
                                </Badge>
                            ))}
                        </Group>
                        <Group>
                            <TextInput
                                placeholder="Neuer Wunsch (z.B. MO-FR)"
                                value={newWish}
                                onChange={(e) => setNewWish(e.currentTarget.value)}
                                style={{ flex: 1 }}
                            />
                            <Button onClick={handleAddWish}><IconPlus size={16} /></Button>
                        </Group>
                        <Group justify="flex-end" mt="xl">
                            <Button variant="default" onClick={closeWunschModal}>Abbrechen</Button>
                            <Button color="blue" onClick={handleSaveWishes}>Wünsche übernehmen</Button>
                        </Group>
                    </Box>
                )}
            </Modal>

            {/* NEU: Modal für Löschbestätigung */}
            <Modal opened={deleteModalOpened} onClose={closeDeleteModal} title="Mitarbeiter löschen" centered>
                <Text>Bist du sicher, dass du den Mitarbeiter "{mitarbeiterToDelete?.vorname} {mitarbeiterToDelete?.nachname}" endgültig löschen möchtest?</Text>
                <Group justify="flex-end" mt="lg">
                    <Button variant="default" onClick={closeDeleteModal}>Abbrechen</Button>
                    <Button color="red" onClick={confirmDelete}>Endgültig löschen</Button>
                </Group>
            </Modal>
        </div>
    );
}

export default MitarbeiterVerwaltung;
