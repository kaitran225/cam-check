@echo off
echo Starting CamCheck with 30MB memory limit...

:: Build the project
call mvn clean package -DskipTests

:: Run with memory constraints
java -Xmx40m -Xms20m ^
     -XX:MaxMetaspaceSize=64m ^
     -XX:CompressedClassSpaceSize=16m ^
     -XX:ReservedCodeCacheSize=16m ^
     -XX:+UseSerialGC ^
     -XX:+UseStringDeduplication ^
     -XX:+DisableExplicitGC ^
     -XX:SoftRefLRUPolicyMSPerMB=0 ^
     -XX:MaxDirectMemorySize=5M ^
     -Djava.awt.headless=true ^
     -Djava.security.egd=file:/dev/./urandom ^
     -Dvm.capacity.measure=true ^
     -Ddynamic.memory.enabled=true ^
     -Ddynamic.memory.target.percent=75 ^
     -DLOW_RESOURCE_MODE=true ^
     -jar target/cam-check-0.0.1-SNAPSHOT.jar

pause 