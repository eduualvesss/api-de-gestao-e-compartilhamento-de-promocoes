# REQUISITOS PARA RODAR O SISTEMA COMPLETO DE PROMOÇÕES

> **Pipeline Alvo:** `1. API de Gestão/Busca de Promoções` $\rightarrow$ `2. API de Filtros` $\rightarrow$ `3. Banco de Dados` $\rightarrow$ `4. API de Compartilhamento`
>
> **Divergências Registradas entre `prompt-gerar-pdf-refinamento.md` e o Código Real (Regra: o Código Prevalece):**
> 1. **`.gitignore` e binários locais:** O `.md` (linhas 65, 82 e 172) afirmou que `.gitignore` não existia e que `maven.zip`, `maven_dir/` e `target/` poluíam o versionamento. No código real, `.gitignore` existe (`.gitignore:1-44`) e já ignora `target/` (`.gitignore:16`), `maven_dir/` (`.gitignore:38`) e `maven.zip` (`.gitignore:39`). Além disso, como o host Windows atual não possui `mvn` global no `PATH`, `maven_dir/apache-maven-3.9.9/bin/mvn.cmd` é necessário para compilar localmente.
> 2. **Contagem de endpoints originais:** O `.md` (linha 85) mencionou "17 endpoints", mas a soma real dos 5 controllers originais no código é de **20 endpoints** (`ProdutoController.java`: 5, `PrecoFinalController.java`: 1, `PromocaoController.java`: 5, `EstoqueController.java`: 3, `CatalogoController.java`: 6). Com a expansão desta branch (`feat/expansao-busca`), foram adicionados **9 novos endpoints** em `LojaConectorController` (`/lojas`, 6 rotas) e `BuscaOfertaController` (`/ofertas`, 3 rotas), totalizando **29 endpoints REST** + `/actuator/health`.
> 3. **Contagem de tabelas JPA:** O `.md` (linha 128) citou "8 tabelas", mas o modelo JPA real em `src/main/java/com/ecommerce/catalogo/model/` mapeia **10 tabelas** (`produto`, `produto_fisico`, `produto_digital`, `promocao`, `desconto_percentual`, `desconto_quantidade`, `cupom_promocional`, `estoque`, `catalogo`, `catalogo_produto`).
>
> **Premissa Verificada sobre os Demais Serviços:** Os repositórios da **API de Filtros** e da **API de Compartilhamento** não estão presentes no workspace local (`c:\Users\Eduardo\Desktop\codigo\API de gestao`, e `c:\Users\Eduardo\Desktop\codigo\api` encontra-se vazio). Os requisitos de runtime/portas para os Serviços 2 e 4 abaixo seguem o contrato arquitetural estabelecido em `.env.example`, `docker-compose.proposto.yml` e `PENDENCIAS-OUTROS-SERVICOS.md`.

---

## 1. Runtime e Ferramentas Necessárias

- [x] **Java JDK 17+ (compatível com JDK 17 a 26):**
  - Configurado `<java.version>17</java.version>` em `pom.xml:15`.
  - **Estado atual no host:** Instalado `Java 26.0.2.1` (`C:\Program Files\Java\jdk-26.0.2.1`) e `jdk-25.0.4`. Compilação `--release 17` e testes Surefire (`pom.xml:56-62`) validados com sucesso.
- [x] **Apache Maven 3.9+:**
  - **Estado atual no host:** Não está no `PATH` global, mas está disponível localmente em `.\maven_dir\apache-maven-3.9.9\bin\mvn.cmd` (`v3.9.9`).
- [ ] **PostgreSQL 16+ (Serviço 3 — Produção/Conjunto Completo):**
  - Driver `org.postgresql:postgresql` (`42.7.4`) configurado em `pom.xml:34-38`.
  - **Estado atual no host:** Não instalado localmente (`psql` e serviço Windows `postgresql*` ausentes). Para desenvolvimento/smoke test imediato na máquina sem PostgreSQL, foi habilitado o perfil `local` com H2 em memória (`pom.xml:39-43` e `src/main/resources/application-local.properties:1-6`).
- [ ] **Docker Engine 24+ e Docker Compose v2 (`docker compose`):**
  - Necessários para subir o conjunto orquestrado via `docker-compose.proposto.yml`.
  - **Estado atual no host:** Não instalado (`docker` ausente no `PATH` e em `C:\Program Files\Docker`).
