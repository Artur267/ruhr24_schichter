import React from 'react';
import styles from './EmployeeResourceCell.module.css';

// Diese Komponente erhält die 'extendedProps' von unserer Ressource
function EmployeeResourceCell({ name, role, hours }) {
    return (
        <div className={styles.cellContainer}>
            <div className={styles.name}>{name}</div>
            <div className={styles.details}>
                <span className={styles.role}>{role}</span>
                <span className={styles.hours}>{hours}h / Woche</span>
            </div>
        </div>
    );
}

export default EmployeeResourceCell;