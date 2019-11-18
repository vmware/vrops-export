# Wavefront integration

The Wavefront output plugin allows vRealize Operations to be exported to Wavefront by VMware.

The usage is very simple: All metrics exported become metrics in Wavefront and any properties become tags in Wavefront. Take care when exporting properties, since the Wavefront tags become part of the unique identity of an object. Thus, if you export a property that changes frequently, you will create multiple objects, which is probably not what you want.

It is highly recommended to use the "align" directive to round to the nearest five minutes. Otherwise, overlapping export of the same time range may show up as multiple data points.

The export can run in two different modes: Direct ingest, were data is sent directly to the Wavefront servers in the cloud or proxy ingest, where data is sent to a local proxy before being transmitted to Wavefront.

Here is an example of a simple definition file for direct ingest:

    resourceType: VirtualMachine
    rollupType: AVG
    rollupMinutes: 5
    align: 300
    outputFormat: wavefront
    wavefrontConfig:
        wavefrontURL: "https://try.wavefront.com"
        token: "some-secret-stuff"
    dateFormat: "yyyy-MM-dd HH:mm:ss"
    fields:
      # CPU fields
      - alias: vrops.cpu.demand
        metric: cpu|demandPct
      - alias: vrops.cpu.ready
        metric: cpu|readyPct
      - alias: vrops.cpu.costop
        metric: cpu|costopPct
    # Memory fields
      - alias: vrops.mem.demand
        metric: mem|guest_demand
      - alias: vrops.mem.swapOut
        metric: mem|swapoutRate_average
      - alias: vrops.mem.swapIn
        metric: mem|swapinRate_average
     # Storage fields
      - alias: vrops.storage.demandKbps
        metric: storage|demandKBps
     # Network fields
      - alias: vrops.net.bytesRx
        metric: net|bytesRx_average
      - alias: vrops.net.bytesTx
        metric: net|bytesTx_average
     # Host CPU
      - alias: vrops.host.cpu.demand
        metric: $parent:HostSystem.cpu|demandmhz
     # Host name
      - alias: esxiHost
        prop: summary|parentHost
        
  Another example, this time featuring proxy-based ingest:
  
  ```
  resourceType: VirtualMachine
rollupType: AVG
rollupMinutes: 5
align: 300
outputFormat: wavefront
wavefrontConfig:
  proxyHost: localhost
  proxyPort: 2878
dateFormat: "yyyy-MM-dd HH:mm:ss"
fields:
# CPU fields
  - alias: vrops.cpu.demand
    metric: cpu|demandPct
  - alias: vrops.cpu.ready
    metric: cpu|readyPct
  - alias: vrops.cpu.costop
    metric: cpu|costopPct
# Memory fields
  - alias: vrops.mem.demand
    metric: mem|guest_demand
  - alias: vrops.mem.swapOut
    metric: mem|swapoutRate_average
  - alias: vrops.mem.swapIn
    metric: mem|swapinRate_average
 # Storage fields
  - alias: vrops.storage.demandKbps
    metric: storage|demandKBps
 # Network fields
  - alias: vrops.net.bytesRx
    metric: net|bytesRx_average
  - alias: vrops.net.bytesTx
    metric: net|bytesTx_average
 # Host CPU
  - alias: vrops.host.cpu.demand
    metric: $parent:HostSystem.cpu|demandmhz
# Host name
  - alias: esxiHost
    prop: summary|parentHost
```
       
