# Valuable
<<<<<<< HEAD
A lightweight async library on Android
=======
Valuable 是一个轻量的异步库。
他有点像 RxJava 的 Single 和 CompletableFuture 的综合体，他更面向数据，也更加轻量友好，相应的也只提供最基础最核心的功能。
相比于 RxJava 一兆多的代码量，Valuable 仅 35KB，更适用于对包体积大小有严格要求的应用。

## 安装依赖
在 build.gradle 中加入以下一行即可

```groovy
implementation 'com.dieyidezui.valuable:valuable:1.0.0-rc1'
```

## 使用 Valuable
### Valuable 入门
#### Valuable.empty
Valuable 是一个被观察者，最简单的创建方式就是 ```Valuable.empty(Schedulers.immediate())```
此 Valuable 没有任何额外的动作，也不产生任何数据，因此我们需要主动调用他的方法提供数据或者异常

#### Valuable.notifyResult
```java
Valuable<String> valuable = Valuable.empty(Schedulers.immediate());
valuable.notifyResult("Hello World");
System.out.println(valuable.get());

结果：
Hello World
```

Schedulers 暂且略过，以上的代码就是首先创建了一个空的 Valuable<String>， 随后主动的向他提供了结果 "Hello World"，因此输出的结果自然是该字符串了。

每个 Valuable 只可以接受一次结果或者异常，无论是手动调用 notifyResult/notifyError 方法还是 Valuable 自带的。

#### Valuable.notifyError
```java
Valuable<String> valuable = Valuable.empty(Schedulers.immediate());
valuable.notifyError(new Exception("???"));
System.out.println(valuable.get());

结果：
com.dieyidezui.valuable.exceptions.ValuableException: java.lang.Exception: ???
	at ...
```

```Valuable.get()``` 会阻塞等待 Valuable 完成，无论是正确的得到结果还是异常。如果 Valuable 是因异常结束的，那么 get 会抛出 ValuableException， 其 cause 为真正的异常原因。

#### Valuable.success/catchError/complete

上面说了 Valuable 是一个被观察者，因此他既可以同步等待数据也可以注册观察者。

```java
Valuable.call(new Callable<String>() {
    @Override
    public String call() throws Exception {
        Thread.sleep(500L);
        return "OK";
    }
}).success(new Consumer<String>() {
    @Override
    public void accept(String s) {
        System.out.println("1: " + s);
    }
}).catchError(new Consumer<Exception>() {
    @Override
    public void accept(Exception e) {
        e.printStackTrace();
    }
}).complete(new Consumer<String>() {
    @Override
    public void accept(String s) {
        System.out.println("2: " + s);
    }
}, new Consumer<Exception>() {
    @Override
    public void accept(Exception e) {
        e.printStackTrace();
    }
});

结果：
1: OK
2: OK
```

Valuable.call 是使用一个 Callable 创建一个 Valuable， 那么我们可以通过 success/complete/catchError 来监听他成功或者失败的回调，显然 Callable 是没有出现异常的，那么只会回调监听成功的接口。


#### 其他创建方式
1. Valuable.result(R r): 内置一个成功的结果
2. Valuable.error(Exception e): 内置一个失败的结果
3. Valuable.supply(Supplier<R> supplier): 使用一个 Supplier 来创建 Valuable<R>
4. Valuable.run(Runnable runnable): 使用一个 Runnable 创建一个 Valuable<Void>，如果 Runnable 执行无异常，则返回 null
5. combine(EFunction<Object[], R> combiner, Valuable<?>... valuables): 将多个 Valuable 的结果映射到 EFunction 的结果，中间环节任一出错，则该 Valuable<R> 则错误

### 操作 Valuable
#### map

```java
Integer res = Valuable.result("OK")
        .map(new EFunction<String, Integer>() {
            @Override
            public Integer apply(String s) throws Exception {
                return s.length();
            }
        }).get();
System.out.println(res);

结果：
2
```

这里有两个细节：
1. Valuable 是允许 null 的结果的， 因此直接取```s.length()```是有可能空指针的。
2. EFunction 是允许抛出 Exception 的，因此在 apply 期间的异常均会被捕获，并回调给 Valuable 失败。

```java
Integer res = Valuable.<String>result(null)
        .map(new EFunction<String, Integer>() {
            @Override
            public Integer apply(String s) throws Exception {
                return s.length();
            }
        }).get();
System.out.println(res);

结果：
com.dieyidezui.valuable.exceptions.ValuableException: java.lang.NullPointerException
	at ...
```

