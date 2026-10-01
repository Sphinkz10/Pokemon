# Pokémon PvP V1.55 — Pokédex Nacional + Coleção / Pokémon 360

**Estado:** CI concluído com sucesso em [Build #36940790132](https://github.com/Sphinkz10/Pokemon/actions/runs/36940790132), APK V1.55 gerada e assinatura Android V2 verificada; teste em telemóvel pendente. **Canal:** build debug de testes com id isolado `com.rui.pvpgo.installtestv155` para evitar incompatibilidade das chaves de debug temporárias.

## Alterações funcionais

1. Todas as entradas da Pokédex Nacional são selecionáveis, mesmo quando não têm equivalente no catálogo PvP. O detalhe separa dados da espécie dos dados PvP não disponíveis.
2. Um perfil informativo independente apresenta número da Pokédex, geração, artwork e quantidade de exemplares guardados, com a fonte do índice. Não inventa stats, ataques nem ranks.
3. Quando há dados PvP validados, a pessoa pode abrir IV Targets. Quando há um exemplar guardado, pode visitar os seus Pokémon da mesma espécie.
4. A navegação Voltar preserva o caminho original para detalhes individuais, comparações de exemplares e IV Targets.
5. Pesquisa na Pokédex por número, incluindo `#0001`, `0001`, `#1` e `1`; pesquisa numérica também na Coleção guardada.
6. Filtros da Coleção são deslocáveis horizontalmente para ecrãs de 320 dp.
7. Resumo visual dinâmico com quatro valores: Exemplares, Espécies, Shiny e IV 100%, sempre derivados dos `OwnedPokemon` reais.
8. Coleção e Pokémon 360 deixam de usar valores `Color(0xFF…)` fixos: a paleta vem do sistema de skins.

## Proteções e testes

- Reutiliza testes de integridade de V1.52, V1.53 e V1.54.
- Restaura as 28 verificações de estrutura e escala da Pokédex nacional.
- 22 verificações estáticas de navegação, origem de dados, skin e rotas.
- **7 testes JUnit** da lógica real `NationalDexPolicy` para numeração, 19 limites de gerações, formas de pesquisa, deduplicação e URLs de artwork (a executar no CI).
- Workflow requer `:app:testDebugUnitTest`, APK assinada e teste do package de diagnóstico.

## Provas CI concluídas

- Compilação `:engine:compileKotlin :app:assembleDebug` — PASS.
- `:app:testDebugUnitTest`: **7 testes executados, 0 falhas**.
- Escala da Pokédex — 28/28.
- Navegação, detalhes e skins — 22/22.
- Guardas anteriores de dados/skins/eventos — PASS.
- `apksigner verify` com Android Signature Scheme V2 — PASS.
- Package independente `com.rui.pvpgo.installtestv155`, `versionCode=55`.
- ZIP transferido e `app-debug.apk` extraída com verificação do arquivo sem erros.

## Testes manuais obrigatórios (ainda pendentes)

1. Abrir Pokédex, pesquisar `#0001`, `#037`, `#1025` e filtrar por G9 / Novas.
2. Abrir uma espécie sem dados PvP e confirmar que mostra perfil informativo, sem ações PvP inventadas.
3. Abrir espécie com exemplar: alternar espécie / exemplar / comparação / IV Targets e usar Voltar repetidamente.
4. Usar filtros e cartões da Coleção em 320, 390 e 430 dp, com Shiny e múltiplos exemplares.
5. Selecionar cada uma das quatro skins e confirmar contraste e persistência após reinício.
6. Verificar scroll de 1.000+ espécies, cache de artwork e funcionamento offline.

A V1.55 é uma entrega de desenvolvimento, não um release de produção. Os gates globais da app só passam após evidência de interação real, screenshots ou testes instrumentados.
