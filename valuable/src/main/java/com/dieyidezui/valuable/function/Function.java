package com.dieyidezui.valuable.function;

/**
 * created by dieyidezui on 2018/8/2.
 */
public interface Function<T, R> {

    R apply(T t) throws Exception;
}
