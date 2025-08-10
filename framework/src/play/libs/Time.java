package play.libs;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time utils
 *
 * Provides a parser for time expression.
 * <p>
 * Time expressions provide the ability to specify complex time combinations
 * such as &quot;2d&quot;, &quot;1w2d3h10s&quot; or &quot;2d4h10s&quot;.
 * </p>
 *
 */
public class Time {
    private static final Pattern p = Pattern.compile("(([0-9]+?)((d|h|mi|min|mn|s)))+?");
    private static final int MINUTE = 60;
    private static final int HOUR = 60 * MINUTE;
    private static final int DAY = 24 * HOUR;

    /**
     * Parse a duration
     *
     * @param duration
     *            3h, 2mn, 7s or combination 2d4h10s, 1w2d3h10s
     * @return The number of seconds
     */
    public static int parseDuration(String duration) {
        if (duration == null) {
            return 30 * DAY;
        }

        Matcher matcher = p.matcher(duration);
        int seconds = 0;
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration pattern : " + duration);
        }

        matcher.reset();
        while (matcher.find()) {
            if (matcher.group(3).equals("d")) {
                seconds += Integer.parseInt(matcher.group(2)) * DAY;
            } else if (matcher.group(3).equals("h")) {
                seconds += Integer.parseInt(matcher.group(2)) * HOUR;
            } else if (matcher.group(3).equals("mi") || matcher.group(3).equals("min") || matcher.group(3).equals("mn")) {
                seconds += Integer.parseInt(matcher.group(2)) * MINUTE;
            } else {
                seconds += Integer.parseInt(matcher.group(2));
            }
        }

        return seconds;
    }

    /**
     * Parse a CRON expression
     *
     * @param cron
     *            The CRON String
     * @return The next Date that satisfy the expression
     */
    public static Date parseCRONExpression(String cron) {
        try {
            return new CronExpression(cron).getNextValidTimeAfter(new Date());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CRON pattern : " + cron, e);
        }
    }

    /**
     * Compute the number of milliseconds between the next valid date and the
     * one after
     *
     * @param cron
     *            The CRON String
     * @return the number of milliseconds between the next valid date and the
     *         one after, with an invalid interval between
     */
    public static long cronInterval(String cron) {
        return cronInterval(cron, new Date());
    }

    /**
     * Compute the number of milliseconds between the next valid date and the
     * one after
     *
     * @param cron
     *            The CRON String
     * @param date
     *            The date to start search
     * @return the number of milliseconds between the next valid date and the
     *         one after, with an invalid interval between
     */
    public static long cronInterval(String cron, Date date) {
        try {
            return new CronExpression(cron).getNextInterval(date);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid CRON pattern : " + cron, e);
        }
    }

    /**
     * https://www.logicbig.com/how-to/java-8-date-time-api/calender-to-localdatetime.html
     * @param calendar
     * @return
     */
    public static LocalDateTime toLocalDateTime(Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        TimeZone tz = calendar.getTimeZone();
        ZoneId zid = tz == null ? ZoneId.systemDefault() : tz.toZoneId();
        return LocalDateTime.ofInstant(calendar.toInstant(), zid);
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static LocalDateTime toLocalDateTime(long milis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(milis), ZoneId.systemDefault());
    }

    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date toDate(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }
}
