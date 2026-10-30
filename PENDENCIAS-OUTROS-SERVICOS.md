# PENDÊNCIAS NOS OUTROS SERVIÇOS (FORA DO ESCOPO DESTA EXPANSÃO)

> **Regra de Escopo Aplicada:** Durante esta execução, apenas o **Serviço 1 (API de Gestão/Busca de Promoções)** pôde ser alterado. Os demais componentes do pipeline (**API de Filtros**, **Banco de Dados Compartilhado** e **API de Compartilhamento**) foram tratados como **SOMENTE LEITURA**.
>
> **Constatação no Ambiente Local (Premissa Verificada):** Foi inspecionado o workspace local (`c:\Users\Eduardo\Desktop\codigo\API de gestao`) e diretórios irmãos (`c:\Users\Eduardo\Desktop\codigo\api`, que está vazio). Os repositórios da **API de Filtros** e da **API de Compartilhamento** não estão presentes localmente, nem há instância PostgreSQL ativa na porta `5432`. Abaixo estão todas as pendências obrigatórias e evolutivas que devem ser executadas nos outros 3 componentes para suportar integralmente a expansão da API de Busca.

---

## 1. API DE FILTROS (SERVIÇO 2)

### P-FIL-01 — Implementar/Expor Endpoint de Avaliação em Lote (`POST /api/v1/filter/offers:evaluate-batch`) [BLOQUEANTE PARA O PIPELINE]
- **O que mudar:** Implementar o endpoint `POST /api/v1/filter/offers:evaluate-batch` aceitando o payload `FilterBatchRequest` enviado pelo `FilterServiceClient` da API de Busca, além de validar os headers `X-Internal-Api-Key`, `X-Idempotency-Key` e `X-Correlation-Id`.
- **Por quê:** Antes desta expansão, não havia nenhum contrato ativo no código da API de Busca (`src/main/java/com/ecommerce/catalogo/` não possuía clientes HTTP). A API de Busca agora envia lotes de ofertas normalizadas no formato `UnifiedOffer` (incluindo o campo explícito `dataSourceMode`: `"REAL"` ou `"SANDBOX"`).
- **Impacto se não feito:** A API de Busca continuará coletando e deduplicando ofertas normalmente (degradação graciosa implementada em `FilterServiceClient` quando `FILTER_API_ENABLED=false` ou em falha HTTP), mas as ofertas não passarão pelo crivo de qualidade nem seguirão para publicação automática.
- **Contrato JSON Esperado:**
  - **Request (`POST /api/v1/filter/offers:evaluate-batch`):**
    ```json
    {
      "batchId": "b8c4e1a0-92f1-4c11-8d2e-112233445566",
      "collectedAt": "2026-10-29T23:50:00Z",
      "sourceInstance": "catalogo-promocoes",
      "offers": [
        {
          "internalId": "0192d8f4-7a10-7b2c-9d3e-4f5a6b7c8d9e",
          "retailer": "SHOPEE",
          "connectorSource": "SHOPEE_OPEN_API",
          "dataSourceMode": "REAL",
          "externalId": "2481920391",
          "eanGtin": "7891234567890",
          "skuCanonical": "EAN-7891234567890",
          "title": "Apple iPhone 15 128GB Preto 5G",
          "urlOriginal": "https://shopee.com.br/product/123456/2481920391",
          "urlAffiliate": "https://s.shopee.com.br/4f9aBcDeFg",
          "pricing": {
            "currency": "BRL",
            "currentPrice": 4299.00,
            "originalPrice": 5499.00,
            "discountAbsolute": 1200.00,
            "discountPercentage": 21.82,
            "isInternational": false,
            "estimatedImportTax": 0.00
          }
        }
      ]
    }
    ```
  - **Response (`200 OK` ou `207 Multi-Status`):**
    ```json
    {
      "batchId": "b8c4e1a0-92f1-4c11-8d2e-112233445566",
      "processedAt": "2026-10-29T23:50:02Z",
      "summary": { "received": 1, "approved": 1, "rejected": 0 },
      "results": [
        {
          "internalId": "0192d8f4-7a10-7b2c-9d3e-4f5a6b7c8d9e",
          "externalId": "2481920391",
          "retailer": "SHOPEE",
          "status": "APPROVED",
          "qualityScore": 92.5,
          "realDiscountVerified": true,
          "rejectionReasons": []
        }
      ]
    }
    ```

