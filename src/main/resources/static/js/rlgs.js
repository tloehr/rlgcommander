var stompClient = null;
var game_state_colors = {
    "EMPTY": "lightgrey",
    "PROLOG": "#f7d1d5",
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
        stompClient.subscribe('/topic/messages', messageOutput => {
            update_game_state(JSON.parse(messageOutput.body));
            console.log($(location).attr('pathname'));
            console.log($(location).attr('pathname') === '/ui/active/base');
            if ($(location).attr('pathname') === '/ui/active/base') location.reload();
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
    const state = message['game_state'];
    const color = game_state_colors[state];
    sessionStorage.setItem('state', state);
    $('#game_state').html('&nbsp;' + state).attr('style', 'color: ' + color);
}

function loadJson(url) {
    var data1;
    let xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = () => {
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

function to_string_segment_list(jsonArray) {
    let result = [];
    jsonArray.forEach(segment => {
        result.push(segment.join(','));
    });
    return result.join(';');
}


function from_string_segment_list(list) {
    let outer = [];
    list.split(/\n|;/).forEach(segment => {
        let inner = [];
        segment.split(',').forEach(agent => {
            inner.push(agent);
        });
        outer.push(inner);
    });
    console.log(outer);
    return outer;
}

function isEmpty(str) {
    return !str.trim().length;
}

function from_string_list(list) {
    var result = [];
    if (list !== '') {
        list.split(/\n|,/).forEach(item => {
            result.push(item);
        });
    }
    return result;
}

function get_rest(resturi, param_json) {
    const xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = () => {
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

function post_rest(resturi, param_json, body, callback) {
    const xhttp = new XMLHttpRequest();
    xhttp.onload = () => {
        if (xhttp.status >= 400) {
            $('#rest_result').html(`&nbsp;${xhttp.status}`).attr(
                {
                    'class': 'bi bi-lightning-fill bi-md text-danger',
                    'data-bs-toggle': 'tooltip',
                    'data-bs-placement': 'bottom',
                    'data-bs-title': 'oh shit',
                    'title': JSON.parse(xhttp.responseText).message
                }
            );
        } else {
            $('#rest_result').html(`&nbsp;${xhttp.status}`).attr(
                {
                    'class': 'bi bi-check-circle-fill bi-md text-success',
                    'data-bs-toggle': 'tooltip',
                    'data-bs-placement': 'bottom',
                    'data-bs-title': 'oh shit',
                    'title': 'OK'
                }
            );
        }
        if (callback) callback(xhttp);
    };
    const base_url = window.location.origin + '/api/' + resturi;
    const myuri = base_url + (param_json ? '?' + jQuery.param(param_json) : '');
    xhttp.open('POST', myuri, true);
    xhttp.setRequestHeader('Content-type', 'application/json');
    xhttp.send(body ? JSON.stringify(body) : '{}');
}

function go_back_to_active_game(){
    window.location.href = window.location.origin + '/ui/active/base?' + jQuery.param({'id': sessionStorage.getItem('game_id')});
    return false;
}

