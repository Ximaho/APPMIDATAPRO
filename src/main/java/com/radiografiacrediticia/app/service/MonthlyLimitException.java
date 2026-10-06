package com.radiografiacrediticia.app.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * El usuario ya generó su radiografía del mes.
 */
public class MonthlyLimitException extends RuntimeException {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "CO"));

    private final LocalDate nextAvailable;

    public MonthlyLimitException(LocalDate nextAvailable) {
        super("Ya generaste tu radiografía de este mes. Podrás generar la siguiente a partir del "
                + formatDate(nextAvailable) + ".");
        this.nextAvailable = nextAvailable;
    }

    /** Fecha en español, p. ej. "1 de noviembre". */
    public static String formatDate(LocalDate date) {
        return date.format(FORMAT);
    }

    public LocalDate getNextAvailable() {
        return nextAvailable;
    }
}
