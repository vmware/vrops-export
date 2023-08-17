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

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class BeanToolTest {

  class SampleBean {
    private String string = "aString";

    private int integer = 42;

    private Map<String, String> map = new HashMap<>();

    private SampleBean nested;

    public String getString() {
      return string;
    }

    public void setString(final String string) {
      this.string = string;
    }

    public int getInteger() {
      return integer;
    }

    public void setInteger(final int integer) {
      this.integer = integer;
    }

    public Map<String, String> getMap() {
      return map;
    }

    public void setMap(final Map<String, String> map) {
      this.map = map;
    }

    public SampleBean getNested() {
      return nested;
    }

    public void setNested(final SampleBean nested) {
      this.nested = nested;
    }
  }

  @Test
  public void testSimpleGetter()
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    final SampleBean b = new SampleBean();
    Assert.assertEquals(b.getInteger(), BeanTools.get(b, "integer"));
    Assert.assertEquals(b.getString(), BeanTools.get(b, "string"));
    Assert.assertEquals(b.getMap(), BeanTools.get(b, "map"));
  }

  @Test
  public void testSimpleSetter()
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException,
          NoSuchFieldException {
    final SampleBean b = new SampleBean();
    BeanTools.set(b, "string", "new string");
    BeanTools.set(b, "integer", 123);
    final Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
    BeanTools.set(b, "map", map);
    Assert.assertEquals("new string", b.getString());
    Assert.assertEquals(123, b.getInteger());
    Assert.assertEquals(map, b.getMap());
  }

  @Test
  public void testNestedGetter()
      throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    final SampleBean b = new SampleBean();
    final SampleBean nested = new SampleBean();
    nested.setInteger(43);
    final SampleBean doubleNested = new SampleBean();
    doubleNested.setInteger(44);
    nested.setNested(doubleNested);
    b.setNested(nested);
    Assert.assertEquals(b.getNested().getInteger(), BeanTools.get(b, "nested.integer"));
    Assert.assertEquals(
        b.getNested().getNested().getInteger(), BeanTools.get(b, "nested.nested.integer"));
  }

  @Test
  public void testNestedSetter()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException,
          IllegalAccessException {
    final SampleBean b = new SampleBean();
    final SampleBean nested = new SampleBean();
    final SampleBean doubleNested = new SampleBean();
    nested.setNested(doubleNested);
    b.setNested(nested);
    BeanTools.set(b, "nested.integer", 44);
    Assert.assertEquals(44, b.getNested().getInteger());
  }
}
