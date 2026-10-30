# PAPEL E OBJETIVO DESTE PROMPT

Você é um **Engenheiro de Software Principal, Arquiteto de Integrações e Especialista em Editoração Técnica**.
Sua tarefa é executar um script em Python para gerar um **relatório técnico e arquitetural executivo em formato PDF**, completo, profundo, visualmente impecável e autossuficiente, intitulado:

> **"Plano Diretor de Arquitetura e Integração Real de Varejistas — Refinamento da API de Busca de Promoções e Pipeline de Ofertas"**

O arquivo PDF final deve ser salvo no diretório raiz do workspace com o nome:
`relatorio-refinamento-api-busca-promocoes.pdf`

---

# 1. ESPECIFICAÇÕES DE FORMATO, ESTILO E FERRAMENTAL (OBRIGATÓRIO)

1. **Idioma:** Português do Brasil (pt-BR), com tom técnico, objetivo, preciso e executivo.
2. **Formato de Página:** A4 (`210mm x 297mm`), orientação Retrato (Portrait), margens de `18mm` (esquerda/direita) e `20mm` (superior/inferior). Use páginas em Paisagem (Landscape) ou tabelas compactas com `Paragraph` dentro das células para tabelas largas (como Diagnóstico e Comparativo de Varejistas) a fim de evitar qualquer corte ou sobreposição de texto.
3. **Tipografia e Identidade Visual:**
   - Fonte limpa e legível (`Helvetica` / `Helvetica-Bold` / `Courier` para código no ReportLab, ou `Inter`/`Roboto`/`JetBrains Mono` via HTML→PDF com WeasyPrint/Playwright).
   - Paleta de cores corporativa: Azul Escuro (`#0F172A` / `#1E3A8A`) para títulos e cabeçalhos de tabela; Cinza Escuro (`#334155`) para corpo de texto; Cinza Claro (`#F8FAFC` / `#F1F5F9`) para zebra striping em tabelas e blocos de código; cores semânticas de severidade:
     - **CRÍTICO:** Vermelho (`#DC2626`, fundo suave `#FEF2F2`)
     - **IMPORTANTE:** Laranja/Âmbar (`#D97706`, fundo suave `#FFFBEB`)
     - **DESEJÁVEL:** Azul/Verde (`#2563EB`, fundo suave `#EFF6FF`)
4. **Elementos Obrigatórios de Layout:**
   - **Capa dedicada** (Página 1) com título, subtítulo, metadados do projeto (`com.ecommerce:catalogo-promocoes v0.0.1-SNAPSHOT`), data de geração e identificação da arquitetura.
   - **Cabeçalho e Rodapé** em todas as páginas (exceto capa): cabeçalho com título curto do documento e linha divisória sutil; rodapé com data, classificação ("Confidencial — Arquitetura de Engenharia") e **numeração de páginas no formato `Página X de Y`**.
   - **Sumário (Índice) estruturado** logo após a capa, enumerando todas as seções principais e subseções.
   - **Diagramas Arquiteturais:** Desenhe os diagramas diretamente no PDF usando primitivas gráficas (`Drawing`, `Rect`, `String`, `Line`, `Polygon` no ReportLab ou SVG embutido se usar HTML→PDF). Não use ASCII art puro sem formatação gráfica para os diagramas principais; construa caixas vetorizadas com setas claras.
   - **Tabelas com quebra automática de linha:** Toda célula de tabela deve usar objetos `Paragraph` (se ReportLab) para garantir word-wrap perfeito, nunca texto cru que transborde a coluna.
5. **Processo de Geração e Verificação Visual (Regra de Ouro):**
   - Escreva um script Python (ex.: `gerar_pdf.py`) utilizando `reportlab` (ou `weasyprint` / `playwright`, instalando pacotes se necessário).
   - Execute o script para gerar `relatorio-refinamento-api-busca-promocoes.pdf`.
   - **Revisão Visual Obrigatória:** Após gerar o PDF, inspecione-o visualmente (usando leitura direta de PDF ou convertendo páginas para PNG com `pypdfium2` / `pdf2image` / `fitz` e inspecionando com `read_file`) para verificar se não há textos cortados, tabelas estourando margens, sobreposições ou páginas em branco indesejadas. Se encontrar qualquer defeito visual, corrija o script e regere o PDF antes de concluir.

---

# 2. ESTRUTURA OBRIGATÓRIA DO PDF (SEÇÕES 1 A 11)

Você DEVE incluir integralmente todo o conteúdo técnico, evidências de código (`arquivo:linha`), tabelas, payloads, schemas SQL/JSON e links oficiais detalhados abaixo. **Não resuma nem omita achados.**

---

## SEÇÃO 1: CAPA E RESUMO EXECUTIVO

### 1.1 Contexto do Pipeline de 4 Serviços
O ecossistema alvo do cliente é composto por 4 serviços integrados:
1. **API de Busca de Promoções** *(foco desta análise)* — Responsável por coletar, normalizar e deduplicar ofertas reais em varejistas (Mercado Livre, AliExpress, Shopee, Amazon, Magazine Luiza, KaBuM!, Americanas, Casas Bahia, Netshoes).
2. **API de Filtros** — Recebe as ofertas coletadas pela Busca e aplica regras de qualidade, relevância, score de desconto real e prevenção de falsas promoções.
3. **Banco de Dados** — Armazena o catálogo unificado, ofertas filtradas, histórico de preços (SCD / séries temporais) e metadados de afiliados.
4. **API de Compartilhamento** — Publica e distribui as ofertas aprovadas nos canais finais (Telegram, WhatsApp, X/Twitter, Web/App) com links monetizados (tracking de afiliado).

### 1.2 Descoberta Crítica da Fase 1 (Divergência Arquitetural)
A auditoria estática do repositório atual (`com.ecommerce:catalogo-promocoes`, Java 17 + Spring Boot 3.3.4) revelou um **gap arquitetural fundamental**:
- O projeto contido no repositório atual é um **CRUD monolítico local de Gestão de Catálogo, Estoque e Promoções Internas** (produtos físicos e digitais cadastrados manualmente no PostgreSQL, com cálculo sob demanda de descontos percentuais, "leve N pague M" e cupons fixos locais).
- **Não existe atualmente no código nenhuma integração HTTP externa, nenhum conector de varejista, nenhum agendador (scheduler/worker), nenhuma fila de mensagens e nenhuma chamada para a API de Filtros ou API de Compartilhamento.**
- Além disso, há **3 simulações/mocks hardcoded** no domínio atual (`ProdutoDigital.java:30-34`, `ProdutoFisico.java:13-15`, e ausência de clientes externos) e **0% de cobertura de testes automatizados** (`src/test` não existe).
- Portanto, o refinamento exige transformar/estender este serviço de um simples cadastro passivo para um **Motor Ativo de Agregação e Busca de Ofertas Multi-Varejista ( arquitetura Adapter/Connector)** conectado ao pipeline de 4 serviços.

---

## SEÇÃO 2: RECONHECIMENTO PROFUNDO DO REPOSITÓRIO ATUAL (FASE 1)

Apresente no PDF o mapeamento exato do repositório com todas as evidências de arquivo e linha:

### 2.1 Stack, Dependências e Configuração
- **Linguagem e Build:** Java 17 (`pom.xml:15`), Spring Boot `3.3.4` (`pom.xml:6-10`), empacotamento Maven (`pom.xml:11-13`, `groupId=com.ecommerce`, `artifactId=catalogo-promocoes`, `version=0.0.1-SNAPSHOT`). Note-se que o repositório versiona indevidamente um binário `maven.zip` e a pasta `maven_dir/apache-maven-3.9.9` na raiz.
- **Dependências (`pom.xml:17-45`):**
  - `spring-boot-starter-web` (`pom.xml:18-21`) — Servidor REST Tomcat embutido.
  - `spring-boot-starter-data-jpa` (`pom.xml:22-25`) — Persistência Hibernate/JPA.
  - `spring-boot-starter-validation` (`pom.xml:26-29`) — Jakarta Bean Validation.
  - `org.postgresql:postgresql` (`pom.xml:30-34`, `scope=runtime`) — Driver PostgreSQL.
  - `com.h2database:h2` (`pom.xml:35-39`, `scope=test`) e `spring-boot-starter-test` (`pom.xml:40-44`, `scope=test`).
  - **Lacunas críticas em dependências:** Não há cliente HTTP declarativo/reativo (`spring-cloud-starter-openfeign` ou `spring-boot-starter-webflux`), não há resiliência (`resilience4j`), não há cache (`spring-boot-starter-cache` / `caffeine` / `spring-boot-starter-data-redis`), não há observabilidade (`spring-boot-starter-actuator` / `micrometer-registry-prometheus`), não há migração de banco (`flyway-core` / `liquibase`), nem mensageria (`spring-amqp` / `spring-kafka`).
- **Configuração e Variáveis de Ambiente (`src/main/resources/application.properties:1-6`):**
  - Linha 1: `spring.application.name=catalogo-promocoes`
  - Linha 2: `spring.datasource.url=jdbc:postgresql://localhost:5432/catalogo`
  - Linha 3: `spring.datasource.username=postgres`
  - Linha 4: `spring.datasource.password=postgres` *(Credencial em texto claro hardcoded, sem uso de variáveis de ambiente `${DB_PASSWORD}`)*
  - Linha 5: `spring.jpa.hibernate.ddl-auto=update` *(Inseguro para produção)*
  - Linha 6: `spring.jpa.open-in-view=false`
- **Testes, Docker e CI/CD:**
  - Diretório `src/test/`: **Não encontrado** (nenhum teste unitário ou de integração existe no projeto).
  - `Dockerfile`, `docker-compose.yml`, `.github/workflows`, `Jenkinsfile`, `.gitignore`: **Não encontrados**.

### 2.2 Mapeamento Completo de Rotas e Endpoints Atuais
Documente em tabela todos os 17 endpoints existentes nos 5 Controllers REST:

