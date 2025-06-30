package com.ruhr24.schichter.domain;

public enum SchichtTyp {
    // Standard-Schicht-Typen (können auch als blockTyp auftreten und werden in den Schichten verwendet)
    KERNDIENST("KERNDIENST"), // Für generische Kerndienst-Schichten
    CVD_FRUEHDIENST("CVD_FRUEHDIENST"), // Als Schichttyp
    CVD_SPAETDIENST("CVD_SPAETDIENST"),   // Als Schichttyp
    VERWALTUNG("VERWALTUNG"), // Als Schichttyp (für Admin-Blöcke)
    ACHT_STUNDEN_DIENST("8-Stunden-Dienst"), // Die eigentliche 8h-Schicht (z.B. innerhalb eines Blocks)
    WOCHENEND_DIENST("Wochenend-Dienst"), // Die eigentliche Wochenend-Schicht (z.B. innerhalb eines Blocks)

    // NEUE Enum-Konstanten, die direkt als BLOCK-Typen verwendet werden und im Generator/Constraints referenziert werden
    // Ihre displayValues sind die spezifischen Block-Strings.
    STANDARD_8H_DIENST("STANDARD_8H_DIENST"), // Blocktyp für flexible 8h-Kerndienst-Slots
    STANDARD_WOCHENEND_DIENST("STANDARD_WOCHENEND_DIENST"), // Blocktyp für flexible Wochenend-Kerndienst-Slots
    CVD_WOCHENENDE_FRUEH_BLOCK("CVD_WOCHENENDE_FRUEHDIENST_BLOCK"), // Blocktyp für Sa/So CvD Frühdienst (2-Tage-Block)
    CVD_WOCHENENDE_SPAET_BLOCK("CVD_WOCHENENDE_SPAETDIENST_BLOCK"), // Blocktyp für Sa/So CvD Spätdienst (2-Tage-Block)

    CVD_FRUEH_7_TAGE_BLOCK("CVD_FRUEH_7_TAGE_BLOCK"), // Blocktyp für Mo-So CvD Frühdienst
    CVD_SPAET_7_TAGE_BLOCK("CVD_SPAET_7_TAGE_BLOCK"), // Blocktyp für Mo-So CvD Spätdienst
    KERN_20H_8H_8H_4H_BLOCK("KERN_20H_8H_8H_4H_BLOCK"), // Für 20h Muster-Block
    KERN_MO_DO_32H_BLOCK("KERN_MO_DO_32H_BLOCK"), // Für 32h Mo-Do Block
    KERN_MO_FR_40H_BLOCK("KERN_MO_FR_40H_BLOCK"), // Für 40h Mo-Fr Block
    KERN_MO_FR_20H_BLOCK("KERN_MO_FR_20H_BLOCK"), // Für 20h Mo-Fr Block
    KERN_7_TAGE_BLOCK("KERN_7_TAGE_BLOCK"), // Für generische 7-Tage-Kerndienst-Blöcke
    CVD_MOSO_WOCHENENDE_LEAD_BLOCK("CVD_MOSO_WOCHENENDE_LEAD_BLOCK"), // Für den komplexen CvD Mo-So Block
    WOCHENEND_KERNDIENST_AUSGLEICH_BLOCK("WOCHENEND_KERNDIENST_AUSGLEICH_BLOCK"), // Für Wochenend-Dienst mit Ausgleichstagen
    ADMIN_MO_FR_BLOCK("ADMIN_MO_FR_BLOCK"), // Blocktyp für den Admin Mo-Fr Block

    // Flexible Einzelschichten (als Schicht-Typen innerhalb von Blöcken oder für flexible Blöcke)
    VIER_STUNDEN_DIENST_FRUEH("4-Stunden-Dienst (früh)"),
    VIER_STUNDEN_DIENST_SPAET("4-Stunden-Dienst (spät)"),
    FUENF_STUNDEN_DIENST("5-Stunden-Dienst"),
    SECHS_STUNDEN_DIENST("6-Stunden-Dienst"),
    SIEBEN_STUNDEN_SCHICHT("7-Stunden-Schicht"),
    WEITERBILDUNG("Weiterbildung"),

    // Status-Typen (diese werden typischerweise nicht als 'blockTyp' verwendet, aber sind Schicht-Typen)
    URLAUB("URLAUB"),
    KRANK("KRANK"),
    FREI("FREI");

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
        // Fallback: If displayValue doesn't match, try to match by enum name (case-insensitive)
        try {
            return SchichtTyp.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Kein SchichtTyp mit dem Namen '" + text + "' gefunden.");
        }
    }
}
