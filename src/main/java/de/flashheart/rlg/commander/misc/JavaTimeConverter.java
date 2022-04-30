package de.flashheart.rlg.commander.misc;

import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class JavaTimeConverter {

    public static final LocalDateTime THE_VERY_BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);
    public static final LocalDateTime UNTIL_FURTHER_NOTICE = LocalDateTime.of(9999, 12, 31, 23, 59, 59);


    public static boolean isBefore(Date one, Date two) {
        return toJavaLocalDateTime(one).toLocalDate().isBefore(toJavaLocalDateTime(two).toLocalDate());
    }

    public static boolean isAfter(Date one, Date two) {
        return toJavaLocalDateTime(one).toLocalDate().isAfter(toJavaLocalDateTime(two).toLocalDate());
    }

    public static Date toDate(LocalDateTime ldt) {
        return new Date(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public static Period between(Date one, Date two) {
        return Period.between(toJavaLocalDateTime(one).toLocalDate(), toJavaLocalDateTime(two).toLocalDate());
    }

    public static LocalDateTime toJavaLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static String to_iso8601() {
        return to_iso8601(LocalDateTime.now());
    }


    public static String to_iso8601(Date date) {
        return to_iso8601(toJavaLocalDateTime(date));
    }

    public static String to_iso8601(LocalDateTime now) {
        return now.atZone(ZoneId.systemDefault()).toString();
    }

    public static LocalDateTime from_iso8601(String iso8601) {
        return ZonedDateTime.parse(iso8601).toLocalDateTime();
    }
}
