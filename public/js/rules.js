function loadRules() {
  fetch('/richtlinien')
    .then(res => res.json())
    .then(rules => {
      const list = document.getElementById('rules-list');
      list.innerHTML = '';
      rules.forEach(rule => {
        const li = document.createElement('li');
        li.innerHTML = `
        <div class="rule-text">${rule.text}</div>
        <div class="delete-icon" onclick="deleteRule('${rule.id}')">🗑️</div>
        `;
        list.appendChild(li);
      });
    });
}

function deleteRule(id) {
  if (confirm("Willst du diese Regel wirklich löschen?")) {
    fetch(`/richtlinie/${id}`, { method: 'DELETE' })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          loadRules(); // direkt neu laden
        } else {
          alert("Fehler beim Löschen.");
        }
      });
  }
}

function addRule() {
  const input = document.getElementById('new-rule-input');
  const text = input.value.trim();
  if (!text) return;

  fetch('/richtlinie', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text })
  })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        input.value = '';
        loadRules();
      } else {
        alert('Fehler beim Hinzufügen der Regel.');
      }
    });
}

document.addEventListener('DOMContentLoaded', () => {
  loadRules();
});
