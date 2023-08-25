/*
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
 */
package com.vmware.vropsexport;

import com.vmware.vropsexport.elasticsearch.ElasticSearchConfig;
import com.vmware.vropsexport.exceptions.ValidationException;
import com.vmware.vropsexport.json.JsonConfig;
import com.vmware.vropsexport.models.ResourceRequest;
import com.vmware.vropsexport.sql.SQLConfig;
import com.vmware.vropsexport.wavefront.WavefrontConfig;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
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

  private List<Field> fields;
  private String resourceType;
  private String rollupType = "AVG";
  private long rollupMinutes = 5;
  private String dateFormat = "yyyy-MM-dd HH:mm:ss";
  private String outputFormat = "csv";
  private SQLConfig sqlConfig = new SQLConfig();
  private WavefrontConfig wavefrontConfig = new WavefrontConfig();
  private ResourceRequest query = new ResourceRequest();
  private boolean compact;
  private String compactifyAlg = "LATEST";
  private CSVConfig csvConfig = new CSVConfig(true, ",");
  private JsonConfig jsonConfig = new JsonConfig();
  private ElasticSearchConfig elasticSearchConfig = new ElasticSearchConfig();
  private int align = 0;
  private boolean allMetrics = false;
  private NameSanitizerConfig nameSanitizer = new NameSanitizerConfig();
  private ZoneId timezone = ZoneId.systemDefault();

  public Config() {}

  @Override
  public void validate() throws ValidationException {
    if (allMetrics && fields != null) {
      throw new ValidationException("The 'allMetric' and 'fields' settings are mutually exclusive");
    }
    if (query.getResourceKind().isEmpty()) {
      throw new ValidationException("'resourceType' must be specified");
    }
    if (outputFormat == null) {
      throw new ValidationException("'outputFormat' must be specified");
    }
    if ("sql".equals(outputFormat)) {
      sqlConfig.validate();
    }
    if ("wavefront".equals(outputFormat)) {
      wavefrontConfig.validate();
    }
    if ("elastic".equals(outputFormat)) {
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
    return nameSanitizer != null && nameSanitizer.forbidden != null
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

  public List<Field> getFields() {
    return fields;
  }

  public void setFields(final List<Field> fields) {
    this.fields = fields;
  }

  public void setResourceType(final String resourceType) {
    final Matcher m = Patterns.adapterAndResourceKindPattern.matcher(resourceType);
    if (m.matches()) {
      query.setAdapterKind(Collections.singletonList(m.group(1)));
      query.setResourceKind(Collections.singletonList(m.group(2)));
    } else {
      query.setResourceKind(Collections.singletonList(resourceType));
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
    new SimpleDateFormat(dateFormat); // Validate it before we use it.
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
    return query.getResourceKind().get(0);
  }

  public void setResourceKind(final String resourceKind) {
    query.setResourceKind(Collections.singletonList(resourceKind));
  }

  public String getAdapterKind() {
    return query.getAdapterKind() == null || query.getAdapterKind().isEmpty()
        ? null
        : query.getAdapterKind().get(0);
  }

  public void setAdapterKind(final String adapterKind) {
    query.setAdapterKind(Collections.singletonList(adapterKind));
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
    return query.getAdapterKind().get((0));
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

  public ResourceRequest getQuery() {
    return query;
  }

  public void setQuery(final ResourceRequest query) {
    this.query = query;
  }

  public String getTimezone() {
    return timezone.getId();
  }

  public ZoneId getZoneId() {
    return timezone;
  }

  public void setTimezone(final String timezone) {
    this.timezone = ZoneId.of(timezone);
  }

  public void setTimezone(final ZoneId timezone) {
    this.timezone = timezone;
  }
}