- [x] **Cliente HTTP CLI (`curl` / `Invoke-RestMethod`):**
  - Disponível nativamente no Windows (`curl.exe` / PowerShell) para smoke tests.

---

## 2. Dependências (Status e Comandos de Instalação)

- [x] **Dependências Maven da API de Gestão/Busca (`pom.xml:17-50`):**
  - `spring-boot-starter-web` (`3.3.4`, `pom.xml:18-21`)
  - `spring-boot-starter-data-jpa` (`3.3.4`, `pom.xml:22-25`)
  - `spring-boot-starter-validation` (`3.3.4`, `pom.xml:26-29`)
  - `spring-boot-starter-actuator` (`3.3.4`, adicionado na expansão em `pom.xml:30-33` para `/actuator/health`)
  - `org.postgresql:postgresql` (`42.7.4`, `scope=runtime`, `pom.xml:34-38`)
  - `com.h2database:h2` (`2.2.224`, alterado para `scope=runtime` em `pom.xml:39-43` para viabilizar o perfil `local`)
  - `spring-boot-starter-test` (`3.3.4`, `scope=test`, `pom.xml:44-48`)
  - `maven-surefire-plugin` configurado com `-Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading` (`pom.xml:56-62`) para compatibilidade com JDK 25/26.
- [x] **Comando exato para baixar e compilar todas as dependências da API de Gestão/Busca:**
  ```powershell
  .\maven_dir\apache-maven-3.9.9\bin\mvn.cmd dependency:resolve test-compile
  ```

---

## 3. Variáveis de Ambiente (Lista Completa com Evidência `arquivo:linha`)

> **Nota sobre o código antes da expansão:** No commit original (`main`), `src/main/resources/application.properties:1-6` não lia nenhuma variável de ambiente e continha usuário/senha fixos (`postgres`/`postgres` nas linhas 3-4). Na branch `feat/expansao-busca`, todas as variáveis foram externalizadas conforme a tabela abaixo:

### 3.1 Serviço 1 — API de Gestão/Busca de Promoções (`catalogo-promocoes`)

