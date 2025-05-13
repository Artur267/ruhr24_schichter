// scheduler.js
document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("schedule-form");
  const output = document.getElementById("shift-output");
  const promptContainer = document.getElementById("prompt-container");
  const promptPreview = document.getElementById("prompt-preview");

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const von = document.getElementById("von").value;
    const bis = document.getElementById("bis").value;

    console.log("[DEBUG] Von:", von);
    console.log("[DEBUG] Bis:", bis);

    if (!von || !bis || new Date(bis) < new Date(von)) {
      alert("Bitte wähle einen gültigen Zeitraum (Von darf nicht nach Bis liegen).");
      return;
    }

    try {
      console.log("[DEBUG] Lade Regeln und Mitarbeiterdaten...");

      const [rules, employees] = await Promise.all([
        fetch('/richtlinien').then(res => res.json()),
        fetch('/mitarbeiter-daten').then(res => res.json())
      ]);

      console.log("[DEBUG] Regeln:", rules);
      console.log("[DEBUG] Mitarbeiter:", employees);

      const rulesText = rules.map(r => `- ${r.text}`).join("\n");
      const employeesText = employees.map(e =>
        `${e.vorname} ${e.nachname}, ${e.stunden}h, ${e.ressort}${e.cvd ? ", CvD" : ""}`
      ).join("\n");
      console.log("[DEBUG] Employeestext Text:", employeesText);
      const article = `Regeln:\n${rulesText}\n\nMitarbeiter:\n${employeesText}`;
      const prompt = `
        Du bist ein intelligentes Planungssystem und sollst auf Basis der folgenden Daten einen detaillierten Schichtplan im CSV-Format erstellen.

        Die Liste enthält Mitarbeiter mit folgenden Angaben:
        - ID (eindeutig, z. B. 1, 2, 3)
        - Vorname
        - Nachname
        - Ressort (z. B. Politik, Sport, etc.)
        - CvD (true/false)
        - Wochenstunden (z. B. 20, 40)
        - Optional: Hinweise wie Urlaub oder Einschränkungen

        Berücksichtige dabei:
        - Den Zeitraum ${von} – ${bis}
        - CvD-Rollen müssen regelmäßig, aber nicht gleichzeitig vergeben werden
        - Die Arbeitsstunden pro Woche sollen zur Vertragszeit passen (±10 %)
        - Die Schichten sollen fair und gleichmäßig verteilt sein
        - Wenn jemand Einschränkungen hat (z. B. "nicht montags"), soll das respektiert werden
        - Die Ressortverteilung soll möglichst ausgeglichen sein

        **WICHTIG**:
        Gib den Plan **im CSV-Format** aus, damit ich ihn direkt in Excel oder Google Sheets importieren kann.

        ### Format:
        - Zeile 1: Enthält das Datum pro Tag im Format "Montag 13.05.2025", "Dienstag 14.05.2025", usw. (jeweils eine eigene Spalte ab Spalte G)
        - Zeile 2: Spaltenüberschriften:
          - Spalte A: ID
          - Spalte B: Nachname
          - Spalte C: Vorname
          - Spalte D: Ressort
          - Spalte E: CvD
          - Spalte F: Wochenstunden
          - Spalte G+: Pro Datum (z. B. Montag 13.05.2025) die Schichteinträge

        ### Schichteintrag in Zellen:
        - Trage in den Tageszellen **konkrete Arbeitszeiten** im Format „09:00–17:00“ ein
        - Wenn jemand an einem Tag CvD ist, schreibe z. B. „09:00–17:00 [CvD]“
        - Lasse Zellen leer, wenn jemand nicht arbeitet

        ### Beispielzelle:
        "09:00–17:00 [CvD]" oder "13:00–18:00"

        Am Ende des Plans soll zusätzlich eine Übersicht erscheinen:

        ### Übersicht:
        - Auflistung, wie viele Stunden pro Person eingeplant wurden
        - Ob die Wochenstunden erfüllt wurden (±10 % erlaubt)
        - Kurze Hinweise zu Abweichungen oder Besonderheiten (z. B. CvD-Verteilung, Urlaub, Einschränkungen)

        Zusätzlich soll es um folgende Mitarbeiter gehen und diese zusätzlichen Regeln gelten:\n\n${article}

        Halte dich **strikt an das vorgegebene Format**, damit der Output direkt als CSV importiert werden kann.
        benutze Testweise erstmal Testdaten für 15 Testpersonen! die Liste mit unseren Mitarbeitern und deren Feiertagsdaetn ist unvollständig
        `;

      console.log("[DEBUG] Generierter Prompt:", prompt);

      // Prompt in Textarea anzeigen
      if (promptPreview) {
        promptPreview.value = prompt;
      }

      console.log("[DEBUG] Sende Anfrage an /starte-scheduler...");
      const res = await fetch("/starte-scheduler", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ von: von, bis: bis, prompt: prompt, article: article })
      });

      console.log("[DEBUG] Antwortstatus:", res.status);

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(`Fehler vom Server: ${res.status} - ${errorText}`);
      }

      const data = await res.json();
      console.log("[DEBUG] Antwortdaten:", data);

      output.textContent = data.answer || "Keine Antwort erhalten.";

    } catch (err) {
      console.error("Fehler beim Abruf des Schichtplans:", err);
      output.textContent = "Fehler beim Abruf des Schichtplans. Details siehe Konsole.";
    }
  });
});

// Funktion zum Ein-/Ausblenden des Prompt-Containers
function togglePromptView() {
  const promptContainer = document.getElementById("prompt-container");
  if (!promptContainer) return;

  const isVisible = promptContainer.style.display === "block";
  promptContainer.style.display = isVisible ? "none" : "block";
}