| Controller | Método e Rota | Entrada (DTO / Params) | Saída (DTO / Status) | Evidência (`arquivo:linha`) |
|---|---|---|---|---|
| `ProdutoController` | `POST /produtos` | `@Valid @RequestBody ProdutoRequest` | `201 Created` + `ProdutoResponse` + Header `Location` | `src/main/java/com/ecommerce/catalogo/controller/ProdutoController.java:25-30` |
| `ProdutoController` | `GET /produtos` | `@RequestParam(required=false) String busca` | `200 OK` + `List<ProdutoResponse>` | `src/main/java/com/ecommerce/catalogo/controller/ProdutoController.java:32-35` |
| `ProdutoController` | `GET /produtos/{sku}` | `@PathVariable String sku` | `200 OK` + `ProdutoResponse` | `src/main/java/com/ecommerce/catalogo/controller/ProdutoController.java:37-40` |
| `ProdutoController` | `PUT /produtos/{sku}` | `@PathVariable String sku`, `@Valid @RequestBody ProdutoRequest` | `200 OK` + `ProdutoResponse` | `src/main/java/com/ecommerce/catalogo/controller/ProdutoController.java:42-45` |
| `ProdutoController` | `DELETE /produtos/{sku}` | `@PathVariable String sku` | `204 No Content` | `src/main/java/com/ecommerce/catalogo/controller/ProdutoController.java:47-51` |
| `PrecoFinalController` | `GET /produtos/{sku}/preco-final` | `@PathVariable String sku`, `@RequestParam(defaultValue="1") int quantidade`, `@RequestParam(required=false) String cupom` | `200 OK` + `PrecoFinalResponse` | `src/main/java/com/ecommerce/catalogo/controller/PrecoFinalController.java:17-22` |
| `PromocaoController` | `POST /promocoes/percentual` | `@Valid @RequestBody DescontoPercentualRequest` | `201 Created` + `PromocaoResponse` | `src/main/java/com/ecommerce/catalogo/controller/PromocaoController.java:21-25` |
| `PromocaoController` | `POST /promocoes/quantidade` | `@Valid @RequestBody DescontoQuantidadeRequest` | `201 Created` + `PromocaoResponse` | `src/main/java/com/ecommerce/catalogo/controller/PromocaoController.java:27-31` |
| `PromocaoController` | `POST /promocoes/cupons` | `@Valid @RequestBody CupomRequest` | `201 Created` + `PromocaoResponse` | `src/main/java/com/ecommerce/catalogo/controller/PromocaoController.java:33-37` |
| `PromocaoController` | `GET /promocoes` | `@RequestParam(required=false) String produto`, `@RequestParam(required=false) String cupom` | `200 OK` + `List<PromocaoResponse>` | `src/main/java/com/ecommerce/catalogo/controller/PromocaoController.java:39-43` |
| `PromocaoController` | `PATCH /promocoes/{id}/desativar` | `@PathVariable Long id` | `200 OK` + `PromocaoResponse` | `src/main/java/com/ecommerce/catalogo/controller/PromocaoController.java:45-48` |
| `EstoqueController` | `GET /estoque/{sku}` | `@PathVariable String sku` | `200 OK` + `EstoqueResponse` | `src/main/java/com/ecommerce/catalogo/controller/EstoqueController.java:20-23` |
| `EstoqueController` | `POST /estoque/{sku}/reservas` | `@PathVariable String sku`, `@Valid @RequestBody QuantidadeRequest` | `201 Created` + `EstoqueResponse` | `src/main/java/com/ecommerce/catalogo/controller/EstoqueController.java:25-29` |
| `EstoqueController` | `POST /estoque/{sku}/reposicoes` | `@PathVariable String sku`, `@Valid @RequestBody QuantidadeRequest` | `201 Created` + `EstoqueResponse` | `src/main/java/com/ecommerce/catalogo/controller/EstoqueController.java:31-35` |
| `CatalogoController` | `POST /catalogos` | `@Valid @RequestBody CatalogoRequest` | `201 Created` + `CatalogoResponse` | `src/main/java/com/ecommerce/catalogo/controller/CatalogoController.java:23-27` |
| `CatalogoController` | `GET /catalogos` | *(nenhum)* | `200 OK` + `List<CatalogoResponse>` | `src/main/java/com/ecommerce/catalogo/controller/CatalogoController.java:29-32` |
| `CatalogoController` | `PUT /catalogos/{id}` | `@PathVariable Long id`, `@Valid @RequestBody CatalogoRequest` | `200 OK` + `CatalogoResponse` | `src/main/java/com/ecommerce/catalogo/controller/CatalogoController.java:34-37` |
| `CatalogoController` | `POST /catalogos/{id}/produtos` | `@PathVariable Long id`, `@Valid @RequestBody CatalogoItemRequest` | `201 Created` + `CatalogoResponse` | `src/main/java/com/ecommerce/catalogo/controller/CatalogoController.java:39-43` |
| `CatalogoController` | `DELETE /catalogos/{id}/produtos` | `@PathVariable Long id`, `@RequestParam String sku` | `200 OK` + `CatalogoResponse` | `src/main/java/com/ecommerce/catalogo/controller/CatalogoController.java:46-49` |
| `CatalogoController` | `PATCH /catalogos/{id}/produtos` | `@PathVariable Long id`, `@Valid @RequestBody CatalogoItemRequest` | `200 OK` + `CatalogoResponse` | `src/main/java/com/ecommerce/catalogo/controller/CatalogoController.java:52-55` |

### 2.3 Como a Busca e os Cálculos Funcionam Hoje (Evidências no Código)
1. **Busca de Produtos (`ProdutoService.buscar`):**
   - Em `src/main/java/com/ecommerce/catalogo/service/ProdutoService.java:68-73`, se o parâmetro `busca` for nulo ou vazio, executa `repository.findAll()` (carregando toda a tabela `produto` na memória sem paginação). Caso contrário, executa `repository.findByNomeContainingIgnoreCaseOrSkuContainingIgnoreCase(busca, busca)` (`ProdutoRepository.java:10`), que gera `ILIKE '%termo%'` no PostgreSQL sem índice GIN/trigram e sem `Pageable`.
2. **Listagem de Promoções (`PromocaoService.listar`):**
   - Em `src/main/java/com/ecommerce/catalogo/service/PromocaoService.java:70-76`, executa **`promocaoRepository.findAll().stream()`** e filtra por `produto` e `cupom` **em memória no Java**, em vez de filtrar via cláusula `WHERE` no banco de dados! Além disso, retorna promoções inativas misturadas com ativas.
3. **Cálculo de Preço Final (`PrecoFinalService.calcular`):**
   - Em `src/main/java/com/ecommerce/catalogo/service/PrecoFinalService.java:39-73`, busca o produto ativo pelo `sku`, consulta `promocaoRepository.findByAtivaTrueAndSkuProduto(sku)` (`PromocaoRepository.java:10`), descarta cupons (`!"CUPOM".equals(p.getTipo())`), calcula em memória `p.aplicarDesconto(produto, quantidade)` duas vezes por promoção (no `.filter` da linha 51 e no `.max(Comparator.comparing(...))` da linha 52, e uma terceira vez no `.map` da linha 54!), soma com cupom opcional (`CupomRepository.java:10`), limita ao subtotal e retorna `PrecoFinalResponse`.
4. **Dados Simulados / Hardcoded no Código Atual:**
   - **Link temporário simulado:** Em `src/main/java/com/ecommerce/catalogo/model/ProdutoDigital.java:30-34`, o método `gerarLinkTemporario()` possui o comentário explícito `/** Gera link com token único e expiração de 24h (simulado). */` concatenando `?token=UUID&expira=epoch` sem persistência nem validação, e sequer é chamado por algum controller ou service (código morto).
   - **Cálculo de frete hardcoded:** Em `src/main/java/com/ecommerce/catalogo/model/ProdutoFisico.java:13-15` e `38-45`, há constantes fixas `FIXO = new BigDecimal("5.00")` e `TARIFA = new BigDecimal("0.50")` (`/** Tarifa de exemplo: R$ 0,50 por kg por 100 km, mais R$ 5,00 fixos. */`). O método `calcularFrete(double distanciaKm)` também é código morto nunca exposto na API.
5. **Paginação, Timeouts, Retry, Cache e Logs:**
   - **Paginação:** Não encontrada em nenhum endpoint (`ProdutoController.java:33`, `PromocaoController.java:40`, `CatalogoController.java:30`).
   - **Timeouts / Retries / Circuit Breaker:** Não encontrados.
   - **Cache:** Não encontrado.
   - **Logs:** Não encontrados (nenhum logger SLF4J/Logback declarado em toda a base de código; `GlobalExceptionHandler.java:59-62` captura `Exception.class` e retorna HTTP 500 `"Erro interno inesperado"` engolindo silenciosamente a stack trace sem logar nada!).
6. **Contratos Atuais com API de Filtros, Banco de Dados e API de Compartilhamento:**
   - **API de Filtros:** **Não encontrado no código.** Atualmente não há integração de saída da Busca para a API de Filtros. (O contrato necessário será especificado na Fase 4).
   - **API de Compartilhamento:** **Não encontrado no código.**
   - **Banco de Dados:** A aplicação assume acesso direto e exclusivo a um banco PostgreSQL local chamado `catalogo` (`application.properties:2`), gerenciando 8 tabelas via Hibernate `ddl-auto=update`: `produto`, `produto_fisico`, `produto_digital`, `promocao`, `desconto_percentual`, `desconto_quantidade`, `cupom_promocional`, `estoque`, `catalogo`, `catalogo_produto`.

---

## SEÇÃO 3: ARQUITETURA ATUAL VS. ARQUITETURA ALVO (COM DIAGRAMAS)

