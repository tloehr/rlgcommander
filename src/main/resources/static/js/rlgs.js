var stompClient = null;
const game_state_colors = {
    "EMPTY": "lightgrey",
    "PROLOG": "#f7d1d5",
    "TEAMS_NOT_READY": "orange",
    "TEAMS_READY": "yellow",
    "RUNNING": "green",
    "PAUSING": "cyan",
    "RESUMING": "blue",
    "EPILOG": "#DA70D6"
}
// https://gist.github.com/pngmark/31f2298ee6ea27831cb1e9864fc7b047
const statusMessages = {
    '200': 'OK',
    '201': 'Created',
    '202': 'Accepted',
    '203': 'Non-Authoritative Information',
    '204': 'No Content',
    '205': 'Reset Content',
    '206': 'Partial Content',
    '207': 'Multi-Status (WebDAV)',
    '208': 'Already Reported (WebDAV)',
    '226': 'IM Used',
    '300': 'Multiple Choices',
    '301': 'Moved Permanently',
    '302': 'Found',
    '303': 'See Other',
    '304': 'Not Modified',
    '305': 'Use Proxy',
    '306': '(Unused)',
    '307': 'Temporary Redirect',
    '308': 'Permanent Redirect (experimental)',
    '400': 'Bad Request',
    '401': 'Unauthorized',
    '402': 'Payment Required',
    '403': 'Forbidden',
    '404': 'Not Found',
    '405': 'Method Not Allowed',
    '406': 'Not Acceptable',
    '407': 'Proxy Authentication Required',
    '408': 'Request Timeout',
    '409': 'Conflict',
    '410': 'Gone',
    '411': 'Length Required',
    '412': 'Precondition Failed',
    '413': 'Request Entity Too Large',
    '414': 'Request-URI Too Long',
    '415': 'Unsupported Media Type',
    '416': 'Requested Range Not Satisfiable',
    '417': 'Expectation Failed',
    '418': 'I`m a teapot (RFC 2324)',
    '420': 'Enhance Your Calm (Twitter)',
    '422': 'Unprocessable Entity (WebDAV)',
    '423': 'Locked (WebDAV)',
    '424': 'Failed Dependency (WebDAV)',
    '425': 'Reserved for WebDAV',
    '426': 'Upgrade Required',
    '428': 'Precondition Required',
    '429': 'Too Many Requests',
    '431': 'Request Header Fields Too Large',
    '444': 'No Response (Nginx)',
    '449': 'Retry With (Microsoft)',
    '450': 'Blocked by Windows Parental Controls (Microsoft)',
    '451': 'Unavailable For Legal Reasons',
    '499': 'Client Closed Request (Nginx)',
    '500': 'Internal Server Error',
    '501': 'Not Implemented',
    '502': 'Bad Gateway',
    '503': 'Service Unavailable',
    '504': 'Gateway Timeout',
    '505': 'HTTP Version Not Supported',
    '506': 'Variant Also Negotiates (Experimental)',
    '507': 'Insufficient Storage (WebDAV)',
    '508': 'Loop Detected (WebDAV)',
    '509': 'Bandwidth Limit Exceeded (Apache)',
    '510': 'Not Extended',
    '511': 'Network Authentication Required',
    '598': 'Network read timeout error',
    '599': 'Network connect timeout error'
};

function connect() {
    var socket = new SockJS('/chat');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        stompClient.subscribe('/topic/messages', messageOutput => {
            update_game_state(JSON.parse(messageOutput.body));
            if (location.pathname === '/ui/active/base') location.reload();
            //if ($(location).attr('pathname') === '/ui/active/base') location.reload();
        });
    });
}


/**
 * Registers cleanup functions before we navigate to a different page.
 */
window.onbeforeunload = function () {
    // disable onclose handler first
    disconnect();
};

function disconnect() {
    if (stompClient != null) {
        stompClient.disconnect();
    }
}

function update_game_state(message) {
    const state = message['game_state'];
    const color = game_state_colors[state];
    sessionStorage.setItem('state', state);
    document.getElementById('game_state').innerHTML = '&nbsp;' + state;
    document.getElementById('game_state').setAttribute('style', 'color: ' + color);
    //$('#game_state').html('&nbsp;' + state).attr('style', 'color: ' + color);
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
    console.log("REST GET: " + resturi);
    const xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = () => {
        if (xhttp.readyState === 4) {
            if (xhttp.status === 200) {
                //console.log(xhttp.responseText);
                update_game_state({'game_state': xhttp.responseText})
            }
            update_rest_status_in_session(xhttp);
        }
    }
    const base_url = window.location.origin + '/api/' + resturi;
    const myuri = base_url + (param_json ? '?' + jQuery.param(param_json) : '');
    xhttp.open("GET", myuri, false); // true for asynchronous
    xhttp.send('{}');
}

function update_rest_status_in_session(xhttp) {
    if (xhttp.readyState !== 4) return;
    sessionStorage.setItem('rest_result_html', `&nbsp;${xhttp.status}&nbsp;${statusMessages[xhttp.status]}`);
    sessionStorage.setItem('rest_result_class', xhttp.status >= 400 ? 'bi bi-lightning-fill bi-md text-danger' : 'bi bi-check-circle-fill bi-md text-success');
}

function post_rest(resturi, param_json, body, callback) {
    const xhttp = new XMLHttpRequest();
    xhttp.onload = () => {
        //update_rest_status_in_session(xhttp);
        if (callback) callback(xhttp);
    };

    xhttp.onreadystatechange = () => {
        // https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest/readyState
        update_rest_status_in_session(xhttp);
        if (xhttp.readyState === 4) {
            sessionStorage.removeItem('last_rest_result');
            if (xhttp.status >= 400) sessionStorage.setItem('last_rest_result', JSON.stringify(JSON.parse(xhttp.responseText), null, 4));
        }
    };

    const base_url = window.location.origin + '/api/' + resturi;
    const myuri = base_url + (param_json ? '?' + jQuery.param(param_json) : '');
    // async needs to be false, otherwise, Safari and Firefox will have wrong status reports on the xhttp.status
    // https://stackoverflow.com/a/61587856/1171329
    xhttp.open('POST', myuri, false);
    xhttp.setRequestHeader('Content-type', 'application/json');
    xhttp.send(body ? JSON.stringify(body) : '{}');
}

function go_back_to_active_game() {
    window.location.href = window.location.origin + '/ui/active/base?' + jQuery.param({'id': sessionStorage.getItem('game_id')});
    return false;
}

