package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditoriaViewController {

    @GetMapping("/auditoria")
    public String auditoria(Model model) {
        model.addAttribute("content", "auditoria :: content");
        return "layout";
    }
}
