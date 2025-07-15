import React from 'react';
import { Routes, Route } from 'react-router-dom';
import './index.css'; // Globale Styles
import Header from './components/Header/Header';
import SchichtplanAnsicht from './pages/SchichtplanAnsicht/SchichtplanAnsicht';
import PlanungErstellen from './pages/PlanungErstellen/PlanungErstellen'; // Für später
import LandingPage from './pages/LandingPage/LandingPage';
import MitarbeiterVerwaltung from './pages/MitarbeiterVerwaltung/MitarbeiterVerwaltung'; // Für später
import PlanBearbeiten from './pages/PlanBearbeiten/PlanBearbeiten';

function App() {
  return (
    <div className="dashboard-layout">
      <Header />
      <main className="dashboard-main">
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/erstellen" element={<PlanungErstellen />} />
          <Route path="/betrachten" element={<SchichtplanAnsicht />} />
          <Route path="/mitarbeiter" element={<MitarbeiterVerwaltung />} />
          <Route path="/bearbeiten" element={<PlanBearbeiten />} />
          {/* Hier können weitere Routen hinzugefügt werden */}

        </Routes>
      </main>
    </div>
  );
}

export default App;