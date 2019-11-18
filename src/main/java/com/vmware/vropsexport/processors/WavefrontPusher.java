package com.vmware.vropsexport.processors;

import com.vmware.vropsexport.*;
import com.vmware.vropsexport.wavefront.WavefrontConfig;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.proxy.WavefrontProxyClient;
import org.apache.http.HttpException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class WavefrontPusher implements RowsetProcessor {

    public static class Factory implements RowsetProcessorFacotry {
        public RowsetProcessor makeFromConfig(BufferedWriter bw, Config config, DataProvider dp) throws ExporterException {
            if(config.getWavefrontConfig() == null) {
                throw new ExporterException("Wavefront config must be specified for Wavefront output");
            }
            WavefrontConfig wfc = config.getWavefrontConfig();
            WavefrontSender sender = null;
            if(wfc.getProxyHost() != null) {
                if(wfc.getWavefrontURL() != null || wfc.getToken() != null) {
                    throw new ExporterException("The 'wavefrontURL' and 'token' config items are not compatible with proxy");
                }
                WavefrontProxyClient.Builder b = new WavefrontProxyClient.Builder(wfc.getProxyHost());
                if(wfc.getProxyPort() != 0) {
                    b.metricsPort(wfc.getProxyPort());
                }
                sender = b.build();
            } else {
                if(wfc.getWavefrontURL() == null && wfc.getToken() == null) {
                    throw new ExporterException("The 'wavefrontURL' and 'token' must be specified if proxy is not used");
                }
                WavefrontDirectIngestionClient.Builder b = new WavefrontDirectIngestionClient.Builder(wfc.getWavefrontURL(), wfc.getToken());
                sender = b.build();
            }
            return new WavefrontPusher(sender, dp);
        }

        @Override
        public boolean isProducingOutput() {
            return false;
        }
    }
    private final DataProvider dp;

    private WavefrontSender sender;

    public WavefrontPusher(WavefrontSender sender, DataProvider dp) {
        this.sender = sender;
        this.dp = dp;
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
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Integer> metric : meta.getMetricMap().entrySet()) {

                    // Build string on the format <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
                    //
                    Double d = r.getMetric(metric.getValue());
                    if(d == null)
                        continue;
                    Map<String, String> tags = new HashMap<>(meta.getPropMap().size());
                    for(Map.Entry<String, Integer> prop : meta.getPropMap().entrySet()) {
                        Integer p = prop.getValue();
                        if(p == null)
                            continue;
                        tags.put(meta.getAliasForProp(prop.getKey()), r.getProp(p));
                    }
                    sender.sendMetric(meta.getAliasForMetric(metric.getKey()),d,ts/1000, resourceName, tags);
                }
            }
        } catch (IOException | HttpException e) {
            throw new ExporterException(e);
        }
    }

    @Override
    public void close() throws ExporterException {
        try {
            sender.flush();
            sender.close();
        } catch(IOException e) {
            throw new ExporterException(e);
        }
    }
}
