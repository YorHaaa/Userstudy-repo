package com.google.inject.internal;

import static java.util.stream.Collectors.joining;

import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.internal.util.Classes;
import com.google.inject.spi.ElementSource;
import com.google.inject.spi.ErrorDetail;
import com.google.inject.spi.Message;
import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Utility methods for {@link Message} objects 
 * 
 * @author crazybob@google.com (Bob Lee)
 * 
*/
public final class Messages {
  private Messages() {}
  /** Prepends the list of sources to the given {@link Message} */
  static Message mergeSources(List<Object> sources, Message message) {
    List<Object> messageSources = message.getSources();
    if (!sources.isEmpty()
        && !messageSources.isEmpty()
        && Objects.equal(messageSources.get(0), sources.get(sources.size() - 1))) {
        // Avoid duplicate source if the end of `sources` and the beginning of `messageSources` overlap.
        // This can happen due to how errors are merged by InternalFactoryToProviderAdapter.  
      messageSources = messageSources.subList(1, messageSources.size());
    }
    return message.withSource(
        ImmutableList.builder().addAll(sources).addAll(messageSources).build());
  }

  /**
   * Calls {@link String#format} after converting the arguments using some standard guice formatting
   * for {@link Key}, {@link Class} and {@link Member} objects.
   * 
   */
  public static String format(String messageFormat, Object... arguments) {
    for (int i = 0; i < arguments.length; i++) {
      arguments[i] = convert(arguments[i]);
    }
    return String.format(messageFormat, arguments);
  }

  /** Returns the formatted message for an exception with the specified messages. 
   * 
   * If there are multiple messages with the same error id, they will be merged together
   * and displayed as a single message with the index of the first message.
   * * If there is a single cause for all the messages, it will be displayed at the end.
   * * If there are multiple causes, they will not be displayed.
   * * @param heading The heading for the error messages, e.g. "Errors" or "Warnings"
   * 
  */
  public static String formatMessages(String heading, Collection<Message> errorMessages) {
    Formatter fmt = new Formatter().format(heading).format(":\n\n");
    int index = 1;
    boolean displayCauses = getOnlyCause(errorMessages) == null;

    // Sort the error messages by their error id, so that the same errors are grouped together.
    List<ErrorDetail<?>> remainingErrors =
        errorMessages.stream().map(Message::getErrorDetail).collect(Collectors.toList());

    Map<Equivalence.Wrapper<Throwable>, Integer> causes = Maps.newHashMap();
    while (!remainingErrors.isEmpty()) {
      ErrorDetail<?> currentError = remainingErrors.get(0);
      // Split the remaining errors into 2 groups, one that contains mergeable errors with
      // currentError and the other that need to be formatted separately in the next iteration.
      Map<Boolean, List<ErrorDetail<?>>> partitionedByMergeable =
          remainingErrors.subList(1, remainingErrors.size()).stream()
              .collect(Collectors.partitioningBy(currentError::isMergeable));

      remainingErrors = partitionedByMergeable.get(false);

      currentError.format(index, partitionedByMergeable.get(true), fmt);

      Throwable cause = currentError.getCause();
      if (displayCauses && cause != null) {
        Equivalence.Wrapper<Throwable> causeEquivalence = ThrowableEquivalence.INSTANCE.wrap(cause);
        if (!causes.containsKey(causeEquivalence)) {
          causes.put(causeEquivalence, index);
          fmt.format("Caused by: %s", Throwables.getStackTraceAsString(cause));
        } else {
          int causeIdx = causes.get(causeEquivalence);
          fmt.format(
              "Caused by: %s (same stack trace as error #%s)",
              cause.getClass().getName(), causeIdx);
        }
      }
      fmt.format("\n");
      index++;
    }

    if (index == 2) {
      fmt.format("1 error");
    } else {
      fmt.format("%s errors", index - 1);
    }

    return PackageNameCompressor.compressPackagesInMessage(fmt.toString());
  }

  /**
   * Creates a new Message without a cause.
   */
  public static Message create(ErrorId errorId, String messageFormat, Object... arguments) {
    return create(errorId, null, messageFormat, arguments);
  }

  /**
   * Creates a new Message with the given cause.
   */
  public static Message create(
      ErrorId errorId, Throwable cause, String messageFormat, Object... arguments) {
    return create(errorId, cause, ImmutableList.of(), messageFormat, arguments);
  }

  /**
   * Creates a new Message with the given cause and a binding source stack.
   */
  public static Message create(
      ErrorId errorId,
      Throwable cause,
      List<Object> sources,
      String messageFormat,
      Object... arguments) {
    String message = format(messageFormat, arguments);
    return new Message(errorId, sources, message, cause);
  }

  /** Formats an object in a user friendly way. */
  static Object convert(Object o) {
    ElementSource source = null;
    if (o instanceof ElementSource) {
      source = (ElementSource) o;
      o = source.getDeclaringSource();
    }
    return convert(o, source);
  }

