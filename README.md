# Analytical Server OHLC

Analytical Server OHLC (Open/High/Low/Close) series based on trade

## Prerequisites
Java 8 or higher

Git

## Running Application
Git clone the project into your directory
```
git clone https://github.com/ganeshdeshvini/AnalyticalServer.git
```

Go to that directory in terminal
```
cd AnalyticalServer
```

Check the permission for **gradlew** file, if it doesn't have enough access then execute below command
```
sudo chmod 777 gradlew
```

For running the application we need to specify the **trades.json** file for reading
we can pass the same in argument to gradle using following command

```
sudo ./gradlew clean build runJar -Pfilepath=G:\trades.json
```

replace **filepath** value with the file you want to read

## Log file
log file can be found in logs/application.log
```
tail -f logs/application.log
```