Inclua no PDF dois diagramas vetoriais claros e legíveis:
1. **Diagrama 1 — Arquitetura Atual (As-Is):** Cliente HTTP $\rightarrow$ Controllers REST (`Produto`, `Promocao`, `PrecoFinal`, `Estoque`, `Catalogo`) $\rightarrow$ Services $\rightarrow$ Spring Data JPA Repositories $\rightarrow$ PostgreSQL Local (`catalogo`). Destaque visualmente a ausência de conectores externos e de comunicação com a API de Filtros e Compartilhamento.
2. **Diagrama 2 — Arquitetura Alvo Refinada (To-Be) no Pipeline de 4 Serviços:**
   - **Fontes Externas (Varejistas & Redes):** Mercado Livre API, Shopee Open API (GraphQL), AliExpress Open Platform, Amazon Creators API, Lomadee API v3 / Feeds, Awin Product Feeds.
   - **Serviço 1: API de Busca de Promoções (Refinada):**
     - `Scheduler / Job Orchestrator` + `Search REST Controller`
     - `Connector Registry & Feature Flags`
     - `Retailer Connectors (Padrão Adapter)` com `Resilience4j` (Rate Limiter, Circuit Breaker, Retry com Backoff, Timeouts)
     - `Offer Normalizer & Affiliate Link Enricher`
     - `Deduplication & Cache Layer (Redis)` (Fingerprint por EAN/GTIN + `loja:external_id` + Hash de Preço)
     - `Filter Service Client / Outbox Publisher` (HTTP Assíncrono / RabbitMQ / Kafka)
   - **Serviço 2: API de Filtros:** Motor de Regras de Qualidade, Histórico de Preço (Anti-Fraude "Metade do Dobro"), Score de Relevância, Validação de Vendedor/Avaliação.
   - **Serviço 3: Banco de Dados (PostgreSQL + Timescale/Particionamento):** Tabelas `ofertas_unificadas`, `historico_precos`, `lojas_config`, `regras_filtro`, `publicacoes_canais`.
   - **Serviço 4: API de Compartilhamento:** Formatador de Templates, Encurtador/Validador de Links de Afiliado, Dispatchers (Telegram, WhatsApp, Web/App).

---

## SEÇÃO 4: DIAGNÓSTICO COMPLETO POR SEVERIDADE (FASE 2)

Apresente uma tabela detalhada contendo todos os problemas diagnosticados nas 8 dimensões exigidas, classificados em **CRÍTICO**, **IMPORTANTE** e **DESEJÁVEL**, sempre com evidência (`arquivo:linha`), impacto e correção recomendada:

| ID | Categoria | Severidade | Problema Identificado | Evidência (`arquivo:linha`) | Impacto no Sistema | Correção Técnica Recomendada |
|---|---|---|---|---|---|---|
| **D-01** | Arquitetura | **CRÍTICO** | Ausência total de camada de integração com varejistas externos e com a API de Filtros. O sistema opera apenas como CRUD local isolado. | `pom.xml:17-45` e estrutura de pacotes `com.ecommerce.catalogo.*` | Impossibilita cumprir o propósito do Serviço 1 (Busca Real de Promoções) no pipeline de 4 serviços. | Implementar arquitetura *Hexagonal / Adapter* (`RetailerConnector`), orquestrador de coleta e cliente HTTP/mensageria para envio de lotes à API de Filtros. |
| **D-02** | Segurança | **CRÍTICO** | Credenciais de banco de dados hardcoded em texto puro no repositório (`postgres`/`postgres`). | `src/main/resources/application.properties:2-4` e `README.md:29-31` | Exposição de segredos em controle de versão e risco de comprometimento do banco em produção. | Substituir por variáveis de ambiente (`${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`) e gerenciador de segredos (Vault / AWS Secrets Manager / Kubernetes Secrets). |
| **D-03** | Segurança | **CRÍTICO** | Ausência total de autenticação/autorização entre serviços (API Keys / mTLS / OAuth2 JWT) e ausência de Rate Limiting de entrada. | `pom.xml:17-45`, `src/main/java/com/ecommerce/catalogo/controller/*` | Qualquer agente externo pode cadastrar/apagar produtos, alterar catálogos ou esgotar recursos via ataques DoS. | Adicionar `spring-boot-starter-security`, autenticação via API Key/JWT entre Busca $\leftrightarrow$ Filtros e filtro de Rate Limit (Bucket4j / Resilience4j). |
| **D-04** | Observabilidade | **CRÍTICO** | Supressão silenciosa de exceções não tratadas: o handler genérico captura `Exception` e retorna HTTP 500 sem registrar nenhum log. | `src/main/java/com/ecommerce/catalogo/exception/GlobalExceptionHandler.java:59-62` | Falhas críticas de banco, NullPointerExceptions ou erros de integração ocorrem às cegas, inviabilizando troubleshooting em produção. | Injetar `Logger` (SLF4J) em `GlobalExceptionHandler` e nos Services, logando `log.error("Erro inesperado em {}", req.getRequestURI(), ex)` em formato JSON estruturado. |
| **D-05** | Desempenho | **CRÍTICO** | Filtragem de promoções feita inteiramente em memória (`findAll().stream().filter(...)`) e listagens sem paginação. | `src/main/java/com/ecommerce/catalogo/service/PromocaoService.java:70-76`, `ProdutoService.java:68-73`, `CatalogoService.java:41-43` | Em um agregador real com centenas de milhares de ofertas, `findAll()` causará `OutOfMemoryError` (OOM) imediato e travamento do banco. | Substituir por queries JPA paginadas (`Page<T>`, `Pageable`) com filtros dinâmicos (`Specification` ou `@Query` indexada no banco). |
| **D-06** | Confiabilidade | **CRÍTICO** | Uso de `spring.jpa.hibernate.ddl-auto=update` sem controle de versão de schema (Flyway/Liquibase). | `src/main/resources/application.properties:5` | Alterações de entidades podem corromper tabelas em produção ou falhar silenciosamente em migrações de tipos/constraints. | Mudar `ddl-auto=validate` e adotar **Flyway** (`db/migration/V1__...sql`) para versionamento determinístico do banco. |
| **D-07** | Testes | **CRÍTICO** | Cobertura de testes igual a 0% (diretório `src/test` inexistente), sem mocks de varejistas ou testes de contrato. | `pom.xml:35-44` (dependências de teste presentes, mas `src/test` não encontrado) | Alto risco de regressão em cálculos financeiros, normalização de preços de varejistas e quebra de contrato com a API de Filtros. | Criar suíte JUnit 5 + Mockito + WireMock (para simular respostas reais de ML, Shopee, AliExpress, Amazon, Lomadee) + Spring Cloud Contract / Pact. |
| **D-08** | Qualidade de Dados | **CRÍTICO** | Modelo de dados incapaz de representar ofertas reais de múltiplos varejistas (sem `loja`, `externalId`, `urlOriginal`, `urlAfiliado`, `precoOriginal` vs `precoPromocional`, `moeda`, `vendedor`, `avaliacao`, `dataColeta`). | `src/main/java/com/ecommerce/catalogo/model/Produto.java:16-50` e `Promocao.java:10-47` | Impossível distinguir ofertas do mesmo produto em lojas diferentes, detectar falso desconto ("metade do dobro") ou gerar link comissionado. | Implementar o **Schema Unificado de Oferta (`UnifiedOffer`)** com separação clara entre preço original (`originalPrice`), preço atual (`currentPrice`) e histórico temporal. |
| **D-09** | Confiabilidade | **IMPORTANTE** | Race condition (condição de corrida) na criação automática de estoque em `EstoqueService.obterOuCriar`. | `src/main/java/com/ecommerce/catalogo/service/EstoqueService.java:21-58` | Duas requisições simultâneas para um SKU sem registro de estoque passam pelo `findById` vazio e tentam `saveAndFlush`, gerando `DataIntegrityViolationException` (HTTP 409 inesperado). | Criar o registro de `Estoque` atomicamente no momento do cadastro do produto (`ProdutoService.cadastrar`) ou usar `INSERT ... ON CONFLICT DO NOTHING`. |
| **D-10** | Desempenho | **IMPORTANTE** | Problema de consultas `N+1` ao listar catálogos (`CatalogoService.listar` chama `toResponse` que executa 1 query por catálogo). | `src/main/java/com/ecommerce/catalogo/service/CatalogoService.java:41-43` e `95-99` | Se houver 100 catálogos, `GET /catalogos` executa 101 queries SQL no PostgreSQL, degradando a latência. | Usar `@EntityGraph` ou `JOIN FETCH` para carregar catálogos e seus itens em uma única consulta SQL. |
| **D-11** | Qualidade de Código | **IMPORTANTE** | Código morto / métodos simulados nunca utilizados (`ProdutoDigital.gerarLinkTemporario` e `ProdutoFisico.calcularFrete`) e uso de `double` primitivo para dimensões/cálculos. | `src/main/java/com/ecommerce/catalogo/model/ProdutoDigital.java:30-34`, `ProdutoFisico.java:13-45` | Confusão sobre regras reais de negócio e imprecisão de ponto flutuante (`double` em cálculos tarifários). | Remover métodos simulados mortos e substituir `double` por `BigDecimal` para pesos/dimensões/fretes ou delegar frete ao payload real do varejista. |
| **D-12** | Qualidade de Código | **IMPORTANTE** | Violação de polimorfismo (Open/Closed Principle) com cadeias de `instanceof` e casts manuais em DTOs e Services; reexecução tripla de `aplicarDesconto`. | `src/main/java/com/ecommerce/catalogo/controller/dto/ProdutoResponse.java:18-24`, `PromocaoResponse.java:20-30`, `PrecoFinalService.java:51-54` | Adicionar um novo tipo de promoção ou oferta exige modificar múltiplos DTOs e Services; cálculo repetido desperdiça CPU. | Usar Pattern Matching moderno do Java 17/21 (`switch` com `sealed` classes/interfaces) e calcular o desconto uma única vez mapeando para um par `(promocao, valorDesconto)`. |
| **D-13** |Qualidade de Dados | **IMPORTANTE** | Ausência de expiração automática para `DescontoPercentual` e `DescontoQuantidade`, e dependência de `LocalDate.now()` sem TimeZone explícito em `CupomPromocional`. | `src/main/java/com/ecommerce/catalogo/model/Promocao.java:10-47`, `CupomPromocional.java:34-36`, `PromocaoService.java:56` | Promoções percentuais duram para sempre até desativação manual; `LocalDate.now()` usa timezone da JVM, podendo expirar cupons 3h antes se o container rodar em UTC. | Adicionar `validadeInicio` (`Instant`) e `validadeFim` (`Instant`) na superclasse `Promocao`/`Oferta` e usar `Clock` ou `ZoneId.of("America/Sao_Paulo")`. |
| **D-14** | Segurança | **IMPORTANTE** | Risco de SSRF (Server-Side Request Forgery) e injeção de URLs maliciosas em campos de links (`linkDownload`, URLs de ofertas e imagens). | `src/main/java/com/ecommerce/catalogo/controller/dto/ProdutoRequest.java:20` | Sem validação de protocolo (`https://`) e whitelist de domínios de varejistas, URLs internas (`http://169.254.169.254`, `http://localhost`) podem ser injetadas e seguidas por workers. | Validar URLs com `@URL(protocol = "https")` e whitelist estrita de domínios permitidos por conector antes de realizar chamadas HTTP externas. |
| **D-15** | Observabilidade | **IMPORTANTE** | Ausência de Health Checks (`/actuator/health`), métricas Prometheus (`/actuator/prometheus`) e Distributed Tracing (`traceId`/`spanId`). | `pom.xml:17-45` | Orquestradores (Kubernetes/Docker) não sabem se a aplicação ou os conectores de varejistas estão saudáveis; impossível rastrear uma oferta entre Busca $\rightarrow$ Filtros $\rightarrow$ Compartilhamento. | Adicionar `spring-boot-starter-actuator`, `micrometer-registry-prometheus` e `micrometer-tracing-bridge-otel`, propagando `X-Correlation-ID` / W3C TraceContext. |
| **D-16** | Qualidade de Repositório | **DESEJÁVEL** | Binários do Maven (`maven.zip` de ~9MB e diretório `maven_dir/`) e pasta `target/` presentes na árvore do projeto sem `.gitignore`. | Raiz do projeto (`maven.zip`, `maven_dir/`, `target/`) | Poluição do repositório Git, aumento desnecessário do tamanho do clone e conflitos de binários compilados. | Remover `maven.zip`, `maven_dir/` e `target/`, adotar Maven Wrapper padrão (`mvnw` + `.mvn/wrapper`) e criar `.gitignore` completo. |

