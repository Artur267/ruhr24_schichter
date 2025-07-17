import React from 'react';
import { NavLink } from 'react-router-dom'; // NavLink für aktive Links
import styles from './Navbar.module.css';

function Navbar() {
  return (
    <nav className={styles.navbar}>
      <NavLink to="/" className={({ isActive }) => isActive ? styles.active : ''}>Übersicht</NavLink>
      <NavLink to="/erstellen" className={({ isActive }) => isActive ? styles.active : ''}>Planung Erstellen</NavLink>
      <NavLink to="/betrachten" className={({ isActive }) => isActive ? styles.active : ''}>Plan Betrachten</NavLink>
      <NavLink to="/bearbeiten" className={({ isActive }) => isActive ? styles.active : ''}>Plan Bearbeiten</NavLink>
      <NavLink to="/mitarbeiter-verwalten" className={({ isActive }) => isActive ? styles.active : ''}>Mitarbeiter Verwalten</NavLink>
    </nav>
  );
}
export default Navbar;