package dev.patchreceipt.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class DashboardController {

    @GetMapping("/")
    String dashboard() {
        return "index";
    }
}
