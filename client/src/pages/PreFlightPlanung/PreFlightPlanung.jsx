import React, { useState, useEffect } from "react";
import axios from "axios";
import Select from "react-select"; // NEU: Import für das fancy Dropdown
import styles from "./PreFlightPlanung.module.css"; // NEU: Import für CSS-Module

// --- Helper-Funktionen ---

// Erzeugt dynamisch die nächsten 12 Monate für die Auswahl
function getNextMonths() {
    const months = [];
    const date = new Date();
    for (let i = 0; i < 12; i++) {
        const monthStr = date.toISOString().slice(0, 7);
        const label = date.toLocaleDateString("de-DE", { month: "long", year: "numeric" });
        months.push({ value: monthStr, label: label });
        date.setMonth(date.getMonth() + 1);
    }
    return months;
}

function getDaysInMonth(monthStr) {
    const [year, month] = monthStr.split("-");
    const date = new Date(year, month - 1, 1);
    const days = [];
    while (date.getMonth() === Number(month) - 1) {
        days.push(date.toISOString().slice(0, 10));
        date.setDate(date.getDate() + 1);
    }
    return days;
}

// --- Status-Konstanten ---
const statusCycle = ["default", "on", "off"];
const statusClasses = {
    default: styles.dayDefault,
    on: styles.dayOn,
    off: styles.dayOff,
};

// =======================================================================
// Hauptkomponente
// =======================================================================
export default function PreFlightPlanung() {
    // --- State-Management ---
    const [allEmployees, setAllEmployees] = useState([]);
    const [months, setMonths] = useState([]);
    const [selectedEmployee, setSelectedEmployee] = useState(null); // ist jetzt ein Objekt
    const [selectedMonth, setSelectedMonth] = useState(null);       // ist jetzt ein Objekt
    const [days, setDays] = useState([]);
    const [dayStatus, setDayStatus] = useState({});
    const [loading, setLoading] = useState(false);
    const [saveMsg, setSaveMsg] = useState("");

    // --- Daten laden (Mitarbeiter & Monate) ---
    useEffect(() => {
        // Mitarbeiter vom Backend laden
        axios.get("/api/mitarbeiter-daten") // Annahme: Dein Endpunkt für Mitarbeiter
            .then(res => {
                const employeeOptions = res.data.map(ma => ({
                    value: ma.id,
                    label: `${ma.vorname} ${ma.nachname}` // Annahme: Deine Mitarbeiter-Objekte haben vorname/nachname
                }));
                setAllEmployees(employeeOptions);
            })
            .catch(err => console.error("Fehler beim Laden der Mitarbeiter:", err));
        
        // Monate dynamisch erzeugen
        setMonths(getNextMonths());
    }, []); // Läuft nur einmal beim Start

    // --- Kalender und bestehende Wünsche laden ---
    useEffect(() => {
        if (selectedMonth) {
            setDays(getDaysInMonth(selectedMonth.value));
            setDayStatus({}); // Kalender zurücksetzen bei Monatswechsel
            setSaveMsg("");
        }
        
        if (selectedEmployee && selectedMonth) {
            setLoading(true);
            // Annahme: Dein API-Endpunkt, um gespeicherte PREFLIGHT-Wünsche zu laden
            axios.get(`/api/preflight/${selectedEmployee.value}/${selectedMonth.value}`)
                .then(res => setDayStatus(res.data.details || {}))
                .catch(() => setDayStatus({}))
                .finally(() => setLoading(false));
        }
    }, [selectedEmployee, selectedMonth]);

    // --- Event-Handler ---
    function handleDayClick(day) {
        setDayStatus(prev => {
            const current = prev[day] || "default";
            const next = statusCycle[(statusCycle.indexOf(current) + 1) % statusCycle.length];
            const updated = { ...prev, [day]: next };
            if (next === "default") delete updated[day];
            return updated;
        });
    }

    async function handleSave() {
        if (!selectedEmployee || !selectedMonth) {
            setSaveMsg("Bitte Mitarbeiter und Monat wählen.");
            return;
        }
        setLoading(true);
        setSaveMsg("");
        try {
            // Sende die Daten an den Backend-Endpunkt
            await axios.post("/api/preflight", {
                mitarbeiterId: selectedEmployee.value,
                typ: "PREFLIGHT",
                datum: `${selectedMonth.value}-01`, // Start des Monats als Referenz
                details: dayStatus,
            });
            setSaveMsg("Wunsch-Muster erfolgreich gespeichert!");
        } catch {
            setSaveMsg("Fehler beim Speichern des Musters.");
        }
        setLoading(false);
    }

    // --- JSX-Rendering ---
    return (
        <div className={styles.container}>
            <h2>PreFlight-Planung</h2>
            <p>Hier kannst du für einen Mitarbeiter ein komplettes Wochen- oder Monatsmuster festlegen. Dies hat Vorrang vor allen anderen Regeln.</p>
            
            <div className={styles.controls}>
                <Select
                    className={styles.dropdown}
                    options={allEmployees}
                    value={selectedEmployee}
                    onChange={setSelectedEmployee}
                    placeholder="Mitarbeiter wählen..."
                    isClearable
                    isSearchable
                />
                <Select
                    className={styles.dropdown}
                    options={months}
                    value={selectedMonth}
                    onChange={setSelectedMonth}
                    placeholder="Monat wählen..."
                />
            </div>

            {selectedEmployee && selectedMonth && (
                <div className={styles.calendarContainer}>
                    <h3>Muster für {selectedEmployee.label}, {selectedMonth.label}</h3>
                    <div className={styles.grid}>
                        {days.map(day => {
                            const status = dayStatus[day] || "default";
                            const dateObj = new Date(day + "T00:00:00"); // Zeitzonen-Bug vermeiden
                            const dayNum = dateObj.getDate();
                            const weekday = dateObj.toLocaleDateString("de-DE", { weekday: "short" });
                            return (
                                <div key={day} onClick={() => handleDayClick(day)} className={`${styles.day} ${statusClasses[status]}`}>
                                    <div className={styles.dayNumber}>{dayNum}</div>
                                    <div className={styles.weekday}>{weekday}</div>
                                    <div className={styles.statusIcon}>
                                        {status === "on" ? "✅" : status === "off" ? "❌" : ""}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                    <button onClick={handleSave} disabled={loading} className={styles.saveButton}>
                        {loading ? "Speichert..." : `Muster für ${selectedMonth.label} speichern`}
                    </button>
                    {saveMsg && <div className={saveMsg.includes("Fehler") ? styles.msgError : styles.msgSuccess}>{saveMsg}</div>}
                </div>
            )}
        </div>
    );
}