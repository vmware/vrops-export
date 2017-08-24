package com.vmware.vropsexport.processors;

import com.vmware.vropsexport.*;
import org.apache.http.HttpException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

public class WavefrontSender implements RowsetProcessor {
    public static class Factory implements RowsetProcessorFacotry {
        public RowsetProcessor makeFromConfig(BufferedWriter bw, Config config, DataProvider dp, ProgressMonitor pm) {
            return new WavefrontSender(bw, config, dp, pm);
        }
    }
    private final DataProvider dp;

    private final BufferedWriter bw;

    private final Config config;

    private final ProgressMonitor pm;

    public WavefrontSender(BufferedWriter bw, Config config, DataProvider dp, ProgressMonitor pm) {
        this.dp = dp;
        this.bw = bw;
        this.config = config;
        this.pm = pm;
    }

    @Override
    public void preamble(RowMetadata meta, Config conf) throws ExporterException {
        // Nothing to do here
    }

    @Override
    public void process(Rowset rowset, RowMetadata meta) throws ExporterException {
        try {
            for (Row r : rowset.getRows().values()) {
                long ts = r.getTimestamp();
                String resourceName = dp.getResourceName(rowset.getResourceId());
                resourceName = resourceName.replace(' ', '_');
                StringBuffer sb = new StringBuffer();
                for (Map.Entry<String, Integer> metric : meta.getMetricMap().entrySet()) {

                    // Build string on the format <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
                    //
                    Double d = r.getMetric(metric.getValue());
                    if(d == null)
                        continue;
                    synchronized(bw) {
                        bw.write(meta.getAliasForMetric(metric.getKey()));
                        bw.write(" ");
                        bw.write(Double.toString(d));
                        bw.write(" ");
                        bw.write(Long.toString(ts / 1000));
                        bw.write(" source=");
                        bw.write(resourceName);
                        for(Map.Entry<String, Integer> prop : meta.getPropMap().entrySet()) {
                            Integer p = prop.getValue();
                            if(p == null)
                                continue;
                            bw.write(" ");
                            bw.write(meta.getAliasForProp(prop.getKey()));
                            bw.write('=');
                            bw.write("" + r.getProp(p));
                        }
                        bw.write("\n");
                    }
                }
            }
            bw.flush();
            if (pm != null)
                pm.reportProgress(1);
        } catch (IOException | HttpException e) {
            throw new ExporterException(e);
        }
    }
}
