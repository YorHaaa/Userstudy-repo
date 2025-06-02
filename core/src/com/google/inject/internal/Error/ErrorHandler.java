package com.google.inject.internal;

import com.google.inject.spi.Message;

/**
 * Handles errors in the Injector.
 *
 * @author crazybob@google.com (Bob Lee)
 */
interface ErrorHandler {

  /** Handles an error. */
  void handle(Object source, Errors errors);

  /** Handles a user-reported error. */
  void handle(Message message);
}
