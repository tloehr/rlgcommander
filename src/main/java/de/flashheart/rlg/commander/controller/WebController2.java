package de.flashheart.rlg.commander.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Log4j2
@RequestMapping("websockets")
public class WebController2 {


    @GetMapping("/ws")
    public String greeting() {
        return "ws";
    }


}