| Variável | Obrigatória? | Formato | Padrão | Onde é lida (`arquivo:linha`) |
|---|---|---|---|---|
| `SERVER_PORT` | Opcional | Inteiro (porta TCP) | `8080` | `src/main/resources/application.properties:2` |
| `SPRING_PROFILES_ACTIVE` | Opcional | String (`local` ou vazio) | *(vazio)* | Boot nativo Spring (`application-local.properties:1-6`) |
| `DB_URL` | Obrigatória (Prod) | JDBC URL | `jdbc:postgresql://localhost:5432/catalogo` | `src/main/resources/application.properties:5` |
| `DB_USERNAME` | Obrigatória (Prod) | String | `postgres` | `src/main/resources/application.properties:6` |
| `DB_PASSWORD` | **Obrigatória (Prod)** | String (Segredo) | *(vazio)* | `src/main/resources/application.properties:7` |
| `JPA_DDL_AUTO` | Opcional | `validate`\|`update` | `update` | `src/main/resources/application.properties:8` |
| `FILTER_API_ENABLED` | Opcional | Boolean | `false` | `src/main/resources/application.properties:16` $\rightarrow$ `IntegrationProperties.java:40` |
| `FILTER_API_BASE_URL` | Condicional | URL HTTP(S) | `http://localhost:8081` | `src/main/resources/application.properties:17` $\rightarrow$ `IntegrationProperties.java:41` |
| `FILTER_API_KEY` | Condicional | String (Segredo) | *(vazio)* | `src/main/resources/application.properties:18` $\rightarrow$ `IntegrationProperties.java:42` |
| `FILTER_API_CONNECT_TIMEOUT_MS` | Opcional | Inteiro (ms) | `3000` | `src/main/resources/application.properties:19` $\rightarrow$ `IntegrationProperties.java:43` |
| `FILTER_API_READ_TIMEOUT_MS` | Opcional | Inteiro (ms) | `8000` | `src/main/resources/application.properties:20` $\rightarrow$ `IntegrationProperties.java:44` |
| `INTEGRATION_CONNECT_TIMEOUT_MS` | Opcional | Inteiro (ms) | `3000` | `src/main/resources/application.properties:23` $\rightarrow$ `IntegrationProperties.java:58` |
| `INTEGRATION_READ_TIMEOUT_MS` | Opcional | Inteiro (ms) | `8000` | `src/main/resources/application.properties:24` $\rightarrow$ `IntegrationProperties.java:59` |
| `INTEGRATION_RETRY_MAX_ATTEMPTS` | Opcional | Inteiro ($\ge 1$) | `3` | `src/main/resources/application.properties:25` $\rightarrow$ `IntegrationProperties.java:60` |
| `INTEGRATION_RETRY_INITIAL_DELAY_MS` | Opcional | Long (ms) | `300` | `src/main/resources/application.properties:26` $\rightarrow$ `IntegrationProperties.java:61` |
| `INTEGRATION_CIRCUIT_FAILURE_THRESHOLD` | Opcional | Inteiro | `5` | `src/main/resources/application.properties:27` $\rightarrow$ `IntegrationProperties.java:62` |
| `INTEGRATION_CIRCUIT_OPEN_DURATION_SECONDS` | Opcional | Long (s) | `60` | `src/main/resources/application.properties:28` $\rightarrow$ `IntegrationProperties.java:63` |
| `INTEGRATION_DEDUP_TTL_MINUTES` | Opcional | Long (min) | `360` | `src/main/resources/application.properties:29` $\rightarrow$ `IntegrationProperties.java:80` |
| `CURRENCY_USD_BRL_RATE` | Opcional | Decimal (`0.00`) | `5.75` | `src/main/resources/application.properties:30` $\rightarrow$ `IntegrationProperties.java:88` |
| `AFFILIATE_CAMPAIGN_PREFIX` | Opcional | String | `agy` | `src/main/resources/application.properties:31` $\rightarrow$ `IntegrationProperties.java:96` |
| `RETAILER_SHOPEE_ENABLED` | Opcional | Boolean | `true` | `src/main/resources/application.properties:34` $\rightarrow$ `IntegrationProperties.java:155` |
| `RETAILER_SHOPEE_SANDBOX_MODE` | Opcional | Boolean | `false` | `src/main/resources/application.properties:35` $\rightarrow$ `IntegrationProperties.java:156` |
| `RETAILER_SHOPEE_API_URL` | Opcional | URL HTTPS | `https://open-api.affiliate.shopee.com.br/graphql` | `src/main/resources/application.properties:36` $\rightarrow$ `IntegrationProperties.java:157` |
| `RETAILER_SHOPEE_APP_ID` | Condicional (Real) | String numérica | *(vazio)* | `src/main/resources/application.properties:37` $\rightarrow$ `ShopeeConnector.java:58` |
| `RETAILER_SHOPEE_APP_SECRET` | Condicional (Real) | String (Segredo) | *(vazio)* | `src/main/resources/application.properties:38` $\rightarrow$ `ShopeeConnector.java:58` |
| `RETAILER_ALIEXPRESS_ENABLED` | Opcional | Boolean | `true` | `src/main/resources/application.properties:41` $\rightarrow$ `IntegrationProperties.java:173` |
| `RETAILER_ALIEXPRESS_SANDBOX_MODE` | Opcional | Boolean | `false` | `src/main/resources/application.properties:42` $\rightarrow$ `IntegrationProperties.java:174` |
| `RETAILER_ALIEXPRESS_API_URL` | Opcional | URL HTTPS | `https://api-sg.aliexpress.com/sync` | `src/main/resources/application.properties:43` $\rightarrow$ `IntegrationProperties.java:175` |
| `RETAILER_ALIEXPRESS_APP_KEY` | Condicional (Real) | String | *(vazio)* | `src/main/resources/application.properties:44` $\rightarrow$ `AliExpressConnector.java:60` |
| `RETAILER_ALIEXPRESS_APP_SECRET` | Condicional (Real) | String (Segredo) | *(vazio)* | `src/main/resources/application.properties:45` $\rightarrow$ `AliExpressConnector.java:60` |
| `RETAILER_ALIEXPRESS_TRACKING_ID` | Opcional | String | `default` | `src/main/resources/application.properties:46` $\rightarrow$ `AliExpressConnector.java:76` |
| `RETAILER_MERCADOLIVRE_ENABLED` | Opcional | Boolean | `false` | `src/main/resources/application.properties:49` $\rightarrow$ `IntegrationProperties.java:194` |
| `RETAILER_MERCADOLIVRE_SANDBOX_MODE` | Opcional | Boolean | `false` | `src/main/resources/application.properties:50` $\rightarrow$ `IntegrationProperties.java:195` |
| `RETAILER_MERCADOLIVRE_API_URL` | Opcional | URL HTTPS | `https://api.mercadolibre.com` | `src/main/resources/application.properties:51` $\rightarrow$ `IntegrationProperties.java:196` |
| `RETAILER_MERCADOLIVRE_CLIENT_ID` | Condicional (Real) | String | *(vazio)* | `src/main/resources/application.properties:52` $\rightarrow$ `MercadoLivreConnector.java:66` |
| `RETAILER_MERCADOLIVRE_CLIENT_SECRET` | Condicional (Real) | String (Segredo) | *(vazio)* | `src/main/resources/application.properties:53` $\rightarrow$ `MercadoLivreConnector.java:66` |
| `RETAILER_MERCADOLIVRE_ACCESS_TOKEN` | Condicional (Real) | Bearer Token | *(vazio)* | `src/main/resources/application.properties:54` $\rightarrow$ `MercadoLivreConnector.java:65` |
| `RETAILER_MERCADOLIVRE_REFRESH_TOKEN` | Condicional (Real) | Refresh Token | *(vazio)* | `src/main/resources/application.properties:55` $\rightarrow$ `MercadoLivreConnector.java:66` |
| `RETAILER_MERCADOLIVRE_AFFILIATE_TOOL` | Opcional | String (`matt_tool`) | *(vazio)* | `src/main/resources/application.properties:56` $\rightarrow$ `MercadoLivreConnector.java:196` |
| `RETAILER_MERCADOLIVRE_AFFILIATE_WORD` | Opcional | String (`matt_word`) | *(vazio)* | `src/main/resources/application.properties:57` $\rightarrow$ `MercadoLivreConnector.java:197` |
| `RETAILER_AMAZON_ENABLED` | Opcional | Boolean | `false` | `src/main/resources/application.properties:60` $\rightarrow$ `IntegrationProperties.java:223` |
| `RETAILER_AMAZON_SANDBOX_MODE` | Opcional | Boolean | `false` | `src/main/resources/application.properties:61` $\rightarrow$ `IntegrationProperties.java:224` |
| `RETAILER_AMAZON_API_URL` | Opcional | URL HTTPS | `https://affiliate-program.amazon.com/creatorsapi/v1` | `src/main/resources/application.properties:62` $\rightarrow$ `IntegrationProperties.java:225` |
| `RETAILER_AMAZON_TOKEN_URL` | Opcional | URL HTTPS | `https://api.amazon.com/auth/o2/token` | `src/main/resources/application.properties:63` $\rightarrow$ `IntegrationProperties.java:226` |
| `RETAILER_AMAZON_CREDENTIAL_ID` | Condicional (Real) | String | *(vazio)* | `src/main/resources/application.properties:64` $\rightarrow$ `AmazonConnector.java:80` |
| `RETAILER_AMAZON_CREDENTIAL_SECRET` | Condicional (Real) | String (Segredo) | *(vazio)* | `src/main/resources/application.properties:65` $\rightarrow$ `AmazonConnector.java:81` |
| `RETAILER_AMAZON_PARTNER_TAG` | Condicional (Real) | String (`tag-20`) | *(vazio)* | `src/main/resources/application.properties:66` $\rightarrow$ `AmazonConnector.java:82` |
| `RETAILER_AMAZON_MARKETPLACE` | Opcional | Hostname | `www.amazon.com.br` | `src/main/resources/application.properties:67` $\rightarrow$ `IntegrationProperties.java:230` |
| `RETAILER_AMAZON_MAX_TPS` | Opcional | Decimal | `1` | `src/main/resources/application.properties:68` $\rightarrow$ `AmazonConnector.java:74` |
| `LOMADEE_API_URL` | Opcional | URL HTTPS | `https://api.lomadee.com.br` | `src/main/resources/application.properties:71` $\rightarrow$ `IntegrationProperties.java:104` |
| `LOMADEE_API_KEY` | Condicional (Real) | String (`x-api-key`) | *(vazio)* | `src/main/resources/application.properties:72` $\rightarrow$ `LomadeeRetailerConnector.java:60` |
| `LOMADEE_SOURCE_ID` | Condicional (Real) | String (`channelId`) | *(vazio)* | `src/main/resources/application.properties:73` $\rightarrow$ `LomadeeRetailerConnector.java:60` |
| `RETAILER_<LOJA>_ENABLED` (`MAGALU`, `KABUM`, `CASASBAHIA`, `AMERICANAS`, `NETSHOES`) | Opcional | Boolean | `false` | `src/main/resources/application.properties:75-93` |
| `RETAILER_<LOJA>_SANDBOX_MODE` (`MAGALU`, `KABUM`, `CASASBAHIA`, `AMERICANAS`, `NETSHOES`) | Opcional | Boolean | `false` | `src/main/resources/application.properties:76-92` |
| `RETAILER_<LOJA>_BRAND_ID` (`MAGALU`, `KABUM`, `CASASBAHIA`, `AMERICANAS`, `NETSHOES`) | Opcional | String | slug da marca | `src/main/resources/application.properties:77-93` |
| `AWIN_API_URL` / `AWIN_OAUTH_TOKEN` / `AWIN_PUBLISHER_ID` | Opcional | URL / Token / ID | `https://api.awin.com` | `src/main/resources/application.properties:96-98` |

