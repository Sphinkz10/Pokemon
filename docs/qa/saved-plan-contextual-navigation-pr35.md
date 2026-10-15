# PR #35 — Resolver a próxima ação na secção certa do Pokémon 360

## Lacuna de usabilidade encontrada
Na etapa **3 · Preparar plano**, o PR #34 já explicava "Ação seguinte" com base nos dados guardados, mas o botão **Resolver no Pokémon 360 →** utilizava apenas `onSelectOwned(id)`: abria a espécie e os exemplares de forma genérica, não garantia a **liga guardada no plano** e não levava diretamente ao editor da tarefa apresentada.

Em particular, um plano **Ultra** podia conduzir ao ecrã 360 inicialmente na liga **Great**, com uma procura manual posterior pelo campo relevante. Isso aumentava erros e tornava a jornada pouco intuitiva.

## Implementado
- A lista mantém o caminho genérico de abertura do exemplar, mas o botão de próxima ação agora envia `ownedId`, **`savedPlan.league` real**, e o `SavedPlanNextStep` calculado.
- O router do módulo Coleção rejeita IDs/especies ou planos incoerentes e só navega quando o exemplar e um plano com essa liga constam no Room.
- Abre diretamente o **Pokémon 360** do mesmo exemplar, devolvendo depois ao **guia da coleção**, sem adicionar/substituir Pokémon.
- Mantém a liga de contexto no seletor do editor, mostra subtítulo contextual (ex.: **Ultra · Confirmar ataques**) e desloca a lista para o editor relevante:
  - Espécie → cabeçalho de identidade.
  - Incompatibilidade CP/limite → seletor de liga.
  - CP/nível por confirmar → campos do Build real.
  - Movimentos por confirmar → editor de ataques reais.
  - Stardust/Candy/XL em falta ou alvo por verificar → orçamento/alvo.
  - Plano arquivado/para rever → editor do plano.
- A posição na `LazyColumn` é definida por `SavedPlanFocusPolicy` e limitada ao intervalo válido. A fase de revisão de plano usa índice **relativo ao fim**, não presume número fixo de ataques na lista.
- Nenhuma ação escreve dados ou altera os recursos; apenas desloca o ecrã.

## Gates de teste
- **10 testes JVM** de política de destino, limites de índices e rótulos.
- **3 testes Android Compose reais**: callback com a liga e tarefa corretas, abrir plano Ultra/ataques no 360 e regressar sem mutação, abrir plano Master com CP desconhecido no campo correto sem inventar CP.
- Gate emulador passa de **78 para 81 testes Android executados** em oito/mais suites, todos comprovados por JUnit XML, sem falhas ou skips.
- CI Gradle e verificadores existentes são obrigatórios antes do merge.

## Limitações
Os dados continuam a ser introduzidos pelo utilizador; selecionar secção não garante informação do Pokémon GO, rank competitivo, evolução elegível ou orçamento atual. Os 110 cenários E2E globais não são considerados concluídos automaticamente. Melhorias pendentes incluem testar TalkBack na transição guiada, animação/foco após mudança de layout e casos de múltiplos planos guardados para a mesma espécie.

## Critério de pronto
Quando um utilizador carrega em **Resolver no Pokémon 360**, deve aterrar no exemplar correto, na liga do plano guardado, com a secção da tarefa identificada e sem qualquer gravação ou gasto automático.
