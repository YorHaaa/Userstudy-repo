package com.google.inject;

import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Element;

/**
 * A mapping from a {@link Key} to the strategy for getting instances of the type. This interface is
 * part of the introspection API and is intended primarily for use by tools.
 *
 * <p>Bindings are created in several ways:
 *
 * <ul>
 *   <li>Explicitly in a module, via {@code bind()} and {@code bindConstant()} statements:
 *       <pre>
 *     bind(Service.class).annotatedWith(Red.class).to(ServiceImpl.class);
 *     bindConstant().annotatedWith(ServerHost.class).to(args[0]);</pre>
 *   <li>Implicitly by the Injector by following a type's {@link ImplementedBy pointer} {@link
 *       ProvidedBy annotations} or by using its {@link Inject annotated} or default constructor.
 *   <li>By converting a bound instance to a different type.
 *   <li>For {@link Provider providers}, by delegating to the binding for the provided type.
 * </ul>
 *
 * <p>They exist on both modules and on injectors, and their behaviour is different for each:
 *
 * <ul>
 *   <li><strong>Module bindings</strong> are incomplete and cannot be used to provide instances.
 *       This is because the applicable scopes and interceptors may not be known until an injector
 *       is created. From a tool's perspective, module bindings are like the injector's source code.
 *       They can be inspected or rewritten, but this analysis must be done statically.
 *   <li><strong>Injector bindings</strong> are complete and valid and can be used to provide
 *       instances. From a tools' perspective, injector bindings are like reflection for an
 *       injector. They have full runtime information, including the complete graph of injections
 *       necessary to satisfy a binding.
 * </ul>
 *
 * @param <T> the bound type. The injected is always assignable to this type.
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public interface Binding<T> extends Element {

  /** Returns the key for this binding. */
  Key<T> getKey();

  /**
   * Returns the scoped provider guice uses to fulfill requests for this binding.
   *
   * @throws UnsupportedOperationException when invoked on a {@link Binding} created via {@link
   *     com.google.inject.spi.Elements#getElements}. This method is only supported on {@link
   *     Binding}s returned from an injector.
   */
  Provider<T> getProvider();

  /**
   * Accepts a target visitor. Invokes the visitor method specific to this binding's target.
   *
   * @param visitor to call back on
   * @since 2.0
   */
  <V> V acceptTargetVisitor(BindingTargetVisitor<? super T, V> visitor);

  /**
   * Accepts a scoping visitor. Invokes the visitor method specific to this binding's scoping.
   *
   * @param visitor to call back on
   * @since 2.0
   */
  <V> V acceptScopingVisitor(BindingScopingVisitor<V> visitor);
}
