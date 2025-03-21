let mqttClient;

/**
 * Registers cleanup functions before we navigate to a different page.
 */
window.onbeforeunload = function () {
    // disable onclose handler first
    console.log('disconnecting from server due to page change');
    try {
        mqttClient.disconnect();
    } catch (e) {
        console.error(e);
    }

};

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

// for parameter setup about asking if to count the respawns
const COUNT_NEVER = 0;
const COUNT_ASK = 1;
const COUNT_ALWAYS = 2;

/**
 * connects to the mqtt broker for every new page - the connection is terminated every time
 * a page unloads when navigating to a different one
 *
 * @param channel
 */
function connect_to_mqtt() {
    const now = new Date();
    const client_id = "webclient@" + now.toISOString();

    // Create a client instance
    mqttClient = new Paho.Client(sessionStorage.getItem('mqtt_host'), Number(sessionStorage.getItem('mqtt_ws_port')), '/', client_id);

    // set callback handlers
    mqttClient.onConnectionLost = onConnectionLost;
    mqttClient.onMessageArrived = onMessageArrived;

    // connect the client
    mqttClient.connect({onSuccess: onMQTTConnect});
}

// called when the client loses its connection
function onConnectionLost(responseObject) {
    if (responseObject.errorCode !== 0) {
        console.error("onConnectionLost: " + responseObject.errorMessage);
    }
}

function onMessageArrived(message) {
    console.debug('onMessageArrived: ' + message.payloadString + ' - ' + message.topic);
    const msg = JSON.parse(message.payloadString);
    // update_game_state(msg);
    // if (msg.message_class === '') {
    //
    // }
    update_game_state_by_message(msg);
    update_agent_state_by_message(msg);
    //if (window.location.pathname === '/ui/active/base') window.location.reload();
}

function onMQTTConnect() {
    console.log("onConnect");
    // this is for the update of the upper left game state
    mqttClient.subscribe(sessionStorage.getItem('mqtt_client_notification_topic') + "#");
}

// https://stackoverflow.com/a/78945/1171329
function selectElement(id, valueToSelect) {
    //const element = document.getElementById(id);
    document.getElementById(id).value = valueToSelect;
}

function reload(params) {
    navigate_to(window.location.href, params);
}

/**
 * this function navigates the browser with certain options
 * @param destination the target url to navigate to
 * @param params the params to be used with the url. locale and game_id are always added to this object
 * @returns {boolean}
 */
function navigate_to(destination, params) {
    if (!params) params = {};
    const url = new URL(destination);
    // game_id and locale are always present;
    url.searchParams.set('locale', document.getElementById('locale').value);
    url.searchParams.set('game_id', document.getElementById('game_id').value);

    jQuery.each(params, (key, value) => url.searchParams.set(key, value));
    // reloads the window with the NEW params
    window.location.href = url.href;
    return false; // to prevent a-tag default behaviour
}

function get_current_search_param_value(param, default_value) {
    const urlParams = new URLSearchParams(window.location.search);
    if (!default_value) default_value = "";
    if (urlParams.has(param)) return urlParams.get(param);
    else return default_value;
}

// simple convenience function
function get_game_id() {
    return new URL(window.location.href).searchParams.get("game_id") || '1'
}

function get_locale() {
    return new URL(window.location.href).searchParams.get("locale") || 'de'
}

function update_agent_state_by_message(message) {
    if (window.location.pathname !== '/ui/agents') return;

    if ('agent_state_change'.localeCompare(message.message_class) !== 0
        && 'agent_event'.localeCompare(message.message_class) !== 0) return;

    window.location.reload();
}

function update_game_state_by_message(message) {
    if ('game_state_change'.localeCompare(message.message_class) !== 0) return;
    // this is for a different game - don't care right now
    if (parseInt(get_game_id()) !== message.game_id) return;
    update_game_state_upper_left(message.game_state.state);
    if (window.location.pathname === '/ui/active/base') window.location.reload();
}

