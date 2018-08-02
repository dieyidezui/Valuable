package com.dieyidezui.valuable.internal.valuables;

import android.support.annotation.Nullable;

import com.dieyidezui.valuable.Scheduler;
import com.dieyidezui.valuable.Valuable;
import com.dieyidezui.valuable.exceptions.CanceledException;
import com.dieyidezui.valuable.exceptions.ValuableException;
import com.dieyidezui.valuable.function.Consumer;
import com.dieyidezui.valuable.internal.util.ObjectHelper;
import com.dieyidezui.valuable.shedulers.Schedulers;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * created by dieyidezui on 2018/8/2.
 */
@SuppressWarnings({"unchecked", "NullableProblems"})
public class BaseValuable<T> extends Valuable<T> {

    private final AtomicReference<Result<T>> ref;
    private final CountDownLatch latch;
    private final Consumers consumers;

    public BaseValuable(@Nullable Callable<T> callable, Scheduler scheduler, @Nullable Valuable<?> upStream) {
        ref = new AtomicReference<>(Result.holder());
        latch = new CountDownLatch(1);
        consumers = new Consumers(upStreamToReal(upStream, scheduler)); // 'this' is useless, just avoid warning
        if (callable != null) {
            scheduler.schedule(() -> {
                T t = null;
                Exception e = null;
                try {
                    t = callable.call();
                } catch (Exception ex) {
                    e = ex;
                }
                onComplete(t, e, true);
            });
        }
    }

    private static Scheduler upStreamToReal(@Nullable Valuable<?> source, Scheduler scheduler) {
        if (scheduler.equals(Schedulers.upstream())) {
            if (source == null) {
                throw new AssertionError();
            }
            return source.scheduler();
        }
        return scheduler;
    }


    @Override
    public Valuable<T> success(Consumer<? super T> consumer, Scheduler scheduler) {
        return complete(
                ObjectHelper.requireNonNull(consumer), ObjectHelper.requireNonNull(scheduler),
                null, null);
    }

    @Override
    public Valuable<T> complete(Consumer<? super T> consumer, Consumer<? super Exception> handler, Scheduler scheduler) {
        return complete(
                ObjectHelper.requireNonNull(consumer), ObjectHelper.requireNonNull(scheduler),
                ObjectHelper.requireNonNull(handler), scheduler);
    }

    @Override
    public Valuable<T> catchError(Consumer<? super Exception> consumer, Scheduler scheduler) {
        return complete(
                null, null,
                ObjectHelper.requireNonNull(consumer), ObjectHelper.requireNonNull(scheduler));
    }