### 3.2 Demais Serviços (Premissa Arquitetural — `.env.example` e `docker-compose.proposto.yml`)
- **Banco de Dados (`postgres`):** `POSTGRES_DB` (`catalogo`), `POSTGRES_USER` (`postgres`), `POSTGRES_PASSWORD` (obrigatória, `docker-compose.proposto.yml:22`).
- **API de Filtros (`api-filtros`):** `FILTER_SERVER_PORT` (`8081`), `FILTER_DB_URL`, `FILTER_INTERNAL_API_KEY`, `FILTER_MIN_REAL_DISCOUNT_PCT` (`15.0`), `FILTER_MIN_RATING` (`4.2`), `FILTER_MIN_STOCK` (`3`), `SHARE_API_BASE_URL` (`http://localhost:8082`).
- **API de Compartilhamento (`api-compartilhamento`):** `SHARE_SERVER_PORT` (`8082`), `SHARE_DB_URL`, `SEARCH_API_BASE_URL` (`http://localhost:8080`), `TELEGRAM_ENABLED`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `WHATSAPP_ENABLED`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`.

---

## 4. Credenciais e Contas Externas (Varejistas e Canais)

| Provedor / Canal | Conta / Chave / Aprovação Necessária | Onde Obter (Documentação Oficial Atual) | Prazo e Custo | Comportamento Sem Credencial (Fallback) |
|---|---|---|---|---|
| **Shopee Brasil** | Conta Afiliado Shopee aprovada + solicitação de `AppId` e `Secret` no painel Open API | `https://affiliate.shopee.com.br/` e `https://open-api.affiliate.shopee.com.br/graphql` | Gratuito; aprovação 1 a 5 dias úteis | Se `sandboxMode=false`: reporta `MISSING_CREDENTIALS` e não retorna dados falsos (`SKIPPED_MISSING_CREDENTIALS`). Se `sandboxMode=true`: retorna ofertas marcadas com `dataSourceMode: "SANDBOX"`. |
| **AliExpress** | Conta no AliExpress Portals + App registrado na Open Platform (`AE-Affiliate`: `App Key`, `App Secret`, `Tracking ID`) | `https://portals.aliexpress.com/` e `https://openservice.aliexpress.com/` | Gratuito; aprovação 1 a 3 dias úteis | Mesmo isolamento seguro (`MISSING_CREDENTIALS` em modo real; sandbox explícito por flag). |
| **Mercado Livre** | Conta ML com identidade validada + App no DevCenter (`Client ID`, `Client Secret`, OAuth2 `Refresh Token`) + Cadastro Afiliados (`matt_tool`) | `https://developers.mercadolivre.com.br/pt_br/autenticacao-e-autorizacao` e `https://www.mercadolivre.com.br/l/afiliados-home` | Gratuito; token imediato no DevCenter, aprovação Afiliados 1 a 5 dias | Mesmo isolamento seguro (`MISSING_CREDENTIALS` sem token; auto-refresh em 401 quando configurado). |
| **Amazon Brasil** | Conta Amazon Associados + `PartnerTag` + Credenciais OAuth 2.0 na nova **Creators API** (`Credential ID` e `Credential Secret`). **Exige 3 vendas qualificadas em 180d e atividade a cada 30d.** | `https://associados.amazon.com.br/` e `https://affiliate-program.amazon.com/creatorsapi/docs/` | Gratuito; liberação da API condicionada às 3 primeiras vendas qualificadas | Desativada por padrão (`enabled=false`); suporta `sandboxMode=true` explícito enquanto a conta atinge as 3 vendas. |
| **Magalu, KaBuM!, Casas Bahia, Americanas, Netshoes** | Conta afiliado aprovada na **Lomadee** (`x-api-key` + `sourceId`/canal) e/ou **Awin** (`OAuth2 Bearer Token` + aprovação nos programas de cada anunciante) | `https://docs.lomadee.com.br/` e `https://wiki.awin.com/index.php/Publisher_API` | Gratuito na Lomadee (Awin cobra depósito simbólico reembolsável de cadastro ~R$ 20); aprovação por marca 1 a 7 dias | Desativadas por padrão (`enabled=false`); cada marca possui flag e `sandboxMode` independentes. |
| **Telegram (Compartilhamento)** | Bot criado via `@BotFather` (`TELEGRAM_BOT_TOKEN`) adicionado como administrador do canal/grupo (`TELEGRAM_CHAT_ID`) | `https://core.telegram.org/bots/api` | Gratuito até ~30 msgs/s globais e 20 msgs/min por grupo; instantâneo | Com `TELEGRAM_ENABLED=false`, a API de Compartilhamento apenas registra/loga sem publicar. |
| **WhatsApp Cloud API (Compartilhamento)** | Conta Meta for Developers + Portfólio Empresarial (WABA) + System User Token permanente (`WHATSAPP_ACCESS_TOKEN`) + `PHONE_NUMBER_ID` | `https://developers.facebook.com/docs/whatsapp/cloud-api` | Acesso gratuito à API; cobrança Meta por mensagem/template entregue; 1 a 5 dias para verificação comercial | Sandbox gratuito da Meta disponível para números de teste; `WHATSAPP_ENABLED=false` desativa envios. |

