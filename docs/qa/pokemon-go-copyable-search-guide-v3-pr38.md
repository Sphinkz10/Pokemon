# Pokémon Companion V3 · PR #38 — Pesquisa de candidatos diretamente no Pokémon GO

## A dor do utilizador
O guia em **Coleção → 2 · Comparar IVs** já ajudava com Pokémon previamente registados. Faltava uma forma prática para um principiante encontrar candidatos na sua coleção **dentro do próprio Pokémon GO** sem decorar a sintaxe de pesquisa, sem gravar automaticamente dados e sem criar um segundo percurso concorrente.

## Solução implementada
No guia **2 · Comparar IVs** fica um painel com:
1. Escolher perfil (Ver tudo / Explorar candidatos / Perfil clássico / Ataque alto / IV 100%).
2. Selecionar idioma dos termos de pesquisa (EN ou PT).
3. Copiar a pesquisa gerada para a área de transferência Android.
4. Abrir manualmente Pokémon GO → Pokémon → Lupa, colar, avaliar os exemplares e regressar à app para comparar por espécie/IV Targets.

A liga selecionada no guia propõe **Explorar candidatos** em Little/Great/Ultra e **Ataque alto** em Master, mas o jogador pode mudar o perfil livremente. A seleção da liga **não modifica** a pesquisa dentro do Pokémon GO, não classifica o jogador automaticamente e não altera a coleção da app.

| Perfil | Sintaxe EN | Interpretação |
|---|---|---|
| Ver tudo | `0-4attack` | Não filtra pelos grupos de IV de Ataque |
| Explorar candidatos | `0-2attack&2-4defense&2-4hp` | Exploração alargada, ainda sujeita a omissões |
| Perfil clássico | `0-1attack&3-4defense&3-4hp` | Explora baixo Ataque e alto Def/HP |
| Ataque alto | `3-4attack&3-4defense&3-4hp` | Explora spreads de atributos altos |
| IV 100% | `4*` | Avaliação exata 15/15/15, não rank PvP |

Os equivalentes PT usam `ataque`, `defesa`, `ps`; o nome dos termos pode variar consoante idioma/versão do jogo. O seletor de linguagem permite trocar manualmente.

## Contratos de segurança
- Grupos 0–4 **não são IVs exatos**. Muitos Pokémon com perfil fora do filtro podem ser ótimos em determinadas espécies, limites de nível, evolução, Little Cup, Great, Ultra ou Master.
- Um filtro de busca no Pokémon GO **não estima** CP evoluído, melhor rank IV, meta atual, elegibilidade de copas ou custo de investimento.
- **Nunca automatizar transferências.** O painel avisa para não transferir Pokémon com base nestes filtros.
- Os templates não contêm `!`, CP restritivo, `#` de tags, condições de transferência ou ações de modificação de dados.
- A integração usa só `ClipboardManager.setPrimaryClip` após botão explícito; sem login/controlo de Pokémon GO, sem leituras remotas ou automação da conta.
- **Sem importação automática:** o utilizador decide individualmente que exemplares merece confirmar e guardar na nossa app.

## Provas requeridas
- **14 testes JVM** `PokemonGoSearchPolicyTest`: sugestões por liga, inglês/português, 5 perfis e ausência de buscas destrutivas.
- **5 testes Android Compose** `PokemonGoSearchComposeAndroidTest`: perfil por defeito, área de transferência real após clique, alteração de liga, 4* e integração real com a Coleção vazia.
- O gate Android `tests/v3_android_emulator/verify_instrumentation.py` aumenta de **87 para 92 testes** reais executados via JUnit XML, sem falhas/skips.
- Verificador `tests/v3_go_search/verify_search.py` corre no pipeline habitual APK/JVM.
- Os 110 cenários E2E globais mantêm contagem independente; não somar 5 novos testes como E2E globais sem mapeamento explícito.

## Fontes de sintaxe (consultadas em 16 out. 2026)
- Documentação oficial Pokémon GO de pesquisa e filtragem: https://niantic.helpshift.com/hc/en/6-pokemon-go/faq/1486-searching-filtering-your-pokemon-inventory/
- Termos localizados de atributos: https://niantic.helpshift.com/hc/pt-br/6-pokemon-go/faq/1486-searching-filtering-your-pokemon-inventory/
- Exemplos comunitários, **não prova de eficácia competitiva**: https://www.reddit.com/r/TheSilphArena/comments/1sixkil/search_strings/

## Próximos passos após esta fase
Fazer a ligação didática entre encontrar candidatos no GO, confirmar IVs manualmente, importar a família e comparar o **ranking exato** na app. Criar testes de UX em Android físico em português de Portugal e inglês; avaliar localização dos termos nas diferentes línguas do Pokémon GO; validar acessibilidade/small screens; expandir ajuda por espécies que exigem IVs altos em ligas com teto.
