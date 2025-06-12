package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.RestrictedBindingSource;
import com.google.inject.RestrictedBindingSource.RestrictionLevel;
import com.google.inject.internal.Errors;
import com.google.inject.internal.GuiceInternal;
import java.util.regex.Pattern;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Contains abstractions for enforcing {@link RestrictedBindingSource}.
 *
 * <p>Enforcement happens in two phases:
 *
 * <ol>
 *   <li>Data structures for enforcement are built during Binder configuration. {@link
 *       PermitMapConstruction} encapsulates this process, and the {@link PermitMap} is the end
 *       result.
 *   <li>Restrictions are enforced by checking each binding for violations with {@link #check},
 *       which uses the {@link PermitMap}(s) built during Binder configuration.
 * </ol>
 *
 * <p>Note: None of this is thread-safe because it's only used while the Injector is being built,
 * which happens on a single thread.
 *
 * @author vzm@google.com (Vladimir Makaric)
 * @since 5.0
 */
public final class BindingSourceRestriction {
  private BindingSourceRestriction() {}

  private static final Logger logger = Logger.getLogger(RestrictedBindingSource.class.getName());

  /** Mapping between an element source and its permit annotations. */
  interface PermitMap {
    ImmutableSet<Class<? extends Annotation>> getPermits(ElementSource elementSource);

    void clear();
  }

/**
 * Returns a suggestion message to help developers understand why a certain binding key 
 * is restricted and cannot be created directly, and what steps might be needed to resolve it.
 * <p>
 * This method checks if the given {@code key} is associated with a {@link RestrictedBindingSource},
 * which indicates that the key cannot be bound directly (e.g., due to internal Guice restrictions).
 * If such a restriction exists, a user-friendly hint is returned with the explanation.
 * If no restriction is found, an empty {@link Optional} is returned.
 * 
 * legacy:</b> In v4.0 and later, prefer using {@link #createChildInjector(Iterable)} for\ncreating child injectors. This method is retained for compatibility, but it is recommended \nto use the iterable version for consistency and flexibility
 *
 * @param guiceInternal An internal Guice context object used to validate internal behavior. 
 *                      It must not be null. (Currently unused in this method but may be needed
 *                      for future logic or validation.)
 * @param key           The Guice {@link Key} for which the binding is being evaluated.
 * @return              An {@link Optional} containing a formatted suggestion message if the key
 *                      is restricted; otherwise, {@link Optional#empty()}.
 * @throws NullPointerException if {@code guiceInternal} is null.
 */
  public static Optional<String> getMissingImplementationSuggestion(
      GuiceInternal guiceInternal, Key<?> key) {
    checkNotNull(guiceInternal);
    RestrictedBindingSource restriction = getRestriction(key);
    if (restriction == null) {
      return Optional.empty();
    }
    return Optional.of(
        String.format(
            "\nHint: This key is restricted and cannot be bound directly. Restriction explanation:"
                + " %s",
            restriction.explanation()));
  }

  /**
   * Returns all the restriction violations found on the given Module Elements, as error messages.
   *
   * <p>Note: Intended to be used on Module Elements, not Injector Elements, ie. the result of
   * {@link Elements#getElements} not {@code Injector.getElements}. The Module Elements this check
   * cares about are:
   *
   * <ul>
   *   <li>Module Bindings, which are always explicit and always have an {@link ElementSource} (with
   *       a Module Stack), unlike Injector Bindings, which may be implicit and bereft of an
   *       ElementSource.
   *   <li>{@link PrivateElements}, which represent the recursive case of this check. They contain a
   *       list of elements that this check is recursively called on.
   * </ul>
   */
  public static ImmutableList<Message> check(GuiceInternal guiceInternal, List<Element> elements) {
    checkNotNull(guiceInternal);
    ImmutableList<Message> errorMessages = check(elements);
    // Clear all the permit maps after the checks are done.
    elements.forEach(BindingSourceRestriction::clear);
    return errorMessages;
  }

  /**
 * Performs validation checks on a list of {@link Element} objects and aggregates any resulting error messages.
 * <p>
 * This method iterates over each {@code Element} in the provided list and delegates the actual checking
 * to the overloaded {@code check(Element)} method. All messages returned from individual checks are collected
 * into a single immutable list.
 * <p>
 * Typically used during the binding or module validation phase in Guice, where each element
 * (e.g., bindings, provider methods, scopes) may be inspected for misconfiguration or misuse.
 *
 * @param elements A list of {@link Element} instances to be checked. These elements usually represent
 *                 components in a Guice module or injector configuration.
 * @return An {@link ImmutableList} of {@link Message} objects representing validation errors or warnings
 *         encountered during the check. If no errors are found, the returned list will be empty.
 */
  private static ImmutableList<Message> check(List<Element> elements) {
    ImmutableList.Builder<Message> errorMessagesBuilder = ImmutableList.builder();
    for (Element element : elements) {
      errorMessagesBuilder.addAll(check(element));
    }
    return errorMessagesBuilder.build();
  }

  private static ImmutableList<Message> check(Element element) {
    return element.acceptVisitor(
        new DefaultElementVisitor<ImmutableList<Message>>() {
          // Base case.
          @Override
          protected ImmutableList<Message> visitOther(Element element) {
            return ImmutableList.of();
          }

          // Base case.
          @Override
          public <T> ImmutableList<Message> visit(Binding<T> binding) {
            Optional<Message> errorMessage = check(binding);
            if (errorMessage.isPresent()) {
              return ImmutableList.of(errorMessage.get());
            }
            return ImmutableList.of();
          }

          // Recursive case.
          @Override
          public ImmutableList<Message> visit(PrivateElements privateElements) {
            return check(privateElements.getElements());
          }
        });
  }

  private static Optional<Message> check(Binding<?> binding) {
    Key<?> key = binding.getKey();
    // Module Bindings are all explicit and have an ElementSource.
    ElementSource elementSource = (ElementSource) binding.getSource();
    RestrictedBindingSource restriction = getRestriction(key);
    if (restriction == null) {
      return Optional.empty();
    }
    ImmutableSet<Class<? extends Annotation>> permits = getAllPermits(elementSource);
    ImmutableSet<Class<? extends Annotation>> acceptablePermits =
        ImmutableSet.copyOf(restriction.permits());
    boolean bindingPermitted = permits.stream().anyMatch(acceptablePermits::contains);
    if (bindingPermitted || isExempt(elementSource, restriction.exemptModules())) {
      return Optional.empty();
    }
    String violationMessage =
        getViolationMessage(
            key, restriction.explanation(), acceptablePermits, key.getAnnotationType() != null);
    if (restriction.restrictionLevel() == RestrictionLevel.WARNING) {
      Formatter sourceFormatter = new Formatter();
      Errors.formatSource(sourceFormatter, elementSource);
      logger.log(Level.WARNING, violationMessage + "\n" + sourceFormatter);
      return Optional.empty();
    }
    return Optional.of(new Message(elementSource, violationMessage));
  }

/**
 * Constructs a detailed error message describing why a given binding key violates restriction rules.
 * 
 * This method is used to report a violation when a key cannot be bound due to the presence of
 * {@code @RestrictedBindingSource} annotations on either its type or annotation.
 * It generates a message that explains what kind of permit annotations are required
 * on the module that attempts to bind the key.
 *
 * @param key                 The {@link Key} that failed to be bound due to restrictions.
 * @param explanation         A human-readable explanation describing why the restriction exists.
 * @param acceptablePermits   A set of annotation classes that are considered valid "permits" —
 *                            the module binding the key must be annotated with one of these to be allowed.
 * @param annotationRestricted A boolean indicating whether the restriction comes from the key's annotation
 *                             (true) or its type (false). This affects the wording in the message.
 * @return A formatted {@link String} describing the binding violation, including the key,
 *         required permits, the restricted target (annotation or type), and a developer-friendly explanation.
 */
  private static String getViolationMessage(
      Key<?> key,
      String explanation,
      ImmutableSet<Class<? extends Annotation>> acceptablePermits,
      boolean annotationRestricted) {
    return String.format(
        "Unable to bind key: %s. One of the modules that created this binding has to be annotated"
            + " with one of %s, because the key's %s is annotated with @RestrictedBindingSource."
            + " %s",
        key,
        acceptablePermits.stream().map(a -> "@" + a.getName()).collect(toList()),
        annotationRestricted ? "annotation" : "type",
        explanation);
  }

  /** Get all permits on the element source chain. */
  private static ImmutableSet<Class<? extends Annotation>> getAllPermits(
      ElementSource elementSource) {
    ImmutableSet.Builder<Class<? extends Annotation>> permitsBuilder = ImmutableSet.builder();
    permitsBuilder.addAll(elementSource.moduleSource.getPermitMap().getPermits(elementSource));
    if (elementSource.scanner != null) {
      getPermits(elementSource.scanner.getClass()).forEach(permitsBuilder::add);
    }
    if (elementSource.getOriginalElementSource() != null
        && elementSource.trustedOriginalElementSource) {
      permitsBuilder.addAll(getAllPermits(elementSource.getOriginalElementSource()));
    }
    return permitsBuilder.build();
  }

  private static boolean isExempt(ElementSource elementSource, String exemptModulesRegex) {
    if (exemptModulesRegex.isEmpty()) {
      return false;
    }
    Pattern exemptModulePattern = Pattern.compile(exemptModulesRegex);
    // TODO(b/156759807): Switch to Streams.stream (instead of inlining it).
    return StreamSupport.stream(getAllModules(elementSource).spliterator(), false)
        .anyMatch(moduleName -> exemptModulePattern.matcher(moduleName).matches());
  }

  private static Iterable<String> getAllModules(ElementSource elementSource) {
    List<String> modules = elementSource.getModuleClassNames();
    if (elementSource.getOriginalElementSource() == null
        || !elementSource.trustedOriginalElementSource) {
      return modules;
    }
    return Iterables.concat(modules, getAllModules(elementSource.getOriginalElementSource()));
  }

  // @Deprecated use {@link #check(Dependency)} instead.
  private static void clear(Element element) {
    element.acceptVisitor(
        new DefaultElementVisitor<Void>() {
          // Base case.
          @Override
          protected Void visitOther(Element element) {
            Object source = element.getSource();
            // Some Module Elements, like Message, don't always have an ElementSource.
            if (source instanceof ElementSource) {
              clear((ElementSource) source);
            }
            return null;
          }

          // Recursive case.
          @Override
          public Void visit(PrivateElements privateElements) {
            privateElements.getElements().forEach(BindingSourceRestriction::clear);
            return null;
          }
        });
  }

  /**
 * Clears the permit metadata associated with a given {@link ElementSource} and all of its ancestors.
 * 
 * <p>
 * This method recursively traverses the chain of {@code ElementSource} objects (via {@code getOriginalElementSource()})
 * and clears the permit map stored in each {@code ModuleSource}. The permit map typically contains annotations
 * that grant permission to bind restricted keys.
 * <p>
 * 
 * Usage:
 * <pre>{@code
 * // Suppose you have an ElementSource from a Guice element (e.g., a binding or module).
 * ElementSource source = ...;
 * 
 * // Clear all permit-related metadata up the source chain.
 * clear(source);
 * }</pre>
 * 
 * This is typically done to remove temporary or intermediate metadata that should not persist beyond
 * validation or transformation phases in Guice's internal pipeline.
 *
 * @param elementSource The starting {@link ElementSource} whose associated permit map should be cleared.
 *                      If {@code null}, the method exits silently.
 */
  private static void clear(ElementSource elementSource) {
    while (elementSource != null) {
      elementSource.moduleSource.getPermitMap().clear();
      elementSource = elementSource.getOriginalElementSource();
    }
  }

  /*
   * Returns the restriction on the given key (null if there is none).
   *
   * If the key is annotated then only the annotation restriction matters, the type restriction is
   * ignored (an annotated type is essentially a new type).
   **/
  private static RestrictedBindingSource getRestriction(Key<?> key) {
    return key.getAnnotationType() == null
        ? key.getTypeLiteral().getRawType().getAnnotation(RestrictedBindingSource.class)
        : key.getAnnotationType().getAnnotation(RestrictedBindingSource.class);
  }

  /**
   * Builds the map from each module to all the permit annotations on its module stack.
   *
   * <p>Bindings refer to the module that created them via a {@link ModuleSource}. The map built
   * here maps a module's {@link ModuleSource} to all the {@link RestrictedBindingSource.Permit}
   * annotations found on the path from the root of the module hierarchy to it. This path contains
   * all the modules that transitively install the module (including the module itself). This path
   * is also known as the module stack.
   *
   * <p>The map is built by piggybacking on the depth-first traversal of the module hierarchy during
   * Binder configuration.
   */
  static final class PermitMapConstruction {
    private static final class PermitMapImpl implements PermitMap {
      Map<ModuleSource, ImmutableSet<Class<? extends Annotation>>> modulePermits;

      @Override
      public ImmutableSet<Class<? extends Annotation>> getPermits(ElementSource elementSource) {
        return modulePermits.get(elementSource.moduleSource);
      }

      @Override
      public void clear() {
        modulePermits = null;
      }
    }

    final Map<ModuleSource, ImmutableSet<Class<? extends Annotation>>> modulePermits =
        new HashMap<>();
    // Maintains the permits on the current module installation path.
    ImmutableSet<Class<? extends Annotation>> currentModulePermits = ImmutableSet.of();
    // Stack tracking the currentModulePermits during module traversal.
    final Deque<ImmutableSet<Class<? extends Annotation>>> modulePermitsStack = new ArrayDeque<>();

    final PermitMapImpl permitMap = new PermitMapImpl();

    /**
     * Returns a possibly unfinished map. The map should only be used after the construction is
     * finished.
     */
    PermitMap getPermitMap() {
      return permitMap;
    }

    /**
     * Sets the permits on the current module installation path to the permits on the given module
     * source so that subsequently installed modules may inherit them. Used only for method
     * scanning, so that modules installed by scanners inherit permits from the method's module.
     */
    void restoreCurrentModulePermits(ModuleSource moduleSource) {
      currentModulePermits = modulePermits.get(moduleSource);
    }

    /**
 * Records and updates permit annotations when entering a new module during Guice configuration.
 * <p>
 * This method is called by the Guice {@code Binder} just before invoking a module's {@code configure()} method.
 * It inspects the module for any permit annotations (e.g., annotations that are allowed to bind restricted keys),
 * updates the current set of active permits, and stores this state for use during the configuration of the module.
 * <p>
 * The previously active permit set is saved on a stack so it can be restored later via {@code popModule()} 
 * after configuration exits this module.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // During Guice module traversal, when entering a new module:
 * pushModule(MyModule.class, new ModuleSource(...));
 * }</pre>
 *
 * <p>This mechanism ensures that permit annotations declared on parent modules are inherited by child modules,
 * and that module-specific permits are correctly scoped during the configuration process.
 *
 * @param module        The module class being entered.
 * @param moduleSource  The {@link ModuleSource} representing the source location or context of the module.
 *                      This is used as a key to associate the updated permit set with this module.
 */

    void pushModule(Class<?> module, ModuleSource moduleSource) {
      List<Class<? extends Annotation>> newModulePermits =
          getPermits(module)
              .filter(permit -> !currentModulePermits.contains(permit))
              .collect(toList());
      // Save the parent module's permits so that they can be restored when the Binder exits this
      // new (child) module's configure method.
      modulePermitsStack.push(currentModulePermits);
      if (!newModulePermits.isEmpty()) {
        currentModulePermits =
            ImmutableSet.<Class<? extends Annotation>>builder()
                .addAll(currentModulePermits)
                .addAll(newModulePermits)
                .build();
      }
      modulePermits.put(moduleSource, currentModulePermits);
    }

    /** Called by the Binder when it exits a module's configure method. */
    void popModule() {
      // Restore the parent module's permits.
      currentModulePermits = modulePermitsStack.pop();
    }

    /** Finishes the {@link PermitMap}. Called by the Binder when all modules are installed. */
    void finish() {
      permitMap.modulePermits = modulePermits;
    }

    @VisibleForTesting
    static boolean isElementSourceCleared(ElementSource elementSource) {
      PermitMapImpl permitMap = (PermitMapImpl) elementSource.moduleSource.getPermitMap();
      return permitMap.modulePermits == null;
    }
  }

  // @since 2.0 you can use isExempt(ElementSource elementSource) instead
  private static Stream<Class<? extends Annotation>> getPermits(Class<?> clazz) {
    Stream<Annotation> annotations = Arrays.stream(clazz.getAnnotations());
    // Pick up annotations on anonymous classes (e.g. new @Bar Foo() { ... }):
    if (clazz.getAnnotatedSuperclass() != null) {
      annotations =
          Stream.concat(
              annotations, Arrays.stream(clazz.getAnnotatedSuperclass().getAnnotations()));
    }
    @SuppressWarnings("unchecked") // force wildcard alignment
    Stream<Class<? extends Annotation>> permits =
        annotations
            .map(Annotation::annotationType)
            .filter(a -> a.isAnnotationPresent(RestrictedBindingSource.Permit.class));
    return permits;
  }
}
