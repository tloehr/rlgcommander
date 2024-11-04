package de.flashheart.rlg.commander.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class WebRootController {
    @Value("${server.locale.default}")
    public String default_locale;

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("locale", default_locale);
        return "login";
    }

}
