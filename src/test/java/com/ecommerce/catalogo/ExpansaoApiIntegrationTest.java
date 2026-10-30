package com.ecommerce.catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ExpansaoApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveExporActuatorHealthEListarTodasAsNoveLojasComStatusRealTransparente() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.retailerConnectors.details.totalRegistered").value(9));

        mockMvc.perform(get("/lojas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[0].retailer").value("SHOPEE"));
    }

    @Test
    void deveOperarGestaoDeConectoresBuscaAgregadaEmSandboxEManterRetrocompatibilidadeDeProdutos() throws Exception {
        // 1. Sem credenciais e sandbox=false por padrão, busca não fabrica ofertas falsas
        mockMvc.perform(patch("/lojas/SHOPEE/modo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sandboxMode\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("MISSING_CREDENTIALS"));

        mockMvc.perform(get("/ofertas/busca").param("lojas", "SHOPEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReturned").value(0))
                .andExpect(jsonPath("$.storeStatuses[0].status").value("SKIPPED_MISSING_CREDENTIALS"));

        // 2. Ativa explicitamente sandbox em SHOPEE e AMAZON e executa busca deduplicada
        mockMvc.perform(patch("/lojas/SHOPEE/ativar").param("sandbox", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("UP_SANDBOX"));

        mockMvc.perform(patch("/lojas/AMAZON/ativar").param("sandbox", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("UP_SANDBOX"));

        mockMvc.perform(get("/ofertas/busca")
                        .param("termo", "Galaxy S24")
                        .param("lojas", "SHOPEE,AMAZON")
                        .param("deduplicarEntreLojas", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sandboxDataIncluded").value(true))
                .andExpect(jsonPath("$.totalReturned").value(1))
                .andExpect(jsonPath("$.offers[0].retailer").value("AMAZON"))
                .andExpect(jsonPath("$.offers[0].dataSourceMode").value("SANDBOX"))
                .andExpect(jsonPath("$.offers[0].skuCanonical").value("EAN-7891234567890"));

        // 3. Consulta individual de oferta em sandbox e desativação de loja
        mockMvc.perform(get("/ofertas/SHOPEE/2481920391"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retailer").value("SHOPEE"))
                .andExpect(jsonPath("$.dataSourceMode").value("SANDBOX"));

        mockMvc.perform(patch("/lojas/AMAZON/desativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.operationalStatus").value("DISABLED"));

        // 4. Retrocompatibilidade com o CRUD legado de produtos
        String novoProdutoJson = """
                {
                  "sku": "SKU-TEST-01",
                  "nome": "Teclado Mecanico ABNT2",
                  "preco": 299.90,
                  "ativo": true,
                  "tipoProduto": "FISICO",
                  "peso": 0.85,
                  "altura": 4.0,
                  "largura": 36.0,
                  "profundidade": 13.5
                }
                """;
        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(novoProdutoJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-TEST-01"));
    }
}

