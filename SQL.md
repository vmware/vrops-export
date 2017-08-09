# Using the SQL export option
As of version 2.0, there is an option to export the data to a SQL database rather than a CSV file. For this reason, we have added a few new settings to the configuration file. 

## Configuration file settings
These are the  new configuration file settings:
* outputFormat - Must be set to "sql" for SQL export.
* connectionString - A JDBC connection string. The format depends on the database vendor.
* username - An optional database username.
* password - An optional database password.
* databaseType - Type of database. Currently supports postgres, mysql, mssql and oracle. Additional databases can be supported by using the driver option. Note that "driver" and "databaseType" are mutually exclusive.
* driver - The class name of the JDBC driver. Use this setting, along with the JDBC_JAR environment variable to export to a database type that's not included in the choices for "databaseType".
* sql - The SQL statement to use for inserting data into the database. See below for a full description.

## Specifying the SQL statement
The data is inserted into the database using a user-specified SQL statement (typically an INSERT statement). Variable substitution is done using the metric or property alias preceded by a colon. For example:

```
   sql = "INSERT INTO metrics(timestamp, resname, resId, cpuDemand, memDemand, hostCpuDemand) VALUES (:timestamp, :resName, :resId, :cpuDemand, :memDemand, :hostCPUDemand) ON CONFLICT DO NOTHING"
   .
   .
   .
# Resource ID
  - alias: resId
    prop: $resId
# CPU fields
  - alias: cpuDemand
    metric: cpu|demandPct
# Memory fields
  - alias: memDemand
    metric: mem|guest_demand
 # Host CPU
  - alias: hostCPUDemand
    metric: $parent:HostSystem.cpu|demandmhz
```
Notice how the names in the VALUES clause map to the aliases in the list of fields. The fields "timestamp" and "resName" are always available by default. If you need the resource id, you can define that as a property field mapped to the internal property $resId as shown above. You typically want to use some kind of "upsert" semantics if your database supports it. In the example above, we're using the "ON CONFLICT DO NOTHING" option available in PostgreSQL.

## Configuring JDBC drivers
The pre-built binaries come with JDBC drivers for PostgreSQL and MySQL. Due to licensing issues, you will have to download the JDBC drivers for any other datbase separately. When using a non-bundled database, you need to specify the location of the JDBC jar file in the JDBC_JAR environment variable. For example, to use a MS SQL database driver you haev previously downloaded, simply type:

```
$ export JDBC_JAR=~/tmp/sqljdbc42.jar
$ ./exporttool.sh -u demouser -p demopassword -H https://10.140.46.21 -i -d ../samples/sqlexport.yaml 
```

