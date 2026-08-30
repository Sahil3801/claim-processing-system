package com.claim.demo.exception;

import java.time.LocalDate;

public class InvalidReportDateRangeException extends RuntimeException {

    public InvalidReportDateRangeException(LocalDate from, LocalDate to) {
        super("Report date range is invalid: from=" + from + ", to=" + to);
    }
}
