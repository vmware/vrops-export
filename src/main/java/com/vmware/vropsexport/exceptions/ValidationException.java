package com.vmware.vropsexport.exceptions;

public class ValidationException extends Exception {
  private static final long serialVersionUID = -1701704085247220998L;

  public ValidationException(final String message) {
    super(message);
  }
}
