package de.flashheart.rlg.commander.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class WebRootController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

}