---

## 5. Banco de Dados (Configuração, Migrations e Permissões)

- [ ] **Tipo e Versão:** PostgreSQL `16` (database `catalogo`, encoding `UTF-8`, timezone `America/Sao_Paulo`).
- [ ] **String de Conexão Local:** `jdbc:postgresql://localhost:5432/catalogo` (`src/main/resources/application.properties:5`).
- [ ] **String de Conexão na Rede Docker:** `jdbc:postgresql://postgres:5432/catalogo` (`docker-compose.proposto.yml:62`).
- [ ] **Criação Inicial do Database:**
  ```sql
  CREATE DATABASE catalogo WITH ENCODING 'UTF8';
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
  ```
- [ ] **Migrations / DDL:**
  - As 10 tabelas locais da API de Gestão são criadas automaticamente pelo Hibernate (`JPA_DDL_AUTO=update`, `application.properties:8`).
  - As tabelas compartilhadas de ofertas filtradas e histórico de preços (`loja_parceira`, `oferta_externa`, `historico_preco_oferta`) estão documentadas prontas para execução em `PENDENCIAS-OUTROS-SERVICOS.md` (item `P-DB-01`).
- [x] **Alternativa Imediata Sem PostgreSQL (Perfil `local`):** H2 em memória em modo de compatibilidade PostgreSQL (`jdbc:h2:mem:catalogo_local;MODE=PostgreSQL` em `src/main/resources/application-local.properties:2`).

