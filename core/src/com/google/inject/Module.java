package com.google.inject;

/**
 * A module contributes configuration information, typically interface bindings, which will be used
 * to create an {@link Injector}. A Guice-based application is ultimately composed of little more
 * than a set of {@code Module}s and some bootstrapping code.
 *
 * <p>Your Module classes can use a more streamlined syntax by extending {@link AbstractModule}
 * rather than implementing this interface directly.
 *
 * <p>In addition to the bindings configured via {@link #configure}, bindings will be created for
 * all methods annotated with {@literal @}{@link Provides}. Use scope and binding annotations on
 * these methods to configure the bindings.
 */
@FunctionalInterface
public interface Module {

  /**
   * Contributes bindings and other configurations for this module to {@code binder}.
   *
   * <p><strong>Do not invoke this method directly</strong> to install submodules. Instead use
   * {@link Binder#install(Module)}, which ensures that {@link Provides provider methods} are
   * discovered.
   */
  void configure(Binder binder);
}
