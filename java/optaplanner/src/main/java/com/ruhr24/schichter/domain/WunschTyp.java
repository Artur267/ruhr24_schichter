package com.ruhr24.schichter.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Definiert die Art eines Wunsches.
 * MUSS: Hard Constraint
 * KANN: Soft Constraint
 * FREI: Frei halt
 */

public enum WunschTyp {
    MUSS,
    KANN,
    FREI;

    @JsonCreator
    public static WunschTyp fromString(String value) {
        if (value == null) {
            return null;
        }
        // Prüft den Wert case-insensitiv gegen die Enum-Namen
        for (WunschTyp typ : WunschTyp.values()) {
            if (typ.name().equalsIgnoreCase(value)) {
                return typ;
            }
        }
        // Fängt auch die "schönen" Namen aus dem Frontend ab, falls diese gesendet werden
        if ("Wunsch".equalsIgnoreCase(value)) {
            return KANN;
        }
        if ("Frei (nicht verfügbar)".equalsIgnoreCase(value)) {
            return FREI;
        }
        throw new IllegalArgumentException("Unbekannter WunschTyp: " + value);
    }
}