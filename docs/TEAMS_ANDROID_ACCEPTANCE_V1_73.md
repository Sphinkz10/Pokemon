# Equipas — validação Android real (V1.73)

**Estado: por executar em dispositivo.** A compilação e os testes unitários do GitHub Actions não comprovam persistência Room após reinício, navegação ou acessibilidade real.

## Pré-condições
1. Instalar o artefacto **PokemonPvP-current-main-debug-apk** do último GitHub Actions verde, com o identificador **`com.rui.pvpgo.installtestv158`**, que é deliberadamente diferente do identificador de produção (`com.rui.pvpgo`). Guardar ID da execução, SHA e Android/API do dispositivo. A build de diagnóstico usa uma base de dados própria; **não contém automaticamente a coleção da instalação de produção**.
2. Usar coleção de teste com **pelo menos três Pokémon diferentes** com nível e movimentos completos; confirmar cópia de segurança antes de operações destrutivas.
3. Registar para cada caso: PASS/FAIL, SHA, modelo, Android, evidência (captura ou vídeo), observações.

| Caso | Ação | Resultado esperado | Estado |
|---|---|---|---|
| T01 | Abrir Equipas sem equipas guardadas | Estado vazio e ação para criar | PENDENTE |
| T02 | Criar e guardar equipa válida de 3 Pokémon | Surge na lista com papéis distintos | PENDENTE |
| T03 | Abrir detalhe | Liga, nomes, CP, IV, papéis e notas corretos | PENDENTE |
| T04 | Alterar nome e guardar | Nome atualizado na lista e detalhe | PENDENTE |
| T05 | Fechar app completamente e reabrir | Nome e equipa persistem em Room | PENDENTE |
| T06 | Duplicar equipa | Original mantém-se; cópia com nome sufixado e 3 membros iguais | PENDENTE |
| T07 | Fechar/reabrir após duplicação | Original e cópia continuam presentes | PENDENTE |
| T08 | Eliminar equipa, escolher Cancelar | Nenhuma equipa é eliminada | PENDENTE |
| T09 | Eliminar apenas cópia, confirmar | Cópia desaparece, original permanece | PENDENTE |
| T10 | Abrir Coleção após eliminação | Os 3 Pokémon continuam presentes e inalterados | PENDENTE |
| T11 | Procurar nome inexistente no picker | Mensagem de zero correspondências e Limpar pesquisa | PENDENTE |
| T12 | Limpar pesquisa | Lista elegível reaparece; paginação reinicia | PENDENTE |
| T13 | Alterar orientação/recriar atividade durante pesquisa | Sem crash; pesquisa e picker restaurados | PENDENTE |
| T14 | Forçar falha de persistência em ambiente de teste | Feedback de erro sem falso sucesso | PENDENTE |
| T15 | Testar TalkBack, foco, 200% texto e ecrã estreito | Controlo compreensível, acionável, sem clipping | PENDENTE |

## Critério de fecho
**Não marcar Equipas como validado** enquanto T01–T12 não passarem no dispositivo, T13–T15 não forem analisados e o build testado não estiver associado a um SHA e execução CI verificáveis. Qualquer falha exige issue com passos de reprodução e nova validação.

## Instalação
GitHub → Actions → último workflow Android verde → Artifacts → `PokemonPvP-current-main-debug-apk` → extrair ZIP → instalar `app-debug.apk`. Esta APK de diagnóstico instala-se em paralelo à versão de produção devido ao sufixo `.installtestv158`. Preparar uma coleção de teste nesta instalação. Se já existir outra versão de diagnóstico com assinatura incompatível, **não a desinstalar sem exportar/guardar os dados**: a desinstalação pode apagar a coleção local dessa instalação.
