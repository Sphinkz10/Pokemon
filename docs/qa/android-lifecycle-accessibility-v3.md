# Pokémon Companion V3 — PR #23: ciclo de vida, orientação, leitura acessível e rascunhos

## Problema
A suite Android do PR #22 (34/34) validou navegação, mas não provou o comportamento do **MainActivity de produção** após recriação, passagem a segundo plano ou rotação. A barra inferior tinha só uma aparência de seleção; o clique Compose não expunha `Role.Tab`, o estado `selected` nem um rótulo limpo ao leitor de ecrã. O formulário de recursos usava `remember` para os números ainda não guardados e podia perder o rascunho ao recriar o ecrã.

## Código de produção corrigido
- A barra inferior usa `Modifier.selectable(selected, role = Role.Tab)` e um `contentDescription` legível (Hoje, Coleção, Equipas, Batalhas, Mais), preservando a lógica e as dimensões atuais.
- O formulário `UserResourcePanel` utiliza um `Saver` explícito sobre os sete campos de texto: serializa valores para uma lista ordenada de strings suportada pelo `Bundle`, e recupera as chaves `ResourceBalanceField` depois da recriação. Campos em branco continuam `UNKNOWN`, `0` continua zero intencional.
- Os dados rascunhados **não são escritos em SharedPreferences** até o utilizador premir **Guardar recursos**. O campo guardado pelo utilizador continua a ser a única origem de verdade do inventário.

## Instrumentação Android acrescentada
| ID | Teste | Resultado |
|---|---|---|
| LIFE01 | Separadores da `MainActivity` real | Rótulos TalkBack, semântica `Role.Tab` e seleção/exclusão corretas |
| LIFE02 | Selecionar Coleção, `ActivityScenario.recreate()` | A seleção permanece em Coleção |
| LIFE03 | Coleção → app em segundo plano → primeiro plano | Mantém seleção e atividade válida |
| LIFE04 | Coleção → orientação landscape real | Android configura landscape e conserva o separador |
| LIFE05 | Preencher Stardust e Candy e emular recuperação de estado Compose | O rascunho subsiste, o armazenamento real permanece vazio até guardar |
| LIFE06 | Aumentar fonte Compose para 200% e editar inventário | Campos acessíveis por scroll e guardar saldos efetivo |

**Gate automatizado:** 34 casos prévios + seis novos = **40/40 casos instrumentados num emulador Android API 35**, seis sem regressão de funcionamento; CI exige XML JUnit sem falhas, erros ou skips. Compilação APK/JVM continua obrigatória.

## Provas e limites
- `ActivityScenario.recreate()` testa recriação verdadeira de atividade; `StateRestorationTester` testa restauração do estado Compose, mas não equivale a terminar o processo Android pelo sistema.
- A mudança landscape efetivamente usa `requestedOrientation` no emulador, não um mock estático.
- O teste de 200% substitui `LocalDensity.fontScale` num componente real; ainda falta validar as definições globais de tamanho de letra e acessibilidade com TalkBack num telemóvel físico.
- Não certifica navegação em tablet, redução para 320dp, dobráveis, sistema Android 16, upgrades assinados com a mesma chave, nem os 110 cenários UX E2E globais.
- A contagem global de tarefas só deve subir quando o DoD de cada uma estiver realmente demonstrado, não porque seis testes passaram.

## Próximo
Matriz de configurações (largura 320/390/tablet, escala 100–200%), acessibilidade de todos os botões do Pokémon 360, estado após morte de processo, upgrade de base persistida de uma APK para outra com a mesma assinatura, e testes físicos Android 13–16.
