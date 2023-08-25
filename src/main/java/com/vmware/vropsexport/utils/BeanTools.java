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
import java.util.*;
import java.util.stream.Collectors;

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

  private static String setterToProperty(final Method setter) {
    return Character.toLowerCase(setter.getName().charAt(3)) + setter.getName().substring(4);
  }

  public static Set<String> getSettableProperties(final Class<?> clazz, final boolean recurse) {
    if (!recurse) {
      return getSetters(clazz).stream()
          .map(BeanTools::setterToProperty)
          .collect(Collectors.toSet());
    }
    final Set<String> result = new HashSet<>();
    collectProperties(clazz, result, new HashSet<>(), "");
    return result;
  }

  private static void collectProperties(
      final Class<?> clazz,
      final Set<String> properties,
      final Set<Class<?>> visited,
      final String prefix) {
    if (visited.contains(clazz)) {
      return; // Avoid infinite recursion if class contains reference to itself
    }
    visited.add(clazz);
    for (final Method m : getSetters(clazz)) {
      final String prop =
          prefix.length() == 0 ? setterToProperty(m) : prefix + "." + setterToProperty(m);
      properties.add(prop);
      final Class<?> type = m.getParameters()[0].getType();
      if (!(type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type))) {
        collectProperties(type, properties, visited, prop);
      }
    }
  }

  public static List<Method> getSetters(final Class<?> clazz) {
    return Arrays.stream(clazz.getMethods())
        .filter(m -> m.getName().startsWith("set") && m.getParameters().length == 1)
        .collect(Collectors.toList());
  }
}
