package com.vmware.vropsexport.processors;

import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.DataProvider;
import com.vmware.vropsexport.ExporterException;
import com.vmware.vropsexport.Row;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import com.vmware.vropsexport.RowsetProcessorFacotry;
import com.vmware.vropsexport.wavefront.WavefrontConfig;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.proxy.WavefrontProxyClient;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpException;

@SuppressWarnings("WeakerAccess")
public class WavefrontPusher implements RowsetProcessor {

    public static class Factory implements RowsetProcessorFacotry {
        @Override
        public RowsetProcessor makeFromConfig(final OutputStream out, final Config config, final DataProvider dp) throws ExporterException {
            if (config.getWavefrontConfig() == null) {
                throw new ExporterException("Wavefront config must be specified for Wavefront output");
            }
            final WavefrontConfig wfc = config.getWavefrontConfig();
            WavefrontSender sender = null;
            if (wfc.getProxyHost() != null) {
                if (wfc.getWavefrontURL() != null || wfc.getToken() != null) {
                    throw new ExporterException("The 'wavefrontURL' and 'token' config items are not compatible with proxy");
                }
                final WavefrontProxyClient.Builder b = new WavefrontProxyClient.Builder(wfc.getProxyHost());
                if (wfc.getProxyPort() != 0) {
                    b.metricsPort(wfc.getProxyPort());
                }
                sender = b.build();
            } else {
                if (wfc.getWavefrontURL() == null && wfc.getToken() == null) {
                    throw new ExporterException("The 'wavefrontURL' and 'token' must be specified if proxy is not used");
                }
                final WavefrontDirectIngestionClient.Builder b = new WavefrontDirectIngestionClient.Builder(wfc.getWavefrontURL(), wfc.getToken());
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

    private final WavefrontSender sender;

    public WavefrontPusher(final WavefrontSender sender, final DataProvider dp) {
        this.sender = sender;
        this.dp = dp;
    }

    @Override
    public void preamble(final RowMetadata meta, final Config conf) throws ExporterException {
        // Nothing to do here
    }

    @Override
    public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {
        try {
            for (final Row r : rowset.getRows().values()) {
                final long ts = r.getTimestamp();
                final String resourceName = dp.getResourceName(rowset.getResourceId());
                final StringBuilder sb = new StringBuilder();
                for (final Map.Entry<String, Integer> metric : meta.getMetricMap().entrySet()) {

                    // Build string on the format <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
                    //
                    final Double d = r.getMetric(metric.getValue());
                    if (d == null) {
                        continue;
                    }
                    final Map<String, String> tags = new HashMap<>(meta.getPropMap().size());
                    for (final Map.Entry<String, Integer> prop : meta.getPropMap().entrySet()) {
                        final Integer p = prop.getValue();
                        if (p == null) {
                            continue;
                        }
                        tags.put(meta.getAliasForProp(prop.getKey()), r.getProp(p));
                    }
                    sender.sendMetric(meta.getAliasForMetric(metric.getKey()), d, ts / 1000, resourceName, tags);
                }
            }
        } catch (final IOException | HttpException e) {
            throw new ExporterException(e);
        }
    }

    @Override
    public void close() throws ExporterException {
        try {
            sender.flush();
            sender.close();
        } catch (final IOException e) {
            throw new ExporterException(e);
        }
    }
}
