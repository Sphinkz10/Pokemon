# Pokémon PvP — Roadmap mestre de aceitação · atualizado até V1.58

**Data:** 2026-10-01 · **GitHub:** [Sphinkz10/Pokemon](https://github.com/Sphinkz10/Pokemon) · **Figma:** [Pokémon Companion](https://www.figma.com/design/Y85bgqW7K2jEnrXdqDnwBE)

**Objetivo:** chegar a uma aplicação real, modular, com qualidade visual e funções completas. O roadmap mede apenas **gates de implementação/verificação explicitamente definidos**. Não mede linhas de código nem a percentagem global real do produto; ecrãs existentes no Figma não contam como ecrãs Android terminados.

**Linha de base documentada:** 7/50 gates com prova de fonte/código, CI ou relato de instalação (**14% de gates de aceitação**). É deliberadamente conservador: outras funcionalidades estão no código mas continuam sem prova em telemóvel. Os gates só passam com evidência técnica e ficam sujeitos a regressões.


### V1.82 — Teste instrumentado da migração Room v1→v2
- [x] V1.81: teste de reabertura de SQLite em disco aprovado ([run #37039051714](https://github.com/Sphinkz10/Pokemon/actions/runs/37039051714)).
- [x] APK V1.81 aprovado ([run #37039051468](https://github.com/Sphinkz10/Pokemon/actions/runs/37039051468)).
- [x] Teste instrumentado cria esquema v1 a partir das definições exportadas, insere equipa, abre com migração v1→v2, valida equipa e tabela battle_record (`6b7aac1`).
- [ ] Confirmar CI de emulador [#37039798629](https://github.com/Sphinkz10/Pokemon/actions/runs/37039798629) e compilação [#37039798479](https://github.com/Sphinkz10/Pokemon/actions/runs/37039798479).
- [ ] Testar migração em dispositivo físico com dados existentes.

**Implementação V1.82:** 3/5 (60%, execução pendente). **Aceitação global:** 7/50 (14%).

### V1.81 — Persistência após reabertura da base de dados
- [x] V1.80: quatro testes Room em emulador aprovados ([run #37038237116](https://github.com/Sphinkz10/Pokemon/actions/runs/37038237116)).
- [x] Compilação APK aprovada ([run #37038236950](https://github.com/Sphinkz10/Pokemon/actions/runs/37038236950)).
- [x] Teste instrumentado novo cria ficheiro SQLite real, guarda equipa, fecha a base, reabre e verifica ID, nome e notas (`24811ca`).
- [ ] Confirmar execução do teste de reabertura ([run #37039051714](https://github.com/Sphinkz10/Pokemon/actions/runs/37039051714)).
- [ ] Validar persistência após reinício de Activity/processo em dispositivo físico.

**Implementação V1.81:** 3/5 (60%, execução pendente). **Aceitação global:** 7/50 (14%).

### V1.80 — Integridade da Coleção ao eliminar equipas
- [x] Primeiro workflow instrumentado Android Room aprovado: [run #37037728284](https://github.com/Sphinkz10/Pokemon/actions/runs/37037728284).
- [x] CI APK com compilação de testes instrumentados aprovado: [run #37037711044](https://github.com/Sphinkz10/Pokemon/actions/runs/37037711044).
- [x] Quarto teste Room verifica que eliminar equipa preserva Pokémon e favorito na coleção (`8e6551d`).
- [ ] Confirmar execução do quarto teste no emulador (run #37038237116).
- [ ] Validar os fluxos visuais no Android físico.

**Implementação V1.80:** 3/5 (60%, novo teste ainda em execução). **Aceitação global:** 7/50 (14%).

### V1.79 — Integração Room no emulador Android
- [x] Dependências e runner AndroidX instrumentado (`c054651`).
- [x] Três testes SQLite/Room reais: duplicação+eliminação, renomeação, eliminação desconhecida (`512b0d6`).
- [x] CI APK compila fontes instrumentadas (`ded8740`).
- [x] Workflow de emulador Android 35 criado e disparado (`f03a92b`, run #37037728284).
- [ ] Confirmar resultados dos workflows e corrigir eventuais falhas.
- [ ] Validar UX num Android físico.

**Implementação V1.79:** 4/6 (67%, execução/aceitação pendentes). **Aceitação global:** 7/50 (14%).

### V1.78 — Duplicação com resultado visível
- [x] Após sucesso do Room, regressar à lista de equipas para visualizar a cópia (`dfe6018`).
- [x] Repor filtro para a liga da equipa duplicada (`bc40d90`).
- [x] Guard de integração CI (`964984a`).
- [x] V1.77 CI aprovado: run #37033525311.
- [ ] Validar CI desta versão e confirmar lista/cópia no dispositivo Android.

**Implementação V1.78:** 4/5 (80%, validação pendente). **Aceitação global:** 7/50 (14%).

### V1.77 — Bloqueio completo de edição durante persistência
- [x] Impedir abrir/fechar edição e navegar para Team Lab enquanto uma mutação Room decorre (`978fe03`).
- [x] Diálogo de eliminação não muda de estado durante gravação (`bd4a60c`).
- [x] V1.75 e V1.76 com CI aprovado: runs #37031840930 e #37031991999.
- [ ] Validar nova compilação e testes físicos T01–T15.

**Implementação V1.77:** 3/4 (75%, validação pendente). **Aceitação global:** 7/50 (14%).

### V1.76 — Limite de nomes nas cópias
- [x] Cópia limita nome original a 52 caracteres antes de acrescentar ` (cópia)` (`3fb9505`).
- [x] Teste unitário com nome original de 60 caracteres (`778ff5f`).
- [x] Workflow passa a exigir 5 testes da política (`1524114`).
- [ ] Confirmar CI e validação física.

**Implementação V1.76:** 3/4 (75%, validação pendente). **Aceitação global:** 7/50 (14%).

### V1.75 — Recuperação de estado após recriação Android
- [x] Navegação, equipa selecionada, liga, estilo e âncora em `rememberSaveable` (`e7c03e5`, `64f6c75`).
- [x] Aguardar primeiro carregamento Room antes de redirecionar detalhe restaurado (`6006cc4`).
- [x] Guard CI de estado restaurado (`a818b91`).
- [ ] Compilação aprovada e rotação testada em dispositivo Android.

**Implementação V1.75:** 3/4 (75%, validação pendente). **Aceitação global:** 7/50 (14%).

### V1.74 — Escritas serializadas e instalação segura
- [x] Estado de operação bloqueia múltiplos toques durante renomeação, duplicação e eliminação (`9c8333c`, `7d0a2f5`).
- [x] Guard CI de bloqueio (`039541f`).
- [x] Documentação corrige identificador separado da APK de diagnóstico e isolamento da base de dados (`45f2fdb`).
- [x] CI anterior aprovado: run #37030787879.
- [ ] CI do novo código e ensaios Android físicos.

**Implementação V1.74:** 4/5 (80%, nova compilação e dispositivo pendentes). **Aceitação global:** 7/50 (14%).

### V1.73 — Fecho verificável de Equipas
- [x] CI V1.71 e V1.72 aprovados: runs #37030210991, #37030389759 e #37030398353.
- [x] Artefacto APK renomeado para identificar corretamente main atual (`337e974`).
- [x] Matriz de 15 casos para testes físicos com evidência e critérios de fecho (`docs/TEAMS_ANDROID_ACCEPTANCE_V1_73.md`).
- [ ] Novo CI verde e 15 casos executados em dispositivo real.

**Preparação V1.73:** 3/4 (75%, dispositivo e CI novo pendentes). **Aceitação global:** 7/50 (14%).

### V1.72 — Pesquisa com estado vazio correto
- [x] Distinguir coleção sem candidatos de pesquisa sem correspondências (`09b00b3`).
- [x] Ação limpar pesquisa e repor paginação (`09b00b3`).
- [x] Guard CI (`d1033b1`).
- [ ] CI aprovado e teste físico de pesquisa sem resultados.

**Implementação V1.72:** 3/4 (75%, validação pendente). **Aceitação global:** 7/50 (14%).

### V1.71 — Política de duplicação testável
- [x] Transformação pura separada da UI, sem mutar a equipa original (`a5862ac`).
- [x] Integração com persistência Room (`1a0476c`).
- [x] Quatro testes Android JUnit de cópia, papéis, identidade e data (`a1196e6`).
- [x] CI atualizado para executar testes e validar integração (`185e32e`).
- [ ] Confirmar execução verde e teste físico.

**Implementação V1.71:** 4/5 (80%, validação pendente). **Aceitação global:** 7/50 (14%).

### V1.70 — Eliminação confirmada de equipas
- [x] DAO Room com `DELETE FROM saved_team WHERE id = :id` sem tocar em Pokémon (`42a6649`).
- [x] Diálogo de confirmação e cancelamento (`430cbb0`).
- [x] Tratamento de erro e regresso à lista só após sucesso (`c49db90`).
- [x] Guard CI estrutural (`594381b`).
- [x] CI Android aprovado: [run #37029998762](https://github.com/Sphinkz10/Pokemon/actions/runs/37029998762).
- [ ] Validação física de eliminar/cancelar.

**Implementação V1.70:** 4/5 (80% código, validação pendente). **Aceitação global:** 7/50 (14%).

### V1.69 — Duplicação de equipas
- [x] Ação visível no detalhe (`c7270de`).
- [x] Cópia persistente com novo UUID, sem alterar original nem tornar a cópia principal (`f79b681`).
- [x] Guard CI (`02adc82`).
- [ ] Compilação e testes em Android; validar duplicação na lista e reabertura.

**Implementação V1.69:** 3/4 (75%, CI e dispositivo pendentes). **Aceitação global:** 7/50 (14%).

### V1.68 — Feedback de gravação Room
- [x] Detalhe recebe mensagem de resultado (`6edfd93`).
- [x] `runCatching` em upsert, estados de progresso/sucesso/falha (`8f7a339`).
- [x] Guard estrutural CI (`3c28cfb`).
- [ ] CI verde e testes em dispositivo, incluindo falha induzida.

**Implementação V1.68:** 3/4 (75%, validação pendente); **aceitação global:** 7/50 (14%).

### V1.67 — Renomear equipa guardada
- [x] Edição do nome no detalhe com validação, cancelamento e máximo de 60 caracteres (`9c9cfbe`).
- [x] Atualização persistente do mesmo ID e membros via `upsertSavedTeam` (`2c1ed7e`).
- [x] Guard estrutural no CI (`9c039e0`).
- [ ] CI aprovado, edição testada em Android e erros de gravação tratados na interface.

**Implementação V1.67:** 3/4 (75% de código, validação pendente); **aceitação global:** 7/50 (14%).

### V1.66 — Testes executados de resultados de equipas
- [x] Quatro testes Android JUnit: 3 IDs distintos, seleção verificada, herança de papéis, cobertura inválida (`11177ab`).
- [x] CI exige relatório `TeamsGoldenResultPolicyTest` com quatro testes (`6a01ec6`).
- [x] CI aprovado: [run #37026390151](https://github.com/Sphinkz10/Pokemon/actions/runs/37026390151).
- [ ] Testar guardar/reabrir no dispositivo.

**Implementação V1.66:** 2/3 (67%, execução pendente). **Aceitação global:** 7/50 (14%).

### V1.65 — Estado persistente do seletor
- [x] `rememberSaveable` para pesquisa, abertura e limite progressivo (`eecc2e6`).
- [x] Reiniciar limite a 75 quando a pesquisa muda e guard CI (`56081c1`).
- [x] CI Android aprovado: [run #37025050710](https://github.com/Sphinkz10/Pokemon/actions/runs/37025050710).
- [ ] Scroll exato e rotação testados em dispositivo.

**Implementação/CI V1.65:** 3/4 (75%, dispositivo pendente). **Aceitação global:** 7/50 (14%).

### V1.64 — Seletor sem limite definitivo de 75
- [x] Paginação progressiva de resultados locais (75 de cada vez) e contador completo (`d7e1a81`).
- [x] Guard de wiring no CI (`ca57370`).
- [x] CI Android aprovado: [run #37024700137](https://github.com/Sphinkz10/Pokemon/actions/runs/37024700137).
- [ ] Testes de navegação com coleção grande.
- [ ] Verificação física Android e scroll/estado.

**Implementação V1.64:** 2/4 (50%); **aceitação global:** 7/50 (14%).

### V1.63 — Pesquisa testável e feedback no seletor
- [x] `TeamPickerSearchPolicy` independente (`b237de8`) e integração no Builder com contador/placeholder (`d5426b0`).
- [x] Três testes JUnit de pesquisa (`8fbe061`) e exigência no workflow (`ee9ac8c`).
- [ ] CI aprovado após esta alteração; primeira execução V1.63 falhou por assert estrutural desatualizado, corrigido em `cbb633e`.
- [ ] Verificação visual Android, incluindo coleção superior a 75 exemplares.

**Implementação V1.63:** 2/4 (50%, testes escritos, CI pendente). **Aceitação global:** 7/50 (14%).

### V1.62 — Pesquisa no Builder e cobertura defensiva
- [x] Pesquisa de exemplar por nome, alcunha, speciesId e número Dex (com ou sem `#`) (`9769b1b`).
- [x] Ignorar cobertura primária não finita/fora de 0–100 no ranking de variantes (`694380d`).
- [x] Guard estrutural acrescentado ao CI (`de388aa`).
- [x] CI aprovado: [run #37019771241](https://github.com/Sphinkz10/Pokemon/actions/runs/37019771241), commit `de388aa`.
- [ ] Teste funcional/visual Android.

**Implementação/CI V1.62:** 4/5 (80%, Android pendente). **Aceitação global:** 7/50 (14%).

### V1.61 — Equipas: resultados com três posições seguras
- [x] UI: `GoldenTeamLineup` apresenta sempre três posições, mesmo perante listas incompletas ou excessivas (`182bb90`).
- [x] Guard estrutural no workflow Android (`e34f018`).
- [x] CI aprovado para V1.61: [run #37012727991](https://github.com/Sphinkz10/Pokemon/actions/runs/37012727991), commit `e34f018`.
- [ ] Teste visual e funcional no Android.

**Implementação/CI V1.61:** 3/4 (75%); **aceitação V1.61:** 0/4. **Aceitação global:** 7/50 (14%).

### V1.60 — Equipas: IDs bloqueados repetidos
- [x] Código: validação explícita de mais de três Pokémon bloqueados devolve `REFUSED` (`18728f4`). Nota de correção: a alegação anterior de deduplicação de IDs não era um ganho funcional, pois `lockedOwnedPokemonIds` já é `Set<String>`; a chamada a `distinct()` foi retirada.
- [x] Regra pura `TeamLockPolicy` (`01677a3`), usada pelo Advisor (`c4e9564`), com três testes JUnit Android (`9434b6b`) exigidos no workflow (`ca18cfa`). Fixture de quatro bloqueios mantido; **CI dos novos testes por confirmar**.
- [x] CI Android aprovado: [run #36988020774](https://github.com/Sphinkz10/Pokemon/actions/runs/36988020774), commit `ca18cfa`; compilação, testes JUnit, assinatura e APK artefacto #11217329648.
- [ ] Validação funcional de Equipas no Android.

**Implementação/CI V1.60:** 3/4 (75%, falta validação Android); **aceitação em dispositivo V1.60:** 0/1 (0%). Os gates globais continuam **7/50 (14%)**. Não confundir código escrito com comportamento certificado.

## V1.59 — Correção de restauração de Exemplares (em validação)

- [x] Implementação: impedir fallback de `EXEMPLARS` para `LIST` enquanto catálogo está vazio durante carregamento. Commit [`41ceb0d`](https://github.com/Sphinkz10/Pokemon/commit/41ceb0dd667e53cabad0e8fcef6e0109789fa283).
- [x] Teste JUnit de política para catálogo vazio/carregado e guard estrutural atualizado; commits `f94082b`, `0e0accd`. **Código implementado; execução CI ainda pendente.**
- [x] CI Android debug aprovado no commit `0e0accd`: [run #36982294842](https://github.com/Sphinkz10/Pokemon/actions/runs/36982294842); build, JUnit, verificações, assinatura e artefacto #11215474800 concluídos.
- [ ] Aceitação no Android: recriar Activity/processo em Exemplares e verificar rota, espécie e dados.

**Progresso V1.59:** implementação/CI 3/4 (75%); aceitação em Android 0/1 (0%). **Global:** permanece 7/50 (14%); V1.58 mantém 9/10 (90%) até validação no dispositivo. Não existe nova APK verificada neste checkpoint.

## Checkpoint V1.58 · Restaurar navegação após mudança de aba e recriação Android

**Estado de implementação/CI:** **9/10 tarefas demonstradas (90%)**, com compilação assinada e testes automáticos aprovados. Continua pendente apenas o teste funcional no Android. Os **50 gates de aceitação globais mantêm 7/50 (14%)**, porque nenhuma nova fase foi aceite em dispositivo.

| ID | Trabalho concreto | Evidência / critério | Estado |
|---|---|---|---|
| V58-1 | Guardar aba principal e rota de regresso ao sair dos detalhes | `MainActivity.kt`: `rememberSaveable` | [x] |
| V58-2 | Conservar estado de composição dos 5 separadores | `SaveableStateProvider(tab.name)` | [x] |
| V58-3 | Guardar a rota da Coleção e identificadores de espécie/exemplar | `CoreScreens.kt`: rotas e IDs primitivos com `rememberSaveable` | [x] |
| V58-4 | Conservar os dois IDs da comparação e a liga ativa | IDs e `speciesLeague` salváveis | [x] |
| V58-5 | Conservar rotas de origem para comparação, detalhe e IV Targets | `detailReturnRoute`, `compareReturnRoute`, `targetsReturnRoute` | [x] |
| V58-6 | Evitar descartar fichas restauradas antes de o Room carregar | `collectionLoaded` só depois de primeira emissão | [x] |
| V58-7 | Evitar regressar a Lista durante carregamento do catálogo PvP | `LaunchedEffect` condicionada a catálogo disponível | [x] |
| V58-8 | Usar política testável de navegação para o Back do Android | `CollectionNavigationPolicy`, 7 JUnit e 25 verificações estruturais | [x] |
| V58-9 | Compilar V1.58, passar JUnit, verificar assinatura e extrair APK | [CI #36948417165](https://github.com/Sphinkz10/Pokemon/actions/runs/36948417165): 25/25 guards, 26/26 JUnit, APK v2 verificada, artefacto #11202658262 íntegro | [x] |
| V58-10 | Rodar ecrã, alternar abas, reabrir ficheiro e confirmar estado + dados no Android | Testes físicos + screenshots e logs | [ ] |

**Limite importante:** `rememberSaveable` e `SaveableStateHolder` melhoram a restauração de estado Android, mas não são sincronização entre instalações, nem substituem testes de rotação e *process death*; o armazenamento persistente dos exemplares continua no Room. A versão debug V1.58 instala-se isoladamente, não faz migração de dados da V1.57. O menu e o Figma continuam sujeitos a auditoria visual.

## Checkpoint V1.57 · Coleção estável e edição protegida

**Âmbito:** fechar o percurso Coleção → Pokédex/Pokémon 360 → Exemplar → Editar → Regressar, preservando filtros e sem perda silenciosa de alterações.

**Estado desta implementação:** **9/10 itens verificados por código, JUnit e CI (90%)**. Continua pendente a validação funcional/visual num Android. O progresso global de aceitação mantém-se em **7/50 (14%)**; este checkpoint não é equivalente a ecrãs finais testados.

| Item | Requisito | Evidência | Estado |
|---|---|---|---|
| V57-1 | Preservar estado da lista ao navegar entre rotas | `rememberSaveableStateHolder` em `CollectionModuleScreen` | [x] |
| V57-2 | Preservar pesquisa, tab, filtros, ordenação e geração | `rememberSaveable` em `CollectionOverviewScreen` | [x] |
| V57-3 | Preservar posição separada das listas Coleção/Pokédex | Dois estados `rememberLazyListState` | [x] |
| V57-4 | Identificar espécie selecionada por número Dex, não objeto serializado | `selectedDex` persistente; registo reobtido do índice | [x] |
| V57-5 | Detetar alterações não guardadas em CP/nível/ataques/plano | `hasUnsavedBuild` e `hasUnsavedPlan` | [x] |
| V57-6 | Confirmar descarte pela seta e pelo Back Android | `AlertDialog` e `BackHandler` | [x] |
| V57-7 | Impedir ataques novos fora do movepool conhecido sem descartar ataques legados | `movePoolError`, respeitando o catálogo incompleto | [x] |
| V57-8 | Não assinalar campos desconhecidos como totalmente verificados; três testes JUnit novos | `lastVerifiedAtEpochMs` condicionado + `OwnedBuildValidationTest` | [x] |
| V57-9 | Concluir integração 22/22, 19/19 JUnit, APK e assinatura | [Build verde #36943601185](https://github.com/Sphinkz10/Pokemon/actions/runs/36943601185); artefacto #11200584238 (APKv2) | [x] |
| V57-10 | Prova em Android: pesquisa → ficha → exemplar → editar → voltar, rotação e dados persistentes | Capturas e teste real do Rui | [ ] |

**Limites:** o mecanismo de estado salvável protege a memória de navegação em Compose, mas a persistência após morte do processo e restauração integral exige teste em aparelho. A edição não é sincronização remota. A variante debug tem `applicationIdSuffix=.installtestv157`, separado das instalações anteriores. A versão de produção requer ID e assinatura estáveis, bem como migração de dados.

## Checkpoint V1.56 · Pokémon 360 multiliga e edição de dados (9/10 verificados com código e CI)

**Âmbito real:** Little / Great / Ultra / Master em todos os ecrãs de espécie e comparação; ficha ilustrada para exemplares; validação de CP, nível e ataques. As novas tarefas **não alteram automaticamente o progresso global de 7/50 gates (14%)**, que exige provas de uso real por tarefa completa.

| Gate | Tarefa | Estado e prova |
|---|---|---|
| V56-01 | Classificar e nomear todas as quatro ligas, incluindo CP máximos | [x] `SpeciesLeaguePolicy.kt` |
| V56-02 | Calcular ranking pela liga escolhida com `RankRepository` e coroutines | [x] `SpeciesCollectionScreens.kt` |
| V56-03 | Partilhar a escolha da liga entre espécie, exemplares e comparação | [x] `CollectionModuleScreen` e `SpeciesLeagueSelector` |
| V56-04 | Mostrar rankings indisponíveis sem inventar IV/rank | [x] `SpeciesLeaguePolicy.statusLabel` |
| V56-05 | Melhorar o perfil Owned Pokémon com arte Shiny, IVs reais, fav e dados por confirmar | [x] `OwnedPokemonDetailScreen` |
| V56-06 | Validar CP, nível e moves antes de guardar, sem limpar campos mal escritos | [x] `OwnedBuildValidation.kt` |
| V56-07 | Passar 9 novos testes JVM (5 de ligas, 4 de validação), mantendo 7 testes Pokédex | [x] 16/16 JUnit · [CI #36942297296](https://github.com/Sphinkz10/Pokemon/actions/runs/36942297296) |
| V56-08 | Passar regressão estrutural de 30 checks + todas as anteriores | [x] `tests/v1_56_league_360/verify_wiring.py` + CI verde |
| V56-09 | Construir, verificar assinatura e disponibilizar APK V1.56 isolada | [x] `com.rui.pvpgo.installtestv156` · assinatura v2 e artefacto #11200532066 verificados |
| V56-10 | Testar 4 ligas, rankings, 320/390/430dp, edição válida/inválida no Android | [ ] Telefone do Rui, capturas e logs |

**Limites:** o cálculo de ranking não substitui testes externos de paridade numérica do motor, uma liga não é prova de elegibilidade competitiva, e estes ecrãs ainda não foram validados no dispositivo. A versão de desenvolvimento mantém ID `com.rui.pvpgo.installtestv156` para não interferir com a instalação V1.55.

## Checkpoint V1.53 · Eventos automáticos e skins (9/10 gates validados por código/CI)
  
Este checkpoint mede **trabalho de implementação**, não certificação final. A aplicação precisa de teste no dispositivo, observação real do feed e auditoria visual/contraste.

| Gate | Tarefa concreta | Evidência | Estado |
|---|---|---|---|
| T1 | Quatro paletas semânticas Deep/AMOLED/Mystic/Classic | `PvpSkins.kt` | [x] |
| T2 | Persistir skin no dispositivo e restaurar no arranque | `PvpSkinPreferences`, `MainActivity` | [x] |
| T3 | Controlos de skin em Mais → Aparência | `CoreScreens.kt` | [x] |
| T4 | Migrar os tokens centrais e a navegação/Hoje | `PvpTheme.kt`, `MainActivity.kt`, `TodayScreen.kt` | [x] |
| T5 | Conector comunitário que interpreta datas locais e UTC | `events/EventCalendarRepository.kt` | [x] |
| T6 | Cache 6h, aviso de obsolescência 24h e WorkManager 6h | `EventCalendarRepository` | [x] |
| T7 | Integrar lista e agenda com atribuição, atualizar e estados vazios | `events/EventCalendarScreens.kt`, `TodayScreen.kt` | [x] |
| T8 | Guardas de regressão sem dados fictícios + temas/eventos | `tests/v1_52_dashboard_truth` + `tests/v1_53_skins_events` | [x] |
| T9 | Compilação Android V1.53 debug assinada e pacote extraído | [CI #36936887131](https://github.com/Sphinkz10/Pokemon/actions/runs/36936887131) e APK extraída com ZIP íntegro | [x] |
| T10 | Teste em dispositivo: 4 skins, cache, sincronização, timezone, off-line, contraste | Evidência de dispositivo | [ ] |

**Fonte de eventos:** adaptador de leitura HTTPS da API comunitária automatizada [leak-duck](https://github.com/zhenga8533/leak-duck), baseada no Leek Duck. **Não é uma fonte oficial da Niantic** e pode ficar desatualizada ou mudar de formato. A app identifica a fonte e evita inventar eventos. A atualização automática por WorkManager é aproximada (o Android pode adiar os trabalhos periódicos); não garante sincronização à hora exata.

**Limitações de skins:** as cores semânticas e o menu já têm suporte no código, mas ainda existem cores hex fixas em ecrãs/ilustrações antigos; a skin Classic clara exige auditoria de contraste antes da entrega final. A skin não é, ainda, 100% transversal.

**Roadmap global de 50 gates:** mantém **7/50 (14%) verificados**. Os 9/10 gates acima são implementação parcial em novas subáreas, não justificam alterar automaticamente os gates globais B2/C3/J3.

## Checkpoint V1.56 — Pokémon 360 e IV Targets multi-liga

**Estado verificado:** 9/10 gates da V1.56 concluídos (90%). [CI #36942297296](https://github.com/Sphinkz10/Pokemon/actions/runs/36942297296) com 35/35 verificações de integração, 16/16 JUnit (7 Pokédex + 5 ligas + 4 validação), assinatura APK V2 e artefacto publicado. Continua pendente o teste de utilização num Android. **Aceitação global:** continua 7/50 (14%), sem aumento artificial por existir código.

| Gate | Requisito e evidência | Estado |
|---|---|---|
| V56-01 | Seleção de Little / Great / Ultra / Master baseada no enum real `League` | [x] |
| V56-02 | Rank calculado por `RankRepository.find(species, league, iv)` usando a liga ativa | [x] |
| V56-03 | Liga partilhada entre Espécie, Exemplares, Comparação e IV Targets (`CoreScreens.kt`) | [x] |
| V56-04 | IV Targets geram Top 4 e melhor exemplar para a liga escolhida | [x] |
| V56-05 | Alterar liga limpa rankings antigos e ativa estado de carregamento específico | [x] |
| V56-06 | Distinguir «a calcular», «sem exemplares» e «exemplares sem rank elegível» | [x] |
| V56-07 | Componentes multi-liga e IV Targets com tokens das 4 skins e alvos de toque ≥48 dp | [x] |
| V56-08 | Testes de políticas (labels/CP/seleção) e verificador de integração V1.56 no CI | [x] |
| V56-09 | Confirmar build V1.56 e assinatura debug, com APK descarregada | [x] |
| V56-10 | Testar os 4 contextos com Pokémon reais, trocar liga e comparar no Android | [ ] |

**Limites:** O Rank exibido é o ranking matemático de stat product e não um ranking competitivo por meta. O motor usa níveis até 50 no perfil padrão; casos com requisitos de formas, elegibilidade ou regras especiais exigem verificação. Não usar um Rank para decidir transferências de forma automática. As builds debug continuam com identificador isolado para evitar conflitos de assinatura; a versão final precisará de chave persistente e estratégia de migração.

## Checkpoint V1.55 · Pokédex Nacional e Pokémon 360 (9/10 verificados por código e CI)

**Progresso desta ronda:** 9/10 tarefas com prova de código, testes JUnit ou CI; falta a verificação num dispositivo Android. **Progresso global de aceitação:** mantém-se **7/50 (14%)** até existir teste em dispositivo e evidência integral para um gate global.

| Gate | Trabalho | Evidência | Estado |
|---|---|---|---|
| V55-1 | Abrir perfil para qualquer espécie da Pokédex Nacional | `NationalDexDetailScreen.kt`; fila `selectedNational` na Coleção | [x] |
| V55-2 | Bloquear cálculos PvP se não houver ficha de batalha | `onOpenIvTargets = battleSpecies?.let`, vista informativa independente | [x] |
| V55-3 | Corrigir Voltar segundo origem (espécie, exemplares, comparação e targets) | `CoreScreens.kt`: `detailReturnRoute`, `compareReturnRoute`, `targetsReturnRoute` | [x] |
| V55-4 | Pesquisar números com e sem `#` e zeros iniciais | `NationalDexPolicy.filter`, pesquisa da Coleção | [x] |
| V55-5 | Fazer filtros horizontais acessíveis a 320 dp | `CollectionFilterRow` deslocável | [x] |
| V55-6 | KPIs reais da Coleção: Exemplares/Espécies/Shiny/IV 100% | `CollectionStatsStrip` com dados de `OwnedPokemon` | [x] |
| V55-7 | Remover cores fixas da Coleção e dos ecrãs de espécie/360 | `CollectionOverviewScreen.kt` e `SpeciesCollectionScreens.kt` sem `Color(0xFF...)` | [x] |
| V55-8 | Criar testes JUnit e verificadores de navegação/escala | `NationalDexPolicyTest.kt` (7 testes) + dois scripts Python (50+ checks) | [x] |
| V55-9 | Executar JUnit no Gradle, CI de ponta a ponta e verificar APK | [Execução verde #36940790132](https://github.com/Sphinkz10/Pokemon/actions/runs/36940790132): 7/7 JUnit, 28/28 escala, 22/22 navegação, APK V2 assinada | [x] |
| V55-10 | Validar perfis, imagens, filtros, pesquisa, regressos e skins no Android | Testes reais e capturas 320/390/430 dp | [ ] |

**Limitações abertas:** existem formas alternativas para as quais o índice nacional é apenas informativo; estas não recebem simulações inventadas. O detalhe informativo não equivale ao Pokémon 360 completo e ainda faltam migrações, avaliações de acessibilidade e testes em dispositivos físicos. A nova assinatura debug volta a ter um ID de teste separado para não entrar em conflito com builds anteriores, até termos uma assinatura de release persistente.

## Checkpoint V1.54 · Qualidade visual e integridade de eventos

**Estado nesta entrega:** 4/5 tarefas verificadas por código, testes e CI (80%). Continua pendente a validação real no Android. Os critérios globais de aceitação **continuam 7/50 (14%)** até haver provas adicionais. A recompilação definitiva desta versão encontra-se na [execução V1.54](https://github.com/Sphinkz10/Pokemon/actions/runs/36938928935).

| ID | Tarefa | Resultado / Critério | Situação |
|---|---|---|---|
| V54-1 | Inspecionar o JSON público de eventos | Confirmados grupos com datas locais ISO e timestamps UNIX em [events.json](https://raw.githubusercontent.com/zhenga8533/leak-duck/data/events.json) | [x] |
| V54-2 | Migrar cores de interface do Hoje e aumentar alvos | 19 ocorrências / 13 famílias convertidas para tokens; CTA de 48 dp, sem substituir as cores próprias das ilustrações | [x] |
| V54-3 | Proteger agenda contra formato inválido e títulos longos | Não gravar JSON sem eventos válidos; cache anterior preservada; título limitado a 2 linhas | [x] |
| V54-4 | Passar 14+23+14 verificações e produzir APK V1.54 | [CI #36938928935](https://github.com/Sphinkz10/Pokemon/actions/runs/36938928935): testes + build + assinatura + artefacto verificados | [x] |
| V54-5 | Verificar no telemóvel a Agenda e as quatro skins | Screenshot 320/390/430, rede/offline, fusos e persistência | [ ] |

**Ainda pendente:** migrar para tokens todas as cores fixas dos restantes módulos; contrastes da skin Classic; validar a pontualidade das notificações/WorkManager; testar se os eventos carregam realmente em pelo menos um Android com Internet, sem inventar horários.

## Progresso por módulo

| Módulo | Gates confirmados | Percentagem | Próximo gate |
|---|---:|---:|---|
| A — APK e entrega | 3/5 | 60% | Teste real: arranque, navegação 5 tabs, regresso, rotação e logs sem crash. |
| B — Design System e UI | 1/5 | 20% | Ecrã Android Hoje comparado com GOLDEN Figma a 320, 390 e 430 dp. |
| C — Dashboard · Hoje | 2/5 | 40% | Ligar dados oficiais ou origem configurável de eventos, com proveniência/data. |
| D — Coleção e Pokédex | 1/5 | 20% | Pesquisar nome/#Dex/tag/IV em Android e medir UX com coleções grandes. |
| E — Pokémon 360 e IV | 0/5 | 0% | Detalhe completo e navegação bidirecional Coleção ↔ 360. |
| F — Equipas | 0/5 | 0% | Coleção ligada a escolha de 3 Pokémon com liga/CP elegíveis. |
| G — Batalhas e planeador | 0/5 | 0% | Seleção de liga/equipa/adversário e importação de dados verificáveis. |
| H — Companion | 0/5 | 0% | Modo manual completo para identificar equipas e gerir batalha. |
| I — Dados, offline e desempenho | 0/5 | 0% | Contrato de entidades/versões estável e migrações Room verificadas. |
| J — Segurança e finalização | 0/5 | 0% | Eliminar segredos, dados pessoais e artefactos de debug do release. |

## Inventário completo de gates

### A — APK e entrega

- [x] **A1** — Repo Android no GitHub e workflow com compilação V1.51 verde.
- [x] **A2** — APK de diagnóstico instalada e aberta pelo Rui num Android.
- [x] **A3** — APK V1.52 gerada com CI e assinatura verificada, sem falhas. [Build #36934661709](https://github.com/Sphinkz10/Pokemon/actions/runs/36934661709), artefacto Android validado.
- [ ] **A4** — Teste real: arranque, navegação 5 tabs, regresso, rotação e logs sem crash.
- [ ] **A5** — Release assinada estável, versionada e instalável por atualização.

### B — Design System e UI

- [x] **B1** — Tokens de cores, tipografia, espaçamento e raio definidos no código.
- [ ] **B2** — Ecrã Android Hoje comparado com GOLDEN Figma a 320, 390 e 430 dp.
- [ ] **B3** — Navegação inferior, estados ativos e insets/safe areas com paridade visual.
- [ ] **B4** — Todas as imagens normal/Shiny e placeholders com licença e contraste avaliados.
- [ ] **B5** — Auditoria de acessibilidade: alvos >=44dp, font scale, TalkBack e contraste.

### C — Dashboard · Hoje

- [x] **C1** — Mostrar contagem de espécies realmente carregadas, não valor fabricado.
- [x] **C2** — Retirar evento, contagem decrescente, bónus e agenda inventados; mostrar estado sem feed.
- [ ] **C3** — Ligar dados oficiais ou origem configurável de eventos, com proveniência/data.
- [ ] **C4** — Prioridades derivadas de coleção/IV/objetivos reais, com estado vazio honesto.
- [ ] **C5** — Validar UI, pesquisa, loading/erro/offline e gestos em Android 320/390/430.

### D — Coleção e Pokédex

- [x] **D1** — Arquitetura Nacional dinâmica (1.000+ espécies) com teste sintético 1.250.
- [ ] **D2** — Pesquisar nome/#Dex/tag/IV em Android e medir UX com coleções grandes.
- [ ] **D3** — Filtros G1–G9/Novas, ordenação, favoritos e contadores consistentes.
- [ ] **D4** — Cache de artwork normal/Shiny e catálogo offline sem dados falsos.
- [ ] **D5** — Importar, criar, editar, apagar e exportar coleção sem perda de registos.

### E — Pokémon 360 e IV

- [ ] **E1** — Detalhe completo e navegação bidirecional Coleção ↔ 360.
- [ ] **E2** — Estatísticas CP/IV/rank comprovadas com fixtures PvP documentadas.
- [ ] **E3** — Evoluções, formas, custos e ataques com proveniência e fallback.
- [ ] **E4** — Exemplares, tags, metas e IV targets com dados locais reais.
- [ ] **E5** — Testes automáticos e comparação visual Figma 320/390/430.

### F — Equipas

- [ ] **F1** — Coleção ligada a escolha de 3 Pokémon com liga/CP elegíveis.
- [ ] **F2** — Criar, editar, ordenar, duplicar, apagar e persistir equipas.
- [ ] **F3** — Cobertura/counters com explicação e sem probabilidades inventadas.
- [ ] **F4** — Estados vazios, erros, edição e componentes GOLDEN reproduzidos.
- [ ] **F5** — Testes de rotas/recuperação e regressão em telemóvel.

### G — Batalhas e planeador

- [ ] **G1** — Seleção de liga/equipa/adversário e importação de dados verificáveis.
- [ ] **G2** — Motor de turnos/energia/dano/escudos com fixtures e comparação externa.
- [ ] **G3** — Planner de ataques, buffs/debuffs, switches e tempos.
- [ ] **G4** — Histórico e resultados ligados à equipa, sem dados demonstrativos ocultos.
- [ ] **G5** — Ecrãs GOLDEN, testes de fluxo e performance validada no Android.

### H — Companion

- [ ] **H1** — Modo manual completo para identificar equipas e gerir batalha.
- [ ] **H2** — Assistência em tempo real com estado claro de confiança/incerteza.
- [ ] **H3** — Captura/reconhecimento visual apenas com permissões e calibração explícitas.
- [ ] **H4** — Tratamento de lag, interrupções, erros de leitura e privacidade.
- [ ] **H5** — Teste numa batalha real ou replay permitido, resultados medidos.

### I — Dados, offline e desempenho

- [ ] **I1** — Contrato de entidades/versões estável e migrações Room verificadas.
- [ ] **I2** — Cache, quotas, TTL e origem PokeAPI documentados e testados.
- [ ] **I3** — Modo offline, recuperação da ligação e atualizações idempotentes.
- [ ] **I4** — Medições com 1.000+ Pokémon e coleções extensas: memória/lista/imagens.
- [ ] **I5** — Integração, observabilidade e recuperação de erros com testes.

### J — Segurança e finalização

- [ ] **J1** — Eliminar segredos, dados pessoais e artefactos de debug do release.
- [ ] **J2** — Rever direitos de imagens, políticas de utilização e atribuições.
- [ ] **J3** — Testes instrumentados em 2+ dispositivos/versões Android.
- [ ] **J4** — Checklist de regressão final, instalador e changelog verificados.
- [ ] **J5** — Publicação/release final com guia de atualização e backup.

## Histórico de trabalho V1.52 e V1.53

- **Código real alterado:** `app/src/main/java/com/rui/pvpgo/TodayScreen.kt`: contagem do catálogo carregado e estado não sincronizado de eventos/agenda. Os exemplos de Carbink/Vulpix/Paras passam a estar identificados como espécies de referência, não como recomendações pessoais já calculadas.
- **Versões:** a V1.52 usou `versionCode=52`; a versão atual está em `versionCode=53` e `1.53.0-dev`, mantendo `.installtest` para debug.
- **Regressão automatizada:** `tests/v1_52_dashboard_truth/verify_dashboard.py` com 14 verificações; adicionado ao GitHub Actions.
- **CI:** a [V1.53 compilou e passou os checks de assinatura](https://github.com/Sphinkz10/Pokemon/actions/runs/36936887131). A compilação verde **não substitui o teste final em Android**.

## Ordem de execução sugerida (não saltar gates sem registo)

1. **A4 + T10 (V1.53)** — instalar e testar a APK V1.53 no telemóvel: 5 tabs, 4 skins, persistência, regresso, rotação, feed ativo/offline, horário local e logs.
2. **B2+B3+C5** — corrigir correspondência visual de Hoje e navegação inferior com Figma e screenshots 320/390/430.
3. **D2–D5 e E1–E5** — completar Coleção, catálogo, importação e Pokémon 360, validar persistência.
4. **F1–F5** — Equipas com dados reais.
5. **G1–G5** — Batalhas/engine com testes de paridade.
6. **H1–H5** — Companion por etapas, começando pelo manual estável.
7. **I1–I5 + J1–J5** — robustez, offline, QA final e entrega assinada.

### Critério de pronto

Cada gate passa quando houver **código versionado + teste ou captura comprovável + ligação ao artefacto**, quando aplicável. Não marcar como concluído por existir documentação, mocks, screenshots do Figma ou um botão que ainda não executa a ação.

**Estado atual de eventos:** a V1.53 contém um adaptador HTTPS para a fonte comunitária Leek Duck (não oficial), cache e atualizações periódicas. A sincronização automática e a precisão dos horários ainda precisam de validação num dispositivo real e contra a fonte; o feed pode falhar ou mudar de formato.
