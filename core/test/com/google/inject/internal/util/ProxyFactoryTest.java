package com.google.inject.internal;

import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.any;
import static com.google.inject.matcher.Matchers.not;
import static com.google.inject.matcher.Matchers.only;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.spi.InjectionPoint;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author crazybob@google.com (Bob Lee) */
@RunWith(JUnit4.class)
public class ProxyFactoryTest {

  List<MethodAspect> aspects = Lists.newArrayList();

  @Test
  public void testSimpleCase()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();
    InjectionPoint injectionPoint = InjectionPoint.forConstructorOf(Simple.class);

    aspects.add(new MethodAspect(any(), any(), interceptor));
    ProxyFactory<Simple> factory = new ProxyFactory<>(injectionPoint, aspects);

    ConstructionProxy<Simple> constructionProxy = factory.create();

    Simple simple = constructionProxy.newInstance();
    simple.invoke();
    assertTrue(simple.invoked);
    assertTrue(interceptor.invoked);
  }

  public static class Simple {
    boolean invoked = false;

    public void invoke() {
      invoked = true;
    }
  }

  static class SimpleInterceptor implements MethodInterceptor {

    boolean invoked = false;

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      invoked = true;
      return methodInvocation.proceed();
    }
  }

  @Test
  public void testInterceptOneMethod()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    aspects.add(new MethodAspect(only(Bar.class), annotatedWith(Intercept.class), interceptor));

    ConstructionProxy<Foo> fooFactory =
        new ProxyFactory<Foo>(InjectionPoint.forConstructorOf(Foo.class), aspects).create();
    ConstructionProxy<Bar> barFactory =
        new ProxyFactory<Bar>(InjectionPoint.forConstructorOf(Bar.class), aspects).create();

    Foo foo = fooFactory.newInstance();
    Bar bar = barFactory.newInstance();

    foo.foo();
    assertTrue(foo.fooCalled);
    assertFalse(interceptor.invoked);

    bar.bar();
    assertTrue(bar.barCalled);
    assertFalse(interceptor.invoked);

    bar.intercepted();
    assertTrue(bar.interceptedCalled);
    assertTrue(interceptor.invoked);
  }

  public static class Foo {
    boolean fooCalled;

    @Intercept
    protected void foo() {
      fooCalled = true;
    }
  }

  public static class Bar {

    boolean barCalled;

    protected void bar() {
      barCalled = true;
    }

    boolean interceptedCalled;

    @Intercept
    protected void intercepted() {
      interceptedCalled = true;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Intercept {}

  @Test
  public void testWithConstructorArguments()
      throws InvocationTargetException, NoSuchMethodException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    aspects.add(new MethodAspect(any(), any(), interceptor));
    ProxyFactory<A> factory =
        new ProxyFactory<A>(InjectionPoint.forConstructorOf(A.class), aspects);

    ConstructionProxy<A> constructor = factory.create();

    A a = constructor.newInstance(5);
    a.a();
    assertEquals(5, a.i);
  }

  @Test
  public void testNotProxied()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    aspects.add(new MethodAspect(not(any()), not(any()), interceptor));
    ProxyFactory<A> factory =
        new ProxyFactory<A>(InjectionPoint.forConstructorOf(A.class), aspects);

    ConstructionProxy<A> constructor = factory.create();

    A a = constructor.newInstance(5);
    assertEquals(A.class, a.getClass());
  }

  public static class A {
    final int i;

    @Inject
    public A(int i) {
      this.i = i;
    }

    public void a() {}
  }

  @Test
  public void testMultipleInterceptors()
      throws NoSuchMethodException, InvocationTargetException, ErrorsException {
    DoubleInterceptor doubleInterceptor = new DoubleInterceptor();
    CountingInterceptor countingInterceptor = new CountingInterceptor();

    aspects.add(new MethodAspect(any(), any(), doubleInterceptor, countingInterceptor));
    ProxyFactory<Counter> factory =
        new ProxyFactory<Counter>(InjectionPoint.forConstructorOf(Counter.class), aspects);

    ConstructionProxy<Counter> constructor = factory.create();

    Counter counter = constructor.newInstance();
    counter.inc();
    assertEquals(2, counter.count);
    assertEquals(2, countingInterceptor.count);
  }

  static class CountingInterceptor implements MethodInterceptor {

    int count;

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      count++;
      return methodInvocation.proceed();
    }
  }

  static class DoubleInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      methodInvocation.proceed();
      return methodInvocation.proceed();
    }
  }

  public static class Counter {
    int count;

    protected void inc() {
      count++;
    }
  }
}
