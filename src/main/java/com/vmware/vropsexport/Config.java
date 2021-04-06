/*
 * Copyright 2017-2021 VMware, Inc. All Rights Reserved.
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
 */
package com.vmware.vropsexport;

import com.vmware.vropsexport.elasticsearch.ElasticSearchConfig;
import com.vmware.vropsexport.exceptions.ValidationException;
import com.vmware.vropsexport.json.JsonConfig;
import com.vmware.vropsexport.sql.SQLConfig;
import com.vmware.vropsexport.wavefront.WavefrontConfig;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;

@SuppressWarnings("unused")
public class Config implements Validatable {

  public static class NameSanitizerConfig {
    public String forbidden;

    public char replacement;

    public String getForbidden() {
      return forbidden;
    }

    public void setForbidden(final String forbidden) {
      this.forbidden = forbidden;
    }

    public char getReplacement() {
      return replacement;
    }

    public void setReplacement(final char replacement) {
      this.replacement = replacement;
    }
  }

  @SuppressWarnings("unused")
  public static class Field {
    private String alias;
    private String metric;
    private String prop;

    public Field() {}

    public Field(final String alias, final String name, final boolean isMetric) {
      super();
      this.alias = alias;
      if (isMetric) {
        metric = name;
      } else {
        prop = name;
      }
    }

    public String getAlias() {
      return alias;
    }

    public void setAlias(final String alias) {
      this.alias = alias;
    }

    public String getMetric() {
      return metric;
    }

    public boolean hasMetric() {
      return metric != null;
    }

    public void setMetric(final String metric) {
      this.metric = metric;
    }

    public String getProp() {
      return prop;
    }

    public void setProp(final String prop) {
      this.prop = prop;
    }

    public boolean hasProp() {
      return prop != null;
    }
  }

  private Field[] fields;
  private String resourceType;
  private String rollupType;
  private long rollupMinutes;
  private String dateFormat;
  private String outputFormat;
  private SQLConfig sqlConfig;
  private WavefrontConfig wavefrontConfig;
  private String resourceKind;
  private String adapterKind;
  private boolean compact;
  private String compactifyAlg = "LATEST";
  private CSVConfig csvConfig;
  private JsonConfig jsonConfig;
  private ElasticSearchConfig elasticSearchConfig;
  private int align = 0;
  private boolean allMetrics = false;
  private NameSanitizerConfig nameSanitizer;

  public Config() {}

  @Override
  public void validate() throws ValidationException {
    if (allMetrics && fields != null) {
      throw new ValidationException("The 'allMetric' and 'fields' settings are mutually exclusive");
    }
    if (resourceKind == null) {
      throw new ValidationException("'resourceType' must be specified");
    }
    if (outputFormat == null) {
      throw new ValidationException("'outputFormat' must be specified");
    }
    if ("sql".equals(outputFormat) && sqlConfig == null) {
      throw new ValidationException("'sqlConfig' must be specified for SQL output");
    }
    if ("wavefront".equals(outputFormat) && sqlConfig == null) {
      throw new ValidationException("'wavefrontConfig' must be specified for SQL output");
    }
    if ("elastic".equals(outputFormat) && sqlConfig == null) {
      throw new ValidationException("'elasticConfig' must be specified for SQL output");
    }
    if (sqlConfig != null) {
      sqlConfig.validate();
    }
    if (wavefrontConfig != null) {
      wavefrontConfig.validate();
    }
    if (elasticSearchConfig != null) {
      elasticSearchConfig.validate();
    }
  }

  public NameSanitizerConfig getNameSanitizer() {
    return nameSanitizer;
  }

  public void setNameSanitizer(final NameSanitizerConfig nameSanitizer) {
    this.nameSanitizer = nameSanitizer;
  }

  public NameSanitizer createNameSanitizer() {
    return nameSanitizer != null
        ? new ReplacingNameSanitizer(nameSanitizer.forbidden, nameSanitizer.replacement)
        : s -> s;
  }

  public JsonConfig getJsonConfig() {
    return jsonConfig;
  }

  public void setJsonConfig(final JsonConfig jsonConfig) {
    this.jsonConfig = jsonConfig;
  }

  public ElasticSearchConfig getElasticSearchConfig() {
    return elasticSearchConfig;
  }

  public void setElasticSearchConfig(final ElasticSearchConfig elasticSearchConfig) {
    this.elasticSearchConfig = elasticSearchConfig;
  }

  public boolean isAllMetrics() {
    return allMetrics;
  }

  public void setAllMetrics(final boolean allMetrics) {
    this.allMetrics = allMetrics;
  }

  public int getAlign() {
    return align;
  }

  public void setAlign(final int align) {
    this.align = align;
  }

  public Field[] getFields() {
    return fields;
  }

  public void setFields(final Field[] fields) {
    this.fields = fields;
  }

  public void setResourceType(final String resourceType) {
    final Matcher m = Patterns.adapterAndResourceKindPattern.matcher(resourceType);
    if (m.matches()) {
      adapterKind = m.group(1);
      resourceKind = m.group(2);
    } else {
      resourceKind = resourceType;
    }
  }

  public String getRollupType() {
    return rollupType;
  }

  public void setRollupType(final String rollupType) {
    this.rollupType = rollupType;
  }

  public long getRollupMinutes() {
    return rollupMinutes;
  }

  public void setRollupMinutes(final long rollup) {
    rollupMinutes = rollup;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(final String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public DateFormat getDateFormatter() {
    return "%E".equals(dateFormat) ? new EpochDateFormat() : new SimpleDateFormat(dateFormat);
  }

  public String getOutputFormat() {
    return outputFormat;
  }

  public void setOutputFormat(final String outputFormat) {
    this.outputFormat = outputFormat;
  }

  public SQLConfig getSqlConfig() {
    return sqlConfig;
  }

  public void setSqlConfig(final SQLConfig sqlConfig) {
    this.sqlConfig = sqlConfig;
  }

  public WavefrontConfig getWavefrontConfig() {
    return wavefrontConfig;
  }

  public void setWavefrontConfig(final WavefrontConfig wavefrontConfig) {
    this.wavefrontConfig = wavefrontConfig;
  }

  public boolean hasProps() {
    for (final Field f : fields) {
      if (f.hasProp()) {
        return true;
      }
    }
    return false;
  }

  public String getResourceKind() {
    return resourceKind;
  }

  public void setResourceKind(final String resourceKind) {
    this.resourceKind = resourceKind;
  }

  public String getAdapterKind() {
    return adapterKind;
  }

  public void setAdapterKind(final String adapterKind) {
    this.adapterKind = adapterKind;
  }

  public boolean hasMetrics() {
    for (final Field f : fields) {
      if (f.hasMetric()) {
        return true;
      }
    }
    return false;
  }

  public CSVConfig getCsvConfig() {
    return csvConfig;
  }

  public void setCsvConfig(final CSVConfig csvConfig) {
    this.csvConfig = csvConfig;
  }

  public String getResourceType() {
    return resourceType;
  }

  public boolean isCompact() {
    return compact;
  }

  public void setCompact(final boolean compact) {
    this.compact = compact;
  }

  public String getCompactifyAlg() {
    return compactifyAlg;
  }

  public void setCompactifyAlg(final String compactifyAlg) {
    this.compactifyAlg = compactifyAlg;
  }
}
