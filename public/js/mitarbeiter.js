document.addEventListener("DOMContentLoaded", () => {
  loadEmployees();
});

let currentEditId = null; // Für Bearbeiten-Modus

function loadEmployees() {
  fetch("/mitarbeiter-daten")
    .then(res => res.json())
    .then(employees => {
      const tbody = document.getElementById("employee-list");
      tbody.innerHTML = ""; // Tabelle leeren
      employees.forEach(emp => {
        const row = document.createElement("tr");

        // Kombiniere Rollen/Qualifikationen und Teams/Zugehörigkeiten für die Anzeige in der Tabelle
        const combinedQualisTeams = [
          ...(emp.rollenUndQualifikationen || []),
          ...(emp.teamsUndZugehoerigkeiten || [])
        ].filter(Boolean).join(', '); // Filtern leere Strings und verbinden mit Komma

        row.innerHTML = `
          <td>${emp.vorname}</td>
          <td>${emp.nachname}</td>
          <td>${emp.email || ""}</td>
          <td>${emp.stellenbezeichnung || ""}</td>
          <td>${emp.wochenstunden}</td>
          <td>${emp.ressort || ""}</td>
          <td>${emp.cvd ? "✅" : ""}</td>
          <td>${combinedQualisTeams || ""}</td>
          <td>${emp.notizen || ""}</td>
          <td>
            <button class="icon-btn" onclick="editEmployee('${emp.id}')">✏️</button>
            <button class="icon-btn" onclick="deleteEmployee('${emp.id}')">🗑️</button>
          </td>
        `;
        tbody.appendChild(row);
      });
    })
    .catch(error => console.error("Fehler beim Laden der Mitarbeiter:", error));
}

function showAddForm() {
  currentEditId = null;
  document.getElementById("popup-title").textContent = "Neuer Mitarbeiter";
  clearForm();
  document.getElementById("employee-popup").classList.remove("hidden");
}

function closePopup() {
  document.getElementById("employee-popup").classList.add("hidden");
}

function clearForm() {
  document.getElementById("input-vorname").value = "";
  document.getElementById("input-nachname").value = "";
  document.getElementById("input-email").value = ""; // NEU
  document.getElementById("input-stellenbezeichnung").value = ""; // NEU
  document.getElementById("input-stunden").value = "";
  document.getElementById("input-ressort").value = "";
  document.getElementById("input-cvd").checked = false;
  document.getElementById("input-rollenUndQualifikationen").value = ""; // NEU
  document.getElementById("input-teamsUndZugehoerigkeiten").value = ""; // NEU
  document.getElementById("input-notizen").value = "";
}


function saveEmployee() {
  // NEU: Listen aus Komma-getrennten Strings erstellen
  const rollenUndQualifikationen = document.getElementById("input-rollenUndQualifikationen").value
    .split(',')
    .map(s => s.trim())
    .filter(s => s !== ''); // Leere Strings entfernen

  const teamsUndZugehoerigkeiten = document.getElementById("input-teamsUndZugehoerigkeiten").value
    .split(',')
    .map(s => s.trim())
    .filter(s => s !== ''); // Leere Strings entfernen

  const employeeData = {
    vorname: document.getElementById("input-vorname").value,
    nachname: document.getElementById("input-nachname").value,
    email: document.getElementById("input-email").value, // NEU
    stellenbezeichnung: document.getElementById("input-stellenbezeichnung").value, // NEU
    wochenstunden: parseInt(document.getElementById("input-stunden").value),
    ressort: document.getElementById("input-ressort").value,
    cvd: document.getElementById("input-cvd").checked,
    notizen: document.getElementById("input-notizen").value,
    rollenUndQualifikationen: rollenUndQualifikationen, // NEU
    teamsUndZugehoerigkeiten: teamsUndZugehoerigkeiten // NEU
  };

  const url = currentEditId ? `/mitarbeiter/${currentEditId}` : "/mitarbeiter";
  const method = currentEditId ? "PUT" : "POST";

  fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(employeeData),
  })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        closePopup();
        loadEmployees();
      } else {
        alert("Fehler beim Speichern des Mitarbeiters: " + (data.message || "Unbekannter Fehler."));
      }
    })
    .catch(error => console.error("Fehler beim Speichern des Mitarbeiters:", error));
}

function editEmployee(id) {
  fetch("/mitarbeiter-daten")
    .then(res => res.json())
    .then(employees => {
      const emp = employees.find(e => e.id === id);
      if (!emp) {
        console.warn(`Mitarbeiter mit ID ${id} nicht gefunden.`);
        return;
      }

      currentEditId = id;
      document.getElementById("popup-title").textContent = "Mitarbeiter bearbeiten";
      document.getElementById("input-vorname").value = emp.vorname;
      document.getElementById("input-nachname").value = emp.nachname;
      document.getElementById("input-email").value = emp.email || ""; // NEU
      document.getElementById("input-stellenbezeichnung").value = emp.stellenbezeichnung || ""; // NEU
      document.getElementById("input-stunden").value = emp.wochenstunden; // Beachte: hier emp.wochenstunden statt emp.stunden
      document.getElementById("input-ressort").value = emp.ressort || "";
      document.getElementById("input-cvd").checked = emp.cvd;
      document.getElementById("input-notizen").value = emp.notizen || "";
      // NEU: Listen als Komma-getrennten String in Textareas anzeigen
      document.getElementById("input-rollenUndQualifikationen").value = (emp.rollenUndQualifikationen || []).join(', ');
      document.getElementById("input-teamsUndZugehoerigkeiten").value = (emp.teamsUndZugehoerigkeiten || []).join(', ');

      document.getElementById("employee-popup").classList.remove("hidden");
    })
    .catch(error => console.error("Fehler beim Laden des Mitarbeiters zur Bearbeitung:", error));
}

function deleteEmployee(id) {
  if (!confirm("Willst du diesen Mitarbeiter wirklich löschen?")) return;

  fetch(`/mitarbeiter/${id}`, { method: "DELETE" })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        loadEmployees();
      } else {
        alert("Fehler beim Löschen des Mitarbeiters: " + (data.message || "Unbekannter Fehler."));
      }
    })
    .catch(error => console.error("Fehler beim Löschen des Mitarbeiters:", error));
}