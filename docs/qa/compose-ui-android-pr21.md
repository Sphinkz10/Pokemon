# Pokémon Companion V3 · PR #21 — Testes de interação reais em Jetpack Compose

## Objetivo

O PR #20 garantiu a execução de **19 testes instrumentados no emulador** para SQLite, preferências privadas, matemática de investimento e arranque da atividade. Esse conjunto não abria campos nem carregava botões dentro de Jetpack Compose.

Esta fase acrescenta **10 testes instrumentados de interface Compose** e eleva o gate automatizado mínimo a **29 casos Android**. Usamos Compose `ui-test-junit4` com identificadores semânticos estáveis (`Modifier.testTag`) e interação via `performClick`, `performTextInput` e `performTextClearance`. Não se confunde a existência do código de testes com a sua execução: o gate exige relatório JUnit no emulador.

## Comportamentos de interface cobertos

| ID | Teste Compose | O que demonstra |
|---|---|---|
| CUI01 | Abrir inventário | A interface começa recolhida e explica vazio vs zero |
| CUI02 | Editar dois recursos e guardar | Stardust numérico e Candy explicitamente zero são persistidos |
| CUI03 | Limpar Candy guardado | O valor passa a desconhecido, sem converter para zero |
| CUI04 | Escrever texto inválido | Não substitui o saldo confirmado |
| CUI05 | Fechar sem guardar | O draft não altera SharedPreferences |
| CUI06 | Registar família A | Candy e XL não aparecem na família B |
| CUI07 | Usar nível máximo Master | O botão preenche apenas nível 50 do objetivo local |
| CUI08 | Build com alterações pendentes | Bloqueia seleção de nível e consentimento de custos |
| CUI09 | CP atual incoerente | Desativa sugestão e orçamento |
| CUI10 | Autorizar custo comunitário, XL=0, clicar nível pagável | Apresenta nível 40, preenche draft, não gasta XL nem altera Pokémon |

**Isolamento de dados:** o ecrã de investimento aceita opcionalmente um `UserResourceStore` de teste. A aplicação normal continua a carregar o seu repositório privado sem alterações; os testes utilizam apenas preferências de instrumentação isoladas.

## Critérios de merge obrigatórios

- [ ] Pipeline APK / JVM verde no commit do PR.
- [ ] `connectedDebugAndroidTest` executado no emulador Android 35.
- [ ] XML contém pelo menos 6+1+4+7+1+10 = **29 testes Android**, sem falhas, erros ou ignorados.
- [ ] Caso haja regressões de timing/semântica, corrigir a causa e repetir.
- [ ] Fornecer provas originais e APK de diagnóstico.

## Limitações honestas

Estes 10 cenários exercitam **componentes concretos reais** com ações e persistência, e os 19 anteriores verificam lógica/persistência no emulador. Não demonstram todos os caminhos de navegação da aplicação principal, não substituem o teste de um utilizador real, nem provam TalkBack a 200% ou compatibilidade física Android 13–16. A bateria global previamente definida de **110 cenários E2E de UX permanece por executar** até ser mapeada formalmente e testada, não é encerrada por extrapolação.

## Próxima etapa

Adicionar navegação completa desde Hoje até Pokémon 360, captura da hierarquia semântica/screenshot dos ecrãs, verificações de acessibilidade e preservação de dados em atualizações reais, com um dispositivo/emulador dedicado por versão Android.
