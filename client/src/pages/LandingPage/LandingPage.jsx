import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@mantine/core';
import styles from './LandingPage.module.css';
import rocketImage from '../../assets/rocket.png'; 
import Starfield from '../../components/Starfield/Starfield'; 
import moonImage from '../../assets/moon.png';


function LandingPage() {
  const [position, setPosition] = useState({ x: 0, y: 0 });

  // Dieser Hook verfolgt die Mausposition
  useEffect(() => {
    const handleMouseMove = (e) => {
      // Berechne die Bewegung weg von der Mitte des Bildschirms
      const x = (e.clientX - window.innerWidth / 2) / 50; // Teiler für sanftere Bewegung
      const y = (e.clientY - window.innerHeight / 2) / 50;
      setPosition({ x, y });
    };

    window.addEventListener('mousemove', handleMouseMove);

    // Aufräumen, wenn die Komponente verlassen wird
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, []);

  return (
    <div className={styles.landingContainer}>
        <div className={styles.starfieldContainer}>
            <Starfield />
            <img
              src={moonImage}
              alt="Mond"
              className={styles.floatingMoon}
            />
        </div>

      <img
        src={rocketImage}
        alt="Rakete"
        className={styles.rocket}
        style={{
          transform: `translate(${position.x}px, ${position.y}px)`
        }}
      />

      <div className={styles.content}>
        <h1>Willkommen zum Schichtplaner</h1>
        <p>Bei Bugs oder kleineren Fehlern bitte Feedback an Artur.</p>
        <div className={styles.buttonGroup}>
          <Button component={Link} to="/erstellen" size="lg" color="orange">
            Neue Planung starten
          </Button>
          <Button component={Link} to="/bearbeiten" size="lg" color="orange">
            Plan bearbeiten
          </Button>
        </div>
      </div>
    </div>
  );
}

export default LandingPage;