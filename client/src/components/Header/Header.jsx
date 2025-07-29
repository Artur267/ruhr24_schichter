import React from 'react';
import styles from './Header.module.css';
import logo from '../../assets/logo.png';
import Navbar from '../Navbar/Navbar'; 

function Header() {
  return (
    <header className={styles.header}>
      <img src={logo} alt="Logo" className={styles.logo} />
      
      <h1 className={styles.title}>Schichtplan Dashboard</h1>
        <Navbar />
    </header>
  );
}

export default Header;