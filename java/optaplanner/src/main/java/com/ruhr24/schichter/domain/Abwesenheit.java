package com.ruhr24.schichter.domain;

import java.time.LocalDate;

// Ein Record für eine klare, unveränderliche Datenstruktur
public record Abwesenheit(
    String id,
    String mitarbeiterId,
    LocalDate von,
    LocalDate bis,
    Abwesenheitstyp typ,
    String notiz
) {}