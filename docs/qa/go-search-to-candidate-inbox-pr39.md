# Pokémon Companion V3 — PR #39 · Fechar a passagem do filtro Pokémon GO para a Inbox

## Problema
O PR #38 permite copiar uma pesquisa de avaliação IV do Pokémon GO. Ao regressar à Companion, o utilizador tinha de abandonar o painel do filtro e procurar sozinho o caminho de adição de candidatos. Era uma quebra da jornada que deve ser simples mesmo para quem não conhece IVs.

## Percurso de utilização real
1. Abrir **Coleção → 2 · Comparar IVs** e selecionar a liga.
2. Escolher um dos perfis de IV, copiar a pesquisa e colar na lupa do Pokémon GO.
3. Confirmar **no jogo** os IVs exatos do exemplar encontrado.
4. Regressar ao **mesmo painel** da Companion e tocar **Registar candidato encontrado**.
5. O botão abre o **formulário guiado existente**, não cria uma importação automática. Selecionar espécie e introduzir os três IVs reais; CP, nível e restantes campos ficam opcionais.
6. **Rever antes de guardar** envia apenas um candidato para a Inbox; não cria um Pokémon confirmado na coleção.
7. A **Inbox** oferece a revisão manual e confirmação explícita. O painel também inclui **Rever candidatos na Inbox** para os que já foram registados.
8. Voltar do formulário sem gravar regressa à coleção e não altera os dados.

## Segurança e integridade
- Nada lê a conta do Pokémon GO, acede à sua coleção nem deriva valores dos filtros copiados.
- Copiar para o clipboard **não** importa nem confirma um Pokémon.
- Um filtro com intervalo de avaliação não fornece IV exatos nem classificação PvP.
- O formulário exige os três IVs preenchidos pelo utilizador; as validações e reconciliação existentes continuam a ser reutilizadas.
- O candidato passa pela **Inbox Room real** antes de poder entrar na coleção. A Inbox nunca promove automaticamente candidatos.
- A navegação de retorno e a etapa atual da coleção são preservadas.
- Não foi criado um segundo wizard ou um motor paralelo.

## Provas de QA (emulador Android 35)
A suite `PokemonGoCandidateHandoffAndroidTest` executa 4 casos de ponta a ponta com `PvPGoApp` verdadeiro e Room isolado:
- Painel mostra ações e copiar filtro não altera coleção/Inbox.
- Ação de registo abre formulário guiado; regressar mantém a etapa.
- Ação Inbox abre estado real vazio e não fabrica capturas.
- Escrever espécie e 0/15/15, enviar, confirmar que existe **um pendente** com os IVs certos mas **zero Pokémon confirmados**.

Gate total: 93 testes Android anteriores + 4 novos = **97 testes instrumentados**, todos no JUnit XML gerado pelo emulador. A pipeline JVM/APK mantém todas as verificações estruturais e de segurança.

## Limitações
Os testes não demonstram que o Pokémon GO aceita todos os filtros em cada localização, nem que a Companion tem acesso aos Pokémon detetados. A revisão explícita do candidato e inclusão como exemplar confirmado é comportamento da Inbox existente, já coberto por outras fases. Os 110 cenários UX globais continuam separados e não podem ser dados como completos por extrapolação.
