# API de Gestão de Catálogo e Promoções

Este é o backend REST para o motor de um e-commerce, projetado para lidar com **Produtos (Físicos e Digitais)**, **Estoque**, **Promoções/Cupons (Padrão Strategy)** e organização em **Catálogos**.

## 🛠 Tecnologias e Arquitetura
- **Java 17**
- **Spring Boot 3.3** (Web, Data JPA, Validation)
- **PostgreSQL** (Persistência)
- **H2 Database** (Configurado para ambiente de testes)
- **Maven**
- **Arquitetura em Camadas:** `Controller`, `Service`, `Repository`, `Model`, `Exception` e `DTO`.

## ⚙️ Funcionalidades e Regras de Negócio
- **Produtos:** Suporte nativo a herança (`JOINED`). Produtos Físicos possuem controle de dimensões, enquanto Produtos Digitais gerenciam licenças e links de download, garantindo isolamento total.
- **Promoções (Strategy):** O sistema não altera o preço de base do produto. Os descontos (Percentual, Quantidade, Cupons) são processados no momento da consulta através de regras dinâmicas em tempo real.
- **Estoque Sensível:** Tratamento de Lock Pessimista (`PESSIMISTIC_WRITE`) garantindo transações de reservas e repor de estoque seguras, barrando conflitos e estoque negativo.
- **Catálogos:** Organização flexível com prioridade visual (campo ordem) e travamentos lógicos de exclusão segura (impedindo deleção de produtos vinculados ativamente a algum catálogo).
- **Tratamento de Exceções Global:** Retorno padronizado em JSON e com os devidos status HTTP semânticos (ex: 409 Conflict, 422 Unprocessable Entity, 404 Not Found) usando `@ControllerAdvice`.

## 🚀 Como Executar Localmente

### 1. Pré-requisitos
- Ter o **Java 17+** instalado.
- Ter o **PostgreSQL** rodando localmente.
- O banco de dados definido na aplicação se chama `catalogo`, mas pode ser ajustado no `application.properties`.

**Configuração padrão do banco (`application.properties`):**
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/catalogo
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=update
```

### 2. Iniciando a aplicação
Abra o terminal na raiz do projeto e execute:

```bash
mvn clean install
mvn spring-boot:run
```

A API estará rodando por padrão na porta **8080** (http://localhost:8080).

## 📡 Principais Endpoints

### 🛒 Produtos
- `POST /produtos` - Cadastro de produto.
- `GET /produtos?busca={nome_ou_sku}` - Listagem.
- `GET /produtos/{sku}` - Detalhes do produto.
- `PUT /produtos/{sku}` - Atualização.
- `DELETE /produtos/{sku}` - Remoção.
- `GET /produtos/{sku}/preco-final` - Retorna o preço de venda calculando todas as promoções aplicáveis (e cupom) sob demanda.

### 📦 Estoque
- `GET /estoque/{sku}` - Consulta disponibilidade.
- `POST /estoque/{sku}/reservas` - Reserva estoque.
- `POST /estoque/{sku}/reposicoes` - Adiciona estoque.

### 🏷️ Promoções e Cupons
- `POST /promocoes/percentual` - Desconto Percentual.
- `POST /promocoes/quantidade` - Regra de quantidade (Leve N pague M).
- `POST /promocoes/cupons` - Criação de Cupom.
- `GET /promocoes` - Consulta de promoções ativas.
- `PATCH /promocoes/{id}/desativar` - Desativa uma promoção.

### 📚 Catálogos
- `POST /catalogos` - Cria catálogo.
- `GET /catalogos` - Lista os catálogos disponíveis.
- `POST /catalogos/{id}/produtos` - Insere um produto em um catálogo.
- `DELETE /catalogos/{id}/produtos?sku=ABC` - Remove um produto.
- `PATCH /catalogos/{id}/produtos` - Reordena o produto no catálogo.
