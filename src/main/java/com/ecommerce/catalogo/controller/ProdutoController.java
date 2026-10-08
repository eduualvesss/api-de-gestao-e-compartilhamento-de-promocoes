package com.ecommerce.catalogo.controller;

import com.ecommerce.catalogo.controller.dto.ProdutoRequest;
import com.ecommerce.catalogo.controller.dto.ProdutoResponse;
import com.ecommerce.catalogo.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/produtos")
public class ProdutoController {

    private final ProdutoService service;

    public ProdutoController(ProdutoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProdutoResponse> criar(@Valid @RequestBody ProdutoRequest req) {
        ProdutoResponse resp = ProdutoResponse.de(service.cadastrar(req));
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{sku}").buildAndExpand(resp.sku()).toUri();
        return ResponseEntity.created(uri).body(resp);
    }

    @GetMapping
    public List<ProdutoResponse> listar(@RequestParam(required = false) String busca) {
        return service.buscar(busca).stream().map(ProdutoResponse::de).toList();
    }

    @GetMapping("/{sku}")
    public ProdutoResponse obter(@PathVariable String sku) {
        return ProdutoResponse.de(service.buscarPorSku(sku));
    }

    @PutMapping("/{sku}")
    public ProdutoResponse atualizar(@PathVariable String sku, @Valid @RequestBody ProdutoRequest req) {
        return ProdutoResponse.de(service.atualizar(sku, req));
    }

    @DeleteMapping("/{sku}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable String sku) {
        service.remover(sku);
    }
}
