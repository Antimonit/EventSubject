package me.khol.reactive.subjects;

import java.util.Objects;
import java.util.concurrent.atomic.*;

import io.reactivex.rxjava3.annotations.*;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.internal.disposables.EmptyDisposable;
import io.reactivex.rxjava3.internal.functions.*;
import io.reactivex.rxjava3.internal.fuseable.SimpleQueue;
import io.reactivex.rxjava3.internal.observers.BasicIntQueueDisposable;
import io.reactivex.rxjava3.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.rxjava3.internal.util.ExceptionHelper;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * A Subject similar to {@link io.reactivex.rxjava3.subjects.UnicastSubject UnicastSubject} in a sense that
 * it allows only one {@link Observer} to be subscribed at a time but when the {@code Observer}
 * is unsubscribed, another one is allowed to resubscribe to this subject again.
 * <p>
 * Just like {@code UnicastSubject} whenever no {@code Observer} is observing
 * events, the events are queued up. Whenever a new {@code Observer} subscribes to this, the queued
 * up events are replayed and the queue subsequently emptied. While an {@code Observer} is
 * subscribed all emission are relayed to the {@code Observer} directly.
 * <p>
 * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/UnicastSubject.png" alt="">
 * <p>
 * Note that {@code EventSubject} holds an unbounded internal buffer.
 * <p>
 * This subject does not have a public constructor by design; a new empty instance of this
 * {@code EventSubject} can be created via the following {@code create} methods that
 * allow specifying the retention policy for items:
 * <ul>
 * <li>{@link #create()} - creates an empty, unbounded {@code EventSubject} that
 *     caches all items and the terminal event it receives.</li>
 * <li>{@link #create(int)} - creates an empty, unbounded {@code EventSubject}
 *     with a hint about how many <b>total</b> items one expects to retain.</li>
 * <li>{@link #create(boolean)} - creates an empty, unbounded {@code EventSubject} that
 *     optionally delays an error it receives and replays it after the regular items have been emitted.</li>
 * <li>{@link #create(int, Runnable)} - creates an empty, unbounded {@code EventSubject}
 *     with a hint about how many <b>total</b> items one expects to retain and a callback that will be
 *     called exactly once when the {@code EventSubject} gets terminated or the single {@code Observer} unsubscribes.</li>
 * <li>{@link #create(int, Runnable, boolean)} - creates an empty, unbounded {@code EventSubject}
 *     with a hint about how many <b>total</b> items one expects to retain and a callback that will be
 *     called exactly once when the {@code EventSubject} gets terminated or the single {@code Observer} unsubscribes
 *     and optionally delays an error it receives and replays it after the regular items have been emitted.</li>
 * </ul>
 * <p>
 * If more than one {@code Observer} attempts to subscribe to this {@code EventSubject} at the same
 * time, they will receive an {@code IllegalStateException}.
 * <p>
 * All other properties of this {@code EventSubject} are the same as of {@link io.reactivex.rxjava3.subjects.UnicastSubject}.
 * <p>
 * Example usage:
 * <pre><code>
 * EventSubject&lt;Integer&gt; subject = EventSubject.create();
 *
 * TestObserver&lt;Integer&gt; to1 = subject.test();
 *
 * // fresh EventSubjects are empty
 * to1.assertEmpty();
 *
 * TestObserver&lt;Integer&gt; to2 = subject.test();
 *
 * // A EventSubject only allows one Observer subscribed at a time
 * to2.assertFailure(IllegalStateException.class);
 *
 * subject.onNext(1);
 * to1.assertValue(1);
 *
 * subject.onNext(2);
 * to1.assertValues(1, 2);
 *
 * to1.dispose();
 *
 * // a EventSubject caches events until an Observer subscribes
 * subject.onNext(3);
 * subject.onNext(4);
 *
 * TestObserver&lt;Integer&gt; to3 = subject.test();
 *
 * // the cached events are emitted in order
 * to3.assertValues(3, 4);
 *
 * subject.onComplete();
 * to3.assertResult(3, 4);
 * </code></pre>
 * @param <T> the value type received and emitted by this Subject subclass
 */
public final class EventSubject<T> extends Subject<T> {
    /** The queue that buffers the source events. */
    final SpscLinkedArrayQueue<T> queue;

    /** The single Observer. */
    final AtomicReference<Observer<? super T>> downstream;

    /** The optional callback when the Subject gets cancelled or terminates. */
    final AtomicReference<Runnable> onTerminate;

    /** deliver onNext events before error event. */
    final boolean delayError;

    /** Indicates the source has terminated. */
    volatile boolean done;
    /**
     * The terminal error if not null.
     * Must be set before writing to done and read after done == true.
     */
    Throwable error;

    /** The wip counter and QueueDisposable surface. */
    BasicIntQueueDisposable<T> wip;

    boolean enableOperatorFusion;

    /**
     * Creates an EventSubject with an internal buffer capacity hint 16.
     * @param <T> the value type
     * @return an EventSubject instance
     */
    @CheckReturnValue
    @NonNull
    public static <T> EventSubject<T> create() {
        return new EventSubject<>(bufferSize(), null, true);
    }

    /**
     * Creates an EventSubject with the given internal buffer capacity hint.
     * @param <T> the value type
     * @param capacityHint the hint to size the internal unbounded buffer
     * @return an EventSubject instance
     * @throws IllegalArgumentException if {@code capacityHint} is non-positive
     */
    @CheckReturnValue
    @NonNull
    public static <T> EventSubject<T> create(int capacityHint) {
        ObjectHelper.verifyPositive(capacityHint, "capacityHint");
        return new EventSubject<>(capacityHint, null, true);
    }

    /**
     * Creates an EventSubject with the given internal buffer capacity hint and a callback for
     * the case when the single Subscriber cancels its subscription
     * or the subject is terminated.
     *
     * <p>The callback, if not null, is called exactly once and
     * non-overlapped with any active replay.
     *
     * @param <T> the value type
     * @param capacityHint the hint to size the internal unbounded buffer
     * @param onTerminate the callback to run when the Subject is terminated or cancelled, null not allowed
     * @return an EventSubject instance
     * @throws NullPointerException if {@code onTerminate} is {@code null}
     * @throws IllegalArgumentException if {@code capacityHint} is non-positive
     */
    @CheckReturnValue
    @NonNull
    public static <T> EventSubject<T> create(int capacityHint, Runnable onTerminate) {
        ObjectHelper.verifyPositive(capacityHint, "capacityHint");
        Objects.requireNonNull(onTerminate, "onTerminate");
        return new EventSubject<>(capacityHint, onTerminate, true);
    }

    /**
     * Creates an EventSubject with the given internal buffer capacity hint, delay error flag and
     * a callback for the case when the single Observer disposes its {@link Disposable}
     * or the subject is terminated.
     *
     * <p>The callback, if not null, is called exactly once and
     * non-overlapped with any active replay.
     * <p>History: 2.0.8 - experimental
     * @param <T> the value type
     * @param capacityHint the hint to size the internal unbounded buffer
     * @param onTerminate the callback to run when the Subject is terminated or cancelled, null not allowed
     * @param delayError deliver pending onNext events before onError
     * @return an EventSubject instance
     * @throws NullPointerException if {@code onTerminate} is {@code null}
     * @throws IllegalArgumentException if {@code capacityHint} is non-positive
     * @since 2.2
     */
    @CheckReturnValue
    @NonNull
    public static <T> EventSubject<T> create(int capacityHint, @NonNull Runnable onTerminate, boolean delayError) {
        ObjectHelper.verifyPositive(capacityHint, "capacityHint");
        Objects.requireNonNull(onTerminate, "onTerminate");
        return new EventSubject<>(capacityHint, onTerminate, delayError);
    }

    /**
     * Creates an EventSubject with an internal buffer capacity hint 16 and given delay error flag.
     *
     * <p>The callback, if not null, is called exactly once and
     * non-overlapped with any active replay.
     * <p>History: 2.0.8 - experimental
     * @param <T> the value type
     * @param delayError deliver pending onNext events before onError
     * @return an EventSubject instance
     * @since 2.2
     */
    @CheckReturnValue
    @NonNull
    public static <T> EventSubject<T> create(boolean delayError) {
        return new EventSubject<>(bufferSize(), null, delayError);
    }

    /**
     * Creates an EventSubject with the given capacity hint, delay error flag and callback
     * for when the Subject is terminated normally or its single Subscriber cancels.
     * <p>History: 2.0.8 - experimental
     * @param capacityHint the capacity hint for the internal, unbounded queue
     * @param onTerminate the callback to run when the Subject is terminated or cancelled, null not allowed
     * @param delayError deliver pending onNext events before onError
     * @since 2.2
     */
    EventSubject(int capacityHint, Runnable onTerminate, boolean delayError) {
        this.queue = new SpscLinkedArrayQueue<>(capacityHint);
        this.onTerminate = new AtomicReference<>(onTerminate);
        this.delayError = delayError;
        this.downstream = new AtomicReference<>();
    }

    @Override
    protected void subscribeActual(Observer<? super T> observer) {
	    if (downstream.get() == null && downstream.compareAndSet(null, observer)) {
		    wip = new EventQueueDisposable();
            observer.onSubscribe(wip);
            downstream.lazySet(observer); // full barrier in drain
            drain();
        } else {
            EmptyDisposable.error(new IllegalStateException("Only a single observer allowed."), observer);
        }
    }

    void doTerminate() {
        Runnable r = onTerminate.get();
        if (r != null && onTerminate.compareAndSet(r, null)) {
            r.run();
        }
    }

    @Override
    public void onSubscribe(Disposable d) {
        if (done) {
            d.dispose();
        }
    }

    @Override
    public void onNext(T t) {
        ExceptionHelper.nullCheck(t, "onNext called with a null value.");
        if (done) {
            return;
        }
        queue.offer(t);
        drain();
    }

    @Override
    public void onError(Throwable t) {
        ExceptionHelper.nullCheck(t, "onError called with a null Throwable.");
        if (done) {
            RxJavaPlugins.onError(t);
            return;
        }
        error = t;
        done = true;

        doTerminate();

        drain();
    }

    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;

        doTerminate();

        drain();
    }

    void drainNormal(Observer<? super T> a) {
        int missed = 1;
        SimpleQueue<T> q = queue;
        boolean failFast = !this.delayError;
        boolean canBeError = true;
        for (;;) {
            for (;;) {
                boolean d = this.done;
                T v = queue.poll();
                boolean empty = v == null;

                if (d) {
                    if (failFast && canBeError) {
                        if (failedFast(q, a)) {
                            return;
                        } else {
                            canBeError = false;
                        }
                    }

                    if (empty) {
                        errorOrComplete(a);
                        return;
                    }
                }

                if (empty) {
                    break;
                }

                a.onNext(v);
            }

            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                break;
            }
        }
    }

    void drainFused(Observer<? super T> a) {
        int missed = 1;

        final SpscLinkedArrayQueue<T> q = queue;
        final boolean failFast = !delayError;

        for (;;) {
            boolean d = done;

            if (failFast && d) {
                if (failedFast(q, a)) {
                    return;
                }
            }

            a.onNext(null);

            if (d) {
                errorOrComplete(a);
                return;
            }

            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                break;
            }
        }
    }

    void errorOrComplete(Observer<? super T> a) {
        downstream.lazySet(null);
        Throwable ex = error;
        if (ex != null) {
            a.onError(ex);
        } else {
            a.onComplete();
        }
    }

    boolean failedFast(final SimpleQueue<T> q, Observer<? super T> a) {
        Throwable ex = error;
        if (ex != null) {
            downstream.lazySet(null);
            q.clear();
            a.onError(ex);
            return true;
        } else {
            return false;
        }
    }

    void drain() {
        if (wip == null || wip.getAndIncrement() != 0) {
            return;
        }

        Observer<? super T> a = downstream.get();
        int missed = 1;

        for (;;) {

            if (a != null) {
                if (enableOperatorFusion) {
                    drainFused(a);
                } else {
                    drainNormal(a);
                }
                return;
            }

            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                break;
            }

            a = downstream.get();
        }
    }

    @Override
    @CheckReturnValue
    public boolean hasObservers() {
        return downstream.get() != null;
    }

    @Override
    @Nullable
    @CheckReturnValue
    public Throwable getThrowable() {
        if (done) {
            return error;
        }
        return null;
    }

    @Override
    @CheckReturnValue
    public boolean hasThrowable() {
        return done && error != null;
    }

    @Override
    @CheckReturnValue
    public boolean hasComplete() {
        return done && error == null;
    }

    final class EventQueueDisposable extends BasicIntQueueDisposable<T> {

	    volatile boolean disposed;

	    private static final long serialVersionUID = 8926949470189395511L;

        @Override
        public int requestFusion(int mode) {
            if ((mode & ASYNC) != 0) {
                enableOperatorFusion = true;
                return ASYNC;
            }
            return NONE;
        }

        @Nullable
        @Override
        public T poll() {
            return queue.poll();
        }

        @Override
        public boolean isEmpty() {
            return queue.isEmpty();
        }

        @Override
        public void clear() {
            queue.clear();
        }

        @Override
        public void dispose() {
            if (!disposed) {
                disposed = true;

                downstream.lazySet(null);
                if (wip.getAndIncrement() == 0) {
                    downstream.lazySet(null);
                    if (!enableOperatorFusion) {
                        queue.clear();
                    }
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

    }
}
