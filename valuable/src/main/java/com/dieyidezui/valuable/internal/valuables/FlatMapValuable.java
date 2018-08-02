package com.dieyidezui.valuable.internal.valuables;

import com.dieyidezui.valuable.Scheduler;
import com.dieyidezui.valuable.shedulers.Schedulers;
import com.dieyidezui.valuable.Valuable;
import com.dieyidezui.valuable.function.Function;
import com.dieyidezui.valuable.internal.util.ObjectHelper;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * created by dieyidezui on 2018/8/2.
 */
public class FlatMapValuable<T, R> extends BaseValuable<R> {
    private AtomicReference<WeakReference<Valuable<?>>> atomicRef;
    private WeakReference<Valuable<?>> ref;

    public FlatMapValuable(Valuable<T> source, Function<? super T, Valuable<? extends R>> mapper, Scheduler scheduler) {
        super(null, scheduler, source);
        this.ref = new WeakReference<>(source);
        this.atomicRef = new AtomicReference<>(ref);
        ((BaseValuable<T>) source).complete(
                t -> {
                    if (isCanceled()) return;
                    Valuable<? extends R> target;
                    try {
                        target = ObjectHelper.requireNonNull(mapper.apply(t), "flat map returns null");
                    } catch (Exception e) {
                        if (atomicRef.compareAndSet(ref, null)) {
                            onComplete(null, e, true);
                        }
                        return;
                    }
                    if (atomicRef.compareAndSet(ref, new WeakReference<>(target))) {
                        target.complete(r -> onComplete(r, null, true),
                                e -> onComplete(null, e, true));
                    } else {
                        target.cancel();
                    }
                }, scheduler,
                e -> {
                    if (atomicRef.compareAndSet(ref, null)) {
                        onComplete(null, e, true);
                    }
                }, Schedulers.upstream());
    }

    @Override
    public void cancel() {
        super.cancel();
        WeakReference<Valuable<?>> s = atomicRef.getAndSet(null);
        if (s != null) {
            Valuable<?> v = s.get();
            if (v != null) {
                v.cancel();
            }
        }
    }
}
