// client/src/components/Starfield/Starfield.jsx

import React, { useCallback } from 'react';
import Particles from "react-tsparticles";
import { loadSlim } from "tsparticles-slim";

function Starfield() {
    const particlesInit = useCallback(async engine => {
        // Diese Zeile lädt die Partikel-Engine
        await loadSlim(engine);
    }, []);

    const particlesLoaded = useCallback(async container => {
        // Optional: Etwas tun, wenn die Partikel geladen sind
        console.log("Starfield geladen:", container);
    }, []);

    const options = {
        background: {
            color: {
                value: "#1e2a3a", // Dunkelblau für den Weltraum
            },
        },
        fpsLimit: 120,
        particles: {
            color: {
                value: "#ffffff",
            },
            move: {
                direction: "none",
                enable: true,
                outModes: {
                    default: "out",
                },
                random: true,
                speed: 0.1, // Sehr langsame Bewegung für einen sanften Effekt
                straight: false,
            },
            number: {
                density: {
                    enable: true,
                    area: 800,
                },
                value: 150, // Anzahl der Sterne
            },
            opacity: {
                value: {min: 0.1, max: 0.5}, // Sterne haben unterschiedliche Helligkeit
            },
            shape: {
                type: "circle",
            },
            size: {
                value: { min: 1, max: 2 }, // Sterne haben unterschiedliche Größen
            },
        },
        detectRetina: true,
    };

    return (
        <Particles
            id="tsparticles"
            init={particlesInit}
            loaded={particlesLoaded}
            options={options}
            style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                zIndex: 0 // Stellt sicher, dass es im Hintergrund ist
            }}
        />
    );
}

export default Starfield;