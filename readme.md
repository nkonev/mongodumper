[![Build Status](https://travis-ci.com/nkonev/mongodumper.svg?branch=master)](https://travis-ci.com/nkonev/mongodumper)

Set environment variable `SPRING_DATA_MONGODB_URI` with url of mongo used for store connections list.

[Docker hub](https://hub.docker.com/repository/docker/nkonev/mongodumper)
```bash
docker pull nkonev/mongodumper
```

# Debugging testcontainers webdriver
1\. Set max implicit wait 
```kotlin
driver.manage()?.timeouts()?.implicitlyWait(30000, TimeUnit.SECONDS)
```
2\. Install remmina

3\. Check log and connect to 
```
VNC address vnc://vnc:secret@localhost:33029
```
So user is `vnc`, password is `secret`, host - `localhost`, port(will different) - 33029
![](./.markdown/vnc.png)

4\. Screen
![](./.markdown/remmina.png)