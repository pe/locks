package locks;

import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.teeing;

import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Locks {
   private static final Pattern PATTERN = Pattern.compile(
         "([\\d-]+ [\\d:.]+) Df logind\\[\\d+:[\\da-f]+] \\[com.apple.login:Logind_General] -\\[SessionAgent SA_SetSessionStateForUser:state:reply:]:536: state set to: (\\d)\n");
   private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
   private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

   public static void main(String[] args) {
      StreamEx<Event> events = toEvents(System.in);
      StreamEx<Duration> durations = toDurations(events);
      print(System.out, durations);
   }

   static StreamEx<Event> toEvents(InputStream input) {
      Stream<MatchResult> matches = new Scanner(input).findAll(PATTERN);
      return StreamEx.of(matches).map(matcher -> new Event(matcher.group(1), matcher.group(2)));
   }

   static StreamEx<Duration> toDurations(StreamEx<Event> events) {
      return events
            .dropWhile(Event::isLock)
            .collapse(Event::bothAreUnlock, selectFirst())
            .collapse(Event::bothAreLock, selectLast())
            .collapse(Event::lockAndUnlock, mapping(Event::at, teeing(first(), last(), Duration::new)))
            .intervalMap((d1, d2) -> MINUTES.between(d1.stop, d2.start) < 15, (d1, d2) -> new Duration(d1.start, d2.stop));
   }

   private static BinaryOperator<Event> selectFirst() {
      return (u, v) -> u;
   }

   private static BinaryOperator<Event> selectLast() {
      return (u, v) -> v;
   }

   private static <X> Collector<X, ?, X> first() {
      return collectingAndThen(MoreCollectors.first(), Optional::get);
   }

   private static <X> Collector<X, ?, X> last() {
      return collectingAndThen(MoreCollectors.last(), Optional::get);
   }

   static void print(PrintStream out, StreamEx<Duration> durations) {
      durations.mapToEntry(duration -> duration.start.toLocalDate(), Function.identity())
            .collapseKeys()
            .forKeyValue((day, durationsOfTheDay) -> {
               String eventsOfTheDay = durationsOfTheDay.stream()
                     .flatMap(duration -> StreamEx.of(duration.start, duration.stop))
                     .map(event -> event.format(SHORT_TIME))
                     .collect(Collectors.joining("\t"));
               out.println(day.format(SHORT_DATE) + "\t\t" + eventsOfTheDay);
            });
   }

   record Duration(LocalDateTime start, LocalDateTime stop) {
   }

   record Event(LocalDateTime at, Locks.Event.Type type) {
      private static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .appendOffset("+HHmm", "")
            .toFormatter();

      public Event(String at, String type) {
         this(LocalDateTime.parse(at, DATE_TIME), Event.Type.of(type));
      }

      private boolean isLock() {
         return type == Type.LOCK;
      }

      private boolean isUnlock() {
         return type == Type.UNLOCK;
      }

      private static boolean bothAreLock(Event event1, Event event2) {
         return event1.isLock() && event2.isLock();
      }

      private static boolean bothAreUnlock(Event event1, Event event2) {
         return event1.isUnlock() && event2.isUnlock();
      }

      private static boolean lockAndUnlock(Event event1, Event event2) {
         return event1.isUnlock() && event2.isLock();
      }

      enum Type {
         LOCK, UNLOCK, IGNORED;

         public static Type of(String type) {
            return switch (type) {
               case "2" -> UNLOCK;
               case "3", "5" -> LOCK;
               default -> IGNORED;
            };
         }
      }
   }
}