### P-FIL-02 — Bloqueio de Dados `SANDBOX` em Produção [CRÍTICO / SEGURANÇA OPERACIONAL]
- **O que mudar:** Adicionar regra prioritária na API de Filtros para rejeitar automaticamente (`status = REJECTED`, motivo `SANDBOX_OFFER_NOT_PUBLISHABLE`) qualquer oferta com `"dataSourceMode": "SANDBOX"` quando a API de Filtros estiver rodando em ambiente `prod`.
- **Por quê:** Os conectores da API de Busca operam em modo `SANDBOX` explícito por flag quando não há credenciais reais para testes de homologação. Bloquear no Filtro garante dupla proteção contra envio acidental aos canais finais.
- **Impacto:** Evita publicação de ofertas de homologação no Telegram/WhatsApp em caso de erro humano de configuração de flag.

### P-FIL-03 — Regra Anti-Fraude de Falso Desconto ("Metade do Dobro") e Imposto Internacional [IMPORTANTE]
- **O que mudar:**
  1. Calcular o desconto real comparando `pricing.currentPrice` + `pricing.estimatedImportTax` + `shipping.minShippingCost` (menos cupom aplicável) contra a **mediana dos últimos 30 dias** na tabela `historico_preco_oferta`, ignorando `pricing.originalPrice` inflado pelo vendedor.
  2. Aplicar corte mínimo de estoque (`stock.availableQuantity >= 3` quando informado) e avaliação mínima (`rating.averageStars >= 4.2`).
- **Por quê:** Ofertas internacionais (ex.: AliExpress) vêm da API sem ICMS/Imposto de Importação no `target_sale_price` (estimado agora pela API de Busca em `pricing.estimatedImportTax`), e marketplaces frequentemente inflam o preço riscado em datas sazonais.
- **Impacto:** Protege a credibilidade dos canais de promoções e aumenta a taxa de conversão de afiliados.

---

## 2. BANCO DE DADOS COMPARTILHADO (SERVIÇO 3 — PostgreSQL)

### P-DB-01 — Provisionamento da Instância PostgreSQL e Migração DDL de Ofertas Externas [BLOQUEANTE PARA PERSISTÊNCIA MULTI-SERVIÇO]
- **O que mudar:** Provisionar PostgreSQL 16 (`catalogo`), habilitar a extensão `pg_trgm` e aplicar a migração SQL abaixo no schema compartilhado (via Flyway/Liquibase coordenado pelo serviço dono do schema de ofertas filtradas).
- **Por quê:** O schema atual mapeado em `src/main/java/com/ecommerce/catalogo/model/` contém apenas as 10 tabelas de produtos próprios locais (`produto`, `produto_fisico`, `produto_digital`, `promocao`, `desconto_percentual`, `desconto_quantidade`, `cupom_promocional`, `estoque`, `catalogo`, `catalogo_produto`). Ele não possui tabelas para ofertas multi-varejista aprovadas pela API de Filtros nem série temporal de preços.
- **Impacto:** Sem essas tabelas, a API de Filtros não consegue gravar as ofertas aprovadas nem consultar a mediana histórica de 30/90 dias, e a API de Compartilhamento não tem de onde ler a fila de publicação.
- **DDL SQL Necessário (para aplicar na migração do Banco de Dados):**
  ```sql
  CREATE EXTENSION IF NOT EXISTS pg_trgm;

  CREATE TABLE IF NOT EXISTS loja_parceira (
      id_loja VARCHAR(30) PRIMARY KEY,
      nome_exibicao VARCHAR(100) NOT NULL,
      ativa BOOLEAN NOT NULL DEFAULT TRUE,
      rate_limit_tps NUMERIC(6,2) DEFAULT 5.0,
      criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );

  CREATE TABLE IF NOT EXISTS oferta_externa (
      id UUID PRIMARY KEY,
      id_loja VARCHAR(30) NOT NULL REFERENCES loja_parceira(id_loja),
      id_externo VARCHAR(100) NOT NULL,
      modo_origem VARCHAR(15) NOT NULL DEFAULT 'REAL',
      sku_canonico VARCHAR(120),
      ean_gtin VARCHAR(20),
      titulo VARCHAR(500) NOT NULL,
      url_original TEXT NOT NULL,
      url_afiliado TEXT NOT NULL,
      url_imagem TEXT,
      moeda CHAR(3) NOT NULL DEFAULT 'BRL',
      preco_atual NUMERIC(12,2) NOT NULL,
      preco_original NUMERIC(12,2),
      preco_pix NUMERIC(12,2),
      percentual_desconto NUMERIC(5,2),
      internacional BOOLEAN NOT NULL DEFAULT FALSE,
      imposto_estimado NUMERIC(12,2) NOT NULL DEFAULT 0.00,
      codigo_cupom VARCHAR(60),
      valor_cupom NUMERIC(12,2),
      frete_gratis BOOLEAN NOT NULL DEFAULT FALSE,
      avaliacao_media NUMERIC(3,2),
      total_avaliacoes INT,
      vendedor_nome VARCHAR(200),
      loja_oficial BOOLEAN NOT NULL DEFAULT FALSE,
      estoque_disponivel INT,
      em_estoque BOOLEAN NOT NULL DEFAULT TRUE,
      score_qualidade NUMERIC(5,2),
      status_filtro VARCHAR(20) NOT NULL,
      hash_conteudo VARCHAR(64) NOT NULL,
      coletado_em TIMESTAMPTZ NOT NULL,
      expira_em TIMESTAMPTZ NOT NULL,
      atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CONSTRAINT uq_oferta_loja_externo UNIQUE (id_loja, id_externo)
  );

  CREATE TABLE IF NOT EXISTS historico_preco_oferta (
      id BIGSERIAL PRIMARY KEY,
      id_oferta UUID NOT NULL REFERENCES oferta_externa(id) ON DELETE CASCADE,
      preco_registrado NUMERIC(12,2) NOT NULL,
      imposto_estimado NUMERIC(12,2) NOT NULL DEFAULT 0.00,
      em_estoque BOOLEAN NOT NULL,
      registrado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );

  CREATE INDEX IF NOT EXISTS idx_oferta_ean_gtin ON oferta_externa (ean_gtin) WHERE ean_gtin IS NOT NULL;
  CREATE INDEX IF NOT EXISTS idx_oferta_sku_canonico ON oferta_externa (sku_canonico);
  CREATE INDEX IF NOT EXISTS idx_oferta_status_score ON oferta_externa (status_filtro, score_qualidade DESC, coletado_em DESC) WHERE em_estoque = TRUE;
  CREATE INDEX IF NOT EXISTS idx_historico_oferta_data ON historico_preco_oferta (id_oferta, registrado_em DESC);
  CREATE INDEX IF NOT EXISTS idx_oferta_titulo_trgm ON oferta_externa USING gin (titulo gin_trgm_ops);
  ```

