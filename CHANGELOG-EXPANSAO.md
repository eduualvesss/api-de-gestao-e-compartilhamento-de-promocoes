# CHANGELOG — Expansão da API de Gestão/Busca de Promoções (`feat/expansao-busca`)

Este documento registra todas as entregas implementadas **exclusivamente dentro do escopo da API de Gestão/Busca de Promoções** (`com.ecommerce:catalogo-promocoes`), as divergências verificadas entre `prompt-gerar-pdf-refinamento.md` e o código real, o que permaneceu pendente e os resultados reais de testes automatizados e *smoke tests* ao vivo.

---

## 1. Resumo do que foi implementado na API de Gestão/Busca

### 1.1. Segurança de Configuração, Observabilidade e Execução Local
- **Remoção de credenciais fixas de banco e externalização completa via variáveis de ambiente:**
  - `src/main/resources/application.properties:1-98` — substituição de usuário/senha literais por `${DB_URL:...}`, `${DB_USERNAME:...}`, `${DB_PASSWORD:}` e mapeamento tipado de todas as variáveis `integration.*` (resiliência, câmbio/imposto, API de Filtros e 9 varejistas).
  - `src/main/resources/application-local.properties:1-12` — perfil `local` com H2 em memória (`MODE=PostgreSQL`) para desenvolvimento e *smoke test* sem exigir servidor PostgreSQL instalado no host.
  - `.gitignore:29-33` — proteção de `.env` e `.env.*` mantendo `!.env.example` rastreado.
- **Spring Boot Actuator & Health Indicator customizado por loja:**
  - `pom.xml:40-43` — adição de `spring-boot-starter-actuator` e ajuste do escopo de `com.h2database:h2` para `runtime` (`pom.xml:51-55`).
  - `src/main/java/com/ecommerce/catalogo/integration/health/RetailerConnectorsHealthIndicator.java:1-44` — publica o componente `retailerConnectors` em `GET /actuator/health` com contagem de lojas registradas, ativas, prontas em modo `REAL`, prontas em `SANDBOX`, sem credenciais e com *Circuit Breaker* aberto.
- **Log estruturado em erros internos HTTP 500:**
  - `src/main/java/com/ecommerce/catalogo/exception/GlobalExceptionHandler.java:19,65-72` — registro `log.error` com *stacktrace* antes de retornar HTTP 500, preservando todas as rotas e exceções existentes (`GlobalExceptionHandler.java:21-63`).
  - `src/main/java/com/ecommerce/catalogo/CatalogoApplication.java:7-13` — ativação de `@ConfigurationPropertiesScan`.

### 1.2. Padrão Adapter/Connector, Registro Dinâmico e Feature Flags
- **Contrato unificado de conector (`RetailerConnector`):**
  - `src/main/java/com/ecommerce/catalogo/integration/connector/RetailerConnector.java:1-41` — define `search(SearchCriteria)`, `getOffer(String)`, `normalize(JsonNode, DataSourceMode)`, `buildAffiliateUrl(String)`, `checkHealth()`, `isEnabled()`/`setEnabled(boolean)` e `isSandboxMode()`/`setSandboxMode(boolean)`.
- **Classe base com guarda estrita contra dados falsos disfarçados de reais (`AbstractRetailerConnector`):**
  - `src/main/java/com/ecommerce/catalogo/integration/connector/AbstractRetailerConnector.java:1-223` — se `hasCredentials() == false` e `sandboxMode == false` (padrão em todas as lojas), lança `ConnectorConfigurationException` e reporta `MISSING_CREDENTIALS` (`AbstractRetailerConnector.java:53-77,113-138`). Quando `sandboxMode == true` é ativado explicitamente via flag ou endpoint de gestão, retorna ofertas marcadas com `dataSourceMode: "SANDBOX"` e prefixo `[SANDBOX]` (`AbstractRetailerConnector.java:60-62,155-221`).
- **Registro dinâmico (`ConnectorRegistry`):**
  - `src/main/java/com/ecommerce/catalogo/integration/connector/ConnectorRegistry.java:1-50` — descobre automaticamente todos os beans `RetailerConnector` em `EnumMap<RetailerId, RetailerConnector>`, filtrando conectores ativos por loja na busca.

