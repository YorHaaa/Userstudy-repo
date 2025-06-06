package com.google.inject.internal;

import com.google.common.collect.Lists;
import com.google.inject.spi.PrivateElements;
import java.util.List;

/**
 * Handles {@code Binder.newPrivateBinder()} elements.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class PrivateElementProcessor extends AbstractProcessor {

  private final List<InjectorShell.Builder> injectorShellBuilders = Lists.newArrayList();

  PrivateElementProcessor(Errors errors) {
    super(errors);
  }

  @Override
  public Boolean visit(PrivateElements privateElements) {
    InjectorShell.Builder builder =
        new InjectorShell.Builder().parent(injector).privateElements(privateElements);
    injectorShellBuilders.add(builder);
    return true;
  }

  public List<InjectorShell.Builder> getInjectorShellBuilders() {
    return injectorShellBuilders;
  }
}
