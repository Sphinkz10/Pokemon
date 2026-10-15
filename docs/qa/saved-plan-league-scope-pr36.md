# Pokémon Companion V3 · PR #36 — guia de planos por liga, sem mistura perigosa

## Erro detetado
Na etapa `3 · Preparar plano` a pessoa selecionava Great, Ultra ou Master, mas o `planByOwned` elegia **o plano de maior prioridade de qualquer liga**. Isto podia apresentar um plano Ultra na vista Great, abrir o alvo errado e impedir criar um novo plano Great para o mesmo Pokémon. O texto "Liga do plano" induzia o utilizador em erro.

## Correção
- `CollectionPlanLeaguePolicy.preferredByOwned` escolhe exclusivamente planos da liga visível e exemplar exato.
- No caso de duplicados na mesma liga, privilegia planos não arquivados, depois prioridade, última modificação e ID estável. Um plano arquivado isolado permanece para revisão explícita.
- A lista dos planos já guardados e a sua `Ação seguinte` passam a corresponder à liga atualmente selecionada.
- `Criar plano` considera como não planeado **nesta liga** qualquer exemplar que tenha planos noutras ligas, sem apagar ou editar esses planos.
- Nos restantes separadores, o comportamento de listagem geral permanece inalterado.
- Alternar liga é uma consulta, nunca grava CP, recursos, ataques ou planos.

## Testes
- 12 testes JVM do `CollectionPlanLeaguePolicy`: Great vs Ultra de prioridade maior; múltiplas ligas; vários exemplares; arquivados; estabilidade; campos inválidos; imutabilidade.
- 3 novos testes Android Compose reais: Ultra guardado não bloqueia criar Great; selecionar Ultra revela o plano correto; Great com prioridade superior não desvia o botão de ação de Ultra.
- Adaptação dos testes existentes de handoff Ultra: agora devem **selecionar Ultra** antes de exigir plano Ultra, em vez de assumir visibilidade falsa em Great.
- Gate obrigatório: **84 testes Android instrumentados** (81 + 3), zero falhas/skips e APK/JVM verde.

## Critérios de aceite
1. Com plano apenas Ultra e liga Great escolhida, aparece "Criar plano" e zero planos guardados Great, sem mostrar ação Ultra.
2. Selecionando Ultra, o mesmo exemplar aparece como plano guardado Ultra; deixa de existir botão de criação Ultra.
3. Existindo simultaneamente planos Great e Ultra, a ação Ultra abre Ultra, mesmo que o plano Great tenha maior prioridade.
4. Não são criados planos automaticamente ao trocar de liga.
5. UI navega e dados Room mantêm-se intactos; não confundir com ranking competitivo nem autorização de investimento.

## Limites
A seleção de liga é um filtro de planos, **não uma certificação de elegibilidade** em copas especiais, meta PvP, IVs, recursos ou evolução. Os 110 cenários históricos UX/E2E continuam num contador separado até reconciliação de IDs/DoD.
