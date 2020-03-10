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

### Default 15 secs interval run
For running the application we need to specify the **filepath** for **trades.json** file 

replace **filepath** value with the file you want to read
```
sudo ./gradlew clean build bootRun -Pfilepath=/tmp/lite_trades.json
```

### Custom milliseconds interval run
This will run for the defined interval in milliseconds, have to specify property as **milli**

```
sudo ./gradlew clean build bootRun -Pfilepath=/tmp/lite_trades.json -Pmilli=10
```

### Subscribing for Symbol
Open the below URL & enter the Symbol to subscribe for in the text box & press Subscribe button

```
http://localhost:8080/
```

### Starting the Cron
Hit the below URL to start the Cron(Interval which was set earlier) & in some time you will receive updates from the server
```
http://localhost:8080/workers/start
```


## Log file
log file can be found in logs/application.log
```
tail -f logs/application.log
```