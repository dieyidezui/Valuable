package com.dieyidezui.valuable;

import android.support.annotation.Nullable;
import android.util.Pair;

import com.dieyidezui.valuable.exceptions.ValuableException;
import com.dieyidezui.valuable.function.Consumer;
import com.dieyidezui.valuable.function.Function;
import com.dieyidezui.valuable.function.Supplier;
import com.dieyidezui.valuable.internal.util.ObjectHelper;
import com.dieyidezui.valuable.internal.valuables.BaseValuable;
import com.dieyidezui.valuable.internal.valuables.CombineValuable;
import com.dieyidezui.valuable.internal.valuables.FlatMapValuable;
import com.dieyidezui.valuable.internal.valuables.ForeverValuable;
import com.dieyidezui.valuable.internal.valuables.MapValuable;
import com.dieyidezui.valuable.internal.valuables.OnErrorResumeValuable;
import com.dieyidezui.valuable.shedulers.Schedulers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * The Valuable is like composition of RxJava's Single and CompletableFuture, but more simple and lite weight.
 * created by dieyidezui on 2018/8/2.
 */
@SuppressWarnings({"unused", "WeakerAccess", "unchecked", "NullableProblems"})
public abstract class Valuable<T> implements Supplier<T> {

    /**
     * 创建一个空的 Valuable， 随后可以手动调用 notifyResult / notifyError
     */
    public static <R> Valuable<R> empty(Scheduler scheduler) {
        return create(null, scheduler);
    }

    public static <R> Valuable<R> result(@Nullable R r) {
        return result(r, Schedulers.immediate());
    }

    /**
     * 创建一个已有结果的 Valuable，Scheduler 为默认回调线程
     */
    public static <R> Valuable<R> result(@Nullable R r, Scheduler scheduler) {
        return just(r, null, scheduler);
    }

    public static Valuable<?> error(Exception e) {
        return error(e, Schedulers.immediate());
    }

    /**
     * 创建一个已失败的 Valuable，Scheduler 为默认回调线程
     */
    public static Valuable<?> error(Exception e, Scheduler scheduler) {
        return just(null, ObjectHelper.requireNonNull(e), scheduler);
    }

    private static <R> Valuable<R> just(@Nullable R r, @Nullable Exception e, Scheduler scheduler) {
        return call(() -> {
            if (e != null) {
                throw e;
            }
            return r;
        }, scheduler);
    }

    public static <R> Valuable<R> supply(Supplier<R> supplier) {
        return supply(supplier, Schedulers.computation());
    }

    public static <R> Valuable<R> supply(Supplier<R> supplier, Scheduler scheduler) {
        ObjectHelper.requireNonNull(supplier);
        return create(supplier::get, scheduler);
    }

    public static Valuable<Void> run(Runnable runnable) {
        return run(runnable, Schedulers.computation());
    }

    public static Valuable<Void> run(Runnable runnable, Scheduler scheduler) {
        return create(Executors.callable(runnable, null), scheduler);
    }

    /**
     * 根据 Callable 创建 Valuable
     */
    public static <R> Valuable<R> call(Callable<R> callable) {
        return call(callable, Schedulers.io());
    }

    public static <R> Valuable<R> call(Callable<R> callable, Scheduler scheduler) {
        return create(ObjectHelper.requireNonNull(callable), scheduler);
    }

    private static <R> Valuable<R> create(@Nullable Callable<R> callable, Scheduler scheduler) {
        if (scheduler == Schedulers.upstream()) {
            throw new IllegalArgumentException("Can't use Schedulers.upStream() to create a top Valuable.");
        }
        return new BaseValuable<>(callable, ObjectHelper.requireNonNull(scheduler), null);
    }

    /**
     * 组合多个 Valueable 的结果为一个，任一失败，则该 Valuable 为失败
     */
    public static <R> Valuable<R> combine(Function<Object[], ? extends R> combiner, Valuable<?>... valuables) {
        return combine(combiner, Arrays.asList(valuables), Schedulers.computation());
    }

