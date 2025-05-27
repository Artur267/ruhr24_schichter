package com.ruhr24.schichter.domain;

public enum Ressort {
    RUHR24_GMBH("RUHR24 GmbH"),
    SPORT("Sport"),
    BUZZ("Buzz"),
    RUHR24_DE("RUHR24.de"),
    RUHR24JOBS("RUHR24JOBS"),
    WERKSTUDENTEN("Werkstudent:innen"), // Beachte den Doppelpunkt hier, wenn er in deinen Daten so vorkommt
    JOBS("JOBS"); // Falls JOBS und RUHR24JOBS getrennt sind

    private final String displayValue;

    Ressort(String displayValue) {
        this.displayValue = displayValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    // Optional: Eine Methode, um ein Ressort anhand des DisplayValue zu finden
    public static Ressort fromDisplayValue(String text) {
        for (Ressort r : Ressort.values()) {
            if (r.displayValue.equalsIgnoreCase(text)) {
                return r;
            }
        }
        // Oder eine Exception werfen, wenn der Wert nicht gefunden wird
        throw new IllegalArgumentException("Kein Ressort mit dem Namen '" + text + "' gefunden.");
    }
}