---

## SEÇÃO 5: PLANO DE INTEGRAÇÃO COM VAREJISTAS REAIS (FASE 3)

Esta seção do PDF deve apresentar uma análise exaustiva e comparativa de **CADA varejista** baseada na documentação oficial vigente.

### 5.1 Diretriz Estratégica e Jurídica: API Oficial / Redes de Afiliados vs. Web Scraping
Destaque em um bloco de alerta (`IMPORTANTE / CONFORMIDADE LEGAL`) por que a arquitetura prioriza **100% vias oficiais (APIs Nativas de Afiliados e Product Feeds de Redes como Lomadee e Awin)** e trata **Web Scraping apenas como último recurso estritamente controlado**:
1. **Geração de Link de Afiliado e Comissionamento:** Scraping de páginas HTML públicas **não gera links de afiliado rastreáveis (`sub_id` / `tracking_id`)** por si só. Pior: realizar scraping usando contas ou cookies de afiliado viola diretamente os Termos de Uso da Amazon Associates, Mercado Livre Afiliados e Shopee, resultando em **banimento sumário da conta e retenção de todas as comissões acumuladas**.
2. **Bloqueios Anti-Bot (WAF / Cloudflare / Akamai / DataDome):** Todos os grandes varejistas brasileiros operam proteções agressivas com desafios TLS Fingerprinting (JA3/JA4), CAPTCHAs invisíveis e bloqueio de IPs de datacenters (AWS, GCP, Azure). Manter scrapers exige proxies residenciais rotativos caros, aumentando o custo operacional (OpEx) acima do retorno financeiro.
3. **Instabilidade de Layout (Fragilidade de Seletores CSS/DOM):** Mudanças diárias de front-end (Next.js/React hydration, classes ofuscadas) quebram coletores baseados em HTML, gerando dados corrompidos (ex.: capturar preço parcelado como preço à vista).
4. **Riscos Legais e LGPD (Lei nº 13.709/2018 e Marco Civil da Internet):** Coleta automatizada não autorizada pode configurar violação de Termos de Serviço, concorrência desleal ou coleta inadvertida de dados pessoais de vendedores/avaliações de usuários (PII).

### 5.2 Análise Detalhada por Varejista (Documentação Oficial Atual)

Para cada loja abaixo, inclua no PDF uma subseção dedicada e uma tabela comparativa consolidada:

#### 1. Mercado Livre (Brasil — `MLB`)
- **Documentação Oficial Consultada:**
  - Portal Developers: `https://developers.mercadolivre.com.br/`
  - Autenticação e Autorização: `https://developers.mercadolivre.com.br/pt_br/autenticacao-e-autorizacao`
  - Busca de Itens e Catálogo: `https://developers.mercadolivre.com.br/pt_br/itens-e-buscas`
  - Programa de Afiliados ML: `https://www.mercadolivre.com.br/l/afiliados-home`
- **Existe API Oficial? E Programa de Afiliados?**
  - **Sim para API REST de Catálogo/Busca** (`https://api.mercadolibre.com/sites/MLB/search` e `GET /items?ids=MLB1,MLB2`).
  - **Restrição Crítica de Afiliados:** O Mercado Livre possui um Programa de Afiliados oficial robusto, porém **não fornece um endpoint público na API REST aberta para geração automatizada de links encurtados de afiliado (`meli.la` / `sec`)**. A API `/sites/MLB/search` exige cabeçalho `Authorization: Bearer <ACCESS_TOKEN>` (fluxo OAuth 2.0 Authorization Code / Refresh Token com validade de 6 horas).
- **Como Autenticar:**
  - OAuth 2.0 (`client_id`, `client_secret`, `redirect_uri`). Endpoint de token: `POST https://api.mercadolibre.com/oauth/token` (`grant_type=authorization_code` e depois `grant_type=refresh_token` a cada 6h).
- **Limites de Requisição (Rate Limits) e Paginação:**
  - Rate limit dinâmico por aplicação/IP e por recurso; retorna `HTTP 429 Too Many Requests` quando excedido.
  - Paginação em `/sites/MLB/search`: `limit` (máx. 50 por página) e `offset` (limite máximo de paginação pública sem scroll: `offset <= 1000`).
  - Endpoint `GET /items?ids=...` suporta **multiget de até 20 IDs por chamada**, essencial para economizar cota de requisições!
- **Campos Disponíveis na Resposta Oficial:**
  - `id` (ex.: `MLB123456789`), `title`, `price` (preço atual), `original_price` (preço anterior riscado, quando em promoção!), `currency_id` (`BRL`), `available_quantity`, `sold_quantity`, `permalink`, `thumbnail` (substituir `-I.jpg` por `-O.jpg` para alta resolução), `shipping.free_shipping` (booleano), `seller.id`, `seller.nickname`, `official_store_id`, `category_id`, `attributes` (inclui `GTIN`/`EAN`, `BRAND`, `MODEL`), `sale_price` (endpoint `/items/{id}/sale_price`).
- **Custos e Requisitos de Aprovação:**
  - API gratuita. Exige conta no Mercado Livre com verificação de identidade ativa e criação de aplicação no DevCenter. Para o Programa de Afiliados, exige aprovação de perfil/canal de divulgação.
- **Estratégia Recomendada (Descoberta + Monetização):**
  - Usar a **API Oficial (`GET /sites/MLB/search?q=...&Filter=discount` + Multiget `GET /items?ids=...`)** para descoberta 100% legal de produtos com `original_price > price`, e integrar o enriquecimento do link de afiliado via parâmetro oficial de campanha (`matt_tool` / `matt_word` do programa de afiliados ML) ou fila assistida de conversão de link no serviço de Compartilhamento.
- **Estimativa de Esforço:** **M (Médio — 5 a 8 dias úteis)**.

#### 2. Shopee (Brasil)
- **Documentação Oficial Consultada:**
  - Portal de Afiliados Shopee Brasil: `https://affiliate.shopee.com.br/`
  - Console e Documentação GraphQL Open API: `https://open-api.affiliate.shopee.com.br/graphql` (acessível no painel `affiliate.shopee.com.br/open_api`)
- **Existe API Oficial de Afiliados?**
  - **SIM! Excelente API oficial dedicada a afiliados no Brasil**, baseada em **GraphQL**.
- **Como Autenticar:**
  - Assinatura criptográfica **SHA256 / HMAC** por requisição no header `Authorization`:
  - Credenciais obtidas no painel de afiliado: `AppId` e `Secret`.
  - Cálculo do payload de assinatura:
    $$\text{Factor} = \text{AppId} + \text{Timestamp (Unix segundos)} + \text{Payload JSON (string)} + \text{Secret}$$
    $$\text{Signature} = \text{SHA256\_HEX}(\text{Factor})$$
  - Header enviado em todo `POST https://open-api.affiliate.shopee.com.br/graphql`:
    `Authorization: SHA256 Credential={AppId}, Timestamp={Timestamp}, Signature={Signature}`
- **Queries e Mutations Disponíveis (GraphQL):**
  - `productOfferV2(keyword, sortType, page, limit, listType, isAMSOffer, isKeySeller)`: Busca ofertas de produtos com comissão e desconto.
  - `shopeeOfferV2(keyword, sortType, page, limit)`: Lista campanhas oficiais e páginas de ofertas da Shopee.
  - `generateShortLink(input: { originUrl: "...", subIds: ["canal1", "oferta123"] })`: **Mutation oficial para converter qualquer link da Shopee em link curto de afiliado (`s.shopee.com.br/...`)!**
- **Campos Disponíveis (`productOfferV2.nodes`):**
  - `itemId`, `productName`, `commissionRate`, `commission`, `price`, `priceMin`, `priceMax`, `priceDiscountRate` (% de desconto oficial), `imageUrl`, `shopId`, `shopName`, `shopType`, `productLink`, `offerLink` (já vem com link de afiliado!), `ratingStar`, `sales`, `periodStartTime`, `periodEndTime`.
- **Limites de Requisição e Custos:**
  - API 100% gratuita para afiliados aprovados. Limite padrão de taxa (~2000 chamadas/hora por `AppId`, com paginação `limit` até 50 itens por página).
- **Estimativa de Esforço:** **P (Pequeno — 3 a 5 dias úteis)** — É a integração mais direta e completa para o MVP!

#### 3. AliExpress
- **Documentação Oficial Consultada:**
  - AliExpress Portals (Programa de Afiliados): `https://portals.aliexpress.com/`
  - AliExpress Open Platform (Developers): `https://openservice.aliexpress.com/` e `https://developers.aliexpress.com/`
