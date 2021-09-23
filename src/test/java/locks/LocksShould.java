package locks;

import static java.time.Month.AUGUST;
import static java.time.Month.SEPTEMBER;
import static java.util.Collections.emptyList;
import static locks.Locks.Event.Type.LOCK;
import static locks.Locks.Event.Type.UNLOCK;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import locks.Locks.Duration;
import locks.Locks.Event;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

class LocksShould {
   @Test
   void parseSimpleLines() {
      var input = new ByteArrayInputStream(("""
            2021-08-31 22:28:00.841 Df logind[132:54af3a] [com.apple.login:Logind_General] -[SessionAgent SA_SetSessionStateForUser:state:reply:]:536: state set to: 3
            2021-09-01 20:21:08.899 Df logind[132:561d5e] [com.apple.login:Logind_General] -[SessionAgent SA_SetSessionStateForUser:state:reply:]:536: state set to: 2
            """).getBytes());
      var dateTime1 = LocalDateTime.of(2021, AUGUST, 31, 22, 28, 0, 841000000);
      var dateTime2 = LocalDateTime.of(2021, SEPTEMBER, 1, 20, 21, 8, 899000000);

      StreamEx<Event> events = Locks.toEvents(input);

      assertIterableEquals(List.of(new Event(dateTime1, LOCK), new Event(dateTime2, UNLOCK)), events.toList());
   }

   @Test
   void ignoreNonMatchingLines() {
      var input = new ByteArrayInputStream(("asdf\n").getBytes());

      StreamEx<Event> events = Locks.toEvents(input);

      assertIterableEquals(emptyList(), events.toList());
   }

   @Test
   void convertUnlockLockToDuration() {
      Event unlock = new Event(times.next(), UNLOCK);
      Event lock = new Event(times.next(), LOCK);

      StreamEx<Duration> durations = Locks.toDurations(StreamEx.of(unlock, lock));

      assertIterableEquals(List.of(new Duration(unlock.at(), lock.at())), durations.toList());
   }

   @Test
   void doNothingOnSingleLock() {
      Event lock = new Event(times.next(), LOCK);

      StreamEx<Duration> durations = Locks.toDurations(StreamEx.of(lock));

      assertIterableEquals(emptyList(), durations);
   }

   @Test
   void doNothingOnSingleUnlock() {
      Event unlock = new Event(times.next(), UNLOCK);

      StreamEx<Duration> durations = Locks.toDurations(StreamEx.of(unlock));

      assertIterableEquals(emptyList(), durations);
   }

   @Test
   void ignoreDoubleLock() {
      Event unlock = new Event(times.next(), UNLOCK);
      Event lock1 = new Event(times.next(), LOCK);
      Event lock2 = new Event(times.next(), LOCK);

      StreamEx<Duration> durations = Locks.toDurations(StreamEx.of(unlock, lock1, lock2));

      assertIterableEquals(List.of(new Duration(unlock.at(), lock2.at())), durations.toList());
   }

   @Test
   void ignoreDoubleUnlock() {
      Event unlock1 = new Event(times.next(), UNLOCK);
      Event unlock2 = new Event(times.next(), UNLOCK);
      Event lock = new Event(times.next(), LOCK);

      StreamEx<Duration> durations = Locks.toDurations(StreamEx.of(unlock1, unlock2, lock));

      assertIterableEquals(List.of(new Duration(unlock1.at(), lock.at())), durations.toList());
   }

   @Test
   void ignoreLocksBeforeFirstUnlock() {
      Event lockBeforeUnlock = new Event(times.next(), LOCK);
      Event unlock = new Event(times.next(), UNLOCK);
      Event lock = new Event(times.next(), LOCK);

      StreamEx<Duration> durations = Locks.toDurations(StreamEx.of(lockBeforeUnlock, unlock, lock));

      assertIterableEquals(List.of(new Duration(unlock.at(), lock.at())), durations.toList());
   }

   @Test
   void ignoreUnmatchedUnlock() {
      Event unlock = new Event(times.next(), UNLOCK);
      Event lock = new Event(times.next(), LOCK);
      Event unmatchedLock = new Event(times.next(), UNLOCK);

      StreamEx<Duration> durations = Locks.toDurations(StreamEx.of(unlock, lock, unmatchedLock));

      assertIterableEquals(List.of(new Duration(unlock.at(), lock.at())), durations.toList());
   }

   final Iterator<LocalDateTime> times = new Iterator<>() {
      private static LocalDateTime at = LocalDateTime.of(2021, AUGUST, 31, 0, 0, 0);

      @Override
      public boolean hasNext() {
         return true;
      }

      @Override
      public LocalDateTime next() {
         at = at.plusHours(1);
         return at;
      }
   };
}