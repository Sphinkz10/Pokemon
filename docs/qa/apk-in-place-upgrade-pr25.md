# Pokémon Companion V3 — PR25: atualização real da APK sem perda dos dados do jogador

## Bloqueio resolvido nesta fase
Nos PR #20–#24, o Android emulado executou 51 testes (SQLite, recursos, navegação, acessibilidade, layouts), mas **nenhum teste instalou duas versões distintas da aplicação com a mesma assinatura**, preservando a área de dados instalada. O PR25 acrescenta uma pipeline independente e obrigatória em todos os PRs para `main`.

## Upgrade testado
O próprio runner GitHub constrói **duas APKs com uma única debug keystore**:
- **PR24**: fonte exatamente no commit `4d1dbeb739253fbaf0a5625958285328be9ab5a6`, pacote `com.rui.pvpgo.installtestv190`, `versionCode 58`.
- **PR25**: fonte do PR corrente, *mesmo* pacote `com.rui.pvpgo.installtestv190`, `versionCode 59`, `versionName 1.59.0-dev-installtestv190`.
- Confirmar `aapt dump badging` para nomes e versões e `apksigner verify --print-certs` para os certificados SHA-256 idênticos. Não se pode substituir a base por uma APK obtida de outro runner, pois a assinatura seria diferente.

## Execução no emulador API 35
1. `adb install -r baseline.apk` (a v58 fica instalada).
2. Instalar apenas a APK de instrumentação separada v59, que contém o teste do estado prévio.
3. Instrumentação `seedExistingApp`: gravar um exemplar `OwnedPokemon`, equipa, plano PvP, candidato pendente, batalha e 7 recursos (incluindo `0` conhecido e `null` desconhecido), nas bases **de produção** `user.db` e `pvp_user_resources_v1` mas num emulador dedicado.
4. `am force-stop`, abrir e voltar a fechar o processo; uma **nova instrumentação** `verifyPreservedCollectionResources` confirma preservação integral *antes* da atualização.
5. `adb install -r candidate.apk`, **sem `adb uninstall`, sem `pm clear` e sem apagar ficheiros**.
6. Confirmar versão instalada 59 com `dumpsys package`, abrir a nova app, encerrar o processo, e executar uma **terceira instrumentação** a comparar coleção, equipa, plano, inbox, batalha e saldos idênticos. Um valor desconhecido nunca se torna zero.
7. O CI exige a prova explícita dos três testes Android executados em sessões separadas e um log `UPGRADE_END_TO_END_PASS`. Se faltar uma assinatura, se falhar `adb install -r`, se uma linha de dados desaparecer, ou se faltar JUnit, bloqueia o PR.

A suite diária normal `connectedDebugAndroidTest` mantém os 51 testes e exclui intencionalmente esta classe especial. Executar sozinho o `seedExistingApp` no conjunto regular ou executar `verifyPreservedCollectionResources` numa instalação limpa poderia contaminar dados e criar resultados enganosos.

## Limites honestos
- O teste cobre `versionCode 58 → 59` e **a mesma schema Room v2** (sem migração nova). Não certifica futuras migrações `v2 → v3`.
- `am force-stop` é um encerramento real do processo seguido de relançamento, mas não simula a escolha exata que o gestor de memória Android faz ao matar uma app sem forçar o estado.
- A assinatura é a debug key **partilhada no mesmo runner**. As APKs de dois jobs diferentes podem ser assinadas com chaves diferentes. Esta prova não autoriza instalar uma APK arbitrária sobre a instalação pessoal.
- É uma prova de dados e compatibilidade de instalação no **Android emulado API 35**: não equivale a teste físico Android 13–16, nem valida acessibilidade total, desempenho ou os 110 E2E UX globais.
- O app continua marcado como **diagnóstico**, não publicação em loja.

## Próximas fases
1. Instalar no dispositivo do utilizador com backup e assinatura coerente antes de migrar versões antigas.
2. Automatizar exportação/backup antes de cada upgrade e um procedimento documentado de rollback sem apagar dados.
3. Passar para UX: filtros IV práticos guiados para quem não domina Pokémon GO, coleção até 1000+, progressão por fases e recomendações úteis de eventos.