- **Existe API Oficial de Afiliados?**
  - **SIM! API Oficial madura e completa** dentro do pacote *AE-Affiliate* na AliExpress Open Platform.
- **Como Autenticar:**
  - `App Key`, `App Secret` e `Tracking ID` (ex.: `default` ou ID do canal).
  - Assinatura de requisição padrão TOP/IOP (`sign_method=sha256` ou `md5`): ordenam-se todos os parâmetros alfabeticamente (`app_key`, `method`, `timestamp`, `sign_method`, `v`, mais parâmetros de negócio), concatena-se e gera-se o hash HMAC-SHA256 com o `App Secret`.
  - Endpoint Gateway REST: `https://api-sg.aliexpress.com/sync`
- **Métodos Principais Disponíveis:**
  - `aliexpress.affiliate.product.query`: Busca parametrizada por `keywords`, `category_ids`, `min_sale_price`, `max_sale_price`, `sort` (`SALE_PRICE_ASC`, `DISCOUNT_ASC`, `LAST_VOLUME_DESC`), `target_currency=BRL`, `target_language=PT`, `ship_to_country=BR`, `tracking_id`.
  - `aliexpress.affiliate.hotproduct.query`: Produtos "Hot" em alta conversão e super ofertas.
  - `aliexpress.affiliate.productdetail.get`: Detalhes completos por lista de `product_ids` (até 50 IDs).
  - `aliexpress.affiliate.link.generate`: Gera links curtos/rastreáveis (`s.click.aliexpress.com/...`) em lote.
  - `aliexpress.affiliate.featuredpromo.products.get`: Ofertas de campanhas sazonais (Choice Day, 11.11, SuperDeals).
- **Campos Disponíveis:**
  - `product_id`, `product_title`, `target_sale_price` (preço promocional já convertido para `BRL`!), `target_original_price` (preço original em `BRL`), `target_app_sale_price`, `discount` (ex.: `"45%"`), `product_main_image_url`, `product_small_image_urls`, `product_detail_url`, `promotion_link` (link de afiliado pronto!), `evaluate_rate` (avaliação), `lastest_volume` (vendas recentes), `shop_id`, `shop_url`, `promo_code_info` (dados de cupom aplicável: `promo_code`, `code_value`, `code_available_time_start`, `code_available_time_end`).
- ** Observação Crítica para o Brasil (Remessa Conforme):**
  - A API retorna o preço do produto (`target_sale_price` em BRL), mas **não inclui o ICMS + Imposto de Importação (programa Remessa Conforme)** que é calculado no checkout do AliExpress. O conector do AliExpress DEVE marcar a flag `isInternational = true` e estimar o imposto aproximado (~44,5% para produtos até US$ 50 e ~92% acima de US$ 50) em um campo `estimatedTaxAmount` para que a API de Filtros não aprove falsas pechinchas internacionais que ficam caras com imposto.
- **Limites de Requisição e Custos:**
  - Gratuito. Cota inicial de ~5.000 a 20.000 chamadas/dia por `AppKey`, expansível mediante uso acima de 70%.
- **Estimativa de Esforço:** **P/M (4 a 6 dias úteis)**.

#### 4. Amazon (Brasil — `amazon.com.br`)
- **Documentação Oficial Consultada:**
  - Amazon Associates Central (Brasil): `https://associados.amazon.com.br/`
  - Documentação Oficial Creators API (substituta da PA-API 5.0): `https://affiliate-program.amazon.com/creatorsapi/docs/`
  - Guia Oficial de Migração PA-API 5.0 $\rightarrow$ Creators API: `https://affiliate-program.amazon.com/creatorsapi/docs/en-us/migrating-to-creatorsapi-from-paapi`
  - Documentação PA-API 5.0 (legado): `https://webservices.amazon.com/paapi5/documentation/`
- **Mudança Crítica Recente (PA-API 5.0 $\rightarrow$ Creators API):**
  - A antiga **PA-API 5.0 (Product Advertising API 5.0)** baseada em AWS Signature v4 está sendo **descontinuada/substituída pela nova Amazon Creators API**, que utiliza **OAuth 2.0 (`Credential ID` e `Credential Secret` com Bearer Token válido por 3600s)**. O conector da Amazon deve ser desenhado suportando OAuth 2.0 da Creators API (e mantendo compatibilidade configurável caso a conta legada ainda use endpoint PA-API).
- **Operações e Campos Disponíveis:**
  - Operações: `SearchItems` (busca por `Keywords`, `BrowseNodeId`, `MinSavingPercent`, `MinPrice`, `MaxPrice`, `Merchant="Amazon"`), `GetItems` (consulta por até 10 `ASIN`s por chamada), `GetBrowseNodes`, `GetVariations`.
  - Recursos (`Resources`) retornados: `ItemInfo.Title`, `ItemInfo.ExternalIds` (EANs/UPCs!), `Images.Primary.Large`, `Offers.Listings.Price` (Amount, Currency, Savings.Amount, Savings.Percentage), `Offers.Listings.SavingBasis` (preço original de referência), `Offers.Listings.Availability.Message`, `Offers.Listings.DeliveryInfo.IsFreeShippingEligible`, `Offers.Listings.Promotions`, `DetailPageURL` (já inclui a `PartnerTag` de afiliado, ex.: `tag=seuafiliado-20`).
- **Limites de Requisição e Requisitos Rigorosos de Aprovação:**
  - **Requisito de Elegibilidade:** Exige conta aprovada no Amazon Associados e **pelo menos 3 vendas qualificadas nos primeiros 180 dias**, além de manter vendas qualificadas nos últimos **30 dias corridos** para não perder acesso à API!
  - **Rate Limit Restrito:** Toda conta inicia com exatamente **1 TPS (1 requisição por segundo)** e **8.640 TPD (requisições por dia)**. O limite escala +1 TPS a cada faixa de receita enviada gerada nos últimos 30 dias (até 10 TPS).
- **Estratégia Técnica Obrigatória:**
  - Como 1 TPS é muito baixo para buscas simultâneas, o conector Amazon **precisa obrigatoriamente de um Rate Limiter estrito (`1 req/s`) + fila de prioridade + agrupamento de até 10 ASINs via `GetItems` + Cache Redis agressivo (TTL 30–60 min)**.
- **Estimativa de Esforço:** **M (5 a 7 dias úteis)**.

#### 5. Magazine Luiza (Parceiro Magalu / Lomadee / Magalu Empresas)
- **Documentação Oficial Consultada:**
  - Portal Magalu Devs (OpenAPI para Sellers): `https://developer.magalu.com/`
  - Portal Magalu Empresas (API Catálogo B2B): `https://institucional.magaluempresas.com.br/`
  - Parceiro Magalu (Divulgador): `https://www.parceiromagalu.com.br/`
  - Lomadee API v3 (Rede Oficial de Afiliados): `https://docs.lomadee.com.br/` (`https://api.lomadee.com.br`)
- **Análise das Vias Disponíveis:**
  1. **Parceiro Magalu (Pessoa Física / Divulgador):** Cria uma vitrine (`magazinevoce.com.br/suavitrine`), mas **não oferece API pública aberta** para consulta programática de catálogo para divulgadores individuais.
  2. **Magalu Devs (`developer.magalu.com`):** É voltada para *Sellers* gerenciarem seus próprios produtos dentro do marketplace do Magalu, não para afiliados consultarem ofertas de terceiros.
  3. **Magalu Empresas (`magaluempresas.com.br`):** Possui API B2B de Catálogo e Shopping, mas exige contrato corporativo B2B (fidelidade/incentivo) com onboarding comercial longo.
  4. **Via Oficial Recomendada para Agregador de Promoções — Lomadee API v3 (`https://api.lomadee.com.br`) / Awin:** O Magazine Luiza (assim como KaBuM! e Netshoes, que fazem parte do ecossistema Magalu) disponibiliza campanhas, produtos e links rastreáveis para afiliados através de redes parceiras oficiais como **Lomadee** e **Awin**.
- **Como Integrar via Lomadee API v3:**
  - Autenticação: Header `x-api-key: <SUA_CHAVE_LOMADEE>` obtida no painel Lomadee.
  - Endpoints: `/affiliate/brands` (identificar o `brandId` do Magalu/KaBuM/Netshoes), `/affiliate/products` (buscar produtos paginados com preço, desconto e link de afiliado), `/affiliate/campaigns` (listar ofertas e cupons `GenericCoupon` / `Offer`).
- **Estimativa de Esforço:** **M (4 a 6 dias úteis via Conector Unificado Lomadee/Awin)**.

#### 6. Outros Varejistas Sugeridos: KaBuM!, Americanas, Casas Bahia e Netshoes
- **Documentação Oficial Consultada:**
  - Lomadee API v3: `https://docs.lomadee.com.br/`
  - Awin Developer Center / Wiki: `https://wiki.awin.com/index.php/Publisher_API` e `https://wiki.awin.com/index.php/Product_Feed_List`
- **Viabilidade e Estratégia Ideal (1 Conector de Rede = Múltiplas Lojas):**
  - Nenhum desses 4 varejistas (KaBuM!, Americanas, Casas Bahia, Netshoes) oferece uma API pública self-service exclusiva para afiliados individuais nos moldes da Shopee ou AliExpress.
  - Contudo, **todos eles operam programas de afiliados ativos na Awin e/ou Lomadee!**
  - **Arquitetura Inteligente:** Em vez de construir 5 scrapers frágeis separados, construímos **2 Conectores de Redes Agregadoras Oficiais**:
    1. `LomadeeConnector` (REST API `https://api.lomadee.com.br` com header `x-api-key`).
    2. `AwinProductFeedConnector` (Awin Publisher API `https://api.awin.com` com `Authorization: Bearer <token>`, rate limit de 20 chamadas/min na API REST, combinada com download assíncrono periódicos de **Product Feeds em CSV/JSONL gzip** via `https://productdata.awin.com` / `awinfeeds`, que permite ingerir centenas de milhares de ofertas de KaBuM!, Casas Bahia, Netshoes, Nike, Adidas, Fast Shop e Magalu sem estourar rate limits!).

