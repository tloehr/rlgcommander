var stompClient = null;
var game_state_colors = {
    "EMPTY": "lightgrey",
    "PROLOG": "red",
    "TEAMS_NOT_READY": "orange",
    "TEAMS_READY": "yellow",
    "RUNNING": "green",
    "PAUSING": "cyan",
    "RESUMING": "blue",
    "EPILOG": "#DA70D6"
}
// function setConnected(connected) {
//     document.getElementById('connect').disabled = connected;
//     document.getElementById('disconnect').disabled = !connected;
//     document.getElementById('conversationDiv').style.visibility = connected ? 'visible' : 'hidden';
//     document.getElementById('response').innerHTML = '';
// }

function connect() {
    var socket = new SockJS('/chat');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/messages', function (messageOutput) {
            update_game_state(JSON.parse(messageOutput.body));
        });
    });
}


/**
 * Registers cleanup functions before we navigate to a different page.
 */
window.onbeforeunload = function () {
    // disable onclose handler first
    disconnect();
    console.log("unload");
};

function disconnect() {
    if (stompClient != null) {
        stompClient.disconnect();
    }
    console.log("Disconnected");
}

//
// function sendMessage() {
//     var from = document.getElementById('from').value;
//     var text = document.getElementById('text').value;
//     stompClient.send("/app/chat", {}, JSON.stringify({'from': from, 'text': text}));
// }

function update_game_state(message) {
    console.log(message);
    console.log(game_state_colors['PROLOG']);
    const state = message['game_state'];
    const color = game_state_colors[state];
    $('#game_state').html('&nbsp;'+state).attr('style', 'color: ' + color);
    // var response = document.getElementById('response');
    // var p = document.createElement('p');
    // p.style.wordWrap = 'break-word';
    // p.appendChild(document.createTextNode(messageOutput.from + ": " + messageOutput.text + " (" + messageOutput.time + ")"));
    // response.appendChild(p);
}


function switch_sides() {
    var redfor = $("#redfor").val();
    var blufor = $("#blufor").val();
    $("#redfor").val(blufor);
    $("#blufor").val(redfor);
}

function loadJson(url) {
    var data1;
    var xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function () {
        if (this.readyState == 4 && this.status == 200) {
            data1 = JSON.parse(this.responseText);
        }
        $("#rest_result").val(this.status + ':' + this.responseText);
    };
    xhttp.open("GET", url, true);
    xhttp.send();
    console.log(data1);
    return data1;
}

function from_string_segment_list(list){
    var outer = [];
    list.split(/\n|;/).forEach(function (segment) {
        var inner = [];
        segment.split(',').forEach(function (agent) {
            inner.push(agent);
        });
        outer.push(inner);
    });
    console.log(outer);
    return outer;
}

function from_string_list(list){
    var result = [];
    list.split(/\n|,/).forEach(function (item) {
        result.push(item);
    });
    console.log(result);
    return result;
}

function get_rest(resturi, param_json) {
    const xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function () {
        if (xhttp.readyState == 4 && xhttp.status == 200) {
            //console.log(xhttp.responseText);
            update_game_state({'game_state': xhttp.responseText})
        }
    }
    const base_url = window.location.origin + '/api/' + resturi;
    const myuri = base_url + (param_json ? '?' + jQuery.param(param_json) : '');
    xhttp.open("GET", myuri, true); // true for asynchronous
    xhttp.send('{}');
}

function post_rest(resturi, param_json, body) {
    const xhttp = new XMLHttpRequest();
    xhttp.onload = function () {
        if (xhttp.status >= 400) {
            $('#rest_result').html(`&nbsp;${xhttp.status}`).attr('class', 'bi bi-lightning-fill bi-md text-danger');
        } else {
            $('#rest_result').html(`&nbsp;${xhttp.status}`).attr('class', 'bi bi-check-circle-fill bi-md text-success');
        }
    };
    const base_url = window.location.origin + '/api/' + resturi;
    const myuri = base_url + (param_json ? '?' + jQuery.param(param_json) : '');
    xhttp.open('POST', myuri, true);
    xhttp.setRequestHeader('Content-type', 'application/json');
    xhttp.send(body ? body : '{}');
}

