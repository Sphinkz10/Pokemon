# PR #24 — Persistência em disco e dimensões de interface no Android

## Estado anterior
A app v3 estava na main com **40/40 casos instrumentados Android** aprovados (PR #23), mas a maioria dos testes de coleção/planos utilizava **Room em memória**. Esses testes não demonstram a sobrevivência de dados depois de fechar um ficheiro SQLite e voltar a abrir uma instância. A interface era testada a 200% de tamanho de letra, mas sem uma matriz de larguras.

## Avanço técnico real
- **8 testes novos** de persistência num ficheiro de base Room **nomeado, real, isolado e eliminado após cada teste** (`pr24-persistence-isolated.db`): exemplar integral, atualização de CP e nível para `UNKNOWN`, equipa e plano PvP, inbox de candidatos ainda não confirmados, batalhas, eliminação explícita sem efeitos colaterais, 240 exemplares com integridade dos IDs, ausência de dados inventados nos campos por confirmar.
- Cada caso abre uma instância `Room.databaseBuilder`, escreve e **fecha** a base. Abre posteriormente **uma nova instância** apontada ao mesmo ficheiro e compara os dados originais.
- A base criada declara explicitamente a migração histórica `PvpUserDatabase.MIGRATION_1_2`, sem fallback destrutivo.
- **3 testes de Compose** exercitam o mesmo `UserResourcePanel` sob constraints reais de **320dp a 200%**, **390dp a 150%** e **600dp a 100%**. Cada teste percorre os campos, insere Stardust, grava Candy=0 e verifica que XL continua `UNKNOWN`.
- Todos os testes são executados automaticamente no **emulador Android API 35** por `:app:connectedDebugAndroidTest`, com relatórios JUnit XML e gate obrigatório.
- **Novo mínimo:** 40 testes anteriores + 11 novos = **51/51**, sem falhas ou testes ignorados.

## Conclusões permitidas
Permite concluir que as entidades principais persistem no **SQLite Android**, mesmo após fechar/reabrir Room, e que o formulário de recursos pode ser utilizado nas três larguras e escalas tipográficas selecionadas no emulador.

## O que NÃO foi demonstrado
- **Morte real do processo** (`am kill`/`force-stop` com relançamento): a recriação da Activity no PR #23 não equivale a isto.
- **Upgrade entre APKs diferentes:** exige `adb install -r` com versões distintas do mesmo package ID e a **mesma assinatura**, incluindo comparação de dados antes/depois.
- Rotação em todos os tamanhos, ausência de recortes visual verificada por screenshot, TalkBack ativado num dispositivo físico e matriz Android 13–16.
- A bateria histórica global de **110 E2E UX** ainda não foi executada/completada formalmente. Estes casos não são contabilizados automaticamente como 11/110 sem um mapeamento explícito e critérios idênticos.
- O pacote atual da APK é **debug com sufixo .installtestv190**. Instalar outro APK com package ID diferente não constitui um teste de atualização.

## Próximos gates de segurança
1. CI emulador executa 51/51 + CI JVM/APK completo em PR.
2. Preparar teste `adb install -r` com **mesmo keystore**, snapshot dos dados reais e comparação sem reinstalação destrutiva.
3. Acrescentar relançamento após processo efetivamente eliminado (não confundir com `ActivityScenario.recreate`).
4. Construir matriz verificável dos 110 fluxos do roadmap, distinguindo evidência de emulador de validação manual física.
