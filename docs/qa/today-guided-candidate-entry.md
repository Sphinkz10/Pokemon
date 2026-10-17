# Hoje → registo guiado → Inbox

## Resultado para o utilizador

Após escolher um objetivo e copiar um filtro, **Registar Pokémon encontrado** abre diretamente o formulário já usado na Coleção. As instruções pedem para consultar a avaliação no Pokémon GO e introduzir a espécie e os três IVs. A avaliação não é obtida automaticamente do jogo.

Voltar no formulário, incluindo o botão Android, regressa à etapa de Hoje que originou o pedido. Submeter mantém o percurso existente de candidatos pendentes na Inbox, sem promover automaticamente um exemplar à Coleção. O atalho anterior para rever a Inbox continua disponível, incluindo quando está vazia.

## Verificações automatizadas incluídas

`TodayCandidateJourneyAndroidTest` usa o app shell real e um repositório Room isolado:

1. Hoje → objetivo IVs → cópia real → Inbox vazia → formulário guiado → voltar para Hoje. Abrir posteriormente a Coleção não repete o pedido consumido; um novo toque em Registar abre um novo pedido. A cópia e o cancelamento não criam registos.
2. Hoje → formulário → espécie e IVs 0/15/15 → submeter. Confirma candidato PENDING, IV zero preservado, CP/nível desconhecidos e coleção vazia. Reabrir Hoje mostra a contagem da mesma Inbox; o botão de revisão abre esse candidato.

Os coletores Compose são desmontados antes de fechar a base de testes. O gate de relatórios passa a exigir os dois casos adicionais, sem substituir os anteriores.

## Estado das provas

- Validação de estrutura dos filtros: **13/13 aprovada localmente**.
- Validação de estrutura dos testes Android: **57/57 aprovada localmente**.
- Compilação local: **APK debug e APK de testes Android gerados com sucesso**.
- Testes JVM locais: **403 aprovados, zero falhas, erros ou ignorados**, confirmados nos relatórios JUnit XML.
- Assinatura do APK local: verificada; pacote de diagnóstico `com.rui.pvpgo.installtestv190`, versão `1.59.0-dev-installtestv190` / código 59.
- CI APK/JVM do código `5a6ba29`: **[aprovado](https://github.com/Sphinkz10/Pokemon/actions/runs/38988449705)**.
- Android instrumentado do código `5a6ba29`: **114/114 casos aprovados no emulador Android 35**, em 20 suites, sem falhas ou ignorados. Os **dois casos de `TodayCandidateJourneyAndroidTest` passaram**, conforme auditoria dos relatórios JUnit.
- Atualização real de APK 58→59 com a mesma assinatura e reinício do processo: **[aprovada](https://github.com/Sphinkz10/Pokemon/actions/runs/38988449832)**, no âmbito do pacote de diagnóstico e dos dados do teste.
- Telefone físico e aceitação visual: em aberto.
- Nenhuma tarefa do Excel nem cenário UX global é fechado por inferência.

O [PR #45](https://github.com/Sphinkz10/Pokemon/pull/45) fica em rascunho para revisão visual e aceitação do produto. A [execução dos 114 casos Android](https://github.com/Sphinkz10/Pokemon/actions/runs/38988449707) foi aprovada. A atualização seguinte deste documento altera apenas documentação; as provas acima identificam explicitamente o commit do código executado. A falha intermitente histórica referida no PR #44 não se reproduziu nesta execução; isto não equivale a integrar a correção separada.
