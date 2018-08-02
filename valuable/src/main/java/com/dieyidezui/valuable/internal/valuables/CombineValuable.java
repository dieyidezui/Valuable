package com.dieyidezui.valuable.internal.valuables;

import com.dieyidezui.valuable.Scheduler;
import com.dieyidezui.valuable.shedulers.Schedulers;
import com.dieyidezui.valuable.Valuable;
import com.dieyidezui.valuable.function.Function;

import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * created by dieyidezui on 2018/8/2.
 */
public class CombineValuable<T> extends BaseValuable<T> {
    private Object[] objs;
    private WeakHashMap<Valuable<?>, Valuable<?>> weakSet;
    private AtomicInteger count = new AtomicInteger(0);

    public CombineValuable(Function<Object[], ? extends T> combiner, List<Valuable<?>> valuables, Scheduler scheduler) {
        super(null, scheduler, null);
        objs = new Object[valuables.size()];
        weakSet = new WeakHashMap<>();
        for (Valuable<?> v : valuables) {
            weakSet.put(v, this);
        }
        int i = 0;
        for (Valuable valuable : valuables) {
            BaseValuable<?> v = (BaseValuable) valuable;
            final int index = i++;
            v.complete(o -> {
                        if (isCanceled()) return;
                        Object[] ar = objs;
                        if (ar != null) {
                            ar[index] = o;
                            if (count.incrementAndGet() == ar.length) {
                                objs = null;
                                T t;
                                try {
                                    t = combiner.apply(ar);
                                } catch (Exception e) {
                                    notifyError(e);
                                    return;
                                }
                                notifyResult(t);
                            }
                        }
                    }, scheduler,
                    e -> {
                        objs = null;
                        notifyError(e);
                    }, Schedulers.upstream());
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        for (Valuable<?> o : weakSet.keySet()) {
            if (o != null) {
                o.cancel();
            }
        }
        objs = null;
    }
}