### 1.3. Conectores Oficiais/Afiliados Implementados (9 Varejistas, Zero Scraping)
Seguindo a ordem de viabilidade oficial do plano (sem qualquer *web scraping*):
1. **Shopee Brasil (`SHOPEE`) — API Oficial GraphQL v2:**
   - `src/main/java/com/ecommerce/catalogo/integration/connector/impl/ShopeeConnector.java:1-292` — assinatura `SHA256(AppId + Timestamp + Payload + Secret)` (`CryptoSigningUtils.java:24-39`), query `productOfferV2` e geração de *short link* via `generateShortLink`. Documentação: https://affiliate.shopee.com.br/open_api
2. **AliExpress (`ALIEXPRESS`) — AliExpress Affiliate Open Platform (`taobao.top`):**
   - `src/main/java/com/ecommerce/catalogo/integration/connector/impl/AliExpressConnector.java:1-311` — assinatura `HMAC-SHA256` em caixa alta sobre parâmetros ordenados (`CryptoSigningUtils.java:41-56`), métodos `aliexpress.affiliate.product.query`, `aliexpress.affiliate.productdetail.get` e `aliexpress.affiliate.link.generate`, conversão cambial USD→BRL quando aplicável e cálculo automático de tributo estimado (*Remessa Conforme*). Documentação: https://openservice.aliexpress.com/
3. **Mercado Livre (`MERCADO_LIVRE`) — API Oficial REST `MLB`:**
   - `src/main/java/com/ecommerce/catalogo/integration/connector/impl/MercadoLivreConnector.java:1-323` — autenticação OAuth 2.0 `Bearer` + renovação automática via `refresh_token` (`POST /oauth/token`), busca em `/sites/MLB/search`, detalhes em `/items/{id}` e parametrização de link de afiliado (`matt_tool`/`matt_word`). Documentação: https://developers.mercadolivre.com.br/pt_br/itens-e-buscas
4. **Amazon Brasil (`AMAZON`) — Amazon Creators API (OAuth 2.0 / Login with Amazon):**
   - `src/main/java/com/ecommerce/catalogo/integration/connector/impl/AmazonConnector.java:1-325` — obtenção e cache de token OAuth 2.0 `client_credentials`, chamadas `searchItems` e `getItems` para `www.amazon.com.br` com `partnerTag` e *throttling* dedicado configurável (`AMAZON_MAX_RPS=1.0`). Documentação: https://affiliate-program.amazon.com/creatorsapi/docs/en-us/introduction
5. **Rede Lomadee API v3 (`MAGALU`, `KABUM`, `CASAS_BAHIA`, `AMERICANAS`, `NETSHOES`):**
   - `src/main/java/com/ecommerce/catalogo/integration/connector/impl/LomadeeRetailerConnector.java:1-255` e `src/main/java/com/ecommerce/catalogo/integration/connector/impl/LomadeeConnectorsConfig.java:1-72` — conector parametrizado por `sourceId` + `appToken` + `storeId` consumindo `/v3/{appToken}/offer/_search`, `/offer/_id/{id}` e `/deeplink/_create` para Magazine Luiza, KaBuM!, Casas Bahia, Americanas e Netshoes. Documentação: https://developer.lomadee.com/

### 1.4. Schema Unificado (`UnifiedOffer`), Normalização, Resiliência e Deduplicação
- **Schema `UnifiedOffer`:**
  - `src/main/java/com/ecommerce/catalogo/integration/model/UnifiedOffer.java:1-124` — contempla `internalId`, `retailer`, `connectorSource`, `dataSourceMode` (`REAL`/`SANDBOX`), `externalId`, `eanGtin`, `mpn`, `skuCanonical`, `title`, `urlOriginal`, `urlAffiliate`, `imageUrl`, `pricing` (moeda, preço atual/original, desconto absoluto/percentual, internacional, imposto estimado), `coupon`, `category`, `rating`, `seller`, `shipping`, `stock`, `timestamps` (`collectedAt`, `expiresAt`) e `contentHash` (`sha256:...`).