### P-DB-02 — Usuários Segregados por Serviço (Least Privilege) [DESEJÁVEL / SEGURANÇA]
- **O que mudar:** Criar roles separadas no PostgreSQL (`role_busca`, `role_filtros`, `role_compartilhamento`) em vez de compartilhar o superusuário `postgres`.
- **Por quê:** Evita que um comprometimento na API de Compartilhamento permita `DROP TABLE` ou alteração de regras de filtro.
- **Impacto:** Isolamento de segurança em produção.

---

## 3. API DE COMPARTILHAMENTO (SERVIÇO 4)

### P-SH-01 — Revalidação *Just-in-Time* Antes do Disparo nos Canais [IMPORTANTE]
- **O que mudar:** Antes de enviar uma oferta aprovada ao Telegram ou WhatsApp, chamar `GET http://api-busca:8080/ofertas/{loja}/{idExterno}` na API de Busca expandida para confirmar que `stock.inStock == true` e que `pricing.currentPrice` não subiu desde a coleta.
- **Por quê:** Promoções relâmpago (Shopee, Amazon, Mercado Livre) esgotam em minutos.
- **Impacto:** Elimina envio de ofertas expiradas/esgotadas aos usuários finais.

### P-SH-02 — Templates Ricos com `urlAffiliate`, Cupom, Aviso de Imposto e Compliance CONAR [IMPORTANTE]
- **O que mudar:** Atualizar o formatador de mensagens da API de Compartilhamento para renderizar:
  - `urlAffiliate` (nunca `urlOriginal` pura, para garantir comissionamento);
  - Código de cupom (`coupon.code`) quando presente;
  - Aviso explícito de compra internacional e estimativa de tributos (`pricing.estimatedImportTax`) quando `pricing.isInternational == true`;
  - Rodapé transparente exigido pelo CONAR/CDC (*"Contém link de afiliado. Preço sujeito a alteração."*).
- **Por quê:** Garante monetização real das ofertas e conformidade legal com o Código de Defesa do Consumidor e diretrizes do CONAR para publicidade de afiliados.
- **Impacto:** Monetização efetiva e mitigação de risco jurídico.

### P-SH-03 — Controle de Rate Limit dos Canais (Telegram `retry_after` e WhatsApp `130429`) [IMPORTANTE]
- **O que mudar:** Respeitar o limite de 20 msgs/min por grupo/canal no Telegram (lendo `parameters.retry_after` em respostas HTTP 429) e 80 msgs/s no WhatsApp Cloud API, além de cooldown de 24h por `(id_loja, id_externo)`.
- **Por quê:** Evita bloqueio/mute do bot de promoções pelo Telegram ou suspensão do número WABA pela Meta.
- **Impacto:** Alta disponibilidade na entrega das promoções.