    @Override
    public T get() throws ValuableException {
        boolean inter = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                inter = true;
            }
        }
        if (inter) {
            Thread.currentThread().interrupt();
        }
        Result<T> result = ref.get();
        if (result == null) {
            throw new CanceledException("Canceled");
        }
        if (!result.success()) {
            if (result.e instanceof ValuableException) {
                throw (ValuableException) result.e;
            }
            throw new ValuableException(result.e);
        }
        return result.t;
    }

    @Override
    public T getOrDefault(T defVal) {
        Result<T> result = ref.get();
        if (result != null && result.success()) {
            return result.t;
        }
        return defVal;
    }

    @Override
    public void cancel() {
        if (ref.compareAndSet(Result.holder(), null)) {
            latch.countDown();
            consumers.onCall(null, false);
        } else {
            ref.set(null);
        }
    }

    @Override
    public boolean isCanceled() {
        return ref.get() == null;
    }


    @Override
    public void notifyResult(@Nullable T result) {
        onComplete(result, null, false);
    }

    @Override
    public void notifyError(Exception e) {
        onComplete(null, ObjectHelper.requireNonNull(e), false);
    }

    @Override
    public Scheduler scheduler() {
        return consumers.origin;
    }


    Valuable<T> complete(@Nullable Consumer<? super T> consumer, @Nullable Scheduler consumerScheduler,
                         @Nullable Consumer<? super Exception> handler, @Nullable Scheduler handlerScheduler) {
        consumers.register(consumer, consumerScheduler, handler, handlerScheduler, ref.get());
        return this;
    }

    void onComplete(@Nullable T t, @Nullable Exception e, boolean onScheduler) {
        Result<T> r = new Result<>(t, e);
        if (ref.compareAndSet(Result.holder(), r)) {
            latch.countDown();
            consumers.onCall(r, onScheduler);
        }
    }

    /**
     * T 可能为 null，所以是否为成功用 e 是否为空表示
     */
    static class Result<T> {
        static final Result<?> HOLDER = new Result<>(null, new Exception("Placeholder!"));

        static <T> Result<T> holder() {
            return (Result<T>) HOLDER;
        }

        final T t;
        final Exception e;

        Result(@Nullable T t, @Nullable Exception e) {
            this.t = t;
            this.e = e;
        }

        boolean success() {
            return e == null;
        }

        Object object() {
            return e == null ? t : e;
        }
    }

    static class ResultConsumer {

        static final ResultConsumer DEFAULT = new ResultConsumer(false, null, null);

        private final boolean success;
        private final Consumer consumer;
        private final Scheduler consumerScheduler;

        ResultConsumer(boolean success, @Nullable Consumer consumer, @Nullable Scheduler consumerScheduler) {
            this.success = success;
            this.consumer = consumer;
            this.consumerScheduler = consumerScheduler;
        }

        void enqueueOrCall(ConcurrentLinkedQueue<ResultConsumer> queue, AtomicInteger state, Scheduler scheduler, Result<?> result) {
            int old = state.getAndIncrement();
            if (old > 0) {
                queue.offer(this);
            } else {
                state.decrementAndGet();
                call(result, scheduler, false);
            }
        }

        void call(@Nullable Result<?> result, Scheduler origin, boolean onScheduler) {
            if (consumer == null || consumerScheduler == null) return;
            if (result == null) {
                result = new Result<>(null, new CanceledException("Canceled"));
            }
            final Result<?> r = result;
            if (result.success() == success) {
                if (onScheduler) {
                    if (!consumerScheduler.equals(Schedulers.upstream()) && !consumerScheduler.equals(origin)) {
                        consumerScheduler.schedule(() -> consumer.accept(r.object()));
                    } else {
                        consumer.accept(r.object());
                    }
                } else {
                    if (consumerScheduler == Schedulers.upstream()) {
                        origin.schedule(() -> consumer.accept(r.object()));
                    } else {
                        consumerScheduler.schedule(() -> consumer.accept(r.object()));
                    }
                }
            }
        }
    }

    class Consumers {

        final Scheduler origin;
        final AtomicInteger state = new AtomicInteger(1);
        final ConcurrentLinkedQueue<ResultConsumer> queue = new ConcurrentLinkedQueue<>();


        Consumers(Scheduler origin) {
            this.origin = origin;
            queue.offer(ResultConsumer.DEFAULT);
        }


        void onCall(@Nullable Result<T> r, boolean onScheduler) {
            ResultConsumer s;
            int count;
            do {
                count = 0;
                while ((s = queue.poll()) != null) {
                    if (isCanceled()) {
                        r = null;
                    }
                    s.call(r, origin, onScheduler);
                    count--;
                }

            } while (state.addAndGet(count) != 0);
        }

        void register(@Nullable Consumer<? super T> consumer, @Nullable Scheduler consumerScheduler,
                      @Nullable Consumer<? super Exception> handler, @Nullable Scheduler handlerScheduler, Result<T> t) {
            if (consumer != null) {
                new ResultConsumer(true, consumer, consumerScheduler).enqueueOrCall(queue, state, origin, t);
            }
            if (handler != null) {
                new ResultConsumer(false, handler, handlerScheduler).enqueueOrCall(queue, state, origin, t);
            }
        }
    }
}