- **Normalização monetária e fiscal (`PriceAndCurrencyNormalizer`):**
  - `src/main/java/com/ecommerce/catalogo/integration/service/PriceAndCurrencyNormalizer.java:1-111` — conversão de moedas estrangeiras para `BRL`, prevenção de desconto negativo quando `originalPrice < currentPrice`, cálculo percentual `HALF_UP` e estimativa do *Remessa Conforme* (20% II até US\$ 50 ou 60% II − US\$ 20 acima de US\$ 50 + ICMS por dentro de 17%).
- **Resiliência por loja (`ConnectorResilienceExecutor`):**
  - `src/main/java/com/ecommerce/catalogo/integration/resilience/ConnectorResilienceExecutor.java:1-217` — *Rate Limiter* por janela de RPS, *Retry* com *exponential backoff* + *jitter* apenas para falhas transitórias (HTTP 429, HTTP 5xx e erros de rede/timeout) e *Circuit Breaker* (`CLOSED` / `OPEN` / `HALF_OPEN`) isolado por `RetailerId`.
- **Deduplicação em 2 níveis (`OfferDeduplicator`):**
  - `src/main/java/com/ecommerce/catalogo/integration/service/OfferDeduplicator.java:1-169` — Nível 1 intra-loja (`retailer + externalId`), Nível 2 *cross-store* opcional (`EAN/GTIN` canônico ou marca+modelo normalizados, mantendo o menor custo efetivo `currentPrice + estimatedImportTax + minShippingCost`) e cache temporal de `contentHash` para evitar reenvio redundante à API de Filtros.
- **Orquestrador paralelo tolerante a falhas parciais (`OfferSearchOrchestratorService`) + Cliente da API de Filtros (`FilterServiceClient`):**
  - `src/main/java/com/ecommerce/catalogo/integration/service/OfferSearchOrchestratorService.java:1-166` — fan-out assíncrono com `CompletableFuture` em pool dedicado (`IntegrationHttpConfig.java:24-31`); se uma loja falhar ou não tiver credenciais, as demais seguem normalmente.
  - `src/main/java/com/ecommerce/catalogo/integration/filter/FilterServiceClient.java:1-104` — envia `POST /api/v1/filter/offers:evaluate-batch` com `X-Idempotency-Key`, `X-Correlation-Id` e `X-Internal-Api-Key` opcional; se a API de Filtros estiver indisponível, retorna `status: "FAILED_DEGRADED"` sem derrubar a resposta da API de Busca.

### 1.5. Novos Endpoints REST Adicionados (9 rotas)
- **Gestão operacional de conectores (`src/main/java/com/ecommerce/catalogo/controller/LojaConectorController.java:20-89`):**
  - `GET /lojas` — lista os 9 conectores com status operacional, modo (`REAL`/`SANDBOX`), credenciais e estado do *Circuit Breaker*.
  - `GET /lojas/health` — resumo consolidado de saúde dos conectores.
  - `GET /lojas/{loja}/health` — saúde detalhada de uma loja específica.
  - `PATCH /lojas/{loja}/ativar?sandbox={true|false}` — ativa a *feature flag* da loja (opcionalmente definindo `sandboxMode`) e reseta o *Circuit Breaker*.
  - `PATCH /lojas/{loja}/desativar` — desativa a *feature flag* da loja em tempo de execução.
  - `PATCH /lojas/{loja}/modo` — altera explicitamente `{"sandboxMode": true|false}`.
- **Busca agregada e despacho (`src/main/java/com/ecommerce/catalogo/controller/BuscaOfertaController.java:19-89`):**
  - `GET /ofertas/busca` — busca multi-varejista com filtros (`termo`, `categoria`, `descontoMin`, `precoMax`, `lojas`, `deduplicarEntreLojas`, `enviarParaFiltro`, `pagina`, `limite`).
  - `POST /ofertas/coletar-e-filtrar` — executa a coleta paralela, deduplica ofertas inéditas e despacha lote para a API de Filtros.
  - `GET /ofertas/{loja}/{idExterno}` — consulta/revalida oferta individual diretamente no conector da loja.

