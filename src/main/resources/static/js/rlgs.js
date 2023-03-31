function change_of_sides() {
    var redfor = $("#redfor").val();
    var blufor = $("#blufor").val();
    $("#redfor").val(blufor);
    $("#blufor").val(redfor);
}

function load_header_footer() {
    $("#myheader").load("header.html");
    $("#myfooter").load("footer.html");
};

function loadJson(url) {
    var data1;
    var xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function () {
        if (this.readyState == 4 && this.status == 200) {
            data1 = JSON.parse(this.responseText);
        }
    };
    xhttp.open("GET", url, true);
    xhttp.send();
    console.log(data1);
    return data1;
}

/**
 * Registers cleanup functions before we navigate to a different page.
 */
window.onbeforeunload = function () {
    // disable onclose handler first
    // websocket.onclose = function () {};
    // websocket.close();
    console.log("unload");
};

function post_rest(resturi, param_json, body) {
    const xhttp = new XMLHttpRequest();
    const base_url = window.location.origin + '/api/' + resturi;
    xhttp.onreadystatechange = function () {
        if (this.readyState == 4 && this.status == 200) {
            alert(this.responseText);
        }
    };

    const myuri = base_url + (param_json ? '?' + jQuery.param(param_json) : '');
    xhttp.open('POST', myuri, true);
    xhttp.setRequestHeader('Content-type', 'application/json');
    xhttp.send(body ? body : '{}');
}

function test_agent(agentid, deviceid, pattern) {
    console.log("agentid: " + agentid);
    var xhttp = new XMLHttpRequest();
    var base_url = window.location.origin + "/api/system/test_agent";
    xhttp.onreadystatechange = function () {
        if (this.readyState == 4 && this.status == 200) {
            alert(this.responseText);
        }
    };
    xhttp.open("POST", base_url + "?agentid=" + agentid + "&deviceid=" + deviceid + "&pattern=" + pattern, true);
    xhttp.setRequestHeader("Content-type", "application/json");
    xhttp.send("{}");
}

