# PR #27 — Jornada simples para perceber os IVs da coleção

## Problema de UX
A coleção tinha filtros técnicos horizontais (PvP, 100%, Shiny, Targets) sem explicar por onde começar. Um principiante podia interpretar 0/15/15 como garantia universal de bom IV PvP. A nova jornada organiza as ações em três etapas, sem modificar as regras existentes.

## Três etapas sem falsas recomendações
1. **Confirmar dados:** filtra Pokémon com CP, nível ou outro campo por confirmar no Pokémon GO. Uma ausência continua UNKNOWN, nunca zero.
2. **Comparar IVs:** mostra exemplares cuja espécie está no catálogo e liga cada linha ao comparador IV existente por espécie/liga. Esta etapa NÃO afirma que os IVs registados são verificados, nem que 0/15/15 vence todos os outros IVs.
3. **Preparar plano:** mostra apenas exemplares com um plano PvP já guardado; não inventa metas nem custos.

O utilizador pode regressar a Tudo, utilizar a pesquisa e continuar a utilizar os filtros originais. A etapa ativa é guardada por rememberSaveable e o estado é preservado na navegação. A contagem visível acompanha o filtro, a pesquisa e a etapa.

## Critérios Android e JVM
- 9 testes puros que cobrem UNKNOWN, disponibilidade do catálogo, planos guardados e o caráter informativo da comparação.
- 3 novos testes Compose em emulador: rever só desconhecidos; ação de comparar aponta para espécie correta; etapa de plano vazio nunca cria plano fictício.
- O CI de emulador passa dos 52 testes do PR #25 para 55 casos exigidos, com JUnit XML executado e zero falhas/ignorados.
- Os workflows APK/JVM e de emulador são ambos obrigatórios. O teste de atualização assinada do PR #25 permanece como prova independente de retenção da coleção.

## Próximas melhorias UX
Explicar ranks reais calculados por espécie, forma, liga e CP — distinguindo metas PvP de coleção, competição e capacidade de investimento; percurso de importação simples e assistência ao uso de filtros do Pokémon GO. Testes de usabilidade com utilizadores reais continuam por executar.