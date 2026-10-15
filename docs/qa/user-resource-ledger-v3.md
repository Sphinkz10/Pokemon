# Pokémon Companion V3 · Registo local de recursos — PR #16

## Implementado
O Pokémon 360 permite expandir **Os meus recursos** e introduzir saldos **manualmente**. Zero significa valor introduzido e confirmado; vazio significa valor **desconhecido**, não saldo zero. Existem sete campos: Stardust, Candy e XL Candy da família evolutiva atual, Fast TM, Charged TM, Elite Fast TM e Elite Charged TM.

Os saldos globais aplicam-se a qualquer família; Candy e XL Candy são exclusivos da família representada pelo `familyId` fornecido pelo catálogo atual. O identificador da família deve corresponder à espécie/form do exemplar. Guardam-se nas preferências privadas Android, com commit confirmado; nenhuma tabela Room da coleção é alterada. Não há sincronização automática com Pokémon GO, nem dedução de recursos obtidos/gastos.

O UI lê apenas valores explicitamente gravados. A conversão para `ResourceLedger` do motor só ocorre quando **todos os sete valores** foram fornecidos e a família é válida; qualquer saldo em falta preserva `null`, impedindo que as regras de faltas de recursos interpretem desconhecido como zero. Esta política conservadora é mais estrita do que o motor (cujos campos têm zeros por defeito).

A ligação ao `InvestmentCostQuoteGate` agora recebe o `ResourceLedger` se e só se estiver completo. O **pack de custos continua `null`** porque não foi instalado nem certificado: consequentemente a app **não mostra valores de Stardust/Candy calculados**. Os montantes só podem aparecer numa fase futura após validação externa e versionamento de preços.

## Testes automáticos
- 18 testes unitários de nulidade, zero, limites, validação, conversão e família.
- 19 controlos estruturais obrigatórios no CI de builds e correção das ligações.
- 4 testes instrumentados para Android a confirmar persistência após reinstanciar o repositório, isolamento de família, remoção de campo e rejeição de negativos. **Preparados, não executados em dispositivo.**

## Aceitação Android manual — não executada
| Caso | Teste | Critério |
|---|---|---|
| U01 | Primeira abertura no Pokémon 360 | 0/7 saldos registados, sem zeros fictícios |
| U02 | Introduzir zero no Stardust | Após guardar e reabrir, saldo 0 explícito |
| U03 | Deixar Candy vazio | Continua desconhecido e não é incluído como zero |
| U04 | Introduzir saldo negativo/caracteres | Entrada recusada sem gravação incorreta |
| U05 | Reabrir aplicação após guardar | Saldos preservados |
| U06 | Trocar entre espécies da mesma família | Candy e XL comuns |
| U07 | Trocar para outra família | Candy e XL desconhecidos até preenchimento |
| U08 | Trocar para outra família | Stardust e TMs mantêm os valores globais |
| U09 | Apagar valor conhecido de Candy | Após guardar, valor volta a desconhecido |
| U10 | Preencher apenas 6 de 7 valores | Não converter para ResourceLedger completo |
| U11 | Preencher os 7 valores | Criar ResourceLedger com família certa |
| U12 | Espécie sem match de catálogo | Não expor balanço de família inventada |
| U13 | Preservação da coleção existente | Room v2 intacto |
| U14 | Simular nível objetivo com inventário completo | Ainda sem orçamento enquanto não existir pack certificado |
| U15 | Ecrã estreito, grande escala de letra, TalkBack | Labels e botões de guardar acessíveis |
| U16 | Rodar e voltar ao ecrã durante edição | Sem alteração de recursos persistidos até guardar |

## Gates pendentes
- [ ] CI verde e APK de diagnóstico gerada no commit do PR #16
- [ ] Executar U01–U16 em Android real
- [ ] Executar 4 testes instrumentados (não equivalem a CI JVM)
- [ ] Integrar `InvestmentCostPack` auditado, datado e abrangente
- [ ] Verificar custos Lucky/Shadow/Purified, movimentos e condições especiais
- [ ] Confirmar arredondamento e evolução com dados do jogo

**Estado global de QA:** 0/110 E2E Android executados. Nenhum dos testes manuais acima foi passado por inferência.
