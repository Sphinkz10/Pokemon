# PR #44 · Estabilizar navegação Android com observadores Room

## Falha reproduzida
A main no commit `d777602` (PR #43) passou o build/JVM e o gate de instalação/upgrade, mas o job Android instrumentado **38957456755** completou **112 testes, 111 aprovados e 1 falhado**.

A classe `CollectionNavigationJourneyAndroidTest`, método `switchingBetweenBottomTabsReturnsToTheSavedCollectionRoute`, lançou:
`IllegalStateException: Cannot perform this operation because the connection pool has been closed`.

Os detalhes JUnit apontam para queries reativas `CollectionDao_Impl.observeCollection` e `observePendingInbox` a trabalhar após o fecho do pool SQLite de teste. A classe tinha `@After { database.close() }` mas o `createComposeRule` ainda mantinha a composição e os `Flow` ativos: a regra Compose limpa a UI **após** o `@After` do teste.

## Correção com impacto delimitado
Apenas alterações de **instrumentação**, sem mexer no código de produção:
- A composição da app no teste está condicionada a `compositionActive`.
- O `@After` faz `rule.runOnIdle { compositionActive = false }` e `rule.waitForIdle()` **antes** de `database.close()`, tudo sob `try/finally` para garantir fecho da BD.
- Mantêm-se as rotas reais, o mesmo `CollectionRepository.forTesting` com Room em memória e os mesmos oito testes de navegação.
- Verificação de fonte no gate Android exige essa ordem e uma espera explícita de idle.

## Segunda falha de QA encontrada durante o PR
Na primeira execução do PR #44, o erro de Room já não reapareceu, mas a suite acusou 1 falha em `OwnedIvCompareComposeAndroidTest`: procurava `iv-owned-rank-a` no top 3 sem garantir que o exemplar "a" ficaria no top 3 quando os resultados fossem realmente ordenados por rank IV. **O teste fazia uma suposição falsa acerca da posição**, não demonstrava erro no ranking.

A suite foi corrigida para carregar os rankings verdadeiros e selecionar explicitamente `Ver os 4 exemplares` antes de procurar o ID "a". Um contrato no CI exige esse fluxo, sem alterar os cálculos reais da aplicação. O merge continua bloqueado até **112/112 testes passarem**.

## Gates para integração
1. A compilação/JVM/APK mantém todos os verificadores verdes.
2. O emulador Android 35 executa **112/112 casos**, incluindo a jornada que falhou na main, sem skips nem erros.
3. O pipeline independente de `adb install -r` e relançamento de processo continua verde.
4. Confirmar `main` sincronizada após o merge.

## Limitações
Não representa certificação integral dos 110 cenários de UX nem uma auditoria de acessibilidade de todos os ecrãs. Não altera o conteúdo funcional do Pokémon Companion; corrige a fiabilidade do respetivo gate Android.
