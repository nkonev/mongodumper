[![Build Status](https://travis-ci.com/nkonev/mongodumper.svg?branch=master)](https://travis-ci.com/nkonev/mongodumper)

# Screenshots
![](./.markdown/1.png)
![](./.markdown/2.png)
![](./.markdown/3.png)

# Configuration
Set environment variable `SPRING_DATA_MONGODB_URI` with url of mongo used for store connections list.

(Optional) set environment variable `SERVER_SERVLET_CONTEXT-PATH=/mongodumper`.

## Hooks
There are BEFORE_HOOK and AFTER_HOOK:

```bash
docker run -e BEFORE_HOOK='b="coolest app"; echo start ${b};' -e AFTER_HOOK='a="super app"; echo goodbye ${a};' -it nkonev/mongodumper
```

## Full configuration example
```bash
docker run -e SPRING_DATA_MONGODB_URI=mongodb://172.18.0.3:27017/mongodumper -e BEFORE_HOOK='b="coolest app"; echo start ${b};' -e AFTER_HOOK='a="super app"; echo goodbye ${a};' -e SERVER_SERVLET_CONTEXT-PATH=/mongodumper -it --network=blog-storage_default -p 7070:8080  nkonev/mongodumper
```

# Download
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
