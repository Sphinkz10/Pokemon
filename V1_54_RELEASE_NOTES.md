# Pokémon PvP Android V1.54 — Notas de entrega (diagnóstico)

**Data:** 2026-10-02 (Portugal) · **Versão no Gradle:** 1.54.0-dev (54) · **Canal:** debug install-test.

## Entregas de código

- O ecrã **Hoje** passou a consumir tokens semânticos da skin em 19 ocorrências que antes tinham cores fixas. Foram mantidas as cores próprias dos desenhos dos Pokémon para não alterar a sua aparência.
- Cartão de eventos: área de toque >=48 dp e truncagem de títulos e categorias extensos, de forma a evitar transbordo nos ecrãs pequenos.
- **Eventos:** schema inválido ou eventos sem título/ligação válida não substituem a cache anterior.
- O leitor suporta timestamps UNIX em segundos/milissegundos, strings numéricas, datas locais e datas absolutas.
- Os botões principais usam `MaterialTheme.colorScheme.onPrimary`, melhorando o contraste sobretudo no tema Classic; ações de secção têm alvo >=48 dp.
- CI com gates herdados V1.52, V1.53 e novos testes V1.54, incluindo verificação do formato publicado pelo feed comunitário.

## Correção de instalação

Ao comparar os logs de assinatura, confirmei que a V1.53 e a primeira tentativa V1.54 tinham o mesmo package `com.rui.pvpgo.installtest`, **mas hashes SHA-256 de certificados debug diferentes**. O Android exige a mesma assinatura para atualizar um package existente. A entrega revista V1.54 usa o package separado `com.rui.pvpgo.installtestv154`, pelo que pode ser instalada lado a lado.

Isto evita o conflito na instalação, mas significa que os dados locais da variante anterior **não são migrados automaticamente** para a nova. A solução estável para versões futuras será uma assinatura de release consistente, guardada com segurança (fora de GitHub público), e um package definitivo.

## Ainda não concluído

- **Não há prova de que o calendário carregue corretamente no Android do Rui.** É indispensável testar Internet, sem Internet, refresh, horas de Lisboa e atualização persistente.
- É necessário validar as 4 skins visualmente em 320/390/430 dp (especialmente Classic) e migrar cores fixas dos módulos restantes.
- **Não é release de produção:** usa id alternativo `.installtest` e assinatura debug.
- A fonte Leek Duck é comunitária, não oficial. O Android pode adiar tarefas periódicas.

## Protocolo de validação Android

1. Instalar a APK mais recente sem desinstalar os dados existentes da variante de diagnóstico.
2. Abrir **Mais → Aparência**, escolher cada tema, fechar e reabrir (verificar persistência).
3. Abrir **Hoje → Eventos** e **Agenda** com Internet, verificar última atualização e informação de origem.
4. Desligar Internet, reabrir Agenda e confirmar aviso de cache/desatualização.
5. Verificar títulos longos e botões em largura 320/390/430 dp.
6. Guardar capturas e logs de eventuais problemas, corrigir e repetir CI.

## Evidência de CI

[Acompanhar a build V1.54](https://github.com/Sphinkz10/Pokemon/actions/runs/36938928935).
O estado da APK só deve ser considerado pronto depois de CI concluir `success` e publicar o artefacto `PokemonPvP-v1.54-installtest-debug-apk`.
