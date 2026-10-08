package com.ecommerce.catalogo.controller;

import com.ecommerce.catalogo.controller.dto.*;
import com.ecommerce.catalogo.service.PromocaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/promocoes")
public class PromocaoController {

    private final PromocaoService service;

    public PromocaoController(PromocaoService service) {
        this.service = service;
    }

    @PostMapping("/percentual")
    @ResponseStatus(HttpStatus.CREATED)
    public PromocaoResponse percentual(@Valid @RequestBody DescontoPercentualRequest r) {
        return PromocaoResponse.de(service.criarPercentual(r.sku(), r.percentual()));
    }

    @PostMapping("/quantidade")
    @ResponseStatus(HttpStatus.CREATED)
    public PromocaoResponse quantidade(@Valid @RequestBody DescontoQuantidadeRequest r) {
        return PromocaoResponse.de(service.criarQuantidade(r.sku(), r.qtdMinima(), r.qtdGratis()));
    }

    @PostMapping("/cupons")
    @ResponseStatus(HttpStatus.CREATED)
    public PromocaoResponse cupom(@Valid @RequestBody CupomRequest r) {
        return PromocaoResponse.de(service.criarCupom(r.codigo(), r.valorFixo(), r.validade(), r.skuEspecifico()));
    }

    @GetMapping
    public List<PromocaoResponse> listar(@RequestParam(required = false) String produto,
                                         @RequestParam(required = false) String cupom) {
        return service.listar(produto, cupom).stream().map(PromocaoResponse::de).toList();
    }

    @PatchMapping("/{id}/desativar")
    public PromocaoResponse desativar(@PathVariable Long id) {
        return PromocaoResponse.de(service.desativar(id));
    }
}
