// client/src/pages/MitarbeiterVerwaltung/MitarbeiterVerwaltung.jsx

import React, { useState, useEffect, useMemo } from 'react';
import { TextInput, Modal, Button, SimpleGrid } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import MitarbeiterCard from '../../components/MitarbeiterCard/MitarbeiterCard';
import styles from './MitarbeiterVerwaltung.module.css';

function MitarbeiterVerwaltung() {
    const [mitarbeiterList, setMitarbeiterList] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [editingMitarbeiter, setEditingMitarbeiter] = useState(null);
    const [opened, { open, close }] = useDisclosure(false);

    // Daten laden
    useEffect(() => {
        fetch('/api/mitarbeiter-daten')
            .then(res => res.json())
            .then(data => setMitarbeiterList(data));
    }, []);

    // Mitarbeiter filtern
    const filteredMitarbeiter = useMemo(() => 
        mitarbeiterList.filter(m =>
            `${m.vorname} ${m.nachname}`.toLowerCase().includes(searchTerm.toLowerCase())
        ), [mitarbeiterList, searchTerm]);

    // Bearbeiten-Modal öffnen
    const handleEditClick = (mitarbeiter) => {
        setEditingMitarbeiter(mitarbeiter);
        open();
    };

    // Änderungen speichern
    const handleSave = () => {
        // Hier kommt die fetch-Logik zum Speichern an /api/mitarbeiter/:id
        console.log("Speichere:", editingMitarbeiter);
        // fetch(`/api/mitarbeiter/${editingMitarbeiter.id}`, { method: 'PUT', ... })
        close();
    };

    return (
        <div className={styles.container}>
            <TextInput
                placeholder="Mitarbeiter suchen..."
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.currentTarget.value)}
                className={styles.search}
            />

            <SimpleGrid cols={{ base: 1, sm: 2, md: 3, lg: 4 }}>
                {filteredMitarbeiter.map(m => (
                    <MitarbeiterCard key={m.id} mitarbeiter={m} onEdit={handleEditClick} />
                ))}
            </SimpleGrid>

            {/* Bearbeiten-Modal */}
            <Modal opened={opened} onClose={close} title="Mitarbeiter bearbeiten">
                {editingMitarbeiter && (
                    <div>
                        <p>Hier kommen die Formularfelder für {editingMitarbeiter.vorname} {editingMitarbeiter.nachname} hin.</p>
                        {/* Beispiel: <TextInput label="Ressort" defaultValue={editingMitarbeiter.Ressort} /> */}
                        <Button onClick={handleSave} mt="md">Speichern</Button>
                    </div>
                )}
            </Modal>
        </div>
    );
}

export default MitarbeiterVerwaltung;