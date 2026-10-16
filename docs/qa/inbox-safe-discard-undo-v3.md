# Pokémon Companion V3 — PR #43 · Descarte reversível na Inbox

## Problema observado
Até ao PR #42 a Inbox já tinha pesquisa e filtros (correspondências prováveis, sem correspondência, campos por completar), mas **um toque em “Descartar” marcava imediatamente o candidato como descartado**. Não existia confirmação, nem maneira de recuperar o candidato na interface. Isto tornava fácil perder tempo a reintroduzir IVs por engano — sobretudo em ecrã pequeno.

## Comportamento implementado
1. O botão **Descartar** apenas abre uma caixa de confirmação com **nome, IVs** e consequências.
2. **Cancelar**, botão voltar ou tocar fora fecha a confirmação sem gravar nenhuma alteração.
3. **Descartar candidato** muda apenas o estado do registo na Inbox de `PENDING` para `DISCARDED`. Não elimina a linha, não transfere Pokémon GO, não altera a coleção.
4. A interface mostra um snackbar **Desfazer** após confirmação. Tocar em Desfazer executa `CollectionRepository.restoreDiscarded(inboxId)`.
5. **Desfazer** executa uma transação Room e só aceita registos `DISCARDED`, sem exemplar já associado. Repõe o mesmo ID de Inbox a `PENDING`, apaga timestamp de revisão e preserva os IVs, CP desconhecido, nível e nome original.
6. Se o snackbar expirar ou o utilizador sair do ecrã, não ocorre um restauro automático; o registo permanece **DISCARDED**. Uma interface persistente para histórico/restauro posterior é uma melhoria futura distinta.
7. O antigo fluxo de confirmação de Pokémon continua explícito e separado da ação de descarte.

## Testes Android no PR #43
- Inbox real Compose com Room: clicar em Descartar não muda dados até confirmação, **Cancelar** não muda dados, confirmação muda só a linha correta, **Desfazer** restaura esse mesmo candidato e não cria exemplar na coleção.
- 5 novos testes Room: restauro do registo original com campos desconhecidos, recusa antes do descarte, recusa depois de confirmado, recusa de segundo Desfazer e recusa quando a identidade já existe na coleção.
- Não se remove nem substitui nenhum teste anterior. `tests/v3_android_emulator/verify_instrumentation.py` exige relatório JUnit real das novas suites e nenhuma falha/skip.

## Limites
- Os filtros `POSSIBLE_MATCHES` são indícios de reconciliação, **não prova de que sejam o mesmo Pokémon**. A fusão automática continua bloqueada.
- O botão Desfazer só aparece durante a mensagem temporária; recuperação futura pelo histórico fica aberta.
- O PR não faz gestão real de Pokémon GO nem verifica metas PvP ao vivo.
- Estes casos não fecham automaticamente os 110 cenários de UX do roadmap histórico.

## Aceitação
- [ ] CI APK/JVM + verificadores estáticos verde.
- [ ] Suite emulador Android API35 com todos os novos casos aprovados no JUnit XML.
- [ ] Integração apenas após os dois gates.
