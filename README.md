# vrops-export
Simple utility for exporting data from vRealize Operations.

## Description
A simple command-line data export tool for vRealize Operations. Currently supports CVS and SQL, but additional output formats are planned.

# Installation
The tool can be installed from pre-built binaries or built from source. If you're unclear what "building from source" means, you probably want to use the binaries. Building from source is mainly for people who like to change the code and contribute to the project.

## Installing the binaries
### Prerequisites
* Java JDK 1.8 installed on the machine where you plan to run the tool
* vRealize Operations 6.3 or higher
### Installation on Linux, Mac or other UNIX-like OS
1. Download the binaries from here: https://github.com/prydin/vrops-export/releases
2. Unzip the files:
```
mkdir ~/vrops-export
cd ~/vrops-export
unzip vrops-export-<version>-bin.zip
cd vrops-export-<version>/bin
```
3. Make the start script runnable
```
chmod +x exporttool.sh
```
4. Run it!
```
./exporttool.sh -d ../samples/vmfields.yaml -u admin -p password -H https://my.vrops.host
```

### Installation on Windows
1. Download the binaries from here: https://github.com/prydin/vrops-export/releases
2. Unzip the files into a directory of your choice, e.g. c:\\vropsexport
3. Open a command window and cd into the directory you created, e.g.
```
cd c:\vropsexport
```
4. Cd into the bin directory
```
cd bin
```
5. Runt the tool
```
exporttool.bat -d ..\samples\vmfields.yaml -u admin -p password -H https://my.vrops.host
```

## Building from source
### Prerequisites
* Java JDK 1.8 installed on the machine where you plan to run the tool
* vRealize Operations 6.3 or higher
* Maven 3.3.3 or higher

### Build
1. Get from git
```
git init # Only needed of you haven't already initialized a git repo in the directory 
git clone https://github.com/prydin/vrops-export.git
```
2. Build the code
```
cd vrops-export
mvn package
```
3. Run it!
```
cd target
chmod +x exporttool.sh
./exporttool.sh -d ../samples/vmfields.yaml -u admin -p password -H https://my.vrops.host -i
```

