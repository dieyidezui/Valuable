package com.dieyidezui.valuable.shedulers;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.dieyidezui.valuable.Scheduler;
import com.dieyidezui.valuable.function.Supplier;
import com.dieyidezui.valuable.internal.util.Functions;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * created by dieyidezui on 2018/8/2.
 */
@SuppressWarnings("unused")
public class Schedulers {

    private static final Scheduler MAIN;

    private static final Scheduler UPSTREAM;

    private static final Scheduler IMMEDIATE;

    private static final Scheduler IO;


    private static final Scheduler COMPUTATION;

    private static final Scheduler SINGLE;

    private static final Scheduler NEW_THREAD;


    static {
        MAIN = new SchedulerImpl(() -> HandlerHolder.MAIN_HANDLER::post);

        UPSTREAM = new SchedulerImpl(() -> r -> {
            throw new UnsupportedOperationException("Can't use Schedulers.upStream() directly.");
        });

        IMMEDIATE = new SchedulerImpl(() -> Runnable::run);

        IO = new SchedulerImpl(() -> new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ValuableThreadFactory("IO")));

        COMPUTATION = new SchedulerImpl(() -> {
            int available = Runtime.getRuntime().availableProcessors();
            return new ThreadPoolExecutor(available, available,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    new ValuableThreadFactory("Computation"));
        });


        SINGLE = new SchedulerImpl(() -> new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ValuableThreadFactory("Single")));


        NEW_THREAD = new SchedulerImpl(() -> new Executor() {
            ThreadFactory factory = new ValuableThreadFactory("New");

            @Override
            public void execute(@NonNull Runnable command) {
                factory.newThread(command).start();
            }
        });
    }

    private Schedulers() {
    }

    /**
     * 主线程
     */
    public static Scheduler main() {
        return MAIN;
    }

    /**
     * 使用上游的 Scheduler
     */
    public static Scheduler upstream() {
        return UPSTREAM;
    }

    /**
     * 如果可能的话，立即执行
     */
    public static Scheduler immediate() {
        return IMMEDIATE;
    }

    /**
     * IO密集型的线程池
     */
    public static Scheduler io() {
        return IO;
    }

    /**
     * 计算密集型的线程池
     */
    public static Scheduler computation() {
        return COMPUTATION;
    }

    /**
     * 在一个非主程的单线程执行
     */
    public static Scheduler single() {
        return SINGLE;
    }

    /**
     * 在新线程执行
     */
    public static Scheduler newThread() {
        return NEW_THREAD;
    }

    public static Handler mainHandler() {
        return HandlerHolder.MAIN_HANDLER;
    }

    static class SchedulerImpl implements Scheduler {


        Supplier<Executor> supplier;

        SchedulerImpl(Supplier<Executor> supplier) {
            this.supplier = Functions.cache(supplier);
        }

        @Override
        public void schedule(@NonNull Runnable command) {
            supplier.get().execute(command);
        }

        @Override
        public Executor executor() {
            return supplier.get();
        }
    }

    static class HandlerHolder {
        static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    }

    /**
     * created by dieyidezui on 2018/8/2.
     */
    public static class ValuableThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        ValuableThreadFactory(String type) {
            namePrefix = type + "-pool-" +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            return new ValuableThread(r,
                    namePrefix + threadNumber.getAndIncrement());
        }

        static class ValuableThread extends Thread {

            ValuableThread(@Nullable Runnable r, String name) {
                super(null, r, name, 0);
                setDaemon(false);
            }
        }
    }
}
