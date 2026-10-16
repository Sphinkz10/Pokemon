# Pokémon Companion v3 · PR41 — Revisão final dos candidatos de IVs na Inbox

## Porquê
A jornada guiada do PR39 já permitia:
**Coleção → Comparar IVs → copiar filtro Pokémon GO → registar espécie e IVs confirmados → candidato PENDING**.
Os testes Android desse PR terminavam antes de o utilizador escolher **Guardar** ou **Descartar**. Não demonstravam o destino nem a integridade da informação após a decisão final.

## Alterações
- `InboxScreen` ganhou tags semânticas **por ID de candidato**: `inbox-candidate-{id}`, `inbox-preview-{id}`, `inbox-confirm-{id}`, `inbox-discard-{id}`. Evita ambiguidades ao testar listas com várias candidaturas; sem alterar desenho, persistência ou decisões da app.
- Mais **três ensaios Android instrumentados** na aplicação real com repositório **Room isolado** e espécie de teste controlada; cada ensaio começa com a jornada completa de captura manual e submissão à Inbox, sem inserir candidatos diretamente por testes:
  1. **IV preview**: abrir e fechar ranking não cria nenhum Pokémon nem elimina candidatura.
  2. **Descartar**: tocar no botão remove da lista PENDING, regista `DISCARDED`, não cria qualquer elemento em `owned_pokemon`, sem falso vínculo.
  3. **Confirmar**: tocar em **Guardar** promove apenas aquele candidato a `OwnedPokemon`, preserva ID/espécie/IV 0/15/15, regista `CONFIRMED_NEW` com vínculo ao exemplar confirmado, e abre **Pokémon 360** real.
- A suite de emulador Android API35 passa a exigir **100/100** testes executados com JUnit XML genuíno (97 anteriores + 3), sem falhas, erros ou skips.
- A pipeline de assinatura e upgrade real **v58→v59** continua independente e obrigatória para alterações `app/**`.

## Aceitação e limites
Um botão que chama o método correto no Kotlin não basta; as transições e os registos são verificados no SQLite **depois** de carregar nesse botão no Android. Não fazemos importação silenciosa a partir do clipboard nem confirmamos um candidato sem ação do utilizador.

Isto comprova três percursos representativos da Inbox, mas não valida uma coleção real com milhares de candidatos, a precisão das IVs observadas no Pokémon GO, o desempenho em aparelhos físicos ou todos os **110 cenários UX/E2E** do roadmap original. A cobertura instrumentada é reportada separadamente até cada cenário histórico ser mapeado.
