package org.httpkit;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateFormatter {
    private static ZoneId GMT = ZoneId.of("GMT");
    private static DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US);

    public static String getDate() {
        return ZonedDateTime.now(GMT).format(formatter);
    }
}
