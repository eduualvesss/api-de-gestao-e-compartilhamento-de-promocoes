package com.ecommerce.catalogo.controller;

import com.ecommerce.catalogo.controller.dto.PrecoFinalResponse;
import com.ecommerce.catalogo.service.PrecoFinalService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/produtos")
public class PrecoFinalController {

    private final PrecoFinalService service;

    public PrecoFinalController(PrecoFinalService service) {
        this.service = service;
    }

    @GetMapping("/{sku}/preco-final")
    public PrecoFinalResponse precoFinal(@PathVariable String sku,
                                         @RequestParam(defaultValue = "1") int quantidade,
                                         @RequestParam(required = false) String cupom) {
        return service.calcular(sku, quantidade, cupom);
    }
}
