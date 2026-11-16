package com.conkord.saigt.controller.workflow;

import com.conkord.saigt.dto.workflow.WorkflowResponse;
import com.conkord.saigt.service.workflow.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Só leitura, para MASTER. */
@RestController
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/api/v1/workflows")
    public List<WorkflowResponse> listar() {
        return workflowService.listar();
    }
}
