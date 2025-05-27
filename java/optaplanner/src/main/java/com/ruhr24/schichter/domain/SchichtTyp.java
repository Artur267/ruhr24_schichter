package com.ruhr24.schichter.domain;

public enum SchichtTyp {
    KERNDIENST("KERNDIENST"),
    CVD_FRUEHDIENST("CVD_FRUEHDIENST"),
    CVD_SPAETDIENST("CVD_SPAETDIENST"),
    CVD_WOCHENENDE_FRUEH("CVD_WOCHENENDE_FRUEH"),
    CVD_WOCHENENDE_SPAET("CVD_WOCHENENDE_SPAET"),
    STANDARD_REDAKTION("STANDARD_REDAKTION"), // Vielleicht ein allgemeiner Typ für Redaktionsschichten
    STANDARD_WOCHENENDE("STANDARD_WOCHENENDE"), // Für Kerndienste am Wochenende
    URLAUB("URLAUB"), // Wenn du Urlaub als Schichttyp modellieren willst
    KRANK("KRANK"), // Wenn du Krankheit als Schichttyp modellieren willst
    FREI("FREI"); // Wenn du freie Tage als Schichttyp modellieren willst

    private final String displayValue;

    SchichtTyp(String displayValue) {
        this.displayValue = displayValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public static SchichtTyp fromDisplayValue(String text) {
        for (SchichtTyp st : SchichtTyp.values()) {
            if (st.displayValue.equalsIgnoreCase(text)) {
                return st;
            }
        }
        throw new IllegalArgumentException("Kein SchichtTyp mit dem Namen '" + text + "' gefunden.");
    }
}