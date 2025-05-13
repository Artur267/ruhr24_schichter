document.addEventListener("DOMContentLoaded", () => {
  loadEmployees();
});

function loadEmployees() {
  fetch("/mitarbeiter-daten")
    .then(res => res.json())
    .then(employees => {
      const tbody = document.getElementById("employee-list");
      tbody.innerHTML = "";
      employees.forEach(emp => {
        const row = document.createElement("tr");
        row.innerHTML = `
          <td>${emp.vorname}</td>
          <td>${emp.nachname}</td>
          <td>${emp.stunden}</td>
          <td>${emp.ressort}</td>
          <td>${emp.cvd ? "✅" : ""}</td>
          <td>${emp.notizen || ""}</td>
          <td>
            <button class="icon-btn" onclick="editEmployee('${emp.id}')">✏️</button>
            <button class="icon-btn" onclick="deleteEmployee('${emp.id}')">🗑️</button>
          </td>
        `;
        tbody.appendChild(row);
      });
    });
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
  document.getElementById("input-stunden").value = "";
  document.getElementById("input-ressort").value = "";
  document.getElementById("input-cvd").checked = false;
  document.getElementById("input-notizen").value = "";
}

let currentEditId = null;

function saveEmployee() {
  const newEmployee = {
    vorname: document.getElementById("input-vorname").value,
    nachname: document.getElementById("input-nachname").value,
    stunden: parseInt(document.getElementById("input-stunden").value),
    ressort: document.getElementById("input-ressort").value,
    cvd: document.getElementById("input-cvd").checked,
    notizen: document.getElementById("input-notizen").value,
  };

  const url = currentEditId ? `/mitarbeiter/${currentEditId}` : "/mitarbeiter";
  const method = currentEditId ? "PUT" : "POST";

  fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(newEmployee),
  })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        closePopup();
        loadEmployees();
      }
    });
}

function editEmployee(id) {
  fetch("/mitarbeiter-daten")
    .then(res => res.json())
    .then(employees => {
      const emp = employees.find(e => e.id === id);
      if (!emp) return;

      currentEditId = id;
      document.getElementById("popup-title").textContent = "Mitarbeiter bearbeiten";
      document.getElementById("input-vorname").value = emp.vorname;
      document.getElementById("input-nachname").value = emp.nachname;
      document.getElementById("input-stunden").value = emp.stunden;
      document.getElementById("input-ressort").value = emp.ressort;
      document.getElementById("input-cvd").checked = emp.cvd;
      document.getElementById("input-notizen").value = emp.notizen || "";
      document.getElementById("employee-popup").classList.remove("hidden");
    });
}

function deleteEmployee(id) {
  if (!confirm("Willst du diesen Mitarbeiter wirklich löschen?")) return;

  fetch(`/mitarbeiter/${id}`, { method: "DELETE" })
    .then(res => res.json())
    .then(data => {
      if (data.success) loadEmployees();
    });
}
