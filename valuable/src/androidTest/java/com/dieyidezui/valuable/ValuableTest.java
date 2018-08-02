package com.dieyidezui.valuable;

import android.os.Looper;
import android.support.test.runner.AndroidJUnit4;

import com.dieyidezui.valuable.exceptions.ValuableException;
import com.dieyidezui.valuable.shedulers.Schedulers;
import com.dieyidezui.valuable.function.Function;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * created by dieyidezui on 2018/8/2.
 */
@RunWith(AndroidJUnit4.class)
public class ValuableTest {

    @Test
    public void testThreadMode1() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Thread t = Thread.currentThread();
        Valuable.call(() -> "OK").map(s -> {
            Assert.assertNotEquals(t, Thread.currentThread());
            return s.toLowerCase();
        }).complete(s -> {
            Assert.assertEquals("ok", s);
            c.countDown();
        }, e -> {
            throw new AssertionError(e);
        });

        c.await();
    }

    @Test
    public void testThreadMode2() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Valuable.call(() -> "OK").map(s -> {
            Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
            return s.toLowerCase();
        }, Schedulers.main()).complete(s -> {
            Assert.assertEquals("ok", s);
            c.countDown();
        }, e -> {
            throw new AssertionError(e);
        }).forever();

        c.await();
    }

    @Test
    public void testThreadMode3() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Valuable.call(() -> "OK").map((Function<String, String>) s -> {
            Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
            throw new IOException("OMG");
        }, Schedulers.main()).complete(s -> {
            throw new AssertionError();
        }, e -> {
            Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
            Assert.assertEquals(e.getClass(), IOException.class);
            c.countDown();
        });

        c.await();
    }

    @Test
    public void testThreadMode4() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Valuable.result("OK", Schedulers.computation()).map((Function<String, String>) s -> {
            Assert.assertNotEquals(Looper.getMainLooper(), Looper.myLooper());
            throw new IOException("OMG");
        }).complete(s -> {
            throw new AssertionError();
        }, e -> {
            Assert.assertNotEquals(Looper.getMainLooper(), Looper.myLooper());
            Assert.assertEquals(e.getClass(), IOException.class);
            c.countDown();
        });

        c.await();
    }

    @Test
    public void testThreadMode5() {
        Valuable<String> v = Valuable.result("OK", Schedulers.main()).map(s -> {
            Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
            throw new IOException("OMG");
        });
        new Thread(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            v.cancel();
        }).start();
        try {
            v.get();
            throw new AssertionError();
        } catch (ValuableException e) {
            Assert.assertEquals(e.getCause().getMessage(), "OMG");
        }
    }

    @Test
    public void testThreadMode6() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Valuable.result(1, Schedulers.main())
                .map(i -> {
                    Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
                    return i + 1;
                })
                .map(i -> {
                    Assert.assertNotEquals(Looper.getMainLooper(), Looper.myLooper());
                    return i + 1;
                }, Schedulers.single())
                .map(i -> {
                    Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
                    return i + 1;
                }, Schedulers.main())
                .success(i -> {
                    Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
                    Assert.assertEquals(4L, i.longValue());
                    c.countDown();
                });
        c.await();
    }

    @Test
    public void testCombine1() {
        String res = Valuable.result("OK").combineWith(Valuable.result("NO"), p -> {
            Assert.assertEquals(p.first, "OK");
            Assert.assertEquals(p.second, "NO");
            return p.first + p.second;
        }, Schedulers.immediate()).get();
        Assert.assertEquals(res, "OKNO");
    }

    @Test
    public void testCombine2() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Valuable<String> ok = Valuable.call(() -> {
            Thread.sleep(2000);
            return "OK";
        });
        Valuable<Integer> i = Valuable.result(3);
        Valuable.combine(objects -> objects[0].toString() + objects[1], ok, i).success(s -> {
            Assert.assertEquals(s, "OK3");
            latch.countDown();
        });
        latch.await();
    }

    @Test
    public void testCancel1() throws InterruptedException {
        Valuable<String> s = Valuable.call(() -> {
            Thread.sleep(500);
            return "OK";
        });
        s.complete(t -> {
            throw new AssertionError();
        }, e -> {
            Assert.assertTrue(e instanceof ValuableException);
            Assert.assertEquals(e.getMessage(), "Canceled");
        });
        s.cancel();
        Thread.sleep(600);
    }

    @Test
    public void testCancel2() {
        Valuable<String> s = Valuable.call(() -> {
            Thread.sleep(500);
            return "OK";
        }).flatMap(o -> Valuable.call(() -> o));
        new Thread(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            s.cancel();
        }).start();
        try {
            s.get();
            throw new AssertionError();
        } catch (ValuableException e) {
            Assert.assertEquals(e.getMessage(), "Canceled");
        }
    }

    @Test
    public void testCancel3() {
        Valuable<String> s = Valuable.call(() -> {
            Thread.sleep(500);
            return "OK";
        });
        new Thread(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            s.cancel();
        }).start();
        try {
            s.flatMap(o -> Valuable.call(() -> o)).get();
            throw new AssertionError();
        } catch (ValuableException e) {
            e.printStackTrace();
            Assert.assertEquals(e.getMessage(), "Canceled");
        }
    }

    @Test
    public void testCancel4() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Valuable<String> s1 = Valuable.call(() -> {
            Thread.sleep(500);
            return "OK1";
        });
        Valuable<String> s2 = Valuable.call(() -> {
            Thread.sleep(400);
            return "OK2";
        });
        new Thread(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            s1.cancel();
        }).start();
        s1.combineWith(s2, p -> p.first + p.second, Schedulers.immediate())
                .catchError(s -> {
                    s.printStackTrace();
                    Assert.assertTrue(s instanceof ValuableException);
                    Assert.assertEquals(s.getMessage(), "Canceled");
                    c.countDown();
                });
        c.await();
    }

    @Test
    public void testError() {
        Valuable<String> v = Valuable.call(() -> "OK").flatMap(s -> Valuable.result(s + "3"))
                .map(s -> {
                    throw new IOException("OMG");
                });
        try {
            v.get();
            throw new AssertionError();
        } catch (ValuableException e) {
            Throwable t = e.getCause();
            Assert.assertEquals(t.getMessage(), "OMG");
            Assert.assertEquals(t.getClass(), IOException.class);
        }
    }

    @Test
    public void testFlatMap() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Valuable.result("OK").flatMap(f -> Valuable.call(() -> f + "OK"))
                .success(s -> {
                    Assert.assertEquals(s, "OKOK");
                    c.countDown();
                });
        c.await();
    }

    @Test
    public void testNull1() {
        Valuable<Object> v = Valuable.empty(Schedulers.immediate()).success(Assert::assertNull);
        v.notifyResult(null);
    }

    @Test
    public void testNull2() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(1);
        Valuable.supply(() -> "sss", Schedulers.io())
                .map(s -> null, Schedulers.main())
                .success(t -> {
                    Assert.assertNull(t);
                    Assert.assertEquals(Looper.getMainLooper(), Looper.myLooper());
                    c.countDown();
                });
        c.await();
    }

    @Test
    public void testScheduler1() {
        try {
            Valuable.empty(Schedulers.upstream());
            throw new AssertionError();
        } catch (IllegalArgumentException ignored) {
        }

        try {
            Valuable.empty(Schedulers.io()).combineWith(Valuable.empty(Schedulers.io()), objectObjectPair -> null, Schedulers.upstream());
            throw new AssertionError();
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testScheduler2() {
        Assert.assertEquals(Valuable.call(() -> "")
                .map(String::length, Schedulers.upstream()).scheduler(), Schedulers.io());
    }

    @Test
    public void testScheduler3() {
        Assert.assertEquals(Valuable.call(() -> "")
                .map(String::length, Schedulers.computation()).scheduler(), Schedulers.computation());
    }

    @Test
    public void testScheduler4() throws InterruptedException {
        Valuable[] v = new Valuable[1];
        Valuable t = Valuable.result("OK").flatMap(s -> {
            Thread.sleep(100);
            return v[0] = Valuable.result(s.length());
        }, Schedulers.io());

        Thread.sleep(10);
        t.cancel();

        while (v[0] == null) {
            Thread.sleep(10);
        }
        Thread.sleep(10);
        Assert.assertTrue(v[0].isCanceled());
    }
}
