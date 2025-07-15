import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MantineProvider } from '@mantine/core'; // Importieren
import App from './App.jsx';
import './index.css';

// Mantine CSS importieren
import '@mantine/core/styles.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <MantineProvider withGlobalStyles withNormalizeCSS> {/* Hier wrappen */}
        <App />
      </MantineProvider>
    </BrowserRouter>
  </React.StrictMode>,
);