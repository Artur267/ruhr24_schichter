# scheduler.py

import json
import os
import sys
import requests
import csv
from pathlib import Path
from datetime import datetime, timedelta
from ortools.linear_solver import pywraplp
from dotenv import load_dotenv

# === 1. ENV-VARIABLEN LADEN ===
load_dotenv()
gemini_api_key = os.getenv("GOOGLE_API_KEY")  # Nutzt Gemini statt OpenAI
gemini_url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

# === 2. DATEIPFADE DEFINIEREN ===
def get_base_dir():
    # Bestimme das Basisverzeichnis des Skripts
    return Path(os.getcwd()).resolve()

def get_file_path(filename):
    # Rückgabe des Pfads zu einer Datei im 'data' Ordner basierend auf dem Basisverzeichnis
    return get_base_dir() / "data" / filename

# Dateipfade
EMPLOYEE_DATA_PATH = get_file_path("mitarbeiter.json")
SETTINGS_PATH = get_file_path("settings.json")
RULES_PATH = get_file_path("regelwerk.json")
OUTPUT_PATH = get_base_dir() / "results" / "output.csv"

# Ausgabe der Pfade
print("Employee Data Path:", EMPLOYEE_DATA_PATH)
print("Settings Path:", SETTINGS_PATH)
print("Rules Path:", RULES_PATH)
print("Output Path:", OUTPUT_PATH)


# === 3. HELPER: JSON-DATEIEN LADEN ===
def load_json(path):
    if not path.exists():
        print(f"Datei nicht gefunden: {path}")
        return {}
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)

# === 4. GEMINI AUFRUF ===
if len(sys.argv) >= 3:
    von = sys.argv[1]
    bis = sys.argv[2]
else:
    von = (datetime.today() + timedelta(days=1)).strftime('%Y-%m-%d')
    bis = (datetime.today() + timedelta(days=7)).strftime('%Y-%m-%d')   
    
def call_gemini(prompt_text):
    payload = {
        "contents": [{
            "parts": [{"text": prompt_text}]
        }]
    }
    try:
        response = requests.post(
            f"{gemini_url}?key={gemini_api_key}",
            json=payload,
            headers={"Content-Type": "application/json"}
        )
        response.raise_for_status()
        result = response.json()
        return result['candidates'][0]['content']['parts'][0]['text']
    except Exception as e:
        print("Fehler bei Gemini:", e)
        return "Fehler bei der Verarbeitung durch Gemini."
    
def get_shift_for_day(date):
    """
    Bestimmt die Schichten für einen bestimmten Tag.
    """
    day_of_week = date.strftime('%A')
    shifts = []
    print(f"Tag: {day_of_week}")
    if day_of_week in ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag']:
        # Beispiel: An Wochentagen gibt es Früh- und Spätschicht
        if random.random() < 0.5:
            shifts.append("09:00-17:00")
        else:
            shifts.append("13:00-21:00")
    elif day_of_week in ['Samstag', 'Sonntag']:
        # Beispiel: Am Wochenende gibt es nur eine längere Schicht
        shifts.append("10:00-18:00")

    return ", ".join(shifts) # Gibt die Schichten als CSV-String zurück

def get_fixed_shift_duration(shift):
    if not shift:
        return 0
    start_str, end_str = shift.split(',')
    start = datetime.strptime(start_str, '%H:%M')
    end = datetime.strptime(end_str, '%H:%M')
    duration = (end - start).total_seconds() / 3600
    print(f"Schicht: {shift}, Dauer: {duration} Stunden")
    return duration


# === 5. HAUPTFUNKTION ===
def get_fixed_shift(employee_data, day_of_week):
    ressort = employee_data.get("ressort")
    vorname = employee_data.get("vorname")
    nachname = employee_data.get("nachname")
    weekly_hours = employee_data.get("stunden", 0)
    
    print(f"Ressort: {ressort}, Vorname: {vorname}, Nachname: {nachname}, Tag: {day_of_week}")
    if ressort == "KI" and day_of_week in ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']:
        return "07:30,16:00"
    elif ressort == "IT" and day_of_week in ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday']:
        return "09:00,17:00"
    elif vorname == "Max" and nachname == "Mustermann" and day_of_week in ['Monday', 'Tuesday', 'Wednesday']:
        return "10:00,14:00"
    return ""





