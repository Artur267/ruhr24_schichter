// client/src/components/SchichtplanTable/SchichtplanTable.jsx

import React from 'react';

// Wir übergeben die Daten als "Props" an die Komponente
function SchichtplanTable({ mitarbeiter, dates, datesISO }) {
  
  // Sicherheitsabfrage, falls keine Daten vorhanden sind
  if (!mitarbeiter || mitarbeiter.length === 0) {
    return <p>Keine Plandaten zum Anzeigen vorhanden.</p>;
  }

  return (
    <div className="table-container">
      <table className="mitarbeiter-kalender">
        <thead>
          {/* Header-Zeile 1: Mitarbeiter-Infos und die Datum-Überschriften */}
          <tr>
            <th className="sticky-col sticky-col-0">ID</th>
            <th className="sticky-col sticky-col-1">Nachname</th>
            <th className="sticky-col sticky-col-2">Vorname</th>
            <th>Soll</th>
            <th>Ist</th>
            <th>Delta</th>
            {/* Erzeugt für jedes Datum eine Spaltenüberschrift */}
            {dates.map(date => (
              <th colSpan="2" key={date}>{date}</th>
            ))}
          </tr>
          {/* Header-Zeile 2: "Von" und "Bis" für jedes Datum */}
          <tr>
            <th className="sticky-col sticky-col-0"></th>
            <th className="sticky-col sticky-col-1"></th>
            <th className="sticky-col sticky-col-2"></th>
            <th></th>
            <th></th>
            <th></th>
            {dates.map(date => (
              <React.Fragment key={`${date}-sub`}>
                <th>Von</th>
                <th>Bis</th>
              </React.Fragment>
            ))}
          </tr>
        </thead>
        <tbody>
          {/* Äußere Schleife: Erzeugt eine Zeile für jeden Mitarbeiter */}
          {mitarbeiter.map(m => (
            <tr key={m.NutzerID}>
              <td className="sticky-col sticky-col-0">{m.NutzerID}</td>
              <td className="sticky-col sticky-col-1">{m.Nachname}</td>
              <td className="sticky-col sticky-col-2">{m.Vorname}</td>
              <td>{m.Wochenstunden * 4}h</td>
              <td>{m.MonatsSumme}</td>
              <td>{m.Delta}</td>
              
              {/* Innere Schleife: Geht für jeden Mitarbeiter alle Daten durch */}
              {datesISO.map(isoDate => {
                // Finde die passende Arbeitszeit für den aktuellen Tag
                const arbeitszeit = m.Arbeitszeiten[isoDate] || { Von: '', Bis: '' };
                return (
                  <React.Fragment key={`${m.NutzerID}-${isoDate}`}>
                    <td>{arbeitszeit.Von || '-'}</td>
                    <td>{arbeitszeit.Bis || '-'}</td>
                  </React.Fragment>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default SchichtplanTable;