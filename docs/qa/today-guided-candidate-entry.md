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
- Compilação e testes JVM: em verificação; registar o resultado final antes de considerar o lote validado.
- Android instrumentado: ainda sem resultado executado nesta retoma. A existência do teste e a sua compilação não demonstram que passou num emulador.
- Telefone físico e aceitação visual: em aberto.
- Nenhuma tarefa do Excel nem cenário UX global é fechado por inferência.
