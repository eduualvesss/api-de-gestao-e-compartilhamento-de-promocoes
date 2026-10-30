package com.ecommerce.catalogo.controller;

import com.ecommerce.catalogo.controller.dto.AlterarModoConectorRequest;
import com.ecommerce.catalogo.integration.connector.ConnectorRegistry;
import com.ecommerce.catalogo.integration.connector.RetailerConnector;
import com.ecommerce.catalogo.integration.model.ConnectorHealth;
import com.ecommerce.catalogo.integration.model.RetailerId;
import com.ecommerce.catalogo.integration.resilience.ConnectorResilienceExecutor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de gestão operacional dos conectores de varejistas:
 * listagem, ativação/desativação por Feature Flag, alternância explícita de modo Sandbox e Health Checks.
 */
@RestController
@RequestMapping("/lojas")
public class LojaConectorController {

    private final ConnectorRegistry registry;
    private final ConnectorResilienceExecutor resilienceExecutor;

    public LojaConectorController(ConnectorRegistry registry, ConnectorResilienceExecutor resilienceExecutor) {
        this.registry = registry;
        this.resilienceExecutor = resilienceExecutor;
    }

    @GetMapping
    public List<ConnectorHealth> listar() {
        return registry.checkAllHealth();
    }

    @GetMapping("/health")
    public Map<String, Object> healthConsolidado() {
        List<ConnectorHealth> lojas = registry.checkAllHealth();
        long ativas = lojas.stream().filter(ConnectorHealth::enabled).count();
        long operacionais = lojas.stream()
                .filter(l -> "UP_REAL".equals(l.operationalStatus()) || "UP_SANDBOX".equals(l.operationalStatus()))
                .count();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "UP");
        resp.put("totalLojasRegistradas", lojas.size());
        resp.put("totalLojasAtivas", ativas);
        resp.put("totalLojasProntasParaBusca", operacionais);
        resp.put("conectores", lojas);
        return resp;
    }

    @GetMapping("/{loja}/health")
    public ConnectorHealth healthPorLoja(@PathVariable String loja) {
        RetailerId id = RetailerId.fromPathOrParam(loja);
        return registry.getRequired(id).checkHealth();
    }

    @PatchMapping("/{loja}/ativar")
    public ConnectorHealth ativar(@PathVariable String loja,
                                  @RequestParam(required = false) Boolean sandbox) {
        RetailerId id = RetailerId.fromPathOrParam(loja);
        RetailerConnector connector = registry.getRequired(id);
        if (sandbox != null) {
            connector.setSandboxMode(sandbox);
        }
        connector.setEnabled(true);
        resilienceExecutor.resetState(id);
        return connector.checkHealth();
    }

    @PatchMapping("/{loja}/desativar")
    public ConnectorHealth desativar(@PathVariable String loja) {
        RetailerId id = RetailerId.fromPathOrParam(loja);
        RetailerConnector connector = registry.getRequired(id);
        connector.setEnabled(false);
        return connector.checkHealth();
    }

    @PatchMapping("/{loja}/modo")
    public ConnectorHealth alterarModo(@PathVariable String loja,
                                       @Valid @RequestBody AlterarModoConectorRequest req) {
        RetailerId id = RetailerId.fromPathOrParam(loja);
        RetailerConnector connector = registry.getRequired(id);
        connector.setSandboxMode(req.sandboxMode());
        resilienceExecutor.resetState(id);
        return connector.checkHealth();
    }
}

