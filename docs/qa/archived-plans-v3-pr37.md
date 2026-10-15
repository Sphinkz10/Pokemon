# V3 PR #37 — Plano arquivado não bloqueia recomeçar

O guia **Preparar plano** apresenta o plano arquivado para consulta histórica, mas, quando não existe nenhum plano não arquivado para o mesmo exemplar e liga, apresenta também **Criar plano**.

- Um plano Great arquivado não ocupa Ultra e vice-versa.
- Um plano não arquivado impede duplicação acidental do atalho, mesmo com histórico arquivado.
- A criação abre o editor; só a ação explícita de guardar altera Room. Nunca altera Pokémon GO.
- Não elimina nem modifica o plano arquivado.
- 4 testes JVM de política + 4 testes Compose instrumentados Android, incluindo criação/guardar numa base Room real que confirma que o registo arquivado mantém o seu ID e conteúdo.
- No editor 360, um plano arquivado não é selecionado como plano editável. Quando o ID normal já pertence ao histórico, o novo plano recebe ID próprio para impedir um upsert destrutivo.
- Gate mínimo Android sobe de 84 para **88**; resultados só são válidos quando o JUnit do emulador passar.
- O progresso mestre de 177 tarefas não aumenta automaticamente sem o DoD correspondente.

Próximos passos: UX na alteração de planos, distinção visual entre ativo/arquivado, histórico auditável de versões e acessibilidade física.
