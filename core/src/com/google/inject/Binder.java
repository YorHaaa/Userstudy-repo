package com.google.inject;

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Message;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/** 
 * <h3>The Guice Binding EDSL</h3>
 *
 * Guice uses an <i>embedded domain-specific language</i>, or EDSL, to help you create bindings
 * simply and readably.
 *
 * <pre>
 *     bind(ServiceImpl.class);</pre>
 *
 * This statement mostly does nothing; it "binds the {@code ServiceImpl} class to itself".
 *
 * <pre>
 *     bind(Service.class).to(ServiceImpl.class);</pre>
 *
 * Specifies that a request for a {@code Service} instance should be treated as a request for a
 * {@code ServiceImpl} instance. This <i>overrides</i> {@link ImplementedBy @ImplementedBy} or
 * {@link ProvidedBy @ProvidedBy} annotations on {@code Service}.
 *
 * <pre>
 *     bind(Service.class).toProvider(ServiceProvider.class);</pre>
 *
 * {@code ServiceProvider} must implement {@code Provider<Service>}. Guice resolves the provider,
 * then calls {@link Provider#get get()} to obtain the {@code Service}.
 *
 * <pre>
 *     bind(Service.class).annotatedWith(Red.class).to(ServiceImpl.class);</pre>
 *
 * Like the previous example, but applies to injection requests using the {@code @Red} annotation.
 *
 * <pre>
 *     bind(ServiceImpl.class).in(Singleton.class);
 *     bind(ServiceImpl.class).in(Scopes.SINGLETON);</pre>
 *
 * Places the {@code ServiceImpl} class into singleton scope.
 *
 * <p>Note: a scope specified this way <i>overrides</i> scope annotations on the class.
 *
 * <pre>{@code
 * bind(new TypeLiteral<PaymentService<CreditCard>>() {})
 *     .to(CreditCardPaymentService.class);
 * }</pre>
 *
 * Binds a parameterized type. Guice requires type parameters to be fully specified.
 *
 * <pre>
 *     bind(Service.class).toInstance(new ServiceImpl());
 *     // or, alternatively
 *     bind(Service.class).toInstance(SomeLegacyRegistry.getService());</pre>
 *
 * The module takes responsibility for providing the instance. Guice will inject fields and methods,
 * but ignores constructors.
 *
 * <pre>
 *     bindConstant().annotatedWith(ServerHost.class).to(args[0]);</pre>
 *
 * Sets up a constant binding. Constants must always be annotated.
 * 
 * <pre>
 *   {@literal @}Color("red") Color red; // A member variable (field)
 *    . . .
 *     red = MyModule.class.getDeclaredField("red").getAnnotation(Color.class);
 *     bind(Service.class).annotatedWith(red).to(RedService.class);</pre>
 *
 * If your binding annotation has parameters you can apply different bindings to different specific
 * values of your annotation. Getting your hands on the right instance of the annotation is a bit of
 * a pain -- one approach, shown above, is to apply a prototype annotation to a field in your module
 * class, so that you can read this annotation instance and give it to Guice.
 *
 * <pre>
 *     bind(Service.class)
 *         .annotatedWith(Names.named("blue"))
 *         .to(BlueService.class);</pre>
 *
 * {@link com.google.inject.name.Named @Named} is a standard annotation for named bindings.
 *
 * <pre>{@code
 * Constructor<T> loneCtor = getLoneCtorFromServiceImplViaReflection();
 * bind(ServiceImpl.class)
 *     .toConstructor(loneCtor);
 * }</pre>
 *
 * Binds a specific constructor. Useful when you can't use {@literal @}Inject.
 *
 * <p>The other methods of Binder such as {@link #bindInterceptor}, {@link #install}, {@link 
 * #requestStaticInjection} are not part of the Binding EDSL; you can learn how to use these in 
 * the usual way, from the method documentation.
 * 
 * Binder collects configuration information (primarily <i>bindings</i>) which will be used to create an
 * {@link Injector}. Guice provides this object to your application's {@link Module} implementors so
 * they may each contribute their own bindings and other registrations.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 * @author kevinb@google.com (Kevin Bourrillion)
 */