    public static <R> Valuable<R> combine(Function<Object[], ? extends R> combiner, List<Valuable<?>> valuables, Scheduler scheduler) {
        if (ObjectHelper.requireNonNull(scheduler).equals(Schedulers.upstream())) {
            throw new IllegalArgumentException("Can't use Schedulers.upStream() combine Valuables.");
        }
        return new CombineValuable<>(ObjectHelper.requireNonNull(combiner), ObjectHelper.requireNonNull(valuables), scheduler);
    }

    public final <U, R> Valuable<R> combineWith(Valuable<U> valuable, Function<Pair<T, U>, ? extends R> combiner, Scheduler scheduler) {
        ObjectHelper.requireNonNull(combiner);
        return combine(objects -> combiner.apply(new Pair<>((T) objects[0], (U) objects[1])), Arrays.asList(this, valuable), scheduler);
    }

    /**
     * map结果，返回一个新的 Valuable
     */
    public final <R> Valuable<R> map(Function<? super T, ? extends R> mapper) {
        return map(mapper, Schedulers.upstream());
    }

    public final <R> Valuable<R> map(Function<? super T, ? extends R> mapper, Scheduler scheduler) {
        return new MapValuable<>(this, ObjectHelper.requireNonNull(mapper), ObjectHelper.requireNonNull(scheduler));
    }

    /**
     * flatMap结果，返回一个新的 Valuable，代理 mapper 的返回结果
     */
    public final <R> Valuable<R> flatMap(Function<? super T, Valuable<? extends R>> mapper) {
        return flatMap(mapper, Schedulers.upstream());
    }

    public final <R> Valuable<R> flatMap(Function<? super T, Valuable<? extends R>> mapper, Scheduler scheduler) {
        return new FlatMapValuable<>(this, ObjectHelper.requireNonNull(mapper), ObjectHelper.requireNonNull(scheduler));
    }

    /**
     * 出错时返回 T
     */
    public final Valuable<T> onErrorReturn(T t) {
        return onErrorResume(e -> t);
    }

    public final Valuable<T> onErrorResume(Function<? super Exception, ? extends T> resumer) {
        return onErrorResume(resumer, Schedulers.immediate());
    }

    /**
     * 出错时调用 resumer，其返回值作为成功值
     */
    public final Valuable<T> onErrorResume(Function<? super Exception, ? extends T> resumer, Scheduler scheduler) {
        return new OnErrorResumeValuable<>(this, ObjectHelper.requireNonNull(resumer), ObjectHelper.requireNonNull(scheduler));
    }

    /**
     * 不允许 cancel
     */
    public final Valuable<T> forever() {
        if (this instanceof ForeverValuable) {
            return this;
        }
        return new ForeverValuable(this);
    }

    /**
     * 注册成功的回调，返回自身
     */
    public final Valuable<T> success(Consumer<? super T> consumer) {
        return success(consumer, Schedulers.upstream());
    }

    public abstract Valuable<T> success(Consumer<? super T> consumer, Scheduler scheduler);

    /**
     * 注册成功和失败的回调，返回自身
     */
    public final Valuable<T> complete(Consumer<? super T> consumer, Consumer<? super Exception> handler) {
        return complete(consumer, handler, Schedulers.upstream());
    }

    public abstract Valuable<T> complete(Consumer<? super T> consumer, Consumer<? super Exception> handler, Scheduler schedule);

    /**
     * 注册失败的回调，返回自身
     */
    public final Valuable<T> catchError(Consumer<? super Exception> consumer) {
        return catchError(consumer, Schedulers.upstream());
    }

    public abstract Valuable<T> catchError(Consumer<? super Exception> consumer, Scheduler scheduler);

    /**
     * @throws ValuableException 出现异常或者取消时
     */
    @Nullable
    @Override
    public abstract T get() throws ValuableException;

    /**
     * 如果出错或者尚未完成，则返回默认值
     */
    @Nullable
    public abstract T getOrDefault(@Nullable T defVal);

    /**
     * 取消任务
     */
    public abstract void cancel();

    /**
     * 是否已取消
     */
    public abstract boolean isCanceled();

    /**
     * 将结果直接传入 Valuable
     */
    public abstract void notifyResult(@Nullable T result);

    /**
     * 将异常传入 Valuable
     */
    public abstract void notifyError(Exception e);

    /**
     * 源 Scheduler
     */
    public abstract Scheduler scheduler();

}