---

## 6. Rede, Portas e Autenticação entre Serviços

| Ordem | Serviço | Porta Host | URL Local | URL Interna Docker | Autenticação Recebida |
|---|---|---|---|---|---|
| 1º | **PostgreSQL (`postgres`)** | `5432` | `localhost:5432` | `postgres:5432` | Usuário/Senha (`DB_USERNAME` / `DB_PASSWORD`) |
| 2º | **Redis (`redis` — opcional)** | `6379` | `localhost:6379` | `redis:6379` | Senha opcional (`REDIS_PASSWORD`) |
| 3º | **API de Gestão/Busca (`api-busca`)** | `8080` | `http://localhost:8080` | `http://api-busca:8080` | Rede interna (recomenda-se API Key futura) |
| 4º | **API de Filtros (`api-filtros`)** | `8081` | `http://localhost:8081` | `http://api-filtros:8081` | Header `X-Internal-Api-Key` (`FilterServiceClient.java:68`) |
| 5º | **API de Compartilhamento (`api-compartilhamento`)** | `8082` | `http://localhost:8082` | `http://api-compartilhamento:8082` | Header `X-Internal-Api-Key` (`SHARE_INTERNAL_API_KEY`) |

- **CORS:** Como a comunicação `api-busca` $\rightarrow$ `api-filtros` $\rightarrow$ `api-compartilhamento` é **server-to-server (backend)**, CORS não bloqueia chamadas entre as APIs. Só será necessário configurar `@CrossOrigin` / `WebMvcConfigurer` caso exista um painel administrativo front-end em domínio distinto.

