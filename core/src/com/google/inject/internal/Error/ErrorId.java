/** Enum used to identify a specific Guice error. */
public enum ErrorId {
  /** Multiple type converters found for the same target type. */
  AMBIGUOUS_TYPE_CONVERSION,

  /** AOP (Aspect-Oriented Programming) support is disabled, but interceptors were bound. */
  AOP_DISABLED,

  /** An @Inject annotation is required but not present. */
  AT_INJECT_REQUIRED,

  /** @Target annotation on binding annotation is missing PARAMETER, used incorrectly. */
  AT_TARGET_IS_MISSING_PARAMETER,

  /** A binding was already set previously and is being redefined. */
  BINDING_ALREADY_SET,

  /** Attempt to bind to a core Guice framework type, which is disallowed. */
  BINDING_TO_GUICE_TYPE,

  /** Binding to a Provider type is not allowed directly. */
  BINDING_TO_PROVIDER,

  /** Cannot create a proxy for the specified class. */
  CAN_NOT_PROXY_CLASS,

  /** A child injector attempted to set a binding that was already configured. */
  CHILD_BINDING_ALREADY_SET,

  /** Circular proxies are disabled but required for some binding. */
  CIRCULAR_PROXY_DISABLED,

  /** Constructor does not match the expected type definition. */
  CONSTRUCTOR_NOT_DEFINED_BY_TYPE,

  /** Type mismatch during conversion from string to target type. */
  CONVERSION_TYPE_ERROR,

  /** A type converter returned null during conversion. */
  CONVERTER_RETURNED_NULL,

  /** Multiple binding annotations found on a single member. */
  DUPLICATE_BINDING_ANNOTATIONS,

  /** A duplicate element was added to a multibinder or set binder. */
  DUPLICATE_ELEMENT,

  /** Duplicate keys were found in a MapBinder configuration. */
  DUPLICATE_MAP_KEY,

  /** A scope was bound multiple times for the same annotation. */
  DUPLICATE_SCOPES,

  /** Multiple scope annotations found on the same binding. */
  DUPLICATE_SCOPE_ANNOTATIONS,

  /** An error occurred while trying to enhance a class (e.g., with proxies). */
  ERROR_ENHANCING_CLASS,

  /** Error occurred during constructor injection. */
  ERROR_INJECTING_CONSTRUCTOR,

  /** Error occurred during method injection. */
  ERROR_INJECTING_METHOD,

  /** Error occurred inside a custom provider. */
  ERROR_IN_CUSTOM_PROVIDER,

  /** General error caused by user code. */
  ERROR_IN_USER_CODE,

  /** Error occurred in user-defined injector configuration. */
  ERROR_IN_USER_INJECTOR,

  /** Error while notifying a TypeListener during injection. */
  ERROR_NOTIFYING_TYPE_LISTENER,

  /** A type was exposed in a private module but not bound. */
  EXPOSED_BUT_NOT_BOUND,

  /** Attempted to inject into an abstract method, which is not supported. */
  INJECT_ABSTRACT_METHOD,

  /** Attempted to inject into a final field, which is not supported. */
  INJECT_FINAL_FIELD,

  /** Injection into inner classes is not allowed unless they are static. */
  INJECT_INNER_CLASS,

  /** Injection into local (non-top-level) classes is not supported. */
  INJECT_LOCAL_CLASS,

  /** Injected method declares type parameters, which is unsupported. */
  INJECT_METHOD_WITH_TYPE_PARAMETER,

  /** Raw MembersInjector used, which lacks type safety. */
  INJECT_RAW_MEMBERS_INJECTOR,

  /** Raw Provider used instead of typed provider. */
  INJECT_RAW_PROVIDER,

  /** Raw TypeLiteral used instead of a parameterized one. */
  INJECT_RAW_TYPE_LITERAL,

  /** A just-in-time binding was already set earlier. */
  JIT_BINDING_ALREADY_SET,

  /** Just-In-Time (JIT) bindings are disabled entirely. */
  JIT_DISABLED,

  /** JIT bindings are disabled in the parent injector. */
  JIT_DISABLED_IN_PARENT,

  /** The binding key was not fully specified (e.g., missing type parameter). */
  KEY_NOT_FULLY_SPECIFIED,

  /** Binding annotation is placed incorrectly (e.g., on method rather than parameter). */
  MISPLACED_BINDING_ANNOTATION,

  /** Constant value was expected but not provided in configuration. */
  MISSING_CONSTANT_VALUES,

  /** No suitable constructor was found for injection. */
  MISSING_CONSTRUCTOR,

  /** No implementation was bound for a requested interface or abstract class. */
  MISSING_IMPLEMENTATION,

  /** Custom annotation is missing @Retention(RUNTIME). */
  MISSING_RUNTIME_RETENTION,

  /** Scope annotation is missing on a scoped binding. */
  MISSING_SCOPE_ANNOTATION,

  /** Implementation type is not a subtype of the interface or superclass it's bound to. */
  NOT_A_SUBTYPE,

  /** A null element was added to a multibinder set. */
  NULL_ELEMENT_IN_SET,

  /** Null was injected into a non-nullable field or parameter. */
  NULL_INJECTED_INTO_NON_NULLABLE,

  /** A null value was added to a map binding. */
  NULL_VALUE_IN_MAP,

  /** Constructor marked @Inject(optional=true), which is unsupported. */
  OPTIONAL_CONSTRUCTOR,

  /** A binding was defined in terms of itself, leading to recursion. */
  RECURSIVE_BINDING,

  /** @ImplementedBy refers to the same class it annotates. */
  RECURSIVE_IMPLEMENTATION_TYPE,

  /** @ProvidedBy refers to the same class it annotates. */
  RECURSIVE_PROVIDER_TYPE,

  /** Requested injection types do not match in a single call. */
  REQUEST_INJECTION_WITH_DIFFERENT_TYPES,

  /** Scope annotation used on abstract type, which is not allowed. */
  SCOPE_ANNOTATION_ON_ABSTRACT_TYPE,

  /** No implementation found for a given scope annotation. */
  SCOPE_NOT_FOUND,

  /** Static injection requested on an interface, which is unsupported. */
  STATIC_INJECTION_ON_INTERFACE,

  /** @ImplementedBy or @ProvidedBy failed to provide a subtype. */
  SUBTYPE_NOT_PROVIDED,

  /** More than one constructor marked with @Inject, which is ambiguous. */
  TOO_MANY_CONSTRUCTORS,

  /** Provider method returns void, which is invalid. */
  VOID_PROVIDER_METHOD,

  /** All other internal or user errors not explicitly covered above. */
  OTHER;
}
// Note: This enum is used to categorize and identify specific errors that can occur in Guice.