package com.dieyidezui.valuable.internal.valuables;

import com.dieyidezui.valuable.shedulers.Schedulers;
import com.dieyidezui.valuable.Valuable;

/**
 * created by dieyidezui on 2018/8/2.
 */
public class ForeverValuable<T> extends BaseValuable<T> {

    public ForeverValuable(Valuable<T> source) {
        super(null, source.scheduler(), null);
        ((BaseValuable<T>) source).complete(
                t -> onComplete(t, null, true), Schedulers.upstream(),
                e -> onComplete(null, e, true), Schedulers.upstream());
    }

    @Override
    public void cancel() {
        throw new UnsupportedOperationException("Forever valuable doesn't allow cancel.");
    }
}