### 5.3 Tabela Comparativa Consolidada de Varejistas (Resumo Executivo)

Inclua no PDF esta tabela comparativa completa:

| Varejista / Fonte | Via Recomendada | Protocolo & Endpoint Base | Autenticação | Rate Limit Oficial | Geração de Link Afiliado | Dados de Preço "De/Por" e Cupom | Esforço |
|---|---|---|---|---|---|---|---|
| **Shopee Brasil** | API Oficial de Afiliados | GraphQL (`POST https://open-api.affiliate.shopee.com.br/graphql`) | Header `Authorization: SHA256 Credential, Timestamp, Signature` (HMAC/SHA256) | ~2.000 req/hora (paginação até 50/pág) | Nativa (`offerLink` na busca + mutation `generateShortLink`) | Sim (`price`, `priceDiscountRate`) | **P** (3-5d) |
| **AliExpress** | API Oficial (`AE-Affiliate`) | REST Sync (`https://api-sg.aliexpress.com/sync`) | `app_key` + `sign` (HMAC-SHA256/MD5 dos params ordenados) | 5.000 a 20.000 req/dia por `AppKey` | Nativa (`promotion_link` + `aliexpress.affiliate.link.generate`) | Sim (`target_original_price`, `target_sale_price` em BRL, `promo_code_info`) | **P/M** (4-6d) |
| **Amazon Brasil** | API Oficial (`Creators API` / `PA-API 5.0`) | REST JSON (`https://affiliate-program.amazon.com/creatorsapi` / `webservices.amazon.com.br`) | OAuth 2.0 Bearer Token (`Credential ID/Secret`) na Creators API | Inicial: **1 TPS** e **8.640 TPD** (escala por receita enviada em 30d) | Nativa (`DetailPageURL` já embute `PartnerTag`) | Sim (`Price.Amount`, `SavingBasis`, `Savings.Percentage`, `Promotions`) | **M** (5-7d) |
| **Mercado Livre** | API Oficial Catálogo + Enriquecedor Afiliado | REST JSON (`https://api.mercadolibre.com/sites/MLB/search` e `/items`) | OAuth 2.0 (`Bearer Access Token`, renovado a cada 6h via `refresh_token`) | Dinâmico por App/IP (`HTTP 429`); Multiget até 20 IDs/chamada | Híbrida (Parâmetros de campanha `matt_tool`/`matt_word` ou gerador assistido) | Sim (`price`, `original_price`, `/items/{id}/sale_price`) | **M** (5-8d) |
| **Magazine Luiza** | Rede Oficial (**Lomadee API v3** / **Awin**) | REST JSON (`https://api.lomadee.com.br`) + Awin Feeds | Lomadee: Header `x-api-key`<br>Awin: `Bearer <OAuth2_Token>` | Lomadee: controlado por chave<br>Awin API: 20 req/min | Nativa pela API da rede (links parametrizados por `channelId`/`publisherId`) | Sim (preço de/por + endpoint de cupons `/campaigns`) | **M** (4-6d) |
| **KaBuM!, Casas Bahia, Americanas, Netshoes** | Redes Oficiais (**Awin Product Feeds** + **Lomadee API v3**) | Download Streaming GZIP CSV/JSONL (`productdata.awin.com`) + REST Lomadee | Awin API Key / Bearer Token + Lomadee `x-api-key` | Feeds: Atualização 1x a 4x/dia em background; API: 20 req/min | Nativa (`aw_deep_link` / link Lomadee já pronto no feed/API) | Sim (`rrp_price` / `base_price` vs `search_price` + API de Promoções/Cupons) | **M** (5-7d p/ todas) |

---

## SEÇÃO 6: DESENHO DO PADRÃO ADAPTER/CONNECTOR E SCHEMA UNIFICADO (FASE 3)

### 6.1 Arquitetura do Padrão Adapter/Connector em Java 17 + Spring Boot 3
Apresente no PDF o código das interfaces e classes centrais que devem ser implementadas na API de Busca:

```java
package com.ecommerce.catalogo.integration.connector;

import com.ecommerce.catalogo.integration.model.RetailerId;
import com.ecommerce.catalogo.integration.model.SearchCriteria;
import com.ecommerce.catalogo.integration.model.UnifiedOffer;
import java.util.List;
import java.util.Optional;

/**
 * Contrato comum (Padrão Adapter/Connector) para integração com qualquer varejista
 * ou rede agregadora de afiliados.
 */
public interface RetailerConnector {

    /** Identificador único da loja ou conector (ex.: MERCADO_LIVRE, SHOPEE, ALIEXPRESS, AMAZON, LOMADEE, AWIN). */
    RetailerId getRetailerId();

    /** Verifica se o conector está habilitado via Feature Flag e com credenciais válidas. */
    boolean isEnabled();

    /** Busca ofertas promocionais com base nos critérios (termo, categoria, desconto mínimo, página). */
    List<UnifiedOffer> searchOffers(SearchCriteria criteria);

    /** Obtém os detalhes atualizados de uma oferta específica pelo ID externo na loja. */
    Optional<UnifiedOffer> getOfferById(String externalId);

    /** Converte/enriquece uma URL limpa de produto em URL monetizada de afiliado. */
    String buildAffiliateUrl(String originalUrl, String campaignSubId);
}
```

Mostre também o **Registro Dinâmico de Conectores (`ConnectorRegistry`)** e a configuração de **Feature Flags (`application.yml`)**:
- O Spring Boot injeta automaticamente `List<RetailerConnector>` no construtor de `ConnectorRegistry`, indexando-os em um `Map<RetailerId, RetailerConnector>`.
- As Feature Flags ficam em `@ConfigurationProperties(prefix = " Integration.retailers")`, permitindo ativar/desativar qualquer loja em tempo real via variáveis de ambiente (`RETAILER_SHOPEE_ENABLED=true`, `RETAILER_AMAZON_ENABLED=false`) ou via Spring Cloud Config sem recompilar o código.
- A execução paralela de buscas em múltiplas lojas utiliza `CompletableFuture.supplyAsync(..., virtualThreadOrBoundedExecutor)` com isolamento de falhas (`exceptionally`), garantindo que **se uma loja sofrer timeout ou retornar HTTP 429, as demais lojas continuam retornando ofertas normalmente (degradação graciosa / falha parcial controlada)**.

### 6.2 Schema Unificado de Oferta (`UnifiedOffer`)
Apresente a especificação completa do JSON Schema e do Record Java para `UnifiedOffer`, contemplando todos os 18 grupos de atributos exigidos:

```json
{
  "internalId": "0192d8f4-7a10-7b2c-9d3e-4f5a6b7c8d9e",
  "retailer": "SHOPEE",
  "connectorSource": "SHOPEE_OPEN_API",
  "externalId": "SHP-2481920391",
  "eanGtin": "7891234567890",
  "skuCanonical": "APPLE-IPHONE-15-128GB-PRETO",
  "title": "Apple iPhone 15 128GB Preto 5G Tela 6.1\"",
  "urlOriginal": "https://shopee.com.br/product/123456/2481920391",
  "urlAffiliate": "https://s.shopee.com.br/4f9aBcDeFg",
  "imageUrl": "https://cf.shopee.com.br/file/br-11134207-7r98o-...",
  "pricing": {
    "currency": "BRL",
    "currentPrice": 4299.00,
    "originalPrice": 5499.00,
    "discountAbsolute": 1200.00,
    "discountPercentage": 21.82,
    "installmentText": "10x de R$ 429,90 sem juros",
    "pixOrCashPrice": 4099.00,
    "isInternational": false,
    "estimatedImportTax": 0.00
  },
  "coupon": {
    "code": "TECH200",
    "discountDescription": "R$ 200 OFF acima de R$ 3.000",
    "discountValue": 200.00,
    "discountType": "FIXED",
    "validUntil": "2026-11-01T23:59:59Z"
  },
  "category": {
    "externalCategoryId": "100013",
    "canonicalCategory": "SMARTPHONES"
  },
  "rating": {
    "averageStars": 4.9,
    "reviewsCount": 1420,
    "soldCount": 3850
  },
  "seller": {
    "externalSellerId": "123456",
    "name": "Apple Loja Oficial",
    "isOfficialStore": true,
    "reputationLevel": "PLATINUM"
  },
  "shipping": {
    "freeShipping": true,
    "minShippingCost": 0.00,
    "fulfillmentType": "FULL"
  },
  "stock": {
    "inStock": true,
    "availableQuantity": 42
  },
  "timestamps": {
    "collectedAt": "2026-10-29T23:50:00Z",
    "expiresAt": "2026-10-30T05:50:00Z"
  },
  "contentHash": "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
}
```

### 6.3 Estratégia de Deduplicação Multi-Loja, Atualização/Expiração e Links de Afiliado
1. **Deduplicação em Dois Níveis:**
   - **Nível 1 — Deduplicação Intra-Loja (Idempotência de Coleta):** Chave composta `(retailer, externalId)` + `contentHash = SHA256(retailer + ":" + externalId + ":" + currentPrice + ":" + couponCode + ":" + inStock)`. Armazenada no Redis com TTL de 6 horas. Se a API de Busca coletar a mesma oferta na mesma loja com exatamente o mesmo preço, cupom e estoque dentro da janela de 6h, ela **não reenvia** para a API de Filtros, economizando até 85% de tráfego!
   - **Nível 2 — Deduplicação e Agrupamento Entre Lojas (Product Matching):**
     - **Prioridade 1 (Match Exato):** Código de barras universal `EAN-13 / GTIN` (disponível nos atributos do Mercado Livre, Amazon `ExternalIds` e feeds da Awin/Lomadee).
     - **Prioridade 2 (Match por Marca + Modelo/MPN):** Combinação normalizada `UPPER(marca) + "_" + UPPER(modelo)`.
     - **Prioridade 3 (Similaridade Textual Tokenizada):** Normalização de título (remoção de stopwords, extração de tokens de capacidade como `128GB`, `8GB RAM`, `110V`/`Bivolt`) + índice `pg_trgm` (Trigram Similarity $\ge 0.82$) no PostgreSQL. Ofertas do mesmo produto em lojas diferentes recebem o mesmo `skuCanonical`, permitindo comparar se o preço na Shopee está mais barato que na Amazon ou no Mercado Livre.
