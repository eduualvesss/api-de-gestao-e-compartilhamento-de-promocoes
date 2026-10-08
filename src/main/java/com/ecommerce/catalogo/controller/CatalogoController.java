package com.ecommerce.catalogo.controller;

import com.ecommerce.catalogo.controller.dto.CatalogoItemRequest;
import com.ecommerce.catalogo.controller.dto.CatalogoRequest;
import com.ecommerce.catalogo.controller.dto.CatalogoResponse;
import com.ecommerce.catalogo.service.CatalogoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/catalogos")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogoResponse criar(@Valid @RequestBody CatalogoRequest req) {
        return service.criar(req);
    }

    @GetMapping
    public List<CatalogoResponse> listar() {
        return service.listar();
    }

    @PutMapping("/{id}")
    public CatalogoResponse atualizar(@PathVariable Long id, @Valid @RequestBody CatalogoRequest req) {
        return service.atualizar(id, req);
    }

    @PostMapping("/{id}/produtos")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogoResponse adicionar(@PathVariable Long id, @Valid @RequestBody CatalogoItemRequest req) {
        return service.adicionarProduto(id, req.sku(), req.ordem());
    }

    /** DELETE /catalogos/{id}/produtos?sku=XYZ */
    @DeleteMapping("/{id}/produtos")
    public CatalogoResponse remover(@PathVariable Long id, @RequestParam String sku) {
        return service.removerProduto(id, sku);
    }

    /** Reordena um item: PATCH /catalogos/{id}/produtos com {sku, ordem}. */
    @PatchMapping("/{id}/produtos")
    public CatalogoResponse reordenar(@PathVariable Long id, @Valid @RequestBody CatalogoItemRequest req) {
        return service.alterarOrdem(id, req.sku(), req.ordem());
    }
}