这里因为 s.length() 空指针，因此 Valuable 是处于一个失败的状态，调用 get 会抛出异常，而如果调用 catchError 则会得到该异常的回调。

#### flatMap

```java
Valuable<Integer> v = Valuable.<String>result(null)
        .flatMap(new EFunction<String, Valuable<? extends Integer>>() {
            @Override
            public Valuable<? extends Integer> apply(String s) throws Exception {
                return Valuable.result(s == null ? -1 : s.length());
            }
        });
System.out.println(v.get());

结果：
-1
```

flatMap 和 map 有一点不同的是， flatMap 的 EFunction.apply 的返回值不允许为 null。

#### onErrorReturn/onErrorResume

```java
Integer res = Valuable.<String>result(null)
        .map(new EFunction<String, Integer>() {
            @Override
            public Integer apply(String s) throws Exception {
                return s.length();
            }
        }).onErrorResume(new EFunction<Exception, Integer>() {
            @Override
            public Integer apply(Exception e) throws Exception {
                e.printStackTrace();
                return -1;
            }
        }).get();
System.out.println(res);

结果：
java.lang.NullPointerException
    at ...
-1
```

当 Valuable 出现异常时可以恢复并返回一个正常值。

#### cancel

```java
Valuable<String> v = Valuable.call(new Callable<String>() {
    @Override
    public String call() throws Exception {
        Thread.sleep(500L);
        return "OK";
    }
}).catchError(Throwable::printStackTrace);

v.cancel();

结果：
com.dieyidezui.valuable.exceptions.CanceledException: Canceled
	at ...
```

当调用 Cancel 后 Valuable 进入取消状态，此时调用 get 会抛出异常，catchError 会回调错误。


#### forever 

Valuable.forever() 调用后生成的 Valuable 是不允许调用 cancel 方法的，如果调用会直接抛出```UnsupportedOperationException```。

#### getOrDefault

和 get 是类似的，但是 Valuable 处于 未完成、出错、取消的状态时，会返回默认值。

### Scheduler

#### 源调度器
Valuable 和 Scheduler 是密切关联的，每个 Valuable 创建时都会绑定一个 Scheduler，称之为源调度器。

如果 Valuable 是包含异步任务的，无论是 Runnable/Supplier/Callable 等，那么就会执行在源调度器的线程上。

#### Schedulers
Schedulers 提供内置的 Scheduler：
- main: 主线程
- immediate: 当前线程
- io: IO 密集型线程池
- computation: 计算密集型线程池
- single: 单线程池
- newThread: 每次创建一个新的线程

除了上面列出来的之外，还有一个 **Schedulers.upstream()**。
upstream 比较特殊，他不作为源调度器而存在，中间用操作符做转换时，可以指定和上游的 Valuable 的源调度器保持一致。

这也是为什么每个 Valuable 创建时一定会有一个 Scheduler，即使是 Valuable.empty(Scheduler)，也会带一个 Scheduler 参数。其他的创建方式都给予了带调度器的重载方法，不带调度器参数的都会内置一个默认的，可以在源码中直接看到，因此使用时要注意默认的调度器线程类型。

#### 操作符调度器
操作符也是自带调度器的，如果没有就是内置了默认的。
操作符转换后的 Valuable 其实是一个全新的对象，他的源调度器就是操作符自带的调度器。
每个操作符的函数接口都会执行在操作符调度器的线程中。

#### 观察者调度器

##### 当一个 Valuable 自带异步任务时
如果一个观察的调度器是 upstream 或者和源调度器相同：
1. 异步任务结束前注册的观察者：会在当前线程直接回调。
2. 异步任务结束后注册的观察者：用源调度器调度。

否则的话就是用观察者自带的调度器调度。

##### 当 Valuable 提前被 notifyXXX 时
由于 notify 的线程不确定，因此选择是：

1.观察者为 upstream -> 源调度器
2.其他 -> 观察者调度器

##### immediate
immediate 有些不一样的是，如果异步任务在执行，带 immediate 的观察者是不会阻塞等待的，还是会在源调度器的线程回调该观察者。而如果 Valuable 处于终结状态，那么带 immediate 的观察者会在注册的地方立即回调。

### TODO
1. 调度器暂不支持定时或者延时的调度
2. 操作符待丰富

详细的用法可以参见： [ValuableTest](valuable/src/androidTest/java/com/dieyidezui/valuable/ValuableTest.java)

## LICENSE

    Copyright (c) 2017-present, dieyidezui.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
>>>>>>> Feature: initial release candidate 1
