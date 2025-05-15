import json
from datetime import datetime, timedelta
import csv
from pathlib import Path
import sys

ROOT_DIR = Path.cwd()
DATA_DIR = ROOT_DIR / "data"
EMPLOYEE_DATA_PATH = DATA_DIR / "mitarbeiter.json"
RULES_DATA_PATH = DATA_DIR / "regelwerk.json"
SETTINGS_DATA_PATH = DATA_DIR / "settings.json"
OUTPUT_PATH_MVP = ROOT_DIR / "results" / "output.csv"

def load_json(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)

def generate_csv_header(start_date_str, end_date_str):
    start_date = datetime.strptime(start_date_str, '%Y-%m-%d').date()
    end_date = datetime.strptime(end_date_str, '%Y-%m-%d').date()
    header_row_1 = []
    header_row_2 = ["NutzerID", "Nachname", "Vorname", "Ressort", "CVD", "Wochenstunden", "Geplant", "Delta"]
    dates = []
    current_date = start_date
    while current_date <= end_date:
        day_name = current_date.strftime("%A")
        day_number = current_date.strftime("%d.%m.%Y")
        header_row_1.extend([f"{day_name} {day_number}", ""]) # Datum, Leere Spalte für 'Bis'
        header_row_2.extend(["Von", "Bis"])
        dates.append(current_date)
        current_date += timedelta(days=1)

    # Füge die leeren Einträge am Anfang von header_row_1 hinzu,
    # um die Ausrichtung mit header_row_2 zu gewährleisten
    header_row_1 = [""] * len(header_row_2[:8]) + header_row_1

    return header_row_1, header_row_2, dates

