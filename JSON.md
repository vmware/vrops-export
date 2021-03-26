# JSON output format

## Sample definition file

```yaml
resourceType: VMWARE:VirtualMachine
rollupType: AVG
rollupMinutes: 5
outputFormat: json
dateFormat: "yyyy-MM-dd HH:mm:ss"
jsonConfig:
  format: elastic
fields:
  - alias: resourceId
    prop: $resId
    # CPU fields
  - alias: cpuDemand
    metric: cpu|demandPct
  - alias: cpuReady
    metric: cpu|readyPct
  - alias: cpuCostop
    metric: cpu|costopPct
  # Memory fields
  - alias: memDemand
    metric: mem|guest_demand
  - alias: memSwapOut
    metric: mem|swapoutRate_average
  - alias: memSwapIn
    metric: mem|swapinRate_average
```

## Formats

When exporting to JSON, the format can be specified using the ```jsonConfig``` section. Currently, the only valid option
is ```format``` which selects the formatting of the JSON output. The following formats are available:

#### compact

The most compact format. One record per resource, one sub-record per metric and a list of samples.

```json
{
  "resources": [
    {
      "metrics": [
        {
          "name": "virtualDisk:scsi0:1|totalReadLatency_average",
          "samples": [
            {
              "t": 1616713319999,
              "v": 0.7333333492279053
            },
            {
              "t": 1616713619999,
              "v": 2.066666603088379
            },
            {
              "t": 1616713919999,
              "v": 0.6000000238418579
            },
            {
              "t": 1616714219999,
              "v": 1.0714285373687744
            },
            {
              "t": 1616714519999,
              "v": 0.5333333611488342
            },
            {
              "t": 1616714819999,
              "v": 1.3333333730697632
            },
            {
              "t": 1616715119999,
              "v": 0.8666666746139526
            },
            {
              "t": 1616715419999,
              "v": 0.2666666805744171
            },
            {
              "t": 1616715719999,
              "v": 0.46666666865348816
            },
            {
              "t": 1616716019999,
              "v": 0.6000000238418579
            },
            {
              "t": 1616716319999,
              "v": 0.3333333432674408
            },
            {
              "t": 1616716619999,
              "v": 0.6000000238418579
            }
          ]
        },
        ...
```

#### chatty

A more verbose format that's useful for some application. Each sample is emitted as a record containing resource name,
timestamp, metric name and value.

```json
{
  "data": [
    {
      "metric": "virtualDisk:scsi0:1|totalReadLatency_average",
      "resourceName": "vrops-02",
      "t": 1616678999999,
      "v": 0.3333333432674408
    },
    {
      "metric": "virtualDisk:Aggregate of all instances|commandsAveraged_average",
      "resourceName": "vrops-02",
      "t": 1616678999999,
      "v": 11.466666221618652
    },
    ...
```

### elastic

This format is optimized for ElasticSearch. Each record represents a timestamp and a resource with the metrics emitted
as a key-value pair.

```json
{
  "data": [
    {
      "timestamp": 1616682479999,
      "resourceName": "vrops-02",
      "OnlineCapacityAnalytics|capacityRemainingPercentage": 0.0,
      "OnlineCapacityAnalytics|cpu|capacityRemaining": 3138.409912109375,
      "OnlineCapacityAnalytics|cpu|recommendedSize": 1899.99853515625,
      "OnlineCapacityAnalytics|cpu|timeRemaining": 366.0,
      "OnlineCapacityAnalytics|diskspace|capacityRemaining": 222.54217529296875,
      "OnlineCapacityAnalytics|diskspace|recommendedSize": 130.2505340576172,
      "OnlineCapacityAnalytics|diskspace|timeRemaining": 366.0,
      "OnlineCapacityAnalytics|mem|capacityRemaining": 0.0,
      "OnlineCapacityAnalytics|mem|recommendedSize": 8546993.0,
      "OnlineCapacityAnalytics|mem|timeRemaining": 0.0,
      "OnlineCapacityAnalytics|timeRemaining": 0.0,
      "System Attributes|alert_count_critical": 0.0,
      "System Attributes|alert_count_immediate": 0.0,
      "System Attributes|alert_count_info": 0.0,

```
