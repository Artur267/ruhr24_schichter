import React from 'react';
import { Card, Text, Badge, Group, Button } from '@mantine/core';
import styles from './MitarbeiterCard.module.css';

function MitarbeiterCard({ mitarbeiter, onEdit }) {
  return (
    <Card shadow="sm" padding="lg" radius="md" withBorder className={styles.card}>
      <Group justify="space-between" mt="md" mb="xs">
        <Text fw={500}>{mitarbeiter.vorname} {mitarbeiter.nachname}</Text>
        <Badge color={mitarbeiter.CVD ? 'orange' : 'gray'} variant="light">
          {mitarbeiter.CVD ? 'CvD' : 'Redaktion'}
        </Badge>
      </Group>

      <Text size="sm" c="dimmed">
        {mitarbeiter.Stellenbezeichnung}
      </Text>
      <Text size="sm" c="dimmed">
        Ressort: {mitarbeiter.Ressort}
      </Text>

      <Button
        variant="light"
        color="blue"
        fullWidth
        mt="md"
        radius="md"
        onClick={() => onEdit(mitarbeiter)}
      >
        Bearbeiten
      </Button>
    </Card>
  );
}

export default MitarbeiterCard;