function update_game_state_upper_left(state) {
    const color = game_state_colors[state];
    document.getElementById('game_state').innerHTML = '&nbsp;' + state;
    document.getElementById('game_state').setAttribute('style', 'color: ' + color);
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

/**
 * this updates the bottom most line which shows the result status of the last REST contact
 */
function update_rest_status_line() {
    document.getElementById('rest_result').innerHTML = sessionStorage.getItem('rest_result_html') || '&nbsp;No REST status, yet.';
    document.getElementById('rest_result').className = sessionStorage.getItem('rest_result_class') || 'bi bi-question-circle-fill bi-md text-secondary';
}

function update_rest_status_in_session_storage(xhttp) {
    if (xhttp.readyState !== 4) return;
    sessionStorage.setItem('rest_result_html', `&nbsp;${xhttp.status}&nbsp;${statusMessages[xhttp.status]}`);
    sessionStorage.setItem('rest_result_class', xhttp.status >= 400 ? 'bi bi-lightning-fill bi-md text-danger' : 'bi bi-check-circle-fill bi-md text-success');
    if (xhttp.status >= 400) {
        const now = new Date();
        sessionStorage.setItem('last_rest_error_timestamp', now.toLocaleString());
        sessionStorage.setItem('last_rest_error_status', `${statusMessages[xhttp.status]}`);
        sessionStorage.setItem('last_rest_error_response', xhttp.response);
    }
}


function rest(rest_uri, param_json, http_method, body, callback) {
    const xhttp = new XMLHttpRequest();
    xhttp.onload = () => {
        console.debug(xhttp);
        if (callback) callback(xhttp);
    };

    xhttp.onreadystatechange = () => {
        console.log(xhttp);
        update_rest_status_in_session_storage(xhttp);
        if (http_method !== "GET") update_rest_status_line();
    };
    // create the uri
    const base_uri = window.location.origin + '/api/' + rest_uri;
    // always add the locale if possible
    if (http_method !== "GET") param_json.locale = document.getElementById('locale').value;

    // param, traditional setting - see https://api.jquery.com/jQuery.param/#jQuery-param-obj-traditional
    const my_uri = base_uri + (param_json ? '?' + jQuery.param(param_json, true) : '');
    // async needs to be false, otherwise, Safari and Firefox will have wrong status reports on the xhttp.status
    // https://stackoverflow.com/a/61587856/1171329
    xhttp.open(http_method, my_uri, false);
    xhttp.setRequestHeader('Content-type', 'application/json');
    xhttp.setRequestHeader('X-API-KEY', sessionStorage.getItem('X-API-KEY'));
    xhttp.send(body ? JSON.stringify(body) : '{}');
    return xhttp.response
}

function get_rest(rest_uri, param_json, callback) {
    return rest(rest_uri, param_json, 'GET', {}, callback);
}

function delete_rest(rest_uri, param_json, body, callback) {
    rest(rest_uri, param_json, 'DELETE', body, callback);
}

function post_rest(rest_uri, param_json, body, callback) {
    rest(rest_uri, param_json, 'POST', body, callback);
}

function put_rest(rest_uri, param_json, body, callback) {
    rest(rest_uri, param_json, 'PUT', body, callback);
}

function patch_rest(rest_uri, param_json, body, callback) {
    rest(rest_uri, param_json, 'PATCH', body, callback);
}

/**
 * Durstenfeld Shuffle
 * https://stackoverflow.com/a/12646864/1171329
 * shuffles array in place
 * @param {Array} array items An array containing the items.
 * @returns the array
 */
function shuffle(array) {
    for (let i = array.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [array[i], array[j]] = [array[j], array[i]];
    }
    return array;
}

/**
 * Kryten's list shuffle algorithm.
 * @returns {*[]}
 */
function krytens_shuffle(lstAgents, nrCycles, nrLookback) {
    shuffle(lstAgents);
    let seqAgents = [...lstAgents]; // ES6 way to clone an array

    for (x = 0; x < nrCycles - 1; x++) {
        const lookBack = lstAgents.slice(-nrLookback);
        const eligibleItems = lstAgents.slice(0, -nrLookback);
        shuffle(eligibleItems);
        lstAgents = eligibleItems.slice(0, nrLookback);
        lookBack.push(...eligibleItems.slice(nrLookback));
        shuffle(lookBack);
        lstAgents.push(...lookBack);
        seqAgents.push(...lstAgents);
    }

    return seqAgents;
}

function get_hours(seconds) {
    return Math.floor(seconds / 3600);
}

function get_minutes(seconds) {
    return Math.floor(seconds / 60);
}

/**
 * delivers a simplified presentation of a period of time.
 * @param seconds
 * @returns {string|*}
 */
function get_simplified_elapsed_time(seconds) {
    let result;
    if (get_hours(seconds) > 0) {
        result = '~' + get_hours(seconds) + 'h';
    } else if (get_minutes(seconds) > 0) {
        result = '~' + get_minutes(seconds) + 'm';
    } else {
        result = seconds + 's';
    }
    return result;
}

// https://stackoverflow.com/a/30800715
function download_json(data, filename) {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(data, null, 4));
    const downloadAnchorNode = document.createElement('a');
    downloadAnchorNode.setAttribute("href", dataStr);
    downloadAnchorNode.setAttribute("download", filename + ".json");
    downloadAnchorNode.click();
    downloadAnchorNode.remove();
}

// https://stackoverflow.com/a/30800715
// >> Download JSON object as a file from browser <<
// function download_game_parameters() {
//     const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(game_parameters, null, 4));
//     const downloadAnchorNode = document.createElement('a');
//     downloadAnchorNode.setAttribute("href", dataStr);
//     downloadAnchorNode.setAttribute("download", game_parameters.mode + ".json");
//     downloadAnchorNode.click();
//     downloadAnchorNode.remove();
// }

function read_preset_game(saved_game_pk) {
    const json_string = get_rest('saved_games/', {saved_game_pk});
    game_parameters = JSON.parse(json_string);

    if (!$.isEmptyObject(game_parameters)) { // check if game_parameters is not empty
        sessionStorage.setItem('game_parameters', json_string);
        navigate_to(window.location.origin + '/ui/params/base', {'game_mode': game_parameters.game_mode});
    }
}

function show_result_alert(message, element_id, alert_class) {
    document.getElementById(element_id).innerHTML = message;
    document.getElementById(element_id).classList.remove('d-none');
    document.getElementById(element_id).classList.add('fade');
    document.getElementById(element_id).classList.add('show');
    document.getElementById(element_id).classList.add(alert_class);
    setTimeout(function () {
        document.getElementById(element_id).classList.add('d-none');
    }, 4000);
}

function show_post_result_alert(xhttp, element_id) {
    const response = JSON.parse(xhttp.responseText)
    document.getElementById(element_id).innerHTML = response['message'];
    document.getElementById(element_id).classList.remove('d-none');
    document.getElementById(element_id).classList.add('fade');
    document.getElementById(element_id).classList.add('show');
    document.getElementById(element_id).classList.add(xhttp.status >= 400 ? 'alert-danger' : 'alert-primary');
    setTimeout(function () {
        document.getElementById(element_id).classList.add('d-none');
    }, 4000);
}

function loadFile(filePath) {
    let result = null;
    const xmlhttp = new XMLHttpRequest();
    xmlhttp.open("GET", filePath, false);
    xmlhttp.send();
    if (xmlhttp.status == 200) {
        result = xmlhttp.responseText;
    }
    return result;
}