## Command syntax
```
usage: exporttool [-d <arg>] [-e <arg>] [-F <arg>] [-H <arg>] [-h] [-i]
       [-l <arg>] [-m <arg>] [-n <arg>] [-o <arg>] [-P <arg>] [-p <arg>]
       [-q] [-R <arg>] [-s <arg>] [-S] [-t <arg>] [-T <arg>] [--trustpass
       <arg>] [-u <arg>] [-v]

 -d,--definition <arg>       Path to definition file
 -e,--end <arg>              Time period end (date format in definition
                             file)
 -F,--list-fields <arg>      Print name and keys of all fields to stdout
 -H,--host <arg>             URL to vRealize Operations Host
 -h,--help                   Print a short help text
 -i,--ignore-cert            Trust any cert (DEPRECATED!)
 -l,--lookback <arg>         Lookback time
 -m,--max-rows <arg>         Maximum number of rows to fetch from API
                             (default=unlimited)
 -n,--namequery <arg>        Name query
 -o,--output <arg>           Output file
 -P,--parent <arg>           Parent resource (ResourceKind:resourceName)
 -p,--password <arg>         Password
 -q,--quiet                  Quiet mode (no progress counter)
 -R,--resource-kinds <arg>   List resource kinds
 -s,--start <arg>            Time period start (date format in definition
                             file)
 -S,--streaming              True streaming processing. Faster but less
                             reliable
 -t,--threads <arg>          Number of parallel processing threads
                             (default=10)
 -T,--truststore <arg>       Truststore filename
    --trustpass <arg>        Truststore password (default=changeit)
 -u,--username <arg>         Username
 -v,--verbose                Print debug and timing information
 ```
 ### Certificate and trust management
 As of version 2.1.0, the -i option has been deprecated for security reasons. Instead, the tool will prompt the user when it encounters an untrusted certificate. If the user chooses to trust the certificate, it is stored in a personal truststore and reused next time the tool is executed against that host. By default, the trusted certs are stored in $HOME/.vropsexport/truststore, but the location can be overridden using the -T flag.
 
 ### Notes:
 * Start and end dates will use the date format specified in the definition file. Since dates tend to contain spaces and special characters, you probably want to put dates within double quotes (").
 * If you're unsure of what the metric names are, use the -F option to print the metric names and keys for a specific resource type, e.g. -F VirtualMachine
 * The -l (lookback) parameter is an alternative to the start and end dates. It sets the end date to the current time and goes back as far as you specify. You specify it as a number and a unit, e.g. 24h for 24 hours back. Valid unit are d=days, h=hours, m=minutes, s=seconds.
 * The -P flag restricts the export to objects sharing a specified parent. Parents must be specified as resource kind and resource name, for example HostSystem:esxi-01 if you want to export only VMs on the host named "esxi-01".
 
 ## Definition file
 The details on what fields to export and how to treat them is expressed in the definition file. This file follows the YAML format. 
 Here is an example of a definition file:
 
 ```
resourceType: VirtualMachine                     # The resource type we're exporting
rollupType: AVG                                  # Rollup type: AVG, MAX, MIN, SUM, LATEST, COUNT
rollupMinutes: 5                                 # Time scale granularity in minutes
dateFormat: yyyy-MM-dd HH:mm:ss                  # Date format. See http://tinyurl.com/pscdf9g
fields:                                          # A list of fields
# CPU fields
  - alias: cpuDemand                             # Name of the field in the output
    metric: cpu|demandPct                        # Reference to a metric field in vR Ops
  - alias: cpuReady
    metric: cpu|readyPct
  - alias: cpuCostop
    metric: cpu|costopPct
# Memory fields
  - alias: memDemand
    metric: mem|object.demand
  - alias: memSwapOut
    metric: mem|swapoutRate_average
  - alias: memSwapIn
    metric: mem|swapinRate_average
 # Storage fields
  - alias: storageDemandKbps
    metric: storage|demandKBps
 # Network fields
  - alias: netBytesRx
    metric: net|bytesRx_average
  - alias: netBytesTx
    metric: net|bytesTx_average
 # Host CPU
  - alias: hostCPUDemand
    metric: $parent:HostSystem.cpu|demandmhz	# Reference to a metric in the parent. 
 # Guest OS
  - alias: guestOS
    prop: config|guestFullName			# Reference to a property (as opposed to metric)
 # Host CPU type
  - alias: hostCPUType
    prop: $parent:HostSystem.cpu|cpuModel		# Reference to a metric in a parent
```

### Special properties in the definition file
There are a number of special properties that are always available for use in a properties file for getting things like parent resources and resource names.

* $resdId - Internal ID of the current resource.
* $resName - Resource name of the current resource
* $parent - Reference to a parent resource. 

### Referencing parent resources
The syntax for referencing parent resources is as follows: 
```
$parent:<Parent Kind>.<metric or property>
``` 
For example:
```
$parent:HostSystem.cpu|demandmhz
``` 
Notice that you can stack several $parent keywords. For example, this gets the total CPU demand of a parent cluster based on a VM:
```
$parent:HostSystem.$parent:ClusterComputeResource.cpu|demandmhz
```

## Exporting to SQL
The tool now supports exporting to a SQL database. For details, please refer to [this document](SQL.md)
    
# Known issues
* Very long time ranges in combination with small interval sizes can cause the server to prematurely close the connection, resulting in NoHttpResponseExceptions to be thrown. If this happens, consider shortening the time range. This seems to happen mostly when exporting over a slow connection.
* Only one parent resource type is supported. This will be fixed in a future release.

# Contributing to the code
Contributing with code and new ideas is encouraged! If you have a great idea for a new or improved feature, please file a feature request under the "issues" tab in Github. 
 



