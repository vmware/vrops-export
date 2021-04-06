# ElasticSearch

The ElasticSearch plugin allows you to feed vRealize Operations metrics directly into an ElasticSearch cluster. Data is
formatted into a simple key-value JSON document that's easy to index with ElasticSearch.

## Configuration

ElasticSearch is enabled by specifying the ElasticSearch output format. In addition, the ```elasticSearchConfig```
sub-configuration has to contain at least the URL and the index.

```yaml
resourceType: VMWARE:VirtualMachine
rollupType: AVG
rollupMinutes: 5
outputFormat: elasticsearch
dateFormat: "yyyy/MM/dd HH:mm:ss"
elasticSearchConfig:
  urls: 
    - https://elastic.something:9200
  index: vrops
  bulkSize: 10
  apiKey: "ABCDEF12345"
  # type: vm # Deprecated
allMetrics: true
```

### Authentication

Currently, we only support ApiKey authentication and no authentication. To add authentication, simply add
an ```apiKey``` entry to the ElasticSearch sub-configuration.

### Date format

The date format specified in the specification file is obeyed for ElasticSearch exports. ElasticSearch supports a wide
range of date formats, but the "yyyy/MM/dd HH:mm:ss" is the default and allows ElasticSearch to automatically map date
fields to native timestamps that can be used for time-series analysis.

### Bulk size

Indexing requests are sent in bulk for maximum performance. The bulk size is configurable through the
```bulkSize``` setting. The default is 10, which is rather conservative. Keep in mind that specifying a very large bulk
size can lead to failures and timeout, so use this setting with caution!

### Types

The "type" feature of ElasticSearch has been deprecated as of version 7 and completely removed in version 8. While
the ```type``` is available as a configuration setting, it is not recommended that you use it.

## Request Throttling

When an ElasticSearch cluster becomes overloaded, it may reject indexing requests as part of a throttling mechanism.
When this happens, the plugin resorts to an exponentially growing wait algorithm until the throttling is lifted.
Throttling severely impacts performance, so make sure you configure the number of working threads in the vRealize
Operations Export Tool to a reasonable number that will avoid overloading the cluster.
