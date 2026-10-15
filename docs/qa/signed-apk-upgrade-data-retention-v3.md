# PR #25 — Instalação da nova APK por cima da antiga, com preservação dos dados

## Prova em emulador Android

1. Fazer checkout do commit base (main aceite) e do commit candidato em pastas separadas.
2. Construir APK debug anterior (versionCode 58) e nova (versionCode 59) com o mesmo keystore local de debug; construir também instrumentação isolada.
3. Exigir identificador idêntico com.rui.pvpgo.installtestv190 e certificados de assinatura SHA-256 idênticos. APKs devem ser diferentes.
4. Instalar APK anterior, sem apagar dados, e executar instrumentação para guardar dois Pokémon, equipa, plano, candidato pendente, batalha e recursos reais do pacote.
5. Arrancar atividade antiga; confirmar PID Android vivo; executar am force-stop; confirmar desaparecimento do processo; reiniciar instrumentação e verificar dados.
6. Executar adb install -r da nova APK, sem desinstalar, sem limpar dados. Verificar versão nova e comparar coleção, equipa, plano, pendências, histórico e recursos.
7. Guardar identidade APK, logs dos três testes, prova de processo eliminado e retorno de instalação.

## Limites de segurança

Keystore de debug comum dentro do mesmo job CI prova apenas compatibilidade dessas duas APKs. Não prova assinatura de APKs antigas já instaladas no telemóvel do utilizador; poderá ser necessária a chave de assinatura original ou migração/exportação explícita.
Este é um teste real em emulador Android API 35, não validação física Android 13–16 ou TalkBack; os 110 cenários UX/E2E históricos permanecem sem fecho formal.
Os 51 testes Android normais continuam como gate independente. Os três métodos de upgrade são order-dependent e isolados via propriedade Gradle upgradeSuite; nunca são incluídos na contagem da suite ordinária.

## Dados de aceitação

- Pokémon com CP e nível conhecidos e outro com CP/nível explicitamente UNKNOWN.
- Equipa Great League, plano PvP, candidato PENDING, batalha passada.
- Stardust, Candy=0, XL, TMs e Elite Fast TM UNKNOWN, sem inventar zeros.
- Verificação tanto depois de force-stop como depois de adb install -r, com o mesmo pacote e assinatura.

## Próximo passo

Automatizar casos correspondentes aos 110 percursos UX, dando prioridade à experiência simples de leitura de IVs e aos filtros por fases para quem não domina PvP.