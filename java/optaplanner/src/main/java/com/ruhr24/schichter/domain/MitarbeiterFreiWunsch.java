package com.ruhr24.schichter.domain;

import java.time.LocalDate;

// Ein Record ist eine kompakte Klasse für reine Datenobjekte.
public record MitarbeiterFreiWunsch(String mitarbeiterId, LocalDate datum) {
}