---

## 2. Divergências Registradas (`prompt-gerar-pdf-refinamento.md` vs. Código Real)

Conforme a regra onde o **código prevalece**:
1. **Existência de `.gitignore`:** o `.md` afirmava que `.gitignore` estava ausente, mas o arquivo já existia na raiz (`.gitignore:1-44`) ignorando `target/`, `maven_dir/` e `maven.zip`. Apenas acrescentamos `.env`, `.env.*` e `!.env.example` (`.gitignore:29-33`).
2. **Contagem de endpoints originais:** o código original possuía **20 endpoints REST** em 5 controllers (`ProdutoController` [6], `CatalogoController` [6], `PromocaoController` [4], `EstoqueController` [3], `PrecoFinalController` [1]), e não 17. Todos os 20 endpoints originais foram preservados intactos.
3. **Contagem de tabelas JPA:** o modelo relacional original gera **10 tabelas** (`produtos`, `produtos_fisicos`, `produtos_digitais`, `catalogos`, `catalogo_produtos`, `estoques`, `promocoes`, `promocoes_percentual`, `promocoes_quantidade`, `cupons_promocionais`), e não 8.

---

## 3. O que Ficou Pendente

### 3.1. Dentro da API de Gestão/Busca (evoluções opcionais futuras)
- Persistência em Redis das *feature flags* alteradas em runtime (`PATCH /lojas/{loja}/ativar|desativar|modo`) caso a API de Busca escale horizontalmente para múltiplas réplicas (hoje o estado em runtime é mantido em memória por instância e inicializado via variáveis de ambiente `*_ENABLED` / `*_SANDBOX_MODE`).
- Agendador periódico (`@Scheduled`) de varredura automática por lista de termos — hoje o disparo pode ser acionado sob demanda ou por *cron* externo chamando `POST /ofertas/coletar-e-filtrar`.

### 3.2. Fora do Escopo (registrado em `PENDENCIAS-OUTROS-SERVICOS.md`)
- Criação/disponibilização dos repositórios e imagens da **API de Filtros** (`:8081`) e da **API de Compartilhamento** (`:8082`), ausentes no workspace local.
- Migrations Flyway/Liquibase no PostgreSQL compartilhado para suportar `UnifiedOffer` (`offers`, `offer_price_history`, `publication_log`) e substituição de `ddl-auto=update` por `validate`.
- Provisionamento humano das credenciais oficiais de afiliados (Shopee, AliExpress, Mercado Livre, Amazon Creators API, Lomadee) e canais (Telegram, WhatsApp Cloud API, Discord).

---

## 4. Como Testar e Resultados Reais Obtidos

### 4.1. Suíte Automatizada (Unitários por Conector + Resiliência + Contrato API de Filtros + Integração MockMvc)
Comando executado no PowerShell (usando o Maven 3.9.9 embutido no workspace e JDK 26):
```powershell
.\maven_dir\apache-maven-3.9.9\bin\mvn.cmd test
```
- **Configuração Java 26 (`pom.xml:75-83`):** configurado `maven-surefire-plugin` com `-Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading` para compatibilidade do Mockito/ByteBuddy com Java 26.
- **Resultado real obtido:**
  - `RetailerConnectorsUnitTest` (`src/test/java/com/ecommerce/catalogo/integration/RetailerConnectorsUnitTest.java:1-388`): **6/6 PASS** — testa assinaturas criptográficas SHA-256/HMAC-SHA256, *parsing* de payloads reais gravados para Shopee, AliExpress (incluindo *Remessa Conforme*), Mercado Livre, Amazon Creators API e Lomadee (Magalu/KaBuM!), além do bloqueio contra dados falsos quando `sandboxMode=false`.
  - `ResilienceAndDeduplicationTest` (`src/test/java/com/ecommerce/catalogo/integration/ResilienceAndDeduplicationTest.java:1-153`): **3/3 PASS** — testa *retry* com *backoff* em HTTP 429, abertura do *Circuit Breaker* em falhas consecutivas, isolamento de falha parcial entre lojas no `OfferSearchOrchestratorService` e deduplicação intra-loja e *cross-store* por EAN.
  - `FilterServiceContractTest` (`src/test/java/com/ecommerce/catalogo/integration/FilterServiceContractTest.java:1-158`): **2/2 PASS** — valida via `MockRestServiceServer` o payload JSON, campos obrigatórios e cabeçalhos (`X-Idempotency-Key`, `X-Correlation-Id`, `X-Internal-Api-Key`) em `POST /api/v1/filter/offers:evaluate-batch` e a degradação graciosa (`FAILED_DEGRADED`) diante de HTTP 503.
  - `ExpansaoApiIntegrationTest` (`src/test/java/com/ecommerce/catalogo/ExpansaoApiIntegrationTest.java:1-101`): **2/2 PASS** — sobe o contexto Spring Boot completo no perfil `local` e testa os endpoints `/lojas`, `/lojas/{loja}/ativar?sandbox=true`, `/ofertas/busca`, `/actuator/health` e `/produtos` via `MockMvc`.
  - **Total consolidado:** `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`.

