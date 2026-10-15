# PR #31 — Saída prática dos filtros sem resultados na Coleção

## Problema observado
O guia da Coleção passou a ajudar na escolha de uma liga e nos filtros CP/IV (PRs #27–#30). Algumas combinações legítimas, como **Master → acima do teto de CP** (que não existe), **sem planos guardados**, ou uma pesquisa sem correspondências, deixavam a lista vazia com instruções vagas e sem recuperação imediata.

## Alteração visual e de fluxo
- Se existem Pokémon guardados e **0 resultados visíveis**, o próprio guia mostra **“Nenhum exemplar corresponde a esta seleção”** e botão **“Limpar filtros e mostrar todos”**.
- O estado vazio da lista apresenta também **“LIMPAR SELEÇÃO”**, que executa a mesma recuperação.
- Um clique repõe **pesquisa, filtro rápido, etapa e verificação extra CP/IV**. O utilizador conserva a liga que escolheu, a ordenação e o contexto independente do Pokédex.
- **Não são apagados, alterados nem reclassificados Pokémon.** Não se afirma que IVs/CP filtrados representam baixa qualidade competitiva. O resultado vazio só descreve a seleção corrente.
- Sem Pokémon guardados, o CTA continua a ser **“ADICIONAR POKÉMON”**, não o reset sem utilidade.
- Os controlos possuem identificadores Compose para QA e acessibilidade: `collection-guide-empty-explanation`, `collection-guide-reset-empty`.

## Três testes Android instrumentados novos
| Caso | Ação real | Verificação |
| --- | --- | --- |
| REC01 | Comparar → Master → acima do teto | 0 resultados → limpar → recuperar três exemplares sem alterar dados |
| REC02 | Preparar plano sem planos guardados | 0 resultados → limpar → escolher exemplar real |
| REC03 | Comparação com resultados válidos | O botão de recuperação não aparece indevidamente |

Os novos casos reutilizam **CollectionOverviewScreen de produção** e o conjunto de três exemplares já validado nos PRs anteriores. O gate de execução no emulador aumenta de **62 para 65 testes Android** (Junit XML, zero falhas ou ignorados). O pipeline JVM/APK também é obrigatório.

## Qualidade e limites
Isto remove um beco sem saída no percurso de escolha, mas não executa automaticamente os **110 cenários UX/E2E globais** e não comprova que o utilizador sabe interpretar IVs sem explicação adicional. Próximas tarefas de usabilidade: testar com utilizadores não especialistas, estabelecer ranking IV e recomendações condicionadas às copas e eventos com dados verificados, validar fluxo de importação e capturar capturas de ecrã/contraste com fontes grandes.
