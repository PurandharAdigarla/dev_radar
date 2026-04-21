package com.devradar.mcp;

import com.devradar.action.application.ActionApplicationService;
import com.devradar.domain.ApiKeyScope;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.ActionProposalDTO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ActionMcpTools {

    private final ActionApplicationService actions;

    public ActionMcpTools(ActionApplicationService actions) { this.actions = actions; }

    public record ProposePrResult(String status, String prUrl) {}

    @Tool(name = "propose_pr_for_cve",
          description = "Execute a previously proposed CVE-fix PR on the user's GitHub repo. Requires WRITE scope.")
    @RequireScope(ApiKeyScope.WRITE)
    public ProposePrResult proposePrForCve(
        @ToolParam(description = "The ActionProposal ID to approve") Long proposalId,
        @ToolParam(description = "The target fix version to upgrade to") String fixVersion) {

        Long uid = SecurityUtils.getCurrentUserId();
        ActionProposalDTO out = actions.approveForUser(uid, proposalId, fixVersion);
        return new ProposePrResult(out.status().name(), out.prUrl());
    }
}