---

## 7. Ordem de Inicialização e Comandos Exatos para Subir

### Opção A — Rodar Localmente AGORA (Sem Docker e Sem PostgreSQL, usando Perfil `local` H2)
```powershell
# 1. Na raiz de "API de gestao", rode os testes automatizados:
.\maven_dir\apache-maven-3.9.9\bin\mvn.cmd test

# 2. Suba a API de Gestão/Busca na porta 8080 com perfil local (H2 em memória):
.\maven_dir\apache-maven-3.9.9\bin\mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### Opção B — Subir via Docker Compose (PostgreSQL + Redis + API de Busca)
```powershell
# 1. Copie .env.example para .env e defina POSTGRES_PASSWORD
Copy-Item .env.example .env

# 2. Suba infraestrutura + API de Busca:
docker compose -f docker-compose.proposto.yml up -d postgres redis api-busca

# 3. (Quando ../api-filtros e ../api-compartilhamento estiverem disponíveis) Suba o pipeline completo:
docker compose -f docker-compose.proposto.yml --profile full-pipeline up -d
```

---

## 8. Smoke Test de Ponta a Ponta (Comandos `curl`)

Execute na sequência com a API rodando em `http://localhost:8080`:

```bash
# Elo 1: Health Check geral e indicador dos 9 conectores registrados
curl -s http://localhost:8080/actuator/health

# Elo 2: Listar status operacional de todas as 9 lojas (verifica transparência MISSING_CREDENTIALS vs UP_REAL)
curl -s http://localhost:8080/lojas

# Elo 3: Ativar modo SANDBOX explícito em Shopee, AliExpress e Amazon para validar pipeline sem chaves reais
curl -s -X PATCH "http://localhost:8080/lojas/SHOPEE/ativar?sandbox=true"
curl -s -X PATCH "http://localhost:8080/lojas/ALIEXPRESS/ativar?sandbox=true"
curl -s -X PATCH "http://localhost:8080/lojas/AMAZON/ativar?sandbox=true"

# Elo 4: Busca paralela multi-varejista com deduplicação entre lojas (mesmo EAN colapsa para o menor custo efetivo BRL)
curl -s "http://localhost:8080/ofertas/busca?termo=Galaxy&deduplicarEntreLojas=true"

# Elo 5: Revalidação individual de oferta (usada pela API de Compartilhamento antes de publicar)
curl -s "http://localhost:8080/ofertas/ALIEXPRESS/1005006218991"

# Elo 6: Coleta + Despacho de lote para a API de Filtros (valida degradação graciosa se Serviço 2 estiver offline)
curl -s -X POST "http://localhost:8080/ofertas/coletar-e-filtrar?termo=JBL"
```

---

## 9. Matriz de Bloqueios Atuais (Dentro vs. Fora do Escopo)