2. **Estratégia de Atualização (Refresh) e Expiração (TTL):**
   - Ofertas de e-commerce são altamente voláteis. Toda oferta recebe um `expiresAt` padrão de **4 a 6 horas** (ou igual à data fim da campanha relâmpago `periodEndTime`).
   - Antes da API de Compartilhamento disparar uma oferta em um canal (ex.: Telegram/WhatsApp), ou quando a oferta está próxima de expirar, um worker executa `connector.getOfferById(externalId)` para confirmar preço e estoque em tempo real (*Just-in-Time Revalidation*). Se `inStock == false` ou `currentPrice > originalOfferPrice`, a oferta é marcada como `EXPIRED` imediatamente.
3. **Estratégia de Geração de Link de Afiliado:**
   - Geração em lote na origem quando suportado (`Shopee: offerLink / generateShortLink`, `AliExpress: promotion_link / link.generate`, `Amazon: DetailPageURL com PartnerTag`, `Lomadee/Awin: deeplink`).
   - Inclusão sistemática de `subId` rastreável no formato `agy_{retailer}_{canonicalCategory}_{date}` para permitir medir qual categoria e qual loja geram mais receita de comissão.

---

## SEÇÃO 7: CONTRATOS E MUDANÇAS NOS DEMAIS COMPONENTES DO PIPELINE (FASE 4)

Esta seção deve detalhar todas as alterações necessárias nos Serviços 2, 3 e 4 para suportar os dados reais vindos da nova API de Busca, indicando claramente o que é **MIGRAÇÃO OBRIGATÓRIA** vs. **EVOLUTIVA**.

### 7.1 Contrato Oficial entre API de Busca (Serviço 1) e API de Filtros (Serviço 2)
- **Endpoint na API de Filtros:** `POST /api/v1/filter/offers:evaluate-batch`
- **Headers Obrigatórios:**
  - `Content-Type: application/json`
  - `X-Internal-Api-Key: <SHARED_SECRET>` (ou `Authorization: Bearer <SERVICE_JWT>`)
  - `X-Idempotency-Key: <UUID_DO_LOTE>`
  - `X-Correlation-Id: <TRACE_ID>`
- **Payload de Envio (Request):** Lote (`batchId`, `collectedAt`, `sourceInstance`, `offers: List<UnifiedOffer>`) contendo até 100 ofertas normalizadas por requisição.
- **Formato de Resposta da API de Filtros (`200 OK` / `207 Multi-Status`):**
```json
{
  "batchId": "b8c4e1a0-92f1-4c11-8d2e-112233445566",
  "processedAt": "2026-10-29T23:50:02Z",
  "summary": {
    "received": 50,
    "approved": 12,
    "rejected": 38
  },
  "results": [
    {
      "internalId": "0192d8f4-7a10-7b2c-9d3e-4f5a6b7c8d9e",
      "externalId": "SHP-2481920391",
      "retailer": "SHOPEE",
      "status": "APPROVED",
      "qualityScore": 92.5,
      "realDiscountVerified": true,
      "historicalAvgPrice30d": 5120.00,
      "historicalLowestPrice90d": 4199.00,
      "rejectionReasons": []
    },
    {
      "internalId": "0192d8f4-8888-7b2c-9d3e-999999999999",
      "externalId": "MLB-99887766",
      "retailer": "MERCADO_LIVRE",
      "status": "REJECTED",
      "qualityScore": 31.0,
      "realDiscountVerified": false,
      "historicalAvgPrice30d": 199.90,
      "historicalLowestPrice90d": 179.90,
      "rejectionReasons": [
        "FAKE_DISCOUNT_DETECTED: Preço atual (R$ 195,00) é apenas 2.4% menor que a média dos últimos 30 dias (R$ 199,90), apesar do varejista anunciar 'De R$ 399,00'.",
        "LOW_SELLER_REPUTATION"
      ]
    }
  ]
}
```

### 7.2 Mudanças Necessárias na API de Filtros (Serviço 2)
1. **[MIGRAÇÃO OBRIGATÓRIA] Regra Anti-Fraude de Falso Desconto ("Metade do Dobro"):**
   - Não confiar no campo `originalPrice` ("preço riscado") informado pelo varejista isoladamente.
   - A API de Filtros deve consultar a tabela `historico_precos` dos últimos 30 e 90 dias no Banco de Dados:
     $$\text{Desconto Real (\%)} = \left(1 - \frac{\text{Preço Atual Efetivo (com cupom + imposto)}}{\text{Mediana do Preço nos Últimos 30 Dias}}\right) \times 100$$
   - Só aprovar ofertas cujo **Desconto Real** seja $\ge X\%$ (ex.: $\ge 15\%$) em relação à mediana de 30 dias (ou para produtos inéditos sem histórico, exigir vendedor oficial/reputação alta e desconto anunciado $\ge 25\%$).
2. **[MIGRAÇÃO OBRIGATÓRIA] Regra de Custo Efetivo Total (Frete + Imposto de Importação):**
   - Para ofertas internacionais (`AliExpress`, `Shopee Internacional`), somar `currentPrice + estimatedImportTax + minShippingCost` antes de avaliar se o preço bate os concorrentes nacionais.
3. **[MIGRAÇÃO OBRIGATÓRIA] Filtro de Qualidade de Vendedor e Estoque Mínimo:**
   - Rejeitar ofertas com `availableQuantity < 3` (evita postar ofertas que esgotam em 5 segundos gerando frustração nos usuários), `rating.averageStars < 4.2` ou vendedores sem reputação.
4. **[EVOLUTIVA] Score de Relevância (0 a 100):**
   - Combinar `% Desconto Real (peso 40%)` + `Volume de Vendas/Reviews (peso 25%)` + `Loja Oficial/Full (peso 20%)` + `Presença de Cupom/Frete Grátis (peso 15%)`.

### 7.3 Mudanças Necessárias no Banco de Dados (Serviço 3 — Scripts DDL SQL)
Apresente no PDF o script de migração **Flyway (`V2__create_real_retailer_offers_schema.sql`)** com as novas tabelas, colunas e índices obrigatórios:

1. **[MIGRAÇÃO OBRIGATÓRIA] Tabela `loja_parceira`:** Cadastro das lojas (`MERCADO_LIVRE`, `SHOPEE`, `ALIEXPRESS`, `AMAZON`, `MAGALU`, `KABUM`, `AMERICANAS`, `CASAS_BAHIA`, `NETSHOES`), status ativo, configurações de comissão e rate limit.
2. **[MIGRAÇÃO OBRIGATÓRIA] Tabela `oferta_externa`:**
   - Colunas: `id UUID PRIMARY KEY`, `id_loja VARCHAR(30) NOT NULL`, `id_externo VARCHAR(100) NOT NULL`, `sku_canonico VARCHAR(120)`, `ean_gtin VARCHAR(20)`, `titulo VARCHAR(500) NOT NULL`, `url_original TEXT NOT NULL`, `url_afiliado TEXT NOT NULL`, `url_imagem TEXT`, `moeda CHAR(3) DEFAULT 'BRL'`, `preco_atual NUMERIC(12,2) NOT NULL`, `preco_original NUMERIC(12,2)`, `preco_pix NUMERIC(12,2)`, `percentual_desconto NUMERIC(5,2)`, `imposto_estimado NUMERIC(12,2) DEFAULT 0`, `codigo_cupom VARCHAR(60)`, `valor_cupom NUMERIC(12,2)`, `frete_gratis BOOLEAN DEFAULT FALSE`, `avaliacao_media NUMERIC(3,2)`, `total_avaliacoes INT`, `vendedor_nome VARCHAR(200)`, `loja_oficial BOOLEAN DEFAULT FALSE`, `estoque_disponivel INT`, `em_estoque BOOLEAN DEFAULT TRUE`, `score_qualidade NUMERIC(5,2)`, `status_filtro VARCHAR(20) NOT NULL`, `hash_conteudo VARCHAR(64) NOT NULL`, `coletado_em TIMESTAMPTZ NOT NULL`, `expira_em TIMESTAMPTZ NOT NULL`, `atualizado_em TIMESTAMPTZ NOT NULL`.
   - **Constraint Única Obrigatória:** `UNIQUE (id_loja, id_externo)` para suportar `UPSERT` (`INSERT ... ON CONFLICT (id_loja, id_externo) DO UPDATE`).
