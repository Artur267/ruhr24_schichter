import React from 'react';
import { Routes, Route } from 'react-router-dom';
import './index.css'; // Globale Styles
import Header from './components/Header/Header';
import SchichtplanAnsicht from './pages/SchichtplanAnsicht/SchichtplanAnsicht';
import PlanungErstellen from './pages/PlanungErstellen/PlanungErstellen';
import LandingPage from './pages/LandingPage/LandingPage';
import MitarbeiterVerwaltung from './pages/MitarbeiterVerwaltung/MitarbeiterVerwaltung'; 
import PlanBearbeiten from './pages/PlanBearbeiten/PlanBearbeiten';
import Wunschplanung from './pages/Wunschplanung/Wunschplanung';
import PreFlightPlanung from './pages/PreFlightPlanung/PreFlightPlanung'; 

function App() {
  return (
      <div className="dashboard-layout">
        <Header />
        <main className="dashboard-main">
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/erstellen" element={<PlanungErstellen />} />
            <Route path="/betrachten" element={<SchichtplanAnsicht />} />
            <Route path="/mitarbeiter-verwalten" element={<MitarbeiterVerwaltung />} />
            <Route path="/bearbeiten" element={<PlanBearbeiten />} />
            <Route path="/wunschplanung" element={<Wunschplanung />} />
            <Route path="/preflight-planung" element={<PreFlightPlanung />} />
            {/* Hier können weitere Routen hinzugefügt werden */}

          </Routes>
        </main>
      </div>
  );
}

export default App;