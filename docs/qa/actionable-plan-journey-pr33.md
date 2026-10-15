# PR #33 — Fechar a etapa 3 do guia: criar plano PvP sem becos sem saída

## Problema comprovado
As etapas **Confirmar → Comparar IVs → Preparar plano** já eram navegáveis, mas **Preparar plano** mostrava apenas Pokémon com um plano previamente criado. Um jogador sem planos via **0 exemplares** e a única ação era **limpar filtros**, regressando à coleção sem saber como criar o primeiro plano.

## Mudanças na aplicação
- A etapa 3 continua a indicar **só planos realmente guardados** no contador e na lista "POKÉMON": nunca conta como plano um exemplar que ainda não foi planeado.
- Acrescenta uma segunda lista, lazy e ordenada, dos Pokémon **sem plano** com espécie conhecida no catálogo e o botão **Criar plano · [Pokémon] · [liga] →**.
- O jogador escolhe Great, Ultra, Little ou Master **na própria etapa 3**. A liga escolhida acompanha o exemplar até ao editor.
- O botão abre diretamente o Pokémon 360 **do exemplar exato**, no **editor do Plano PvP**, evitando passar primeiro pela página da espécie e percorrer manualmente todos os campos.
- **Não há gravação implícita:** só `Criar plano PvP` no editor grava um `PvpBuildPlan` real na base Room. Não muda CP, nível, ataques reais ou saldo de recursos.
- Ao regressar, o plano verdadeiramente guardado surge na etapa 3 e o exemplar deixa de aparecer como "por planear". Pode ser editado por tap na linha normal.
- Exemplares sem espécies no catálogo não recebem CTA que conduza a um editor não contextualizado. Permanecem na coleção com o seu estado real.

## Aceitação — Android real API 35
| Caso | Verificação |
|---|---|
| PLAN01 | Com zero planos, o guia continua a mostrar zero planos guardados **mas permite iniciar um** |
| PLAN02 | A liga escolhida (Master) chega ao callback do exemplar correto sem alterar o Pokémon |
| PLAN03 | Quando o plano existe, o exemplar é contado como planeado, não volta a ser oferecido como criação |
| PLAN04 | Percurso completo: Coleção → etapa 3 → Pokémon 360 → botão **Criar plano**; antes do clique a base Room está vazia, depois contém o plano do mesmo exemplar, e não muda nível/CP |

**Gate CI:** 70 casos instrumentados anteriores + 4 novos = **74 testes Android no emulador**, todas as suites exigidas no XML JUnit sem falhas ou ignorados. O build APK/JVM e os testes antigos permanecem obrigatórios.

## Limites
- Isto cria uma **intenção de plano PvP**; não recomenda automaticamente que o Pokémon é meta/competitivo.
- Não deduz que 0/15/15 é universalmente o melhor IV e não inventa CP, nível, Candy ou Stardust desconhecidos.
- Não realiza power-up no Pokémon GO e não modifica a coleção sem uma ação explícita de gravação.
- Não fecha automaticamente os 110 cenários globais de auditoria UX/E2E que ainda exigem mapeamento/validação específica.

## Próximo incremento
Enriquecer o plano guardado com **próxima ação prática**, indicação clara do que ainda é desconhecido, e aviso quando não existem recursos confirmados suficientes — sem bloquear criação de rascunhos.
