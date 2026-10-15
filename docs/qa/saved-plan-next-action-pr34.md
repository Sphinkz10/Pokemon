# Pokémon Companion v3 · PR #34 — Ação seguinte para planos PvP guardados

## Lacuna de UX resolvida
Após criar um plano na etapa **3 · Preparar plano** (PR #33), a Coleção já mostrava o exemplar com plano, mas não respondia à pergunta fundamental: **“E agora, o que faço?”**. Uma pessoa sem conhecimento técnico tinha de adivinhar se faltava confirmar CP, escolher ataques, registar Candy ou começar a calcular os gastos.

## Mudanças implementadas
Cada exemplar com plano guardado na etapa 3 mostra, sem aumentar o número de planos nem alterar o exemplar:
- **Ação seguinte** específica do plano real e da liga guardada;
- motivo curto, concreto e com ressalva da informação desconhecida;
- botão **Resolver no Pokémon 360 →**, que abre exatamente esse exemplar.

Ordem explícita e conservadora da política `SavedPlanNextActionPolicy`:
1. Plano arquivado ou associado ao exemplar errado → rever plano;
2. Espécie/forma sem correspondência válida no catálogo → confirmar identidade;
3. CP ou nível desconhecidos ou campos assinalados como incertos → confirmar no Pokémon GO;
4. CP/nível incoerentes ou não elegíveis para limites genéricos da liga → rever;
5. Fast Move ou Charged Move real não registado → confirmar movimentos;
6. Stardust, Candy ou XL não confirmados → registar saldos; **desconhecido não é zero**;
7. Três saldos conhecidos (mesmo que zero) → escolher nível alvo e validar custos, **nunca declarar investimento viável**.

A política reutiliza `InvestmentReadinessPolicy` para coerência real de CP e elegibilidade base, e `UserResourceStore` para saldos manuais específicos da família do Pokémon. Não chama uma API Pokémon GO nem tenta fazer power-ups no jogo. Não altera CP, nível, movimentos, Candy, Stardust ou qualquer plano sem ação explícita do utilizador.

## Evidência e testes
- **10 novos testes JVM** verificam associações de planos, arquivo, identidade, desconhecidos, inconsistência/limite genérico, movimentos, recursos desconhecidos e zero intencional.
- **4 novos testes Compose no emulador Android**: explicar CP desconhecido; explicar ataques reais não confirmados; listar recursos em falta; com Stardust/Candy/XL explicitamente conhecidos mostrar o alvo como *passo ainda por validar* e abrir o Pokémon 360 do ID correto.
- Gate Android sobe de **74 para 78 casos JUnit instrumentados**. Os testes antigos mantêm-se obrigatórios, com zero falhas/ignorado e compilação APK/JVM completa.

## Limitações explícitas
- O texto não é uma recomendação de meta, de melhor Pokémon ou de cupas/eventos especiais.
- Saber que há `0` Stardust/Candy/XL no inventário não significa haver recursos suficientes. O orçamento só é verificado com nível alvo, pack de custos válido e restrições reais.
- Os quatro testes novos não equivalem a fechar quatro dos 110 cenários E2E históricos sem correspondência formal dos critérios.
- Só foi contemplado um plano de maior prioridade por exemplar na Coleção; um exemplar com vários planos noutras ligas continua a precisar de uma vista detalhada específica.

## Próxima fase
Vincular uma ação do plano a um objetivo CP/nível efetivamente persistido, com comparação de necessidades Stardust/Candy/XL **sem exigir TMs que não entram no power-up**, mantendo custos comunitários apenas por consentimento. Continuar testes físicos e auditoria UX de toda a jornada.
