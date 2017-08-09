@ECHO OFF
REM Copyright 2017 VMware, Inc. All Rights Reserved.
REM
REM SPDX-License-Identifier: Apache-2.0
REM
REM Licensed under the Apache License, Version 2.0 (the "License");
REM you may not use this file except in compliance with the License.
REM You may obtain a copy of the License at
REM 
REM http://www.apache.org/licenses/LICENSE-2.0
REM 
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.

SET DIR=%~dp0
SET VERSION=2.1.1
SET JAVA=java
IF [%JAVA_HOME%] == [] GOTO :NO_JAVA_HOME
SET JAVA="%JAVA_HOME%\bin\java"
:NO_JAVA_HOME
SET CP=%DIR%\vrops-export-%VERSION%.jar
IF [%JDBC_JAR%] == [] GOTO :NO_JDBC_JAR
SET CP=%CP%;%JDBC_JAR%
:NO_JDBC_JAR
JAVA -cp %CP% -Djsse.enableSNIExtension=false com.vmware.vropsexport.Main %*