  /** Appends information about which modules installed this source (for debugging). */
  static Object convert(Object o, ElementSource source) {
    for (Converter<?> converter : converters) {
      if (converter.appliesTo(o)) {
        return appendModules(converter.convert(o), source);
      }
    }
    return appendModules(o, source);
  }

  private static Object appendModules(Object source, ElementSource elementSource) {
    String modules = SourceFormatter.getModuleStack(elementSource);
    if (modules.length() == 0) {
      return source;
    } else {
      return source + " (installed by: " + modules + ")";
    }
  }

  private abstract static class Converter<T> {

    final Class<T> type;

    Converter(Class<T> type) {
      this.type = type;
    }

    boolean appliesTo(Object o) {
      return o != null && type.isAssignableFrom(o.getClass());
    }

    String convert(Object o) {
      return toString(type.cast(o));
    }

    /**Converts the object to a string representation, appending information about which modules
     * installed this source if available.
     */
    String convert(T t, ElementSource source) {
      String result = toString(t);
      if (source != null) {
        return result + " (installed by: " + SourceFormatter.getModuleStack(source) + ")";
      }
      return result;
    }

    abstract String toString(T t);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final Collection<Converter<?>> converters =
      ImmutableList.of(
          new Converter<Class>(Class.class) {
            @Override
            public String toString(Class c) {
              return c.getName();
            }
          },
          new Converter<Member>(Member.class) {
            @Override
            public String toString(Member member) {
              return Classes.toString(member);
            }
          },
          new Converter<Key>(Key.class) {
            @Override
            public String toString(Key key) {
              if (key.getAnnotationType() != null) {
                return key.getTypeLiteral()
                    + " annotated with "
                    + (key.getAnnotation() != null ? key.getAnnotation() : key.getAnnotationType());
              } else {
                return key.getTypeLiteral().toString();
              }
            }
          });

  /**Returns the cause throwable if there is exactly one cause in {@code messages}. If there are
   * zero or multiple messages with causes, null is returned.
   */
  public static Throwable getOnlyCause(Collection<Message> messages) {
    Throwable onlyCause = null;
    for (Message message : messages) {
      Throwable messageCause = message.getCause();
      if (messageCause == null) {
        continue;
      }

      if (onlyCause != null && !ThrowableEquivalence.INSTANCE.equivalent(onlyCause, messageCause)) {
        return null;
      }

      onlyCause = messageCause;
    }

    return onlyCause;
  }
  
  /**
   * @see com.google.inject.spi.ErrorDetail#format(Formatter)
   * @see com.google.inject.spi.ErrorDetail#getCause()
  */
  private static final class ThrowableEquivalence extends Equivalence<Throwable> {
    static final ThrowableEquivalence INSTANCE = new ThrowableEquivalence();

    @Override
    /* 
     * Returns true if the two exceptions are equivalent, i.e. they have the same class, message,
     * stack trace and cause.
     * 
     */
    protected boolean doEquivalent(Throwable a, Throwable b) {
      return a.getClass().equals(b.getClass())
          && Objects.equal(a.getMessage(), b.getMessage())
          && Arrays.equals(a.getStackTrace(), b.getStackTrace())
          && equivalent(a.getCause(), b.getCause());
    }

    /**
     * Returns true if the two exceptions are equivalent, i.e. they have the same class, message,
     * stack trace and cause.
     */
    @Override
    protected int doHash(Throwable t) {
      return Objects.hashCode(t.getClass().hashCode(), t.getMessage(), hash(t.getCause()));
    }
  }

  /** ANSI escape codes for formatting text in the console. 
   *    
   * * <p>Note: These codes are not supported on all consoles, and may not work on some platforms.
   */
  private enum FormatOptions {
    RED("\u001B[31m"),
    BOLD("\u001B[1m"),
    FAINT("\u001B[2m"),
    ITALIC("\u001B[3m"),
    UNDERLINE("\u001B[4m"),
    RESET("\u001B[0m");

    private final String ansiCode;

    FormatOptions(String ansiCode) {
      this.ansiCode = ansiCode;
    }
  }

  private static final String formatText(String text, FormatOptions... options) {
    if (!InternalFlags.enableColorizeErrorMessages()) {
      return text;
    }
    return String.format(
        "%s%s%s",
        Arrays.stream(options).map(option -> option.ansiCode).collect(joining()),
        text,
        FormatOptions.RESET.ansiCode);
  }

  public static final String bold(String text) {
    return formatText(text, FormatOptions.BOLD);
  }

  public static final String redBold(String text) {
    return formatText(text, FormatOptions.RED, FormatOptions.BOLD);
  }

  public static final String underline(String text) {
    return formatText(text, FormatOptions.UNDERLINE);
  }

  public static final String faint(String text) {
    return formatText(text, FormatOptions.FAINT);
  }
}