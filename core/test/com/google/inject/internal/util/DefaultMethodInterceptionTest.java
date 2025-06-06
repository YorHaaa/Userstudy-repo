package com.google.inject;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.google.inject.internal.InternalFlags;
import com.google.inject.matcher.Matchers;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for interception of default methods.
 *
 * @author cgdecker@google.com (Colin Decker)
 */
@RunWith(JUnit4.class)
public class DefaultMethodInterceptionTest {

  private static final AtomicInteger callCount = new AtomicInteger(0);
  private static final AtomicInteger interceptedCallCount = new AtomicInteger(0);

  // the interceptor's a lambda too
  private final MethodInterceptor interceptor =
      invocation -> {
        interceptedCallCount.incrementAndGet();
        return invocation.proceed();
      };

  @Before
  public void checkBytecodeGenIsEnabled() {
    assumeTrue(InternalFlags.isBytecodeGenEnabled());
  }

  @Before
  public void setUp() throws Exception {
    callCount.set(0);
    interceptedCallCount.set(0);
  }

  @Retention(RUNTIME)
  @Target({METHOD, TYPE})
  public @interface InterceptMe {}

  /** Interface with a default method annotated to be intercepted. */
  public interface Foo {
    @InterceptMe
    default String defaultMethod() {
      callCount.incrementAndGet();
      return "Foo";
    }
  }

  /** Foo implementation that does not override the default method. */
  public static class NonOverridingFoo implements Foo {
    public String methodCallingDefault() {
      return "NonOverriding-" + defaultMethod();
    }
  }

  @Test
  public void testInterceptedDefaultMethod() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindInterceptor(
                    Matchers.any(), Matchers.annotatedWith(InterceptMe.class), interceptor);
                bind(Foo.class).to(NonOverridingFoo.class);
              }
            });

    Foo foo = injector.getInstance(Foo.class);
    assertEquals("Foo", foo.defaultMethod());
    assertEquals(1, callCount.get());
    assertEquals(1, interceptedCallCount.get());
  }

  @Test
  public void testInterceptedDefaultMethod_calledByAnotherMethod() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindInterceptor(
                    Matchers.any(), Matchers.annotatedWith(InterceptMe.class), interceptor);
              }
            });

    NonOverridingFoo foo = injector.getInstance(NonOverridingFoo.class);
    assertEquals("NonOverriding-Foo", foo.methodCallingDefault());
    assertEquals(1, callCount.get());
    assertEquals(1, interceptedCallCount.get());
  }

  /** A base class defining a method with the same signature as Foo's default method. */
  public static class BaseClass {
    // the definition of this method on the class will win over the default method
    public String defaultMethod() {
      callCount.incrementAndGet();
      return "BaseClass";
    }
  }

  /** Foo implementation that should use superclass method rather than default method. */
  public static class InheritingFoo extends BaseClass implements Foo {}

  @Test
  public void testInterceptedDefaultMethod_whenParentClassDefinesNonInterceptedMethod() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindInterceptor(
                    Matchers.any(), Matchers.annotatedWith(InterceptMe.class), interceptor);
                bind(Foo.class).to(InheritingFoo.class);
              }
            });

    // the concrete implementation that wins is not annotated
    Foo foo = injector.getInstance(Foo.class);
    assertEquals("BaseClass", foo.defaultMethod());
    assertEquals(1, callCount.get());
    assertEquals(0, interceptedCallCount.get());
  }

  /**
   * A base class defining an intercepted method with the same signature as Foo's default method.
   */
  public static class BaseClass2 {
    // the definition of this method on the class will win over the default method
    @InterceptMe
    public String defaultMethod() {
      callCount.incrementAndGet();
      return "BaseClass2";
    }
  }

  /**
   * Foo implementation that should use intercepted superclass method rather than default method.
   */
  public static class InheritingFoo2 extends BaseClass2 implements Foo {}

  @Test
  public void testInterceptedDefaultMethod_whenParentClassDefinesInterceptedMethod() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindInterceptor(
                    Matchers.any(), Matchers.annotatedWith(InterceptMe.class), interceptor);
                bind(Foo.class).to(InheritingFoo2.class);
              }
            });

    // the concrete implementation that wins is not annotated
    Foo foo = injector.getInstance(Foo.class);
    assertEquals("BaseClass2", foo.defaultMethod());
    assertEquals(1, callCount.get());
    assertEquals(1, interceptedCallCount.get());
  }

  public interface Baz {
    default String doSomething() {
      return "Baz";
    }

    String doSomethingElse();
  }

  public static class BazImpl implements Baz {

    @Override
    public String doSomethingElse() {
      return "BazImpl";
    }
  }

  @Test
  public void testInterception_ofAllMethodsOnType() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindInterceptor(Matchers.subclassesOf(Baz.class), Matchers.any(), interceptor);
                bind(Baz.class).to(BazImpl.class);
              }
            });

    Baz baz = injector.getInstance(Baz.class);

    assertEquals("Baz", baz.doSomething());
    assertEquals("BazImpl", baz.doSomethingElse());

    assertEquals(2, interceptedCallCount.get());
  }

  @Test
  public void testInterception_ofAllMethodsOnType_interceptsInheritedDefaultMethod() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindInterceptor(Matchers.subclassesOf(BazImpl.class), Matchers.any(), interceptor);
                bind(Baz.class).to(BazImpl.class);
              }
            });

    Baz baz = injector.getInstance(Baz.class);

    assertEquals("Baz", baz.doSomething());
    assertEquals("BazImpl", baz.doSomethingElse());

    assertEquals(2, interceptedCallCount.get());
  }
}
