import React from 'react';
import { NavLink } from 'react-router-dom'; // NavLink für aktive Links
import styles from './Navbar.module.css';

function Navbar() {
  return (
    <nav className={styles.navbar}>
      <NavLink to="/" className={({ isActive }) => isActive ? styles.active : ''}>Home</NavLink>
      <NavLink to="/erstellen" className={({ isActive }) => isActive ? styles.active : ''}>Schichtplan Erstellen</NavLink>
      <NavLink to="/betrachten" className={({ isActive }) => isActive ? styles.active : ''}>Schichtplan Betrachten</NavLink>
      <NavLink to="/bearbeiten" className={({ isActive }) => isActive ? styles.active : ''}>Schichtplan Bearbeiten</NavLink>
      <NavLink to="/mitarbeiter-verwalten" className={({ isActive }) => isActive ? styles.active : ''}>Mitarbeiter Verwalten</NavLink>
      <NavLink to="/wunschplanung" className={({ isActive }) => isActive ? styles.active : ''}>Wünsche Bearbeiten</NavLink>
      <NavLink to="/preflight-planung" className={({ isActive }) => isActive ? styles.active : ''}>PreFlight (BETA)</NavLink>
    </nav>
  );
}
export default Navbar;