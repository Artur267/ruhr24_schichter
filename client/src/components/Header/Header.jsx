// client/src/components/Header/Header.jsx

import React from 'react';
import styles from './Header.module.css'; // Der Import des CSS-Moduls
import logo from '../../assets/logo.png';
import Navbar from '../Navbar/Navbar'; 

function Header() {
  return (
    // Hier werden die Klassen aus dem `styles`-Objekt verwendet
    <header className={styles.header}>
      {/* KORREKTUR HIER: className={styles.logo} statt className="logo" */}
      <img src={logo} alt="Logo" className={styles.logo} />
      
      <h1 className={styles.title}>Schichtplan-Dashboard</h1>
        <Navbar /> {/* Hier wird die Navbar-Komponente eingebunden */}
    </header>
  );
}

export default Header;