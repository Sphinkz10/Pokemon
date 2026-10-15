# Pokémon Companion v3 — Maior nível atingível com os recursos registados (PR #19)

## Problema do utilizador
Até ao PR #18, o Pokémon 360 conseguia indicar o nível CP máximo da liga e o custo de chegar lá. O utilizador tinha de experimentar vários níveis para responder a uma pergunta mais prática: **até onde consigo subir já, com os recursos que tenho registados?**

## Experiência implementada
No Pokémon 360, a secção **Até onde chegam os meus recursos?**:
1. Usa o exemplar guardado (espécie, forma, IVs, CP e nível), sem inventar dados.
2. Resolve o nível mais alto dentro do limite genérico da liga com `LeagueCapTargetPolicy.suggest`, sem permitir descidas de nível.
3. Exige o consentimento explícito e não pré-selecionado para a tabela comunitária datada.
4. Exige os sete saldos registados e o Candy/XL Candy correspondentes à família evolutiva atual.
5. Valida a proveniência, o hash pinado e a integridade do pack `CommunityNormalPowerUpPack`. Lucky, Shadow, Purified, Mega, Primal e formas de custos especiais permanecem excluídas.
6. Consulta `InvestmentTargetSimulator` e `InvestmentCostQuoteGate` para cada nível experimentado, com o `InvestmentPlanner` real a avaliar custo e recursos.
7. Usa pesquisa binária sobre incrementos de meio nível. Se a tabela de qualquer nível consultado tiver custo desconhecido, não apresenta um “nível aproximado”.
8. Mostra o maior nível pagável, CP resultante, incrementos, custo acumulado estimado e os recursos em falta para o próximo incremento e para o nível máximo.
9. **Usar o nível que consigo pagar** altera só a escolha temporária do nível objetivo; não grava nem altera Pokémon, inventário, movimentos ou planos.

A autorização da tabela comunitária é partilhada com a estimativa manual; não surgem duas caixas para a mesma opção. A consulta é local e não consome recursos. Os saldos são declarações do utilizador e podem estar desatualizados; não vêm do jogo.

## O que NÃO está implementado
- Não é uma previsão de utilidade competitiva, rank de meta, vencedor provável ou recomendação para gastar recursos.
- Não abrange evolução, ataques, desbloqueio de segunda charged move, Candy fora desta família, descontos Lucky, modificadores Shadow/Purified ou custos especiais.
- Não certifica regras de copas específicas ou eventos, Best Buddy nível >50, Pokémon GO atualizações de servidores ou preço oficial.
- Não executa investimento no jogo, não altera a conta e não reserva recursos no inventário.

## Testes automáticos
- 28 novos testes Kotlin/JVM para gate por consentimento, pacote alterado, proveniência falsa, CP e nível inválidos, saldos incompletos, família errada, compatibilidade das formas, recursos exatos e insuficientes, fronteira XL 40, zero incrementos, limite de liga, não mutação e explicação de limitações.
- Verificador estrutural `tests/v3_affordable_level/verify_affordable.py` no workflow Android.
- Deve continuar verde o conjunto pré-existente de todos os PRs #11–#18, a compilação APK e assinatura.

## Aceitação Android — NÃO executada
| ID | Ação | Resultado esperado |
| --- | --- | --- |
| A01 | Abrir Pokémon 360 de espécime com CP e nível | Informação correta sem estimativa automática por defeito |
| A02 | Utilizador não autoriza fonte comunitária | O nível pagável não aparece |
| A03 | Introduzir os sete saldos e aceitar a fonte | Ver nível pagável e custo acumulado |
| A04 | 20→40 com recursos suficientes | Alcançar nível 40, custo correto |
| A05 | 20→50, 0 XL Candy | Parar em 40, mostrar falta de XL para o próximo incremento |
| A06 | 20→50, 0 Stardust | Manter o nível atual sem inventar compra |
| A07 | Stardust suficiente, 0 regular Candy | Parar no nível atual |
| A08 | Inventário de outra família | Não emprestar Candy de outro Pokémon |
| A09 | Campo XL vazio | Custos/nível pagável bloqueados, nunca usar zero implícito |
| A10 | Guardar saldo atualizado | Nível pagável recalculado |
| A11 | Modificar CP/nivel ainda por guardar | Suspender sugestão e pedir para guardar |
| A12 | Usar nível pagável | Alterar somente input do nível objetivo |
| A13 | Usar nível máximo | Mantém a escolha independente do saldo do jogador |
| A14 | Trocar Great/Ultra/Master | Recalcular teto por limite próprio de CP |
| A15 | Pokémon já acima do CP limite | Sem recomendar descer de nível |
| A16 | Pokémon já nível 50 (Master) | Zero incrementos e nenhum gasto |
| A17 | Lucky, Shadow, Purified, Mega ou Primal | Sem aplicar a tabela normal |
| A18 | Ecrã pequeno, TalkBack, letras grandes | Botões/caveats/valores utilizáveis |

## Critérios antes de anunciar aplicação final
- [ ] Compilação, testes Kotlin e CI verde para o commit exato.
- [ ] A01–A18 realmente executados em Android físico/emulador.
- [ ] Confirmar custos no jogo e revalidar a fonte comunitária quando mudar.
- [ ] Cobrir evolução, moves e condições especiais sem inventar custos.
- [ ] Separar qualidade PvP/metagame de condições para gastar recursos.

**Roadmap global:** manter a contagem 34/177 = 19,2% até demonstração dos gates do Definition of Done; não converter testes escritos em testes Android executados.
