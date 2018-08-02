package com.dieyidezui.valuable.internal.valuables;

import com.dieyidezui.valuable.Scheduler;
import com.dieyidezui.valuable.shedulers.Schedulers;
import com.dieyidezui.valuable.Valuable;
import com.dieyidezui.valuable.function.Function;

import java.lang.ref.WeakReference;

/**
 * created by dieyidezui on 2018/8/2.
 */
public class MapValuable<T, R> extends BaseValuable<R> {
    private WeakReference<Valuable<T>> ref;

    public MapValuable(Valuable<T> source, Function<? super T, ? extends R> mapper, Scheduler scheduler) {
        super(null, scheduler, source);
        this.ref = new WeakReference<>(source);
        ((BaseValuable<T>) source).complete(
                t -> {
                    if (isCanceled()) return;
                    R r;
                    try {
                        r = mapper.apply(t);
                    } catch (Exception ex) {
                        onComplete(null, ex, true);
                        return;
                    }
                    onComplete(r, null, true);
                }, scheduler,
                e -> onComplete(null, e, true), Schedulers.upstream());
    }

    @Override
    public void cancel() {
        super.cancel();
        Valuable<T> s = ref.get();
        if (s != null) {
            s.cancel();
        }
    }
}
