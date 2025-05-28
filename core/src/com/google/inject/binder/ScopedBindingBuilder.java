package com.google.inject.binder;

import com.google.inject.Scope;
import java.lang.annotation.Annotation;

/**
 * See the EDSL examples at {@link com.google.inject.Binder}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface ScopedBindingBuilder {

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  void in(Class<? extends Annotation> scopeAnnotation);

  /** See the EDSL examples at {@link com.google.inject.Binder}. */
  void in(Scope scope);

  /**
   * Instructs the {@link com.google.inject.Injector} to eagerly initialize this singleton-scoped
   * binding upon creation. Useful for application initialization logic. See the EDSL examples at
   * {@link com.google.inject.Binder}.
   */
  void asEagerSingleton();
}
