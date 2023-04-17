package de.flashheart.rlg.commander.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

@Controller
@Log4j2
public class MyErrorController implements ErrorController {

    BuildProperties buildProperties;

    public MyErrorController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @ModelAttribute("server_version")
    public String getServerVersion() {
        return String.format("v%s b%s", buildProperties.getVersion(), buildProperties.get("buildNumber"));
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        log.debug(status);

        // todo: analysieren

//        if (status != null) {
//            Integer statusCode = Integer.valueOf(status.toString());
//
//            if(statusCode == HttpStatus.NOT_FOUND.value()) {
//                return "error404";
//            }
//            else if(statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
//                return "error500";
//            }
//        }
        return "error";
    }

}