def plan_weekly_shifts(employee_data, week_dates):
    weekly_contract_hours = employee_data.get("stunden", 0)
    employee_shifts = {}
    planned_hours_this_week = 0
    work_days_this_week = 0
    max_work_days = 5  # Annahme: Max. 5 Arbeitstage pro Woche für die Verteilung

    # Zuerst alle Tage der Woche als "frei" markieren
    for d in week_dates:
        employee_shifts[d.strftime('%Y-%m-%d')] = ""

    if weekly_contract_hours > 0:
        hours_per_day = weekly_contract_hours / max_work_days
        current_hour = 9  # Startzeit als Beispiel

        for d in week_dates:
            day_name = d.strftime('%A')
            date_str = d.strftime('%Y-%m-%d')

            if day_name in ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'] and planned_hours_this_week < weekly_contract_hours and work_days_this_week < max_work_days:
                shift_duration = min(hours_per_day, weekly_contract_hours - planned_hours_this_week)
                end_hour = current_hour + shift_duration
                shift = f"{current_hour:02.0f}:00,{end_hour:02.0f}:00"
                employee_shifts[date_str] = shift
                planned_hours_this_week += shift_duration
                work_days_this_week += 1
                current_hour = 9 # Für den nächsten Tag wieder bei der Startzeit beginnen (vereinfacht)

    return employee_shifts

def generate_schedule(von_str, bis_str):
    von_date = datetime.strptime(von_str, '%Y-%m-%d').date()
    bis_date = datetime.strptime(bis_str, '%Y-%m-%d').date()
    dates = [von_date + timedelta(days=i) for i in range((bis_date - von_date).days + 1)]
    num_days = (bis_date - von_date).days + 1

    employees = load_json(EMPLOYEE_DATA_PATH)

    header_row_1 = [""] * 9
    for d in dates:
        header_row_1 += [d.strftime("%d.%m.%y"), ""]

    header_row_2 = [
        "NutzerID", "Nachname", "Vorname", "Ressort", "CVD",
        "Notizen", "Wochenstunden", "MonatsSumme", "Delta"
    ] + ["Von", "Bis"] * len(dates)

    data_rows = []
    for emp in employees:
        weekly_contract_hours = emp.get("stunden", 0)
        monthly_planned_hours = 0
        all_employee_shifts = {}
        current_week_dates = []

        expected_monthly_hours = (weekly_contract_hours / 7) * num_days if weekly_contract_hours else 0

        row = [
            emp.get("id", ""),
            emp.get("nachname", ""),
            emp.get("vorname", ""),
            emp.get("ressort", ""),
            emp.get("cvd", ""),
            emp.get("notizen", ""),
            weekly_contract_hours,
            0,
            0
        ]

        for i, d in enumerate(dates):
            current_week_dates.append(d)
            if d.strftime('%A') == 'Sunday' or i == len(dates) - 1:
                weekly_shifts = plan_weekly_shifts(emp, current_week_dates)
                all_employee_shifts.update(weekly_shifts)
                current_week_dates = []

        for d in dates:
            shift = all_employee_shifts.get(d.strftime('%Y-%m-%d'), "")
            monthly_planned_hours += get_fixed_shift_duration(shift)
            row.extend(shift.split(',') if shift else ["", ""])

        row[7] = f"{monthly_planned_hours:.2f}"
        row[8] = f"{monthly_planned_hours - expected_monthly_hours:.2f}"
        data_rows.append(row)

    OUTPUT_PATH.parent.mkdir(exist_ok=True)
    with open(OUTPUT_PATH, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f, delimiter=",")
        writer.writerow(header_row_1)
        writer.writerow(header_row_2)
        writer.writerows(data_rows)

    print(f"CSV exportiert nach: {OUTPUT_PATH}")

# === 6. ENTRYPOINT ===
if __name__ == "__main__":
    if len(sys.argv) >= 3:
        generate_schedule(sys.argv[1], sys.argv[2])
    else:
        today = datetime.today()
        default_start = (today + timedelta(days=1)).strftime('%Y-%m-%d')
        default_end = (today + timedelta(days=7)).strftime('%Y-%m-%d')
        generate_schedule(default_start, default_end)
