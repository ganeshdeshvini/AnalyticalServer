var ws;
var url = 'ws://localhost:8080/symbol';

function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);
    if (connected) {
        $("#notificationlistener").show();
    } else {
        $("#notificationlistener").hide();
    }
    $("#greetings").html("");
}

function connect() {
    ws = new WebSocket(url);
    ws.onopen = function (event) {
        console.log("onopen called!!!")
        sendSubscriptionToSymbol();
    };
    ws.onmessage = function (data) {
        console.log("onmessage called!!!")
        showSubscribedSymbolEvents(data.data);
    }
    setConnected(true);
}

function disconnect() {
    if (ws != null) {
        ws.close();
    }
    setConnected(false);
    console.log("Disconnected");
}

function sendSubscriptionToSymbol() {
    var data = JSON.stringify({'event': 'subscribe', 'symbol': $("#symbol").val()})
    ws.send(data);
}

function showSubscribedSymbolEvents(message) {
    $("#subsribedevents").append("<tr><td> " + message + "</td></tr>");
}

$(function () {
    $("form").on('submit', function (e) {
        e.preventDefault();
    });
    $("#connect").click(function () {
        connect();
    });
    $("#disconnect").click(function () {
        disconnect();
    });
});

