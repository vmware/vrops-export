# EXPERIMENTAL Wavefront integration

This experimental formatter generates output that can be sent to a Wavefront proxy and ingested by Wavefront. 

The usage is very simple: All metrics exported become metrics in Wavefront and any properties become tags in Wavefront. This behaviour is 
experimental and subject to change! Take care when exporting properties, since the Wavefront tags become part of the unique identity of an
object. Thus, if you export a property that changes frequently, you will created multiple object, which is probably not what you want.

It is highly recommended to use the "align" directive to round to the nearest five minutes. Otherwise, overlapping export of the same time range may show up as multiple data points.

Here is an example of a simple definition file:

    resourceType: VirtualMachine
    rollupType: AVG
    rollupMinutes: 5
    align: 300
    outputFormat: wavefront
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
