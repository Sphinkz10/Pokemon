# PR #42 — Inbox de candidatos PvP: pesquisa e triagem sem decisões automáticas

## Contexto do utilizador
A jornada já permite copiar pesquisas para Pokémon GO, registar IVs manualmente, pré-visualizar o rank por liga e confirmar ou descartar exemplares na Companion. Ao acumular dezenas de entradas, a lista única tornava a revisão lenta e os possíveis duplicados difíceis de identificar.

## Alterações reais
- A Inbox passa a apresentar resumo **visíveis/pendentes**, **possíveis correspondências**, **sem correspondência** e **dados por completar**.
- Pesquisa local por **nome da espécie**, **speciesId**, **alcunha** ou IVs exatos digitados (ex.: `0/15/15`).
- Quatro filtros combináveis com pesquisa: **Todos**, **Possíveis correspondências**, **Sem correspondências**, **Dados por completar**.
- Os estados de pesquisa/filtro são guardados na composição; se nenhum candidato corresponder, o botão **Mostrar todos os candidatos** repõe a lista sem tocar nos dados.
- A classificação usa `CollectionReconciler.reconcile` com a coleção atual (o mesmo mecanismo que os cartões usavam antes). Candidatos já revistos/descartados não reaparecem como pendentes.
- Nenhuma ação automática de fusão, confirmação, transferência ou descarte é criada. Os botões individuais continuam a exigir toque humano.
- Uma possível correspondência **não é** prova de ser o mesmo indivíduo; a app mantém aviso visível.

## Critérios e provas
- 8 casos JVM do `InboxTriagePolicyTest` para contagens, interseção de pesquisa, explicações, UNKNOWN e exclusão de já revistos.
- 5 casos instrumentados Android do `InboxTriageComposeAndroidTest` usando o shell real, SQLite Room isolado e controlos da Inbox: contagens, possíveis correspondências, campos por completar, recuperação de zero resultados e descarte individual.
- Contrato de integridade Python na pipeline APK/JVM.
- Gate Android sobe de 100 para **105 testes instrumentados executados**, não apenas presentes no código.

## Limitações
A reconciliação compara *registos da coleção local*. Não identifica o mesmo Pokémon na conta Pokémon GO, não importa resultados do jogo, não mede força competitiva e não justifica transferências. A soma global dos 110 cenários UX/E2E mantém-se separada dos testes instrumentados. A triagem não substitui revisões manuais nem permite operações destrutivas em massa.

## Seguimento
Auditar fluxo com Inbox grande (100–1000 entradas) e acessibilidade do painel com 200% de letra; simplificar avaliação por liga; fechar tarefas formais do roadmap apenas após cumprir o DoD e rastrear os IDs das verificações.
