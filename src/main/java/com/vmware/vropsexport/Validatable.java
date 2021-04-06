package com.vmware.vropsexport;

import com.vmware.vropsexport.exceptions.ValidationException;

public interface Validatable {
  void validate() throws ValidationException;
}