3. **[MIGRAÇÃO OBRIGATÓRIA] Tabela `historico_preco_oferta` (Série Temporal):**
   - Colunas: `id BIGSERIAL PRIMARY KEY`, `id_oferta UUID NOT NULL REFERENCES oferta_externa(id)`, `preco_registrado NUMERIC(12,2) NOT NULL`, `preco_com_cupom NUMERIC(12,2)`, `em_estoque BOOLEAN NOT NULL`, `registrado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
   - Permite calcular mínimo de 90 dias, mediana de 30 dias e gráfico de evolução de preços.
4. **[MIGRAÇÃO OBRIGATÓRIA] Índices de Alta Performance:**
   - `CREATE UNIQUE INDEX idx_oferta_loja_externo ON oferta_externa (id_loja, id_externo);`
   - `CREATE INDEX idx_oferta_ean_gtin ON oferta_externa (ean_gtin) WHERE ean_gtin IS NOT NULL;`
   - `CREATE INDEX idx_oferta_status_score ON oferta_externa (status_filtro, score_qualidade DESC, coletado_em DESC) WHERE em_estoque = TRUE;`
   - `CREATE INDEX idx_historico_oferta_data ON historico_preco_oferta (id_oferta, registrado_em DESC);`
   - Extensão `pg_trgm`: `CREATE EXTENSION IF NOT EXISTS pg_trgm; CREATE INDEX idx_oferta_titulo_trgm ON oferta_externa USING gin (titulo gin_trgm_ops);`

### 7.4 Mudanças Necessárias na API de Compartilhamento (Serviço 4)
1. **[MIGRAÇÃO OBRIGATÓRIA] Consumo de `urlAffiliate` e Template Dinâmico Rico:**
   - A API de Compartilhamento deve deixar de usar links estáticos e passar a formatar mensagens com: Título, Loja, Preço Anterior Riscado ($\sim\text{R\$ X}\sim$), Preço Atual em Destaque, Preço no PIX, Código do Cupom (se houver, ex.: `🎟 Use o cupom: TECH200`), Aviso de Taxa de Importação (se `isInternational == true`), Menor Preço em 90 dias e `urlAffiliate`.
2. **[MIGRAÇÃO OBRIGATÓRIA] Revalidação Just-in-Time e Controle de Cooldown (Anti-Spam):**
   - Antes de publicar nos canais (Telegram/WhatsApp), verificar se `expira_em > NOW()` e se a mesma oferta (`id_loja + id_externo`) já não foi compartilhada nas últimas 24 horas (exceto se o preço caiu mais $\ge 10\%$).
3. **[EVOLUTIVA] Divulgação Legal de Afiliado (Compliance CONAR / CDC):**
   - Incluir rodapé automático transparente nas publicações: *"Aviso: Contém link de afiliado. Preços e estoques sujeitos a alteração a qualquer momento pelo varejista."*

---

## SEÇÃO 8: ROADMAP DE EXECUÇÃO EM 4 ETAPAS (FASE 5)

Estruture o Roadmap em fases sequenciais claras, com entregas, ordem de execução, critérios de aceite objetivos e riscos:

### Fase 1: Quick Wins & Saneamento da Base Atual (Semana 1 — Dias 1 a 5)
- **Entregas:**
  1. Limpeza do repositório (`maven.zip`, `maven_dir/`, `target/`), criação de `.gitignore` e `Dockerfile` multi-stage.
  2. Externalização de segredos em `application.properties` (`${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`).
  3. Introdução do **Flyway** (`V1__baseline_schema.sql` e `V2__create_real_retailer_offers_schema.sql`) e mudança para `ddl-auto=validate`.
  4. Correção dos bugs críticos diagnosticados: log estruturado em `GlobalExceptionHandler.java:59`, paginação (`Pageable`) em `ProdutoController`/`PromocaoController`, query filtrada via banco em `PromocaoService.listar` (`PromocaoService.java:70-76`), correção do N+1 em `CatalogoService` (`CatalogoService.java:41-43`) e remoção de código morto simulado (`ProdutoDigital.java:30-34`, `ProdutoFisico.java:38-45`).
  5. Adição de `spring-boot-starter-actuator` (`/actuator/health`, `/actuator/prometheus`).
- **Critérios de Aceite:** Build limpo via Maven; 0 credenciais hardcoded; Flyway executando migrações V1 e V2 automaticamente; testes unitários cobrindo 80%+ dos services atuais; endpoint `/actuator/health` retornando `UP`.

### Fase 2: MVP de Integração Real com 2 Lojas + Contrato com Filtros (Semanas 2 e 3)
- **Entregas:**
  1. Implementação do core `RetailerConnector`, `ConnectorRegistry`, Feature Flags e `UnifiedOffer`.
  2. Implementação do **Conector 1: Shopee Open API (GraphQL)** (`productOfferV2` + `generateShortLink` com assinatura SHA256).
  3. Implementação do **Conector 2: AliExpress Open Platform (`AE-Affiliate`)** (`aliexpress.affiliate.product.query` em `BRL` + cálculo de estimativa de imposto Remessa Conforme).
  4. Camada de deduplicação por `contentHash` (Caffeine/Redis) e persistência em `oferta_externa` + `historico_preco_oferta`.
  5. Implementação do cliente HTTP para envio de lotes à **API de Filtros** (`POST /api/v1/filter/offers:evaluate-batch`).
- **Critérios de Aceite:** Coleta real de pelo menos 100 ofertas válidas da Shopee e AliExpress em ambiente de staging; 100% das ofertas contendo `urlAffiliate` funcional e rastreável; suíte de testes de integração com WireMock validando parsing, erros HTTP 429/500 e contrato JSON com a API de Filtros.

### Fase 3: Expansão de Varejistas Nacionais (Semanas 4 e 5)
- **Entregas:**
  1. Implementação do **Conector 3: Mercado Livre (`MLB`)** (fluxo OAuth 2.0 com auto-refresh de token no Redis, busca `/sites/MLB/search`, multiget `/items?ids=` de 20 em 20 e enriquecimento de link de afiliado).
  2. Implementação do **Conector 4: Amazon Brasil (`Creators API` / `PA-API`)** com Rate Limiter estrito de `1 req/s` e batching de 10 ASINs.
  3. Implementação do **Conector 5: Lomadee API v3 & Awin Product Feeds** cobrindo **Magazine Luiza, KaBuM!, Americanas, Casas Bahia e Netshoes** de forma unificada.
  4. Motor de *Product Matching* entre lojas por `EAN/GTIN` e similaridade de título (`pg_trgm`).
- **Critérios de Aceite:** Todas as 9 lojas configuradas via Feature Flags; ingestão de feeds Awin processando arquivos GZIP em streaming (memória constante $< 256\text{MB}$); agrupamento de ofertas do mesmo EAN entre pelo menos 3 lojas distintas.

### Fase 4: Hardening, Resiliência, Observabilidade e Go-Live (Semana 6)
- **Entregas:**
  1. Configuração fina de `Resilience4j` por conector (Circuit Breaker, Retry com Exponential Backoff + Jitter para HTTP 429/503, Timeouts de conexão 3s e leitura 8s, Bulkhead por loja).
  2. Dashboards Grafana + alertas Prometheus (taxa de erro por conector, latência P95/P99, ofertas coletadas vs. aprovadas por loja, validade de tokens OAuth).
  3. Revalidação *Just-in-Time* integrada com a API de Compartilhamento antes do disparo nos canais.
  4. Testes de carga e caos (simulando queda de 2 varejistas simultaneamente para comprovar isolamento de falhas).
- **Critérios de Aceite:** P95 de processamento de lote $< 2\text{s}$; zero perda de mensagens em caso de indisponibilidade temporária da API de Filtros (Outbox/Retry); aprovação nos testes de segurança (autenticação entre serviços e proteção SSRF).

---

## SEÇÃO 9: CHECKLIST DE ACEITE E MATRIZ DE RISCOS E CONFORMIDADE LEGAL

Inclua uma seção executiva contendo:
1. **Checklist de Aceite de Engenharia (Pré-Produção):** Lista verificável de 15 itens cobrindo Segurança, Qualidade de Dados, Resiliência, Banco de Dados, Contratos e Monitoramento.
2. **Matriz de Riscos Técnicos, Operacionais e Legais:**
   - *Risco 1:* Suspensão de chave da Amazon Creators API por falta de 3 vendas iniciais ou inatividade de 30 dias $\rightarrow$ *Mitigação:* Priorizar tráfego inicial nos links Amazon até atingir a meta e usar fallback de desativação automática via Feature Flag.
   - *Risco 2:* Expiração do `refresh_token` do Mercado Livre ou falha concorrente na renovação do token OAuth 2.0 $\rightarrow$ *Mitigação:* Lock distribuído no Redis (`SETNX`) durante o refresh do token e alerta preventivo se o refresh falhar.
   - *Risco 3:* Divergência de preço entre o momento da coleta e o clique do usuário final (oferta relâmpago encerrada) $\rightarrow$ *Mitigação:* Revalidação Just-in-Time via `getOfferById` antes do compartilhamento e aviso legal de timestamp da coleta na mensagem publicada.
   - *Risco 4:* Publicação de falsos descontos ("metade do dobro") em datas sazonais como Black Friday $\rightarrow$ *Mitigação:* Bloqueio obrigatório na API de Filtros baseado na mediana de 30 dias da tabela `historico_preco_oferta`.

---

## SEÇÃO 10: APÊNDICE — LINKS OFICIAIS DAS DOCUMENTAÇÕES CONSULTADAS

Inclua integralmente no Apêndice do PDF todos os links oficiais verificados:
1. **Mercado Livre Developers (Portal Oficial Brasil):** `https://developers.mercadolivre.com.br/`
2. **Mercado Livre — Autenticação e Autorização (OAuth 2.0):** `https://developers.mercadolivre.com.br/pt_br/autenticacao-e-autorizacao`
3. **Mercado Livre — Itens e Buscas (`/sites/MLB/search` e `/items`):** `https://developers.mercadolivre.com.br/pt_br/itens-e-buscas`
4. **Mercado Livre — Programa Oficial de Afiliados:** `https://www.mercadolivre.com.br/l/afiliados-home`
5. **Shopee Brasil — Portal do Programa de Afiliados:** `https://affiliate.shopee.com.br/`
6. **Shopee Brasil — Endpoint e Console GraphQL Affiliate Open API:** `https://open-api.affiliate.shopee.com.br/graphql`
7. **AliExpress — Portal Oficial de Afiliados (AliExpress Portals):** `https://portals.aliexpress.com/`
8. **AliExpress — Open Platform / Developer Console (`AE-Affiliate`):** `https://openservice.aliexpress.com/` e `https://developers.aliexpress.com/`
9. **Amazon Brasil — Programa de Associados:** `https://associados.amazon.com.br/`
10. **Amazon — Documentação Oficial Creators API (Sucessora da PA-API 5.0):** `https://affiliate-program.amazon.com/creatorsapi/docs/`
11. **Amazon — Guia de Migração PA-API 5.0 para Creators API:** `https://affiliate-program.amazon.com/creatorsapi/docs/en-us/migrating-to-creatorsapi-from-paapi`
12. **Amazon — Documentação Product Advertising API 5.0:** `https://webservices.amazon.com/paapi5/documentation/`
13. **Lomadee — Documentação Oficial da API v3 de Afiliados (`api.lomadee.com.br`):** `https://docs.lomadee.com.br/`
14. **Awin — Documentação Oficial Publisher API:** `https://wiki.awin.com/index.php/Publisher_API`
15. **Awin — Documentação Oficial Product Feeds (Download de Catálogos):** `https://wiki.awin.com/index.php/Product_Feed_List`
16. **Magazine Luiza — Portal Magalu Devs (OpenAPI):** `https://developer.magalu.com/`
17. **Magazine Luiza — API B2B Catálogo Magalu Empresas:** `https://institucional.magaluempresas.com.br/`
18. **Magazine Luiza — Parceiro Magalu (Afiliados):** `https://www.parceiromagalu.com.br/`

