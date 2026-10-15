# Pokémon Companion v3 — PR #29: classificação transparente por liga dentro do guia de IVs

## Porquê um único guia
O PR #27 já disponibilizou uma jornada progressiva: **1 Confirmar → 2 Comparar IVs → 3 Preparar plano**. Não devemos apresentar um segundo guia independente na mesma página. Por isso, esta fase **complementa a etapa 2** com escolha de liga e um filtro de verificação CP/IV, inicialmente **Todos** para preservar o comportamento anterior.

## Jornada
1. Abrir **Coleção → 2 · Comparar IVs**.
2. Manter **Todos** (padrão), ou escolher **Little / Great / Ultra / Master**.
3. Selecionar um filtro opcional:
   - **Todos:** mantém a comparação por espécie sem excluir exemplares.
   - **Confirmar:** CP/nível em falta, incoerente com espécie e IVs ou campos incertos.
   - **Cabe na liga:** CP atual consistente dentro do limite geral; **não** afirma rank, moves ou competitividade.
   - **Acima do limite:** CP atual excede o limite; não pode ser reduzido com power-ups.
   - **Perfil de IV:** heurística explicitamente limitada (Little/Great/Ultra ataque 0–5 + def/HP 12–15; Master 15 ataque + def/HP 13–15), **não é ranking** e há muitas exceções.
4. Ver quantos exemplares correspondem ao filtro e a explicação do motivo.
5. Abrir **Comparar IVs por liga** para consultar o ranking calculado por espécie no IV Targets existente. A seleção de liga para rank é confirmada no ecrã IV Targets, explicitamente indicada na interface.

## Integridade e limites
- A coleção mantém-se imutável. Esta melhoria não modifica CP, IVs, espécies, equipas nem recursos.
- A validação do CP verifica espécie e forma exatas, nível até 50, correspondência com PokemonMath.cp e a inexistência de campos pendentes.
- `Master` não tem teto geral de CP; isso não é indicação de força no meta. A validação das copas especiais continua separada.
- Qualquer informação desconhecida ou incoerente é encaminhada para **Confirmar**, não tratada como zero nem como um falso rank.
- A pesquisa, filtros rápidos, Pokédex, botão de comparação e três etapas já existentes são preservados.

## Provas técnicas
- `PvpGuidePolicyTest`: 16 testes Kotlin/JVM de classificação de CP, UNKNOWN, níveis, Master, outros tetos e perfil exploratório.
- `PvpGuidedCollectionComposeAndroidTest`: 3 interações no ecrã **CollectionOverviewScreen** real, emulador Android 35: filtro REVIEW, mudança de liga/teto, perfil exploratório + abertura do comparador IV existente.
- Garante, em conjunto com a suite do PR #27, o mínimo de **58/58 testes Android instrumentados**, validados por JUnit XML real. Workflow JVM/APK também obrigatório.
- O workflow separado do PR #25 mantém a prova de atualização da APK v58→v59 com a mesma assinatura.
- Não se declara automaticamente que os 110 cenários globais UX/E2E foram concluídos.

## Próximo
Validar com jogadores principiantes, ligar a liga selecionada automaticamente ao comparador e iniciar uma jornada de pesquisa/importação dos exemplares a partir de Pokémon GO para quem não domina IVs.