def create_schedule_mvp(start_date_str, end_date_str, mitarbeiter_id, output_to_stdout=False):
    print(f"[PYTHON DEBUG] create_schedule_mvp aufgerufen mit: start={start_date_str}, end={end_date_str}, id={mitarbeiter_id}, stdout={output_to_stdout}")
    mitarbeiter_daten = load_json(EMPLOYEE_DATA_PATH)
    regelwerk = load_json(RULES_DATA_PATH)
    einstellungen = load_json(SETTINGS_DATA_PATH) # Noch nicht intensiv genutzt

    _, _, planungs_tage = generate_csv_header(start_date_str, end_date_str) # Header hier nur generieren

    if output_to_stdout:
        writer = csv.writer(sys.stdout, delimiter=",", quoting=csv.QUOTE_NONE, escapechar='\\')
        # Suche direkt nach dem spezifischen Mitarbeiter
        for mitarbeiter in mitarbeiter_daten:
            print(f"[PYTHON DEBUG] Vergleich: JSON-ID ({mitarbeiter['id']}), Typ JSON ({type(mitarbeiter['id'])}), Übergebene ID ({mitarbeiter_id}), Typ Übergeben ({type(mitarbeiter_id)})")
            if str(mitarbeiter['id']) == mitarbeiter_id:
                print(f"[PYTHON DEBUG] Mitarbeiter mit ID {mitarbeiter_id} gefunden. Starte Berechnung.")
                wochenstunden = mitarbeiter['stunden']
                arbeitstage_pro_woche = 5 if wochenstunden > 0 else 0
                stunden_pro_tag = min(8.5, wochenstunden / arbeitstage_pro_woche) if arbeitstage_pro_woche > 0 else 0
                geplante_stunden_woche = 0
                arbeitszeiten = [mitarbeiter['id'], mitarbeiter['nachname'], mitarbeiter['vorname'],
                                mitarbeiter['ressort'], mitarbeiter.get('cvd', False), wochenstunden, 0.0, 0.0]

                arbeitsplan = {}
                for tag in planungs_tage:
                    arbeitsplan[tag.strftime("%Y-%m-%d")] = {"von": "", "bis": ""}

                arbeitstage_counter = 0
                for tag in planungs_tage:
                    wochentag = tag.strftime("%A")
                    tag_str = tag.strftime("%Y-%m-%d")

                    arbeitszeit_start = einstellungen.get('kernarbeitszeit_start', "09:00") # Beispiel aus Settings
                    fruehschicht_start = einstellungen.get('fruehschicht_start', "07:00")

                    if wochentag != "Montag" or mitarbeiter.get('hinweise') != "nicht montags":
                        if arbeitstage_counter < arbeitstage_pro_woche:
                            start_zeit = fruehschicht_start if mitarbeiter.get('hinweise') == "nur Frühschicht" else arbeitszeit_start
                            end_zeit_float = float(start_zeit.split(':')[0]) + stunden_pro_tag + float(start_zeit.split(':')[1]) / 60
                            end_zeit_stunde = int(end_zeit_float)
                            end_zeit_minute = int((end_zeit_float - end_zeit_stunde) * 60)
                            end_zeit = f"{end_zeit_stunde:02d}:{end_zeit_minute:02d}"

                            arbeitsplan[tag_str]["von"] = start_zeit
                            arbeitsplan[tag_str]["bis"] = end_zeit
                            geplante_stunden_woche += stunden_pro_tag
                            arbeitstage_counter += 1

                for tag in planungs_tage:
                    arbeitszeiten.extend([arbeitsplan[tag.strftime("%Y-%m-%d")]["von"], arbeitsplan[tag.strftime("%Y-%m-%d")]["bis"]])

                arbeitszeiten[6] = round(geplante_stunden_woche, 1)
                arbeitszeiten[7] = round(geplante_stunden_woche - wochenstunden, 1)

                print(f"[PYTHON DEBUG] Inhalt von arbeitszeiten vor dem Print: {arbeitszeiten}")
                print(f"[PYTHON DEBUG] Arbeitszeiten: {arbeitszeiten}")
                writer.writerow(arbeitszeiten)
                break # Das Break ist jetzt richtig, da wir den passenden Mitarbeiter gefunden haben
                return # Füge ein Return hinzu, um die Funktion nach der Verarbeitung zu beenden
    else:
        with open(OUTPUT_PATH_MVP, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f, delimiter=",", quoting=csv.QUOTE_NONE, escapechar='\\')
            header_row_1, header_row_2, _ = generate_csv_header(start_date_str, end_date_str)
            writer.writerow(header_row_1)
            writer.writerow(header_row_2)
            for mitarbeiter in mitarbeiter_daten:
                print(f"[PYTHON DEBUG] Vergleich: Mitarbeiter-ID aus JSON ({mitarbeiter['id']}), Übergebene ID ({mitarbeiter_id}), Typ JSON ({type(mitarbeiter['id'])}), Typ Übergeben ({type(mitarbeiter_id)})")
                if str(mitarbeiter['id']) == mitarbeiter_id:
                    # (Wiederhole hier die Logik zur Mitarbeiterplanung)
                    wochenstunden = mitarbeiter['stunden']
                    arbeitstage_pro_woche = 5 if wochenstunden > 0 else 0
                    stunden_pro_tag = min(8.5, wochenstunden / arbeitstage_pro_woche) if arbeitstage_pro_woche > 0 else 0
                    geplante_stunden_woche = 0
                    arbeitszeiten = [mitarbeiter['id'], mitarbeiter['nachname'], mitarbeiter['vorname'],
                                    mitarbeiter['ressort'], mitarbeiter.get('cvd', False), wochenstunden, 0.0, 0.0]

                    arbeitsplan = {}
                    for tag in planungs_tage:
                        arbeitsplan[tag.strftime("%Y-%m-%d")] = {"von": "", "bis": ""}

                    arbeitstage_counter = 0
                    for tag in planungs_tage:
                        wochentag = tag.strftime("%A")
                        tag_str = tag.strftime("%Y-%m-%d")

                        arbeitszeit_start = einstellungen.get('kernarbeitszeit_start', "09:00") # Beispiel aus Settings
                        fruehschicht_start = einstellungen.get('fruehschicht_start', "07:00")

                        if wochentag != "Montag" or mitarbeiter.get('hinweise') != "nicht montags":
                            if arbeitstage_counter < arbeitstage_pro_woche:
                                start_zeit = fruehschicht_start if mitarbeiter.get('hinweise') == "nur Frühschicht" else arbeitszeit_start
                                end_zeit_float = float(start_zeit.split(':')[0]) + stunden_pro_tag + float(start_zeit.split(':')[1]) / 60
                                end_zeit_stunde = int(end_zeit_float)
                                end_zeit_minute = int((end_zeit_float - end_zeit_stunde) * 60)
                                end_zeit = f"{end_zeit_stunde:02d}:{end_zeit_minute:02d}"

                                arbeitsplan[tag_str]["von"] = start_zeit
                                arbeitsplan[tag_str]["bis"] = end_zeit
                                geplante_stunden_woche += stunden_pro_tag
                                arbeitstage_counter += 1

                    for tag in planungs_tage:
                        arbeitszeiten.extend([arbeitsplan[tag.strftime("%Y-%m-%d")]["von"], arbeitsplan[tag.strftime("%Y-%m-%d")]["bis"]])
                    print(f"[PYTHON DEBUG] Arbeitszeiten vor dem Schreiben: {arbeitszeiten}")
                    writer.writerow(arbeitszeiten)
                    break

    print(f"MVP Schichtplan für Mitarbeiter {mitarbeiter_id} erstellt") # Ausgabe im Backend
if __name__ == "__main__":
    if len(sys.argv) == 4:
        start_date_str = sys.argv[1]
        end_date_str = sys.argv[2]
        mitarbeiter_id = sys.argv[3]
        if mitarbeiter_id == "header":
            header_row_1, header_row_2, _ = generate_csv_header(start_date_str, end_date_str)
            writer = csv.writer(sys.stdout, delimiter=",", quoting=csv.QUOTE_NONE, escapechar='\\')
            writer.writerow(header_row_1)
            writer.writerow(header_row_2)
        else:
            create_schedule_mvp(start_date_str, end_date_str, mitarbeiter_id, True)
    else:
        print("Bitte Startdatum, Enddatum und Mitarbeiter-ID ('header' für Header) als Argumente übergeben (YYYY-MM-DD, YYYY-MM-DD, ID).")