public interface Binder {

  /**
   * Binds method interceptor[s] to methods matched by class and method matchers. A method is
   * eligible for interception if:
   *
   * <ul>
   *   <li>Guice created the instance the method is on
   *   <li>Neither the enclosing type nor the method is final
   *   <li>And the method is package-private, protected, or public
   * </ul>
   * 
   * <p>Note: this API only works if {@code guice_bytecode_gen_option} is set to {@code ENABLED}.
   *
   * @param classMatcher matches classes the interceptor should apply to. For example: {@code
   *     only(Runnable.class)}.
   * @param methodMatcher matches methods the interceptor should apply to. For example: {@code
   *     annotatedWith(Transactional.class)}.
   * @param interceptors to bind. The interceptors are called in the order they are given.
   */
  void bindInterceptor(
      Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher,
      MethodInterceptor... interceptors);

  /** See the EDSL examples at {@link Binder}. */
  <T> LinkedBindingBuilder<T> bind(Key<T> key);

  /** See the EDSL examples at {@link Binder}. */
  <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral);

  /** See the EDSL examples at {@link Binder}. */
  <T> AnnotatedBindingBuilder<T> bind(Class<T> type);

  /** See the EDSL examples at {@link Binder}. */
  AnnotatedConstantBindingBuilder bindConstant();

  /**
   * Upon successful creation, the {@link Injector} will inject instance fields and methods of the
   * given object.
   * this method takes one single arugment to capture the full generic type information of the instance
   *
   * @param instance for which members will be injected
   * @since 2.0
   */
  <T> void requestInjection(TypeLiteral<T> type, T instance);

  /**
   * Upon successful creation, the {@link Injector} will inject instance fields and methods of the
   * given object.
   * this method takes one single arugment with less type info required which will be inferred from runtime
   *
   * @param instance for which members will be injected
   * @since 2.0
   */
  void requestInjection(Object instance);

  /**
   * Upon successful creation, the {@link Injector} will inject static fields and methods in the
   * given classes.
   * 
   * the implementaion should be refactored to use {@link TypeLiteral} in 5.0, so that it can handle generic types
   * kept for backward compatibility
   * 
   * @param types for which static members will be injected
   */
  void requestStaticInjection(Class<?>... types);

  /** Uses the given module to configure more bindings. */
  void install(Module module);

  /**
   * Returns the provider used to obtain instances for the given injection key. The returned
   * provider will not be valid until the {@link Injector} has been created. The provider will throw
   * an {@code IllegalStateException} if you try to use it beforehand.
      * 
   * @since 2.0
   */
  <T> Provider<T> getProvider(Key<T> key);

  /**
   * Returns the provider used to obtain instances for the given injection key. The returned
   * provider will be attached to the injection point and will follow the nullability specified in
   * the dependency. Additionally, the returned provider will not be valid until the {@link
   * Injector} has been created. The provider will throw an {@code IllegalStateException} if you try
   * to use it beforehand.
   * 
   * @param dependency the dependency to get a provider for
   * @param <T> the type of the dependency
   * @throws MessageException if the dependency cannot be resolved
   * @return a provider for the given dependency
   * @since 4.0
   */
  <T> Provider<T> getProvider(Dependency<T> dependency) throws ConfigurationException;

  /**
   * Returns the provider used to obtain instances for the given injection type. The returned
   * provider will not be valid until the {@link Injector} has been created. The provider will throw
   * an {@code IllegalStateException} if you try to use it beforehand.
   *
   * @since 2.0
   */
  <T> Provider<T> getProvider(Class<T> type);

  /**
   * Returns the members injector used to inject dependencies into methods and fields on instances
   * of the given type {@code T}. The returned members injector will not be valid until the main
   * {@link Injector} has been created. The members injector will throw an {@code
   * IllegalStateException} if you try to use it beforehand.
   *
   * @param typeLiteral type to get members injector for
   * @since 2.0
   */
  <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral);

  /**
   * Returns the members injector used to inject dependencies into methods and fields on instances
   * of the given type {@code T}. The returned members injector will not be valid until the main
   * {@link Injector} has been created. The members injector will throw an {@code
   * IllegalStateException} if you try to use it beforehand.
   * 
   * to be removed in 5.0, use {@link #getMembersInjector(TypeLiteral)} instead.
   *
   * @param type type to get members injector for
   * @since 2.0
   */
  <T> MembersInjector<T> getMembersInjector(Class<T> type);

  /**
   * Binds a type converter. The injector will use the given converter to convert string constants
   * to matching types as needed.
   *
   * @param typeMatcher matches types the converter can handle
   * @param converter converts values
   * @since 2.0
   */
  void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher, TypeConverter converter);

  /**
   * Returns a binder that uses {@code source} as the reference location for configuration errors.
   * This is typically a {@link StackTraceElement} for {@code .java} source but it could any binding
   * source, such as the path to a {@code .properties} file.
   *
   * @deprecated in 5.0, use {@link #withSource(Object)} instead.
   * 
   * @param source any object representing the source location and has a concise {@link
   *     Object#toString() toString()} value
   * @return a binder that shares its configuration with this binder
   * @since 2.0
   */
  Binder withSource(Object source);

  /**
   * Returns a binder that skips {@code classesToSkip} when identify the calling code. The caller's
   * {@link StackTraceElement} is used to locate the source of configuration errors.
   * 
   * Returns void when {@code classesToSkip} is empty. 
   *
   * @param classesToSkip library classes that create bindings on behalf of their clients.
   * @return void – this method performs configuration side effects but does not return a value. 
   * When {@code classesToSkip} is empty, no skipping behavior is applied and the method exits immediately.
   * @since 2.0
   */
  Binder skipSources(Class<?>... classesToSkip);

  /**
   * Creates a new private child environment for bindings and other configuration. The returned
   * binder can be used to add and configuration information in this environment. See {@link
   * PrivateModule} for details.
   * 
   * This is no longer used and only present for backward compatibility. 
   *
   * @return a binder that inherits configuration from this binder. Only exposed configuration on
   *     the returned binder will be visible to this binder.
   * @since 2.0
   */
  PrivateBinder newPrivateBinder();

  /**
   * Prevents Guice from injecting dependencies that form a cycle, unless broken by a {@link
   * Provider}. By default, circular dependencies are not disabled.
   *
   * <p>If a parent injector disables circular dependencies, then all child injectors (and private
   * modules within that injector) also disable circular dependencies. If a parent does not disable
   * circular dependencies, a child injector or private module may optionally declare itself as
   * disabling circular dependencies. If it does, the behavior is limited only to that child or any
   * grandchildren. No siblings of the child will disable circular dependencies.
   *
   * @since 3.0
   */
  void disableCircularProxies();

  /**
   * Requires that Guice finds an exactly matching binding annotation. This disables the error-prone
   * feature in Guice where it can substitute a binding for <code>{@literal @}Named Foo</code> when
   * attempting to inject <code>{@literal @}Named("foo") Foo</code>.
   *
   * @since 4.0
   */
  void requireExactBindingAnnotations();

  /**
   * Adds a scanner that will look in all installed modules for annotations the scanner can parse,
   * and binds them like {@literal @}Provides methods. Scanners apply to all modules installed in
   * the injector. Scanners installed in child injectors or private modules do not impact modules in
   * siblings or parents, however scanners installed in parents do apply to all child injectors and
   * private modules.
   *
   * @since 4.0
   */
  void scanModulesForAnnotatedMethods(ModuleAnnotatedMethodScanner scanner);
}