# Pokémon Companion v3 — PR #26 · Guia de triagem PvP em duas etapas

## O problema resolvido
O filtro anterior "PvP" dependia de categorias internas e não ensinava o utilizador a escolher Pokémon úteis. A coleção passa a oferecer um guia **opcional** e compreensível por fases, diretamente sobre os exemplares reais. O modo antigo mantém-se disponível e é o padrão ao abrir a app.

## Jornada proposta
1. Na **Coleção**, premir **"Como escolher Pokémon para PvP? Abrir guia"**.
2. **Escolher liga:** Little / Great / Ultra / Master. O filtro usa os tetos **gerais** de CP da engine, não regras de copas especiais.
3. **Escolher fase:**
   - **1 · Confirmar**: CP/nível em falta ou incoerentes, forma não encontrada ou campos incertos. Estes exemplares não entram silenciosamente em recomendações.
   - **2 · Cabe na liga**: CP atual matematicamente consistente e dentro do teto geral. Sem promessas de desempenho em batalhas.
   - **3 · Acima do limite**: o CP atual excede o teto da liga; um power-up não pode reduzir CP.
   - **4 · Perfil de IV**: heurística exploratória transparente; Little/Great/Ultra: ataque 0–5 e defesa/HP 12–15; Master: ataque 15 e defesa/HP 13–15. **Não é classificação IV** — o rank real continua no módulo IV Targets por espécie e há muitas exceções.
4. Ver contagem e explicação; abrir qualquer exemplar para confirmar dados no Pokémon 360.
5. **Fechar guia** para recuperar a coleção sem filtragem adicional. A pesquisa por nome, se existente, continua aplicada e isso é comunicado.

## Integridade e limites
- Sem apagar, alterar, classificar automaticamente como "bom" ou gastar recursos. O guia só filtra exemplares guardados.
- Para entrar nas fases de CP e perfil, é obrigatório ter espécie/formulário do catálogo correspondente, CP, nível 1–50, zero campos incertos e CP confirmado contra PokemonMath.cp.
- O guia não confirma meta, moves, cup eligibility, evolução, autenticidade dos dados do Pokémon GO ou prioridade real de investimento. "Cabe na liga" **não é recomendação de equipa**.
- Tarefas marcadas como UNKNOWN continuam por confirmar; não são interpretadas como zero.
- A interface oferece um fluxo previsível sem conhecer IVs técnicos.

## Provas de código
- `PvpGuidePolicyTest` — **16 testes JVM** para CP incoerente, campos em falta, mudança de liga, acima do limite, perfil exploratório, Master sem teto e imutabilidade.
- `PvpGuidedCollectionComposeAndroidTest` — **3 testes Android instrumentados** sobre o ecrã real: ativação voluntária, seleção liga/fase com alteração de resultados e retorno à coleção.
- Testes Android anteriores: 52. Nova bateria obrigatória: **55/55** testes Android com JUnit XML real, zero falhas/skips, juntamente com CI JVM/APK.
- A melhoria de UX precisa ainda de validação com utilizadores reais, diferentes coleções, treino da jornada e tarefas do roadmap, não bastando apenas CI.

## Próximos passos
- No ecrã real de IV Targets, oferecer um acesso mais direto por espécie a partir do guia e explicar ranking por `stat product`.
- Adicionar pesquisa orientada pelos próprios filtros do Pokémon GO e uma jornada de importação escalonada para quem ainda não tem coleção.
- Testar com utilizadores principiantes e medir clareza das instruções, tempo até identificar um exemplar e erros por fase.
