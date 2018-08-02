package com.dieyidezui.valuable.internal.util;

import com.dieyidezui.valuable.function.Supplier;

import java.util.concurrent.atomic.AtomicReference;


/**
 * created by dieyidezui on 2018/8/2.
 */
public class Functions {

    /**
     * @param supplier must not return's null
     */
    public static <T> Supplier<T> cache(Supplier<T> supplier) {
        return new SupplierSupplier<>(supplier);
    }

    private static class SupplierSupplier<T> extends CachedSupplier<T> {

        private Supplier<T> supplier;

        SupplierSupplier(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        protected T create() {
            T t = null;
            Supplier<T> s = supplier;
            supplier = null; // avoid memory leak
            if (s != null) {
                t = s.get();
            }
            return t;
        }
    }


    abstract static class CachedSupplier<T> implements Supplier<T> {

        final AtomicReference<T> ref = new AtomicReference<>(null);

        @Override
        public T get() {
            for (; ; ) {
                T t = ref.get();
                if (t != null) {
                    return t;
                }
                t = create();
                if (t != null && ref.compareAndSet(null, t)) {
                    return t;
                }
            }
        }

        protected abstract T create();
    }
}
