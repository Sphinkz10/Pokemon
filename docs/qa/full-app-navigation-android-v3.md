# Pokémon Companion v3 · Navegação em Android real — PR #22

## Problema resolvido
O PR #21 executou 10 testes de campos e botões isolados no Jetpack Compose, mas ainda não demonstrava que o utilizador consegue percorrer **Hoje → Coleção → Espécie → Pokémon 360** e regressar sem perder ou modificar dados. Este PR testa o **shell real da aplicação**, o módulo real da coleção, e os controlos reais sobre uma base SQLite Room em memória.

## Implementação e segurança
- `PvPGoApp` aceita opcionalmente um catálogo de teste e `CollectionRepository` isolado. Na produção, ambos os argumentos ficam `null` e a aplicação carrega exatamente as mesmas fontes e bases anteriores.
- A injeção de um catálogo de teste desativa apenas as cargas remotas e de eventos nessa instância de teste; não cria ecrãs fictícios. Os estados e rotas efetivos, os ecrãs, a renderização e os cliques são de produção.
- `CollectionRepository.forTesting` usa Room real `inMemoryDatabaseBuilder`, não reconfigura o singleton da coleção do utilizador.
- `Modifier.testTag` identifica a navegação de separadores, a linha do Pokémon na coleção, o cartão de espécie, o botão voltar e o editor CP do Pokémon 360.
- O teste nunca mexe na base de produção; ao sair de um exemplar com alterações não guardadas, verifica que o CP e nível no SQLite permanecem inalterados.

## Casos instrumentados (Android 35)
| ID | Jornada | Resultado requerido |
| --- | --- | --- |
| NAV01 | Hoje → Coleção → espécie → exemplar | Abre Pokémon 360 e chega ao simulador de nível |
| NAV02 | Pokémon 360 → espécie → coleção | Voltar funciona e o exemplar permanece no Room |
| NAV03 | Alterar CP sem guardar → Voltar → Descartar | Pede confirmação e mantém o CP/nível anteriores no Room |
| NAV04 | Alterar CP → Voltar → Continuar a editar | Fecha confirmação e mantém detalhe sem alterar o Room |
| NAV05 | Alternar Hoje/Coleção → abrir Pokémon | Mantém coleção visível e permite nova entrada no fluxo |

## Gates de CI
O script `tests/v3_android_emulator/verify_instrumentation.py` exige **34 testes Android instrumentados no total**: 19 persistência/engine/arranque, 10 Compose de componentes, e 5 novas jornadas de navegação no shell. A prova é sempre o XML JUnit da execução no emulador e zero casos falhados ou ignorados. Continua igualmente obrigatório o workflow APK/JVM.

## Limitações
As jornadas são sobre uma base de testes com um exemplar e uma espécie injetados, não sobre uma coleção real preenchida com milhares de Pokémon, nem verificam desempenho/a11y em dispositivo físico. O conjunto de **110 cenários UX/E2E históricos não é promovido automaticamente a concluído**, salvo reconciliação formal e rastreável das identidades e dos critérios desses cenários.
