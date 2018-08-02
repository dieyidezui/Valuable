package com.dieyidezui.valuable;

import java.util.concurrent.Executor;

/**
 * created by dieyidezui on 2018/8/2.
 */
public interface Scheduler {

    void schedule(Runnable command);

    Executor executor();
}
