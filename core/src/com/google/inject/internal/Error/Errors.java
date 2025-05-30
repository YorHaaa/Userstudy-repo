package com.google.inject.internal;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.SourceProvider;
import com.google.inject.spi.ElementSource;
import com.google.inject.spi.InterceptorBinding;
import com.google.inject.spi.Message;
import com.google.inject.spi.ScopeBinding;
import com.google.inject.spi.TypeConverterBinding;
import com.google.inject.spi.TypeListenerBinding;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A collection of error messages. If this type is passed as a method parameter, the method is
 * considered to have executed successfully only if new errors were not added to this collection.
 *
 * <p>Errors can be chained to provide additional context. To add context, call {@link #withSource}
 * to create a new Errors instance that contains additional context. All messages added to the
 * returned instance will contain full context.
 *
 * <p>To avoid messages with redundant context, {@link #withSource} should be added sparingly. A
 * good rule of thumb is to assume a method's caller has already specified enough context to
 * identify that method. When calling a method that's defined in a different context, call that
 * method with an errors object that includes its context.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class Errors implements Serializable {


  /**
   * Throws a ConfigurationException with an NullPointerExceptions as the cause if the given
   * reference is {@code null}.
   */
  static <T> T checkNotNull(T reference, String name) {
    if (reference != null) {
      return reference;
    }

    NullPointerException npe = new NullPointerException(name);
    throw new ConfigurationException(ImmutableSet.of(new Message(npe.toString(), npe)));
  }

  /**
   * Throws a ConfigurationException with a formatted {@link Message} if this condition is {@code
   * false}.
   */
  static void checkConfiguration(boolean condition, String format, Object... args) {
    if (condition) {
      return;
    }

    throw new ConfigurationException(ImmutableSet.of(new Message(Errors.format(format, args))));
  }

  /** The root errors object. Used to access the list of error messages. */
  private final Errors root;

  /** The parent errors object. Used to obtain the chain of source objects. */
  private final Errors parent;

  /** The leaf source for errors added here. */
  private final Object source;

  /** null unless (root == this) and error messages exist. Never an empty list. */
  private List<Message> errors; // lazy, use getErrorsForAdd()

  public Errors() {
    this.root = this;
    this.parent = null;
    this.source = SourceProvider.UNKNOWN_SOURCE;
  }

  public Errors(Object source) {
    this.root = this;
    this.parent = null;
    this.source = source;
  }

  private Errors(Errors parent, Object source) {
    this.root = parent.root;
    this.parent = parent;
    this.source = source;
  }

  /** Returns an instance that uses {@code source} as a reference point for newly added errors. */
  public Errors withSource(Object source) {
    return source == this.source || source == SourceProvider.UNKNOWN_SOURCE
        ? this
        : new Errors(this, source);
  }

  public Errors aopDisabled(InterceptorBinding binding) {
    return addMessage(
      /** AOP (Aspect-Oriented Programming) support is disabled, but interceptors were bound. */
        ErrorId.AOP_DISABLED,
        "Binding interceptor is not supported when bytecode generation is disabled. \nInterceptor"
            + " bound at: %s",
        binding.getSource());
  }

  /**
   * We use a fairly generic error message here. The motivation is to share the same message for
   * both bind time errors:
   *
   * <pre><code>Guice.createInjector(new AbstractModule() {
   *   public void configure() {
   *     bind(Runnable.class);
   *   }
   * }</code></pre>
   *
   * ...and at provide-time errors:
   *
   * <pre><code>Guice.createInjector().getInstance(Runnable.class);</code></pre>
   *
   * Otherwise we need to know who's calling when resolving a just-in-time binding, which makes
   * things unnecessarily complex.
   */
  public Errors missingImplementation(Key<?> key) {
    /**
     * No implementation for %s was bound. If you want to bind it, use
     * {@code bind(%s.class).to(...)} or {@code bind(%s.class).toProvider(...)}.
     */
    return addMessage(ErrorId.MISSING_IMPLEMENTATION, "No implementation for %s was bound.", key);
  }

  /** Within guice's core, allow for better missing binding messages */
  <T> Errors missingImplementationWithHint(Key<T> key, Injector injector) {
    MissingImplementationError<T> error =
        new MissingImplementationError<T>(key, injector, getSources());
      return addMessage(
          /** No implementation for %s was bound. */
          new Message(GuiceInternal.GUICE_INTERNAL, ErrorId.MISSING_IMPLEMENTATION, error));
  }

  public Errors jitDisabled(Key<?> key) {
    return addMessage(
        /** Just-in-time (JIT) binding is disabled, but an explicit binding is required. */
        ErrorId.JIT_DISABLED,
        "Explicit bindings are required and %s is not explicitly bound.",
        key);
  }

  public Errors jitDisabledInParent(Key<?> key) {
    return addMessage(
        /** Just-in-time (JIT) binding is disabled in the parent injector. */
        ErrorId.JIT_DISABLED_IN_PARENT,
        "Explicit bindings are required and %s would be bound in a parent injector.\n"
            + "Please add an explicit binding for it, either in the child or the parent.",
        key);
  }

  public Errors atInjectRequired(TypeLiteral<?> type) {
    return addMessage(
        new Message(
            /** A type requires an @Inject annotation on its constructor. */
            GuiceInternal.GUICE_INTERNAL,
            ErrorId.MISSING_CONSTRUCTOR,
            new MissingConstructorError(type, /* atInjectRequired= */ true, getSources())));
  }

  public Errors converterReturnedNull(
      String stringValue,
      Object source,
      TypeLiteral<?> type,
      TypeConverterBinding typeConverterBinding) {
    return addMessage(
        /** A type converter returned null during conversion. */
        ErrorId.CONVERTER_RETURNED_NULL,
        "Received null converting '%s' (bound at %s) to %s\n using %s.",
        stringValue,
        convert(source),
        type,
        typeConverterBinding);
  }

  public Errors conversionTypeError(
      String stringValue,
      Object source,
      TypeLiteral<?> type,
      TypeConverterBinding typeConverterBinding,
      Object converted) {
    return addMessage(
        /** Type mismatch during conversion from string to target type. */
        ErrorId.CONVERSION_TYPE_ERROR,
        "Type mismatch converting '%s' (bound at %s) to %s\n"
            + " using %s.\n"
            + " Converter returned %s.",
        stringValue,
        convert(source),
        type,
        typeConverterBinding,
        converted);
  }

  public Errors conversionError(
      String stringValue,
      Object source,
      TypeLiteral<?> type,
      TypeConverterBinding typeConverterBinding,
      RuntimeException cause) {
    return errorInUserCode(
        cause,
        "Error converting '%s' (bound at %s) to %s\n using %s.\n Reason: %s",
        stringValue,
        convert(source),
        type,
        typeConverterBinding,
        cause);
  }

  public Errors ambiguousTypeConversion(
      String stringValue,
      Object source,
      TypeLiteral<?> type,
      TypeConverterBinding a,
      TypeConverterBinding b) {
    return addMessage(
        /** Multiple type converters found for the same target type. */
        ErrorId.AMBIGUOUS_TYPE_CONVERSION,
        "Multiple converters can convert '%s' (bound at %s) to %s:\n"
            + " %s and\n"
            + " %s.\n"
            + " Please adjust your type converter configuration to avoid overlapping matches.",
        stringValue,
        convert(source),
        type,
        a,
        b);
  }

  public Errors bindingToProvider() {
    /** Attempt to bind to a Provider type, which is not allowed directly. */
    return addMessage(ErrorId.BINDING_TO_PROVIDER, "Binding to Provider is not allowed.");
  }

  public Errors notASubtype(Class<?> implementationType, Class<?> type) {
    /** Attempt to bind a type that does not extend the expected type. */
    return addMessage(ErrorId.NOT_A_SUBTYPE, "%s doesn't extend %s.", implementationType, type);
  }

  public Errors recursiveImplementationType() {
    return addMessage(
        /** @ImplementedBy points to the same class it annotates. */
        ErrorId.RECURSIVE_IMPLEMENTATION_TYPE,
        "@ImplementedBy points to the same class it annotates.");
  }

  public Errors recursiveProviderType() {
    return addMessage(
        /** @ProvidedBy points to the same class it annotates. */
        ErrorId.RECURSIVE_PROVIDER_TYPE, "@ProvidedBy points to the same class it annotates.");
  }

  public Errors missingRuntimeRetention(Class<? extends Annotation> annotation) {
    return addMessage(
        /** The annotation is missing @Retention(RUNTIME), which is required for Guice to use it. */
        ErrorId.MISSING_RUNTIME_RETENTION,
        format("Please annotate %s with @Retention(RUNTIME).", annotation));
  }

  public Errors missingScopeAnnotation(Class<? extends Annotation> annotation) {
    return addMessage(
        /** The annotation is missing @ScopeAnnotation, which is required for Guice to use it. */
        ErrorId.MISSING_SCOPE_ANNOTATION,
        format("Please annotate %s with @ScopeAnnotation.", annotation));
  }

  public Errors optionalConstructor(Constructor<?> constructor) {
    return addMessage(
        /** Attempt to use an optional constructor, which is not allowed. */
        ErrorId.OPTIONAL_CONSTRUCTOR,
        "%s is annotated @Inject(optional=true), but constructors cannot be optional.",
        constructor);
  }

  public Errors cannotBindToGuiceType(String simpleName) {
    return addMessage(
        /** Attempt to bind to a core Guice framework type, which is disallowed. */
        ErrorId.BINDING_TO_GUICE_TYPE,
        "Binding to core guice framework type is not allowed: %s.",
        simpleName);
  }

  public Errors scopeNotFound(Class<? extends Annotation> scopeAnnotation) {
    return addMessage(
        new Message(
            /** No scope was found for the given annotation. */
            GuiceInternal.GUICE_INTERNAL,
            ErrorId.SCOPE_NOT_FOUND,
            new ScopeNotFoundError(scopeAnnotation, getSources())));
  }

  public Errors scopeAnnotationOnAbstractType(
      Class<? extends Annotation> scopeAnnotation, Class<?> type, Object source) {
    return addMessage(
        /** Scope annotation used on abstract type, which is not allowed. */
        ErrorId.SCOPE_ANNOTATION_ON_ABSTRACT_TYPE,
        "%s is annotated with %s, but scope annotations are not supported "
            + "for abstract types.\n Bound at %s.",
        type,
        scopeAnnotation,
        convert(source));
  }

  public Errors misplacedBindingAnnotation(Member member, Annotation bindingAnnotation) {
    return addMessage(
        /** Binding annotation is placed incorrectly (e.g., on method rather than parameter). */
        ErrorId.MISPLACED_BINDING_ANNOTATION,
        "%s is annotated with %s, but binding annotations should be applied "
            + "to its parameters instead.",
        member,
        bindingAnnotation);
  }

  // TODO(diamondm) don't mention zero-arg constructors if requireAtInjectOnConstructors is true
  private static final String CONSTRUCTOR_RULES =
      "Injectable classes must have either one (and only one) constructor annotated with @Inject"
          + " or a zero-argument constructor that is not private.";

  public Errors missingConstructor(TypeLiteral<?> type) {
    return addMessage(
        new Message(
            /** A type requires a constructor annotated with @Inject. */
            GuiceInternal.GUICE_INTERNAL,
            ErrorId.MISSING_CONSTRUCTOR,
            new MissingConstructorError(type, /* atInjectRequired= */ false, getSources())));
  }

  public Errors tooManyConstructors(Class<?> implementation) {
    return addMessage(
        /** More than one constructor annotated with @Inject, which is ambiguous. */
        ErrorId.TOO_MANY_CONSTRUCTORS,
        "%s has more than one constructor annotated with @Inject. %s",
        implementation,
        CONSTRUCTOR_RULES);
  }

  public Errors constructorNotDefinedByType(Constructor<?> constructor, TypeLiteral<?> type) {
    return addMessage(
        /** Constructor does not match the expected type definition. */
        ErrorId.CONSTRUCTOR_NOT_DEFINED_BY_TYPE, "%s does not define %s", type, constructor);
  }

  public <K, V> Errors duplicateMapKey(Key<Map<K, V>> mapKey, Multimap<K, Binding<V>> duplicates) {
    return addMessage(
        new Message(
            GuiceInternal.GUICE_INTERNAL,
            /** Duplicate keys were found in a MapBinder configuration. */
            ErrorId.DUPLICATE_MAP_KEY,
            new DuplicateMapKeyError<K, V>(mapKey, duplicates, getSources())));
  }

  public Errors duplicateScopes(
      ScopeBinding existing, Class<? extends Annotation> annotationType, Scope scope) {
    return addMessage(
        /** A scope was bound multiple times for the same annotation. */
        ErrorId.DUPLICATE_SCOPES,
        "Scope %s is already bound to %s at %s.\n Cannot bind %s.",
        existing.getScope(),
        annotationType,
        existing.getSource(),
        scope);
  }

  public Errors voidProviderMethod() {
    return addMessage(
        /** Provider methods must return a value, not void. */
        ErrorId.VOID_PROVIDER_METHOD, "Provider methods must return a value. Do not return void.");
  }

  public Errors missingConstantValues() {
    return addMessage(
        /** Attempted to bind a constant without providing its value. */
        ErrorId.MISSING_CONSTANT_VALUES, "Missing constant value. Please call to(...).");
  }

  public Errors cannotInjectInnerClass(Class<?> type) {
    return addMessage(
        /** Injection into inner classes is not allowed unless they are static. */
        ErrorId.INJECT_INNER_CLASS,
        "Injecting into inner classes is not supported.  "
            + "Please use a 'static' class (top-level or nested) instead of %s.",
        type);
  }

  public Errors cannotInjectLocalClass(Class<?> type) {
    return addMessage(
        /** Injection into local (non-top-level) classes is not supported. */
        ErrorId.INJECT_LOCAL_CLASS,
        "Injecting into local classes is not supported.  "
            + "Please use a non-local class instead of %s.",
        type);
  }

  public Errors duplicateBindingAnnotations(
      Member member, Class<? extends Annotation> a, Class<? extends Annotation> b) {
    return addMessage(
        /** Multiple binding annotations found on a single member. */
        ErrorId.DUPLICATE_BINDING_ANNOTATIONS,
        "%s has more than one annotation annotated with @BindingAnnotation: %s and %s",
        member,
        a,
        b);
  }

  public Errors staticInjectionOnInterface(Class<?> clazz) {
    return addMessage(
        /** Attempted to inject into an interface, which is not allowed. */
        ErrorId.STATIC_INJECTION_ON_INTERFACE,
        "%s is an interface, but interfaces have no static injection points.",
        clazz);
  }

  public Errors cannotInjectFinalField(Field field) {
    /** Attempted to inject into a final field, which is not supported. */
    return addMessage(ErrorId.INJECT_FINAL_FIELD, "Injected field %s cannot be final.", field);
  }

  public Errors atTargetIsMissingParameter(
      Annotation bindingAnnotation, String parameterName, Class<?> clazz) {
    return addMessage(
      /** @Target annotation on binding annotation is missing PARAMETER, used incorrectly. */
        ErrorId.AT_TARGET_IS_MISSING_PARAMETER,
        "Binding annotation %s must have PARAMETER listed in its @Targets. It was used on"
            + " constructor parameter %s in %s.",
        bindingAnnotation,
        parameterName,
        clazz);
  }

  public Errors cannotInjectAbstractMethod(Method method) {
    return addMessage(
        /** Attempted to inject into an abstract method, which is not supported. */
        ErrorId.INJECT_ABSTRACT_METHOD, "Injected method %s cannot be abstract.", method);
  }

  public Errors cannotInjectMethodWithTypeParameters(Method method) {
    return addMessage(
        /** Injected method declares type parameters, which is unsupported. */
        ErrorId.INJECT_METHOD_WITH_TYPE_PARAMETER,
        "Injected method %s cannot declare type parameters of its own.",
        method);
  }

  public Errors duplicateScopeAnnotations(
      Class<? extends Annotation> a, Class<? extends Annotation> b) {
    return addMessage(
        /** Multiple scope annotations found on the same binding. */
        ErrorId.DUPLICATE_SCOPE_ANNOTATIONS,
        "More than one scope annotation was found: %s and %s.",
        a,
        b);
  }

  public Errors recursiveBinding(Key<?> key, Key<?> linkedKey) {
    return addMessage(
        /** Binding points to itself, which is not allowed. */
        ErrorId.RECURSIVE_BINDING, "Binding points to itself. Key: %s", Messages.convert(key));
  }

  Errors bindingAlreadySet(Binding<?> binding, Binding<?> original) {
    BindingAlreadySetError error = new BindingAlreadySetError(binding, original, getSources());
    return addMessage(
        /** A binding was already set previously and is being redefined. */
        new Message(GuiceInternal.GUICE_INTERNAL, ErrorId.BINDING_ALREADY_SET, error));
  }

  public Errors bindingAlreadySet(Key<?> key, Object source) {
    return addMessage(
        /** A binding was already set previously and is being redefined. */
        ErrorId.BINDING_ALREADY_SET,
        "A binding to %s was already configured at %s.",
        key,
        convert(source));
  }

  public Errors jitBindingAlreadySet(Key<?> key) {
    return addMessage(
        /** A binding was already set previously and is being redefined. */
        ErrorId.JIT_BINDING_ALREADY_SET,
        "A just-in-time binding to %s was already configured on a parent injector.",
        key);
  }

  public Errors childBindingAlreadySet(Key<?> key, Set<Object> sources) {
    Message message =
        new Message(
            /** A binding was already set previously and is being redefined. */
            GuiceInternal.GUICE_INTERNAL,
            /** A child injector attempted to set a binding that was already configured. */
            ErrorId.CHILD_BINDING_ALREADY_SET,
            new ChildBindingAlreadySetError(key, sources, getSources()));
      return addMessage(message);
  }

  public Errors errorCheckingDuplicateBinding(Key<?> key, Object source, Throwable t) {
    return addMessage(
        /** An error occurred while checking for duplicate bindings. */
        ErrorId.OTHER,
        "A binding to %s was already configured at %s and an error was thrown "
            + "while checking duplicate bindings.  Error: %s",
        key,
        convert(source),
        t);
  }

  public Errors requestInjectionWithDifferentTypes(
      Object instance, TypeLiteral<?> type1, Object type1Source, TypeLiteral<?> type2) {
    return addMessage(
        /** Cannot request injection on one instance with two different types. */
        ErrorId.REQUEST_INJECTION_WITH_DIFFERENT_TYPES,
        "Cannot request injection on one instance with two different types. requestInjection was"
            + " already called for instance %s at %s (with type %s), which is different than type"
            + " %s.",
        instance.getClass().getName() + "@" + System.identityHashCode(instance),
        type1Source,
        type1,
        type2);
  }

  public Errors errorNotifyingTypeListener(
      TypeListenerBinding listener, TypeLiteral<?> type, Throwable cause) {
    return errorInUserCode(
        cause,
        "Error notifying TypeListener %s (bound at %s) of %s.\n Reason: %s",
        listener.getListener(),
        convert(listener.getSource()),
        type,
        cause);
  }

  public Errors exposedButNotBound(Key<?> key) {
    return addMessage(
        /** Could not expose() %s, it must be explicitly bound. */
        ErrorId.EXPOSED_BUT_NOT_BOUND, "Could not expose() %s, it must be explicitly bound.", key);
  }

  public Errors keyNotFullySpecified(TypeLiteral<?> typeLiteral) {
    return addMessage(
        /** The binding key was not fully specified (e.g., missing type parameter). */
        ErrorId.KEY_NOT_FULLY_SPECIFIED,
        "%s cannot be used as a key; It is not fully specified.",
        typeLiteral);
  }

  public Errors errorEnhancingClass(Class<?> clazz, Throwable cause) {
    return errorInUserCode(cause, "Unable to method intercept: %s", clazz);
  }

  public static Collection<Message> getMessagesFromThrowable(Throwable throwable) {
    if (throwable instanceof ProvisionException) {
      return ((ProvisionException) throwable).getErrorMessages();
    } else if (throwable instanceof ConfigurationException) {
      return ((ConfigurationException) throwable).getErrorMessages();
    } else if (throwable instanceof CreationException) {
      return ((CreationException) throwable).getErrorMessages();
    } else {
      return ImmutableSet.of();
    }
  }

  public Errors errorInUserCode(Throwable cause, String messageFormat, Object... arguments) {
    Collection<Message> messages = getMessagesFromThrowable(cause);

    if (!messages.isEmpty()) {
      return merge(messages);
    } else {
      /** General error caused by user code. */
      return addMessage(ErrorId.ERROR_IN_USER_CODE, cause, messageFormat, arguments);
    }
  }

  public Errors cannotInjectRawProvider() {
    return addMessage(
        /** Raw Provider used instead of typed provider. */
        ErrorId.INJECT_RAW_PROVIDER, "Cannot inject a Provider that has no type parameter");
  }

  public Errors cannotInjectRawMembersInjector() {
    return addMessage(
        /** Raw MembersInjector used, which lacks type safety. */  
        ErrorId.INJECT_RAW_MEMBERS_INJECTOR,
        "Cannot inject a MembersInjector that has no type parameter");
  }

  public Errors cannotInjectTypeLiteralOf(Type unsupportedType) {
    return addMessage(ErrorId.OTHER, "Cannot inject a TypeLiteral of %s", unsupportedType);
  }

  public Errors cannotInjectRawTypeLiteral() {
    return addMessage(
        /** Raw TypeLiteral used instead of a parameterized one. */
        ErrorId.INJECT_RAW_TYPE_LITERAL, "Cannot inject a TypeLiteral that has no type parameter");
  }

  public void throwCreationExceptionIfErrorsExist() {
    if (!hasErrors()) {
      return;
    }

    CreationException exception = new CreationException(getMessages());
    throw exception;
  }

  public void throwConfigurationExceptionIfErrorsExist() {
    if (!hasErrors()) {
      return;
    }

    ConfigurationException exception = new ConfigurationException(getMessages());
    throw exception;
  }

  // Guice no longer calls this, but external callers do
  public void throwProvisionExceptionIfErrorsExist() {
    if (!hasErrors()) {
      return;
    }
    ProvisionException exception = new ProvisionException(getMessages());
    throw exception;
  }

  public Errors merge(Collection<Message> messages) {
    List<Object> sources = getSources();
    for (Message message : messages) {
      addMessage(Messages.mergeSources(sources, message));
    }
    return this;
  }

  public Errors merge(Errors moreErrors) {
    if (moreErrors.root == root || moreErrors.root.errors == null) {
      return this;
    }

    merge(moreErrors.root.errors);
    return this;
  }

  public Errors merge(InternalProvisionException ipe) {
    merge(ipe.getErrors());
    return this;
  }

  private List<Object> getSources() {
    List<Object> sources = Lists.newArrayList();
    for (Errors e = this; e != null; e = e.parent) {
      if (e.source != SourceProvider.UNKNOWN_SOURCE) {
        sources.add(0, e.source);
      }
    }
    return sources;
  }

  public void throwIfNewErrors(int expectedSize) throws ErrorsException {
    if (size() == expectedSize) {
      return;
    }

    throw toException();
  }

  public ErrorsException toException() {
    return new ErrorsException(this);
  }

  public boolean hasErrors() {
    return root.errors != null;
  }

  public Errors addMessage(String messageFormat, Object... arguments) {
    // If the messageFormat is null, we still want to add a message, so we use ErrorId.OTHER.
    return addMessage(ErrorId.OTHER, null, messageFormat, arguments);
  }

  public Errors addMessage(ErrorId errorId, String messageFormat, Object... arguments) {
    return addMessage(errorId, null, messageFormat, arguments);
  }

  private Errors addMessage(
      ErrorId errorId, Throwable cause, String messageFormat, Object... arguments) {
    addMessage(Messages.create(errorId, cause, getSources(), messageFormat, arguments));
    return this;
  }

  public Errors addMessage(Message message) {
    if (root.errors == null) {
      root.errors = Lists.newArrayList();
    }
    root.errors.add(message);
    return this;
  }

  // TODO(lukes): inline into callers
  public static String format(String messageFormat, Object... arguments) {
    return Messages.format(messageFormat, arguments);
  }

  public List<Message> getMessages() {
    if (root.errors == null) {
      return ImmutableList.of();
    }

    return new Ordering<Message>() {
      @Override
      public int compare(Message a, Message b) {
        return a.getSource().compareTo(b.getSource());
      }
    }.sortedCopy(root.errors);
  }

  public int size() {
    return root.errors == null ? 0 : root.errors.size();
  }

  // TODO(lukes): inline in callers.  There are some callers outside of guice, so this is difficult
  public static Object convert(Object o) {
    return Messages.convert(o);
  }

  // TODO(lukes): inline in callers.  There are some callers outside of guice, so this is difficult
  public static Object convert(Object o, ElementSource source) {
    return Messages.convert(o, source);
  }

  // TODO(lukes): inline in callers.  There are some callers outside of guice, so this is difficult
  public static void formatSource(Formatter formatter, Object source) {
    formatter.format("  ");
    new SourceFormatter(source, formatter, false).format();
  }
}
