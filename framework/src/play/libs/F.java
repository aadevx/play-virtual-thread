package play.libs;

import play.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class F {

    private final Lock readLock = new ReentrantLock();

    private static CompletableFuture<F.Tuple<Integer, CompletableFuture<Object>>> waitEitherInternal(CompletableFuture<?>... futures) {
            final CompletableFuture<F.Tuple<Integer, CompletableFuture<Object>>> result = new CompletableFuture<>();
            for (int i = 0; i < futures.length; i++) {
                final int index = i + 1;
                CompletableFuture f = futures[i];
                futures[i].thenAccept(completed -> result.complete(new Tuple(index, f)));
            }
            return result;
        }

        public static <A, B> CompletableFuture<F.Either<A, B>> waitEither(CompletableFuture<A> tA, CompletableFuture<B> tB) {
            final CompletableFuture<F.Either<A, B>> result = new CompletableFuture<>();
            CompletableFuture<F.Tuple<Integer, CompletableFuture<Object>>> t = waitEitherInternal(tA, tB);
            t.thenAccept(completed -> {
                switch (completed._1) {
                    case 1:
                        result.complete(Either.<A, B>_1((A) completed._2.getNow(null)));
                        break;
                    case 2:
                        result.complete(Either.<A, B>_2((B) completed._2.getNow(null)));
                        break;
                }
            });
            return result;
        }

        public static <A, B, C> CompletableFuture<F.E3<A, B, C>> waitEither(CompletableFuture<A> tA, CompletableFuture<B> tB, CompletableFuture<C> tC) {
            final CompletableFuture<F.E3<A, B, C>> result = new CompletableFuture<>();
            CompletableFuture<F.Tuple<Integer, CompletableFuture<Object>>> t = waitEitherInternal(tA, tB, tC);
            t.thenAccept(completed -> {
                switch (completed._1) {
                    case 1:
                        result.complete(E3.<A, B, C>_1((A) completed._2.getNow(null)));
                        break;
                    case 2:
                        result.complete(E3.<A, B, C>_2((B) completed._2.getNow(null)));
                        break;
                    case 3:
                        result.complete(E3.<A, B, C>_3((C) completed._2.getNow(null)));
                        break;
                }
            });
            return result;
        }

        public static <A, B, C, D> CompletableFuture<F.E4<A, B, C, D>> waitEither(CompletableFuture<A> tA, CompletableFuture<B> tB, CompletableFuture<C> tC, CompletableFuture<D> tD) {
            final CompletableFuture<F.E4<A, B, C, D>> result = new CompletableFuture<>();
            CompletableFuture<F.Tuple<Integer, CompletableFuture<Object>>> t = waitEitherInternal(tA, tB, tC, tD);
            t.thenAccept(completed -> {
                switch (completed._1) {
                    case 1:
                        result.complete(E4.<A, B, C, D>_1((A) completed._2.getNow(null)));
                        break;
                    case 2:
                        result.complete(E4.<A, B, C, D>_2((B) completed._2.getNow(null)));
                        break;
                    case 3:
                        result.complete(E4.<A, B, C, D>_3((C) completed._2.getNow(null)));
                        break;
                    case 4:
                        result.complete(E4.<A, B, C, D>_4((D) completed._2.getNow(null)));
                        break;
                }
            });

            return result;
        }

        public static <T> CompletableFuture<T> waitAny(CompletableFuture<T>... futures) {
            final CompletableFuture<T> result = new CompletableFuture<>();
            Consumer<CompletableFuture<T>> action = new Consumer<CompletableFuture<T>>() {
                private final Lock readLock = new ReentrantLock();
                @Override
                public void accept(CompletableFuture<T> completed) {
                    try {
                        readLock.lock();
                        if (result.isDone()) {
                            return;
                        }
                    } finally {
                        readLock.unlock();
                    }
                    try {
                        T resultOrNull = completed.get();
                        result.complete(resultOrNull);
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                }
            };

            for (CompletableFuture<T> f : futures) {
                action.accept(f);
            }

            return result;
        }

    public static class Timeout extends CompletableFuture<Timeout> {

        static final Timer timer = new Timer("F.Timeout", true);
        public final String token;
        public final long delay;

        public Timeout(String delay) {
            this(Time.parseDuration(delay) * 1000);
        }

        public Timeout(String token, String delay) {
            this(token, Time.parseDuration(delay) * 1000);
        }

        public Timeout(long delay) {
            this("timeout", delay);
        }

        public Timeout(String token, long delay) {
            this.delay = delay;
            this.token = token;
            final Timeout timeout = this;
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    timeout.complete(timeout);
                }
            }, delay);
        }

        @Override
        public String toString() {
            return "Timeout(" + delay + ")";
        }

    }

    public static Timeout Timeout(String delay) {
        return new Timeout(delay);
    }

    public static Timeout Timeout(String token, String delay) {
        return new Timeout(token, delay);
    }

    public static Timeout Timeout(long delay) {
        return new Timeout(delay);
    }

    public static Timeout Timeout(String token, long delay) {
        return new Timeout(token, delay);
    }

    public static class EventStream<T> {
        private final Lock readLock = new ReentrantLock();
        final int bufferSize;
        final ConcurrentLinkedQueue<T> events = new ConcurrentLinkedQueue<>();
        final List<CompletableFuture<T>> waiting = Collections.synchronizedList(new ArrayList<CompletableFuture<T>>());

        public EventStream() {
            this.bufferSize = 100;
        }

        public EventStream(int maxBufferSize) {
            this.bufferSize = maxBufferSize;
        }

        public CompletableFuture<T> nextEvent() {
            try {
                readLock.lock();
                if (events.isEmpty()) {
                    LazyTask task = new LazyTask();
                    waiting.add(task);
                    return task;
                }
                return new LazyTask(events.peek());
            }finally {
                readLock.unlock();
            }
        }

        public void publish(T event) {
            try {
                readLock.lock();
                if (events.size() > bufferSize) {
                    Logger.warn("Dropping message.  If this is catastrophic to your app, use a BlockingEvenStream instead");
                    events.poll();
                }
                events.offer(event);
                notifyNewEvent();
            }finally {
                readLock.unlock();
            }
        }

        void notifyNewEvent() {
            T value = events.peek();
            for (CompletableFuture<T> task : waiting) {
                task.complete(value);
            }
            waiting.clear();
        }

        class LazyTask extends CompletableFuture<T> {

            public LazyTask() {
            }

            public LazyTask(T value) {
                complete(value);
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                T value = super.get();
                markAsRead(value);
                return value;
            }

            @Override
            public T getNow(T valueIfAbsent) {
                T value = super.getNow(valueIfAbsent);
                markAsRead(value);
                return value;
            }

            private void markAsRead(T value) {
                if (value != null) {
                    events.remove(value);
                }
            }
        }
    }

    public static class BlockingEventStream<T> {
        private final Lock readLock = new ReentrantLock();
        final LinkedBlockingQueue<T> events;
        final List<CompletableFuture<T>> waiting = Collections.synchronizedList(new ArrayList<CompletableFuture<T>>());

        public BlockingEventStream(){
            events = new LinkedBlockingQueue<>();
        }

        public BlockingEventStream(int maxBufferSize) {
            events = new LinkedBlockingQueue<>(maxBufferSize + 10);
        }

        public CompletableFuture<T> nextEvent() {
            try {
                readLock.lock();
                if (events.isEmpty()) {
                    LazyTask task = new LazyTask();
                    waiting.add(task);
                    return task;
                }
                return new LazyTask(events.peek());
            } finally {
                readLock.unlock();
            }
        }

        //NOTE: cannot synchronize since events.put may block when system is overloaded.
        //Normally, I HATE blocking an NIO thread, but to do this correct, we need a token from netty that we can use to disable
        //the socket reads completely(ie. stop reading from socket when queue is full) as in normal NIO operations if you stop reading
        //from the socket, the local nic buffer fills up, then the remote nic buffer fills(the client's nic), and so the client is informed
        //he can't write anymore just yet (or he blocks if he is synchronous).
        //Then when someone pulls from the queue, the token would be set to enabled allowing to read from nic buffer again and it all propagates
        //This is normal flow control with NIO but since it is not done properly, this at least fixes the issue where websocket break down and
        //skip packets.  They no longer skip packets anymore.
        public void publish(T event) {
            try {
                // This method blocks if the queue is full(read publish method documentation just above)
                if (events.remainingCapacity() == 10) {
                    Logger.trace("events queue is full! Setting readable to false.");
//                    ctx.channel().config().setAutoRead(false);
                }
                events.put(event);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyNewEvent();
        }

        void notifyNewEvent() {
            try {
                readLock.lock();
                T value = events.peek();
                for (CompletableFuture<T> task : waiting) {
                    task.complete(value);
                }
                waiting.clear();
            }finally {
                readLock.unlock();
            }
        }

        class LazyTask extends CompletableFuture<T> {

            public LazyTask(){

            }

            public LazyTask(T value) {
                complete(value);
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                T value = super.get();
                markAsRead(value);
                return value;
            }

            @Override
            public T getNow(T valueIfAbsent) {
                T value = super.getNow(valueIfAbsent);
                markAsRead(value);
                return value;
            }

            private void markAsRead(T value) {
                if (value != null) {
                    events.remove(value);
                    //Don't start back up until we get down to half the total capacity to prevent jittering:
//                    if (events.remainingCapacity() > events.size()) {
//                        ctx.channel().config().setAutoRead(true);
//                    }
                }
            }
        }
    }

    public static class IndexedEvent<M> {

        private static final AtomicLong idGenerator = new AtomicLong(1);
        public final M data;
        public final Long id;

        public IndexedEvent(M data) {
            this.data = data;
            this.id = idGenerator.getAndIncrement();
        }

        @Override
        public String toString() {
            return "Event(id: " + id + ", " + data + ")";
        }

        public static void resetIdGenerator() {
            idGenerator.set(1);
        }
    }

    public static class ArchivedEventStream<T> {
        private final Lock readLock = new ReentrantLock();
        final int archiveSize;
        final ConcurrentLinkedQueue<IndexedEvent<T>> events = new ConcurrentLinkedQueue<>();
        final List<FilterTask<T>> waiting = Collections.synchronizedList(new ArrayList<FilterTask<T>>());
        final List<EventStream<T>> pipedStreams = new ArrayList<>();

        public ArchivedEventStream(int archiveSize) {
            this.archiveSize = archiveSize;
        }

        public EventStream<T> eventStream() {
            try {
                readLock.lock();
                EventStream<T> stream = new EventStream<>(archiveSize);
                for (IndexedEvent<T> event : events) {
                    stream.publish(event.data);
                }
                pipedStreams.add(stream);
                return stream;
            }finally {
                readLock.unlock();
            }
        }

        public CompletableFuture<List<IndexedEvent<T>>> nextEvents(long lastEventSeen) {
            try {
                readLock.lock();
                FilterTask<T> filter = new FilterTask<>(lastEventSeen);
                waiting.add(filter);
                notifyNewEvent();
                return filter;
            }finally {
                readLock.unlock();
            }
        }

        public List<IndexedEvent<?>> availableEvents(long lastEventSeen) {
            try {
                readLock.lock();
                List<IndexedEvent<?>> result = new ArrayList<>();
                for (IndexedEvent<?> event : events) {
                    if (event.id > lastEventSeen) {
                        result.add(event);
                    }
                }
                return result;
            }finally {
                readLock.unlock();
            }
        }

        public List<T> archive() {
            List<T> result = new ArrayList<>();
            for (IndexedEvent<T> event : events) {
                result.add(event.data);
            }
            return result;
        }

        public void publish(T event) {
            try {
                readLock.lock();
                if (events.size() >= archiveSize) {
                    Logger.warn("Dropping message.  If this is catastrophic to your app, use a BlockingEvenStream instead");
                    events.poll();
                }
                events.offer(new IndexedEvent<T>(event));
                notifyNewEvent();
                for (EventStream<T> eventStream : pipedStreams) {
                    eventStream.publish(event);
                }
            } finally {
                readLock.unlock();
            }
        }

        void notifyNewEvent() {
            for (ListIterator<FilterTask<T>> it = waiting.listIterator(); it.hasNext();) {
                FilterTask<T> filter = it.next();
                for (IndexedEvent<T> event : events) {
                    filter.propose(event);
                }
                if (filter.trigger()) {
                    it.remove();
                }
            }
        }

        static class FilterTask<K> extends CompletableFuture<List<IndexedEvent<K>>> {

            final Long lastEventSeen;
            final List<IndexedEvent<K>> newEvents = new ArrayList<>();

            public FilterTask(Long lastEventSeen) {
                this.lastEventSeen = lastEventSeen;
            }

            public void propose(IndexedEvent<K> event) {
                if (event.id > lastEventSeen) {
                    newEvents.add(event);
                }
            }

            public boolean trigger() {
                if (newEvents.isEmpty()) {
                    return false;
                }
                complete(newEvents);
                return true;
            }
        }
    }

    public abstract static class Option<T> implements Iterable<T> {

        public abstract boolean isDefined();

        public abstract T get();

        public static <T> None<T> None() {
            return (None<T>) (Object) None;
        }

        public static <T> Some<T> Some(T value) {
            return new Some<>(value);
        }
    }

    public static <A> Some<A> Some(A a) {
        return new Some<A>(a);
    }

    public static class None<T> extends Option<T> {

        @Override
        public boolean isDefined() {
            return false;
        }

        @Override
        public T get() {
            throw new IllegalStateException("No value");
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.<T>emptyList().iterator();
        }

        @Override
        public String toString() {
            return "None";
        }
    }
    public static final None<Object> None = new None<>();

    public static class Some<T> extends Option<T> {

        final T value;

        public Some(T value) {
            this.value = value;
        }

        @Override
        public boolean isDefined() {
            return true;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.singletonList(value).iterator();
        }

        @Override
        public String toString() {
            return "Some(" + value + ")";
        }
    }

    public static class Either<A, B> {

        public final Option<A> _1;
        public final Option<B> _2;

        private Either(Option<A> _1, Option<B> _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public static <A, B> Either<A, B> _1(A value) {
            return new Either(Some(value), None);
        }

        public static <A, B> Either<A, B> _2(B value) {
            return new Either(None, Some(value));
        }

        @Override
        public String toString() {
            return "E2(_1: " + _1 + ", _2: " + _2 + ")";
        }
    }

    public static class E2<A, B> extends Either<A, B> {

        private E2(Option<A> _1, Option<B> _2) {
            super(_1, _2);
        }
    }

    public static class E3<A, B, C> {

        public final Option<A> _1;
        public final Option<B> _2;
        public final Option<C> _3;

        private E3(Option<A> _1, Option<B> _2, Option<C> _3) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
        }

        public static <A, B, C> E3<A, B, C> _1(A value) {
            return new E3(Some(value), None, None);
        }

        public static <A, B, C> E3<A, B, C> _2(B value) {
            return new E3(None, Some(value), None);
        }

        public static <A, B, C> E3<A, B, C> _3(C value) {
            return new E3(None, None, Some(value));
        }

        @Override
        public String toString() {
            return "E3(_1: " + _1 + ", _2: " + _2 + ", _3:" + _3 + ")";
        }
    }

    public static class E4<A, B, C, D> {

        public final Option<A> _1;
        public final Option<B> _2;
        public final Option<C> _3;
        public final Option<D> _4;

        private E4(Option<A> _1, Option<B> _2, Option<C> _3, Option<D> _4) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
            this._4 = _4;
        }

        public static <A, B, C, D> E4<A, B, C, D> _1(A value) {
            return new E4(Option.Some(value), None, None, None);
        }

        public static <A, B, C, D> E4<A, B, C, D> _2(B value) {
            return new E4(None, Some(value), None, None);
        }

        public static <A, B, C, D> E4<A, B, C, D> _3(C value) {
            return new E4(None, None, Some(value), None);
        }

        public static <A, B, C, D> E4<A, B, C, D> _4(D value) {
            return new E4(None, None, None, Some(value));
        }

        @Override
        public String toString() {
            return "E4(_1: " + _1 + ", _2: " + _2 + ", _3:" + _3 + ", _4:" + _4 + ")";
        }
    }

    public static class E5<A, B, C, D, E> {

        public final Option<A> _1;
        public final Option<B> _2;
        public final Option<C> _3;
        public final Option<D> _4;
        public final Option<E> _5;

        private E5(Option<A> _1, Option<B> _2, Option<C> _3, Option<D> _4, Option<E> _5) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
            this._4 = _4;
            this._5 = _5;
        }

        public static <A, B, C, D, E> E5<A, B, C, D, E> _1(A value) {
            return new E5(Option.Some(value), None, None, None, None);
        }

        public static <A, B, C, D, E> E5<A, B, C, D, E> _2(B value) {
            return new E5(None, Option.Some(value), None, None, None);
        }

        public static <A, B, C, D, E> E5<A, B, C, D, E> _3(C value) {
            return new E5(None, None, Option.Some(value), None, None);
        }

        public static <A, B, C, D, E> E5<A, B, C, D, E> _4(D value) {
            return new E5(None, None, None, Option.Some(value), None);
        }

        public static <A, B, C, D, E> E5<A, B, C, D, E> _5(E value) {
            return new E5(None, None, None, None, Option.Some(value));
        }

        @Override
        public String toString() {
            return "E5(_1: " + _1 + ", _2: " + _2 + ", _3:" + _3 + ", _4:" + _4 + ", _5:" + _5 + ")";
        }
    }

    public static class Tuple<A, B> {

        public final A _1;
        public final B _2;

        public Tuple(A _1, B _2) {
            this._1 = _1;
            this._2 = _2;
        }

        @Override
        public String toString() {
            return "T2(_1: " + _1 + ", _2: " + _2 + ")";
        }
    }

    public static <A, B> Tuple<A, B> Tuple(A a, B b) {
        return new Tuple(a, b);
    }

    public static class T2<A, B> extends Tuple<A, B> {

        public T2(A _1, B _2) {
            super(_1, _2);
        }
    }

    public static <A, B> T2<A, B> T2(A a, B b) {
        return new T2(a, b);
    }

    public static class T3<A, B, C> {

        public final A _1;
        public final B _2;
        public final C _3;

        public T3(A _1, B _2, C _3) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
        }

        @Override
        public String toString() {
            return "T3(_1: " + _1 + ", _2: " + _2 + ", _3:" + _3 + ")";
        }
    }

    public static <A, B, C> T3<A, B, C> T3(A a, B b, C c) {
        return new T3(a, b, c);
    }

    public static class T4<A, B, C, D> {

        public final A _1;
        public final B _2;
        public final C _3;
        public final D _4;

        public T4(A _1, B _2, C _3, D _4) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
            this._4 = _4;
        }

        @Override
        public String toString() {
            return "T4(_1: " + _1 + ", _2: " + _2 + ", _3:" + _3 + ", _4:" + _4 + ")";
        }
    }

    public static <A, B, C, D> T4<A, B, C, D> T4(A a, B b, C c, D d) {
        return new T4<>(a, b, c, d);
    }

    public static class T5<A, B, C, D, E> {

        public final A _1;
        public final B _2;
        public final C _3;
        public final D _4;
        public final E _5;

        public T5(A _1, B _2, C _3, D _4, E _5) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
            this._4 = _4;
            this._5 = _5;
        }

        @Override
        public String toString() {
            return "T5(_1: " + _1 + ", _2: " + _2 + ", _3:" + _3 + ", _4:" + _4 + ", _5:" + _5 + ")";
        }
    }

    public static <A, B, C, D, E> T5<A, B, C, D, E> T5(A a, B b, C c, D d, E e) {
        return new T5<>(a, b, c, d, e);
    }

    public abstract static class Matcher<T, R> {

        public abstract Option<R> match(T o);

        public Option<R> match(Option<T> o) {
            if (o.isDefined()) {
                return match(o.get());
            }
            return Option.None();
        }

        public <NR> Matcher<T, NR> and(final Matcher<R, NR> nextMatcher) {
            final Matcher<T, R> firstMatcher = this;
            return new Matcher<T, NR>() {

                @Override
                public Option<NR> match(T o) {
                    for (R r : firstMatcher.match(o)) {
                        return nextMatcher.match(r);
                    }
                    return Option.None();
                }
            };
        }
        public static Matcher<Object, String> String = new Matcher<Object, String>() {

            @Override
            public Option<String> match(Object o) {
                if (o instanceof String string) {
                    return Option.Some(string);
                }
                return Option.None();
            }
        };

        public static <K> Matcher<Object, K> ClassOf(final Class<K> clazz) {
            return new Matcher<Object, K>() {

                @Override
                public Option<K> match(Object o) {
                    if (o instanceof Option opt && opt.isDefined()) {
                        o = opt.get();
                    }
                    if (clazz.isInstance(o)) {
                        return Option.Some((K) o);
                    }
                    return Option.None();
                }
            };
        }

        public static Matcher<String, String> StartsWith(final String prefix) {
            return new Matcher<String, String>() {

                @Override
                public Option<String> match(String o) {
                    if (o.startsWith(prefix)) {
                        return Option.Some(o);
                    }
                    return Option.None();
                }
            };
        }

        public static Matcher<String, String> Re(final String pattern) {
            return new Matcher<String, String>() {

                @Override
                public Option<String> match(String o) {
                    if (o.matches(pattern)) {
                        return Option.Some(o);
                    }
                    return Option.None();
                }
            };
        }

        public static <X> Matcher<X, X> Equals(final X other) {
            return new Matcher<X, X>() {

                @Override
                public Option<X> match(X o) {
                    if (o.equals(other)) {
                        return Option.Some(o);
                    }
                    return Option.None();
                }
            };
        }
    }
}