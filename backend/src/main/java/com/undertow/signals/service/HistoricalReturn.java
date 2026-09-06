package com.undertow.signals.service;

import java.time.LocalDate;

public record HistoricalReturn(LocalDate date, double stockReturn, double sectorReturn) {
}
