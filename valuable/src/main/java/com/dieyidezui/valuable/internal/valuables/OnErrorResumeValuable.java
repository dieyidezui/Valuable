package com.dieyidezui.valuable.internal.valuables;

import com.dieyidezui.valuable.Scheduler;
import com.dieyidezui.valuable.shedulers.Schedulers;
import com.dieyidezui.valuable.Valuable;
import com.dieyidezui.valuable.function.Function;

import java.lang.ref.WeakReference;

/**
 * created by dieyidezui on 2018/8/2.
 */
public class OnErrorResumeValuable<T> extends BaseValuable<T> {

    private WeakReference<Valuable<T>> ref;

    public OnErrorResumeValuable(Valuable<T> source, Function<? super Exception, ? extends T> resumer, Scheduler scheduler) {
        super(null, scheduler, source);
        ref = new WeakReference<>(source);
        ((BaseValuable<T>) source).complete(t -> {
                    onComplete(t, null, true);
                }, Schedulers.upstream(),
                e -> {
                    if (isCanceled()) return;
                    T t;
                    try {
                        t = resumer.apply(e);
                    } catch (Exception ex) {
                        onComplete(null, ex, true);
                        return;
                    }
                    onComplete(t, null, true);
                }, scheduler);
    }

    @Override
    public void cancel() {
        super.cancel();
        Valuable<T> v = ref.get();
        if (v != null) {
            v.cancel();
        }
    }
}
