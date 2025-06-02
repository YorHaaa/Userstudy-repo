package com.google.inject.internal;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

/**
 * An internal binding annotation applied to each element in a multibinding. All elements are
 * assigned a globally-unique id to allow different modules to contribute multibindings
 * independently.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
@Retention(RUNTIME)
@BindingAnnotation
@interface Element {

  enum Type {
    MAPBINDER,
    MULTIBINDER;
  }

  String setName();

  int uniqueId();

  Type type();

  String keyType();
}
