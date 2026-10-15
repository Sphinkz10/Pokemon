# Pokémon Companion v3 — PR #30: liga selecionada acompanha o jogador até IV Targets

## Problema de experiência do utilizador
No guia da coleção o jogador pode filtrar o CP e os perfis IV por **Little, Great, Ultra ou Master**, mas o comando **Comparar IVs por liga** abria o ecrã IV Targets com o estado de liga anterior partilhado por outros percursos (normalmente Great). Isso podia apresentar um ranking diferente daquele que o utilizador pensava estar a consultar.

## Entrega
- O comando mostra explicitamente a liga de destino: **Comparar IVs · [Liga] →**.
- `CollectionOverviewScreen` disponibiliza `onOpenIvTargetsForLeague(speciesId, selectedLeague)` sem remover o callback histórico de um argumento. Os ecrãs/testes de outras origens não mudam de comportamento.
- `CollectionModuleScreen` recebe a liga escolhida e atualiza o estado `speciesLeague` antes de navegar para a rota `CollectionRoute.IV_TARGETS`; o retorno mantém `targetsReturnRoute = LIST`.
- `IvTargetsScreen` expõe um identificador semântico de destino para comprovação de liga; não se altera o motor de rankings e não se inventam rankings com base no perfil exploratório.
- **Quatro novos testes Android de interface:** dois validam a entrega explícita Ultra/Master no callback e dois percorrem a navegação completa no shell verdadeiro Coleção → IV Targets → Coleção, verificando liga e preservação da coleção.
- O verificador de relatórios requer no mínimo **62 testes instrumentados Android reais**, sem erros, falhas nem skips. Continuam obrigatórios CI da APK/JVM e a pipeline separada da atualização 58→59.

## Critérios de aceitação
- [ ] Ao selecionar **Ultra**, o cabeçalho de IV Targets identifica **Ultra**.
- [ ] Ao selecionar **Master**, o cabeçalho identifica **Master**.
- [ ] O retorno deixa o utilizador na coleção, com os exemplares guardados.
- [ ] A rota antiga com callback de um argumento continua funcional, nomeadamente os casos de QA anteriores.
- [ ] 62/62 emulador Android 35 e pipeline APK/JVM aprovados; só depois merge.

## Limites
Não declara melhoria da qualidade do ranking de IVs em si, importação direta de Pokémon GO, sincronização de calendário, acessibilidade física de ponta-a-ponta nem conclusão dos 110 cenários UX/E2E da matriz mestre. Os testes avaliam uma jornada concreta num emulador, distinta de testes físicos e de utilizadores principiantes.
