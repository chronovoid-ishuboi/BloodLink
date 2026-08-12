@echo off
cd /d "%~dp0\.."
mvn -q -DskipTests compile exec:java -Dexec.mainClass=com.bloodlink.util.DatabaseSetup
