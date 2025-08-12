import React, { useState } from 'react';
import styles from './DateRangeSelector.module.css';

// NEU: Importiere die nötigen Helfer und Komponenten
import { Group, ActionIcon, Text } from '@mantine/core';
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react';
import { 
    isAfter, isBefore, isEqual, 
    startOfMonth, endOfMonth, eachDayOfInterval, 
    format, addMonths, subMonths 
} from 'date-fns';
import { de } from 'date-fns/locale'; // Deutscher Monatsname

function DateRangeSelector({ onRangeSelect }) {
    const [currentMonth, setCurrentMonth] = useState(new Date());
    const [startDate, setStartDate] = useState(null);
    const [endDate, setEndDate] = useState(null);
    const [hoverDate, setHoverDate] = useState(null);

    // Diese Logik generiert die Tage für den jeweils aktuellen Monat
    const days = eachDayOfInterval({
        start: startOfMonth(currentMonth),
        end: endOfMonth(currentMonth),
    });

    // NEU: Handler zum Wechseln des Monats
    const goToPreviousMonth = () => setCurrentMonth(subMonths(currentMonth, 1));
    const goToNextMonth = () => setCurrentMonth(addMonths(currentMonth, 1));

    const handleDayClick = (day) => {
        const isoDay = day.toISOString().split('T')[0];
        if (!startDate || (startDate && endDate)) {
            setStartDate(day);
            setEndDate(null);
            onRangeSelect({ start: isoDay, end: null });
        } else if (isBefore(day, startDate)) {
            setEndDate(startDate);
            setStartDate(day);
            onRangeSelect({ start: isoDay, end: startDate.toISOString().split('T')[0] });
        } else {
            setEndDate(day);
            onRangeSelect({ start: startDate.toISOString().split('T')[0], end: isoDay });
        }
    };
    
    return (
        <div className={styles.container}>
            {/* NEU: Die Monats-Navigationsleiste */}
            <Group justify="space-between" mb="md" className={styles.header}>
                <ActionIcon variant="outline" onClick={goToPreviousMonth}>
                    <IconChevronLeft size={16} />
                </ActionIcon>
                <Text fw={500} className={styles.monthDisplay}>
                    {format(currentMonth, 'MMMM yyyy', { locale: de })}
                </Text>
                <ActionIcon variant="outline" onClick={goToNextMonth}>
                    <IconChevronRight size={16} />
                </ActionIcon>
            </Group>
            
            <div className={styles.grid}>
                {days.map(day => {
                    const finalEndDate = endDate || hoverDate;
                    const inRange = startDate && finalEndDate && !isBefore(day, startDate) && !isAfter(day, finalEndDate);
                    const isStart = startDate && isEqual(day, startDate);
                    const isEnd = startDate && finalEndDate && isEqual(day, finalEndDate);

                    let dayClass = styles.day;
                    if (inRange) dayClass += ` ${styles.selected}`;
                    if (isStart) dayClass += ` ${styles.start}`;
                    if (isEnd) dayClass += ` ${styles.end}`;

                    return (
                        <div
                            key={day.toString()}
                            className={dayClass}
                            onClick={() => handleDayClick(day)}
                            onMouseEnter={() => startDate && !endDate && setHoverDate(day)}
                        >
                            {day.getDate()}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

export default DateRangeSelector;