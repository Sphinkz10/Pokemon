# Pokémon PvP — Roadmap mestre de aceitação · atualizado até V1.53

**Data:** 2026-10-01 · **GitHub:** [Sphinkz10/Pokemon](https://github.com/Sphinkz10/Pokemon) · **Figma:** [Pokémon Companion](https://www.figma.com/design/Y85bgqW7K2jEnrXdqDnwBE)

**Objetivo:** chegar a uma aplicação real, modular, com qualidade visual e funções completas. O roadmap mede apenas **gates de implementação/verificação explicitamente definidos**. Não mede linhas de código nem a percentagem global real do produto; ecrãs existentes no Figma não contam como ecrãs Android terminados.

**Linha de base documentada:** 7/50 gates com prova de fonte/código, CI ou relato de instalação (**14% de gates de aceitação**). É deliberadamente conservador: outras funcionalidades estão no código mas continuam sem prova em telemóvel. Os gates só passam com evidência técnica e ficam sujeitos a regressões.

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

## Checkpoint V1.54 · Qualidade visual e integridade de eventos

**Estado nesta entrega:** 4/5 tarefas verificadas por código, testes e CI (80%). Continua pendente a validação real no Android. Os critérios globais de aceitação **continuam 7/50 (14%)** até haver provas adicionais. A recompilação definitiva desta versão encontra-se na [execução V1.54](https://github.com/Sphinkz10/Pokemon/actions/runs/36938413798).

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
