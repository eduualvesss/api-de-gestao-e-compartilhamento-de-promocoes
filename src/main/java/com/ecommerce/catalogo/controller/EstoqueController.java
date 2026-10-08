package com.ecommerce.catalogo.controller;

import com.ecommerce.catalogo.controller.dto.EstoqueResponse;
import com.ecommerce.catalogo.controller.dto.QuantidadeRequest;
import com.ecommerce.catalogo.service.EstoqueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/estoque")
public class EstoqueController {

    private final EstoqueService service;

    public EstoqueController(EstoqueService service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    public EstoqueResponse consultar(@PathVariable String sku) {
        return EstoqueResponse.de(service.consultar(sku));
    }

    @PostMapping("/{sku}/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public EstoqueResponse reservar(@PathVariable String sku, @Valid @RequestBody QuantidadeRequest req) {
        return EstoqueResponse.de(service.reservar(sku, req.quantidade()));
    }

    @PostMapping("/{sku}/reposicoes")
    @ResponseStatus(HttpStatus.CREATED)
    public EstoqueResponse repor(@PathVariable String sku, @Valid @RequestBody QuantidadeRequest req) {
        return EstoqueResponse.de(service.repor(sku, req.quantidade()));
    }
}