### 4.2. Smoke Tests Reais Executados com o JAR Empacotado
Empacotamento executado:
```powershell
.\maven_dir\apache-maven-3.9.9\bin\mvn.cmd package
```
Gerado `target/catalogo-promocoes-0.0.1-SNAPSHOT.jar` (`BUILD SUCCESS`).

1. **Smoke Test A — Perfil padrão PostgreSQL (validação honesta de bloqueio de infraestrutura no host):**
   ```powershell
   java -jar target/catalogo-promocoes-0.0.1-SNAPSHOT.jar --server.port=18080
   ```
   - **Resultado real observado:** falha esperada na conexão JDBC pois PostgreSQL (`:5432`) e Docker não estão instalados nesta máquina Windows:
     `org.postgresql.util.PSQLException: Connection to localhost:5432 refused.`

2. **Smoke Test B — Perfil `local` (H2 em modo PostgreSQL) + chamadas HTTP reais via `curl.exe`:**
   ```powershell
   java -jar target/catalogo-promocoes-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.port=18080 --integration.filter-api.enabled=true
   ```
   - `curl.exe -s http://localhost:18080/actuator/health` → **HTTP 200** `{"status":"UP",...,"retailerConnectors":{"status":"UP","details":{"totalRegistered":9,"enabled":2,"readyReal":0,"readySandbox":0,"missingCredentials":2,"circuitOpen":0}}}`.
   - `curl.exe -s http://localhost:18080/lojas` → **HTTP 200** com `SHOPEE` e `ALIEXPRESS` em `operationalStatus: "MISSING_CREDENTIALS"` e `sandboxMode: false` (sem dados simulados disfarçados de reais).
   - `curl.exe -s -X PATCH "http://localhost:18080/lojas/SHOPEE/ativar?sandbox=true"` (idem para `ALIEXPRESS` e `AMAZON`) → **HTTP 200** transicionando explicitamente para `operationalStatus: "UP_SANDBOX"`.
   - `curl.exe -s "http://localhost:18080/ofertas/busca?termo=Galaxy&deduplicarEntreLojas=true"` → **HTTP 200** com `sandboxDataIncluded: true`, `totalRawOffers: 2`, `crossStoreDuplicatesCollapsed: 1`, mantendo a oferta de menor preço efetivo (`AMAZON` R\$ 3.299,00 vs `SHOPEE` R\$ 3.499,00).
   - `curl.exe -s "http://localhost:18080/ofertas/ALIEXPRESS/1005006218991"` → **HTTP 200** retornando `UnifiedOffer` normalizada com `isInternational: true`, `estimatedImportTax: 64.64` e cupom `BRJBL15`.
   - `curl.exe -s -X POST "http://localhost:18080/ofertas/coletar-e-filtrar?termo=JBL"` → **HTTP 200** retornando as ofertas coletadas e `filterDispatch.status: "FAILED_DEGRADED"` (`Connection refused` em `http://localhost:8081/api/v1/filter/offers:evaluate-batch`), comprovando que a indisponibilidade da API de Filtros não derruba a API de Busca.

