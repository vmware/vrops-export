/*
 *
 * Copyright 2017-2023 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier:	Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.vmware.vropsexport.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.StringTokenizer;

public class BeanTools {
  private static class Target {
    private final Object target;
    private final String key;

    public Target(final Object target, final String key) {
      this.target = target;
      this.key = key;
    }
  }

  private static String getMethodName(final String prefix, final String property) {
    return prefix + property.substring(0, 1).toUpperCase() + property.substring(1);
  }

  private static Method getGetMethod(final Object target, final String key)
      throws NoSuchMethodException {
    try {
      return target.getClass().getMethod(getMethodName("get", key));
    } catch (final NoSuchMethodException e) {
      // Could be a boolean with an "is"-getter
      return target.getClass().getMethod(getMethodName("is", key));
    }
  }

  private static Object innerGet(final Object target, final String key)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    return getGetMethod(target, key).invoke(target);
  }

  private static void innerSet(final Object target, final String key, Object value)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    final String methodName = getMethodName("set", key);
    final Class<?> propertyType = getGetMethod(target, key).getReturnType();

    // Some values may have to be coerced. Luckily, Jackson can do that for us!
    if (value.getClass() != propertyType) {
      value = new ObjectMapper().convertValue(value, propertyType);
    }
    target.getClass().getMethod(methodName, propertyType).invoke(target, value);
  }

  private static Target getTarget(final Object root, final String key)
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    final StringTokenizer st = new StringTokenizer(key, ".");
    int n = st.countTokens() - 1;
    Object t = root;
    while (n > 0) {
      t = innerGet(t, st.nextToken());
      --n;
    }
    return new Target(t, st.nextToken());
  }

  public static Object get(final Object target, final String key)
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    final Target t = getTarget(target, key);
    return innerGet(t.target, t.key);
  }

  public static void set(final Object target, final String key, final Object value)
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException,
          NoSuchFieldException {
    final Target t = getTarget(target, key);
    innerSet(t.target, t.key, value);
  }
}
