# Pokémon PvP — Roadmap de aceitação V1.52

**Data:** 2026-10-01 · **GitHub:** [Sphinkz10/Pokemon](https://github.com/Sphinkz10/Pokemon) · **Figma:** [Pokémon Companion](https://www.figma.com/design/Y85bgqW7K2jEnrXdqDnwBE)

**Objetivo:** chegar a uma aplicação real, modular, com qualidade visual e funções completas. O roadmap mede apenas **gates de implementação/verificação explicitamente definidos**. Não mede linhas de código nem a percentagem global real do produto; ecrãs existentes no Figma não contam como ecrãs Android terminados.

**Linha de base documentada:** 6/50 gates com prova de fonte/código ou relato de instalação (**12% de gates de aceitação**). É deliberadamente conservador: outras funcionalidades estão no código mas continuam sem prova em telemóvel. Os gates só passam com evidência técnica e ficam sujeitos a regressões.

## Progresso por módulo

| Módulo | Gates confirmados | Percentagem | Próximo gate |
|---|---:|---:|---|
| A — APK e entrega | 2/5 | 40% | APK V1.52 gerada com CI e assinatura verificada, sem falhas. |
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
- [ ] **A3** — APK V1.52 gerada com CI e assinatura verificada, sem falhas.
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

## Resultado da ronda V1.52

- **Código real alterado:** `app/src/main/java/com/rui/pvpgo/TodayScreen.kt`: contagem do catálogo carregado e estado não sincronizado de eventos/agenda. Os exemplos de Carbink/Vulpix/Paras passam a estar identificados como espécies de referência, não como recomendações pessoais já calculadas.
- **Número de versão alterado:** `app/build.gradle.kts` para `versionCode=52` e `1.52.0-dev`; mantém o sufixo `.installtest` na variante debug.
- **Regressão automatizada:** `tests/v1_52_dashboard_truth/verify_dashboard.py` com 14 verificações; adicionado ao GitHub Actions.
- **CI:** consultar [Actions](https://github.com/Sphinkz10/Pokemon/actions). Uma APK CI verde é compilada e assinada, mas **não é validação de uso final em dispositivo**.

## Ordem de execução sugerida (não saltar gates sem registo)

1. **A3** — obter APK V1.52 CI green e descarregável; depois **A4** testar no telemóvel.
2. **B2+B3+C5** — corrigir correspondência visual de Hoje e navegação inferior com Figma e screenshots 320/390/430.
3. **D2–D5 e E1–E5** — completar Coleção, catálogo, importação e Pokémon 360, validar persistência.
4. **F1–F5** — Equipas com dados reais.
5. **G1–G5** — Batalhas/engine com testes de paridade.
6. **H1–H5** — Companion por etapas, começando pelo manual estável.
7. **I1–I5 + J1–J5** — robustez, offline, QA final e entrega assinada.

### Critério de pronto

Cada gate passa quando houver **código versionado + teste ou captura comprovável + ligação ao artefacto**, quando aplicável. Não marcar como concluído por existir documentação, mocks, screenshots do Figma ou um botão que ainda não executa a ação.

**Ponto importante:** o bloco de eventos foi corrigido para não exibir horários fictícios; ainda não foi ligado a um feed verificado. A interface mostra explicitamente esse estado.
