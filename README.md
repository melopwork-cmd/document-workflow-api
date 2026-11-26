# Document Workflow API

API REST em Java para receber metadados de documentos e acompanhar a revisão. O projeto usa Spring Boot, Spring JDBC e H2. Uma coluna de versão protege atualizações concorrentes, e a mudança de estado e o histórico são gravados na mesma transação.

## Executar localmente

JDK 17 ou posterior. O Maven Wrapper baixa a versão de Maven usada pelo projeto.

~~~powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
~~~

No Linux ou macOS, use ./mvnw nos mesmos comandos. A API escuta em 127.0.0.1:8080.

~~~powershell
$base = 'http://127.0.0.1:8080'
$document = Invoke-RestMethod "$base/documents" -Method Post -ContentType 'application/json; charset=utf-8' -Body '{"title":"Contrato de demonstração"}'
$id = $document.id
Invoke-RestMethod "$base/documents/$id/transitions" -Method Post -ContentType 'application/json; charset=utf-8' -Body '{"state":"IN_REVIEW","expectedVersion":0}'
Invoke-RestMethod "$base/documents/$id/history"
~~~

## Contrato e decisões

| Requisição | Resultado |
| --- | --- |
| POST /documents | Cria o documento e retorna HTTP 201 com o cabeçalho Location. |
| GET /documents/{id} | Retorna o estado e a versão atuais. |
| POST /documents/{id}/transitions | Aplica uma transição com a versão lida pelo cliente. |
| GET /documents/{id}/history | Lista os eventos em ordem de versão. |
| GET /actuator/health | Informa a saúde da aplicação. |

O fluxo passa por RECEIVED, IN_REVIEW, APPROVED ou REJECTED, e por fim ARCHIVED. Um erro de versão retorna 409. Uma transição inválida retorna 422. IDs desconhecidos retornam 404.

A atualização usa uma condição de versão no SQL. Quando duas requisições partem da mesma versão, apenas uma grava uma mudança. A chave do histórico combina o ID do documento e a versão, o que também impede eventos duplicados. Os testes cobrem a API HTTP, a validação dos dados, o histórico e atualizações concorrentes.

O H2 fica em memória por padrão e é reiniciado junto com a aplicação. O exemplo usa apenas metadados sintéticos de documentos.

## English summary

This Java 17 and Spring Boot sample models document intake and review. It illustrates validated HTTP requests, transactionally consistent event history, optimistic concurrency control, and integration tests. It is an independent portfolio project built with AI assistance.

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Apache Maven Wrapper](https://maven.apache.org/wrapper/)
