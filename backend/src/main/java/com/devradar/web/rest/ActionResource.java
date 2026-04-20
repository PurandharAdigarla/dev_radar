package com.devradar.web.rest;

import com.devradar.action.application.ActionApplicationService;
import com.devradar.web.rest.dto.ActionProposalDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/actions")
public class ActionResource {

    private final ActionApplicationService app;
    public ActionResource(ActionApplicationService app) { this.app = app; }

    @GetMapping("/proposals")
    public List<ActionProposalDTO> proposalsForRadar(@RequestParam("radar_id") Long radarId) {
        return app.listForRadar(radarId);
    }

    @PostMapping("/{id}/approve")
    public ActionProposalDTO approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return app.approve(id, body.getOrDefault("fix_version", "latest"));
    }

    @DeleteMapping("/{id}")
    public ActionProposalDTO dismiss(@PathVariable Long id) {
        return app.dismiss(id);
    }
}