| ID | Bloqueio / Lacuna | Severidade | Escopo | Status / Correção Mínima |
|---|---|---|---|---|
| **B-01** | Ausência de conectores de varejistas, normalizador, deduplicador e endpoints `/lojas` e `/ofertas` na API de Busca | **BLOQUEANTE** | **DENTRO** (API Busca) | **RESOLVIDO nesta branch (`feat/expansao-busca`).** |
| **B-02** | Senha hardcoded em `application.properties` e impossibilidade de subir localmente sem PostgreSQL instalado no Windows | **BLOQUEANTE** | **DENTRO** (API Busca) | **RESOLVIDO nesta branch** via variáveis `${DB_*}` + perfil `local` (H2 em memória). |
| **B-03** | Ausência dos repositórios/processos da **API de Filtros** (`:8081`) e **API de Compartilhamento** (`:8082`) no ambiente local | **BLOQUEANTE** (para o pipeline completo; **PARCIAL** para a API de Busca, que roda isolada) | **FORA** | Registrado em `PENDENCIAS-OUTROS-SERVICOS.md` (`P-FIL-01` a `P-SH-03`) e pré-mapeado em `docker-compose.proposto.yml`. |
| **B-04** | PostgreSQL e Docker Desktop não instalados na máquina Windows atual | **PARCIAL** (impede persistência em Postgres/Compose local, mas API roda via perfil `local`) | **FORA** (Infra Host) | Instalar Docker Desktop para Windows ou PostgreSQL 16 e preencher `.env`. |
| **B-05** | Ausência de credenciais reais de afiliados configuradas no ambiente | **PARCIAL** (impede coleta `REAL`, mas modo `SANDBOX` explícito opera para testes) | **HUMANO** | Seguir a Seção 10 abaixo para emitir chaves oficiais e preencher `.env`. |
| **B-06** | Redis para cache distribuído entre múltiplas réplicas da API de Busca | **OPCIONAL** | **DENTRO/INFRA** | Cache TTL em memória (`ConcurrentHashMap` em `OfferDeduplicator.java`) já atende instância única; Redis incluído no Compose para escala horizontal futura. |

---

## 10. Passos Manuais Humanos (Ordem de Prioridade Recomendada)

- [ ] **Passo 1 (Infra Local — 10 min):** Instalar o **Docker Desktop para Windows** (ou PostgreSQL 16 local), copiar `.env.example` para `.env` e definir `POSTGRES_PASSWORD` e `FILTER_API_KEY`.
- [ ] **Passo 2 (Prioridade 1 de Receita — Shopee Brasil):** Cadastrar/acessar conta em `https://affiliate.shopee.com.br/`, solicitar acesso à **Open API** no painel, copiar `AppId` e `Secret` para `RETAILER_SHOPEE_APP_ID` e `RETAILER_SHOPEE_APP_SECRET` no `.env` e manter `RETAILER_SHOPEE_SANDBOX_MODE=false`.
- [ ] **Passo 3 (Prioridade 2 de Receita — AliExpress):** Cadastrar conta afiliado em `https://portals.aliexpress.com/`, criar aplicação `AE-Affiliate` em `https://openservice.aliexpress.com/` e preencher `RETAILER_ALIEXPRESS_APP_KEY`, `RETAILER_ALIEXPRESS_APP_SECRET` e `RETAILER_ALIEXPRESS_TRACKING_ID`.
- [ ] **Passo 4 (Prioridade 3 — Rede Lomadee para 5 Varejistas Nacionais de uma vez):** Criar conta afiliado em `https://www.lomadee.com.br/`, gerar a chave `x-api-key` (`https://docs.lomadee.com.br/`), anotar o `sourceId`/canal e habilitar `RETAILER_MAGALU_ENABLED=true`, `RETAILER_KABUM_ENABLED=true`, `RETAILER_CASASBAHIA_ENABLED=true`, `RETAILER_AMERICANAS_ENABLED=true` e `RETAILER_NETSHOES_ENABLED=true`.
- [ ] **Passo 5 (Prioridade 4 — Mercado Livre):** Criar aplicação em `https://developers.mercadolivre.com.br/`, executar o fluxo OAuth 2.0 inicial para obter o primeiro `refresh_token`, aderir ao Programa de Afiliados ML (`https://www.mercadolivre.com.br/l/afiliados-home`) e preencher `RETAILER_MERCADOLIVRE_*`.
- [ ] **Passo 6 (Prioridade 5 — Amazon Brasil):** Cadastrar conta em `https://associados.amazon.com.br/`, divulgar links manuais iniciais até completar **3 vendas qualificadas**, gerar `Credential ID` e `Credential Secret` na **Creators API** (`https://affiliate-program.amazon.com/creatorsapi/docs/`) e ativar `RETAILER_AMAZON_ENABLED=true`.
- [ ] **Passo 7 (Canais de Compartilhamento):** Criar bot no Telegram via `@BotFather` (`TELEGRAM_BOT_TOKEN`) e/ou configurar número oficial no Meta for Developers (`WHATSAPP_ACCESS_TOKEN`) para uso pela API de Compartilhamento.

