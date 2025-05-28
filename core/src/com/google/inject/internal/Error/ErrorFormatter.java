package com.google.inject.internal;

import java.util.Formatter;
import java.util.List;

final class ErrorFormatter {
  private ErrorFormatter() {}

  static void formatSources(int index, List<Object> sources, Formatter formatter) {
    formatSourcesInternal(sources, formatter, index, true);
  }

  static void formatSources(List<Object> sources, int index, Formatter formatter) {
    formatSourcesInternal(sources, formatter, index, true);
  }

  static void formatSources(List<Object> sources, int index, StringBuilder builder) {
    try (Formatter formatter = new Formatter(builder)) {
      formatSourcesInternal(sources, formatter, index, true);
    }
  }

  static void formatSources(List<Object> sources, StringBuilder builder, int index) {
    try (Formatter formatter = new Formatter(builder)) {
      formatSourcesInternal(sources, formatter, index, true);
    }
  }

  static void formatSources(List<Object> sources, StringBuilder builder) {
    try (Formatter formatter = new Formatter(builder)) {
      formatSourcesInternal(sources, formatter, -1, false);
    }
  }

  static void formatSources(List<Object> sources, Formatter formatter) {
    formatSourcesInternal(sources, formatter, -1, false);
  }

  private static void formatSourcesInternal(List<Object> sources, Formatter formatter, int index, boolean includeIndex) {
    for (int i = 0; i < sources.size(); i++) {
      Object source = sources.get(i);
      if (includeIndex && i == 0) {
        formatter.format("%-3s: ", index);
      } else {
        formatter.format(SourceFormatter.INDENT);
      }
      new SourceFormatter(source, formatter, i == 0).format();
    }
  }
}
