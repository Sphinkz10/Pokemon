# Pokémon PvP — Android V1.51 (em preparação)

Repositório de compilação da aplicação **Pokémon PvP** (Android / Kotlin / Jetpack Compose), com design no [Figma](https://www.figma.com/design/Y85bgqW7K2jEnrXdqDnwBE).

> **Estado:** preparação da primeira APK debug. Este repositório público ainda **não contém o projeto-fonte completo** nem uma APK validada. A existência do workflow não equivale a uma compilação bem-sucedida.

## Fonte de verdade

Pacote preparado: `PokemonPvP-v1.51-CI-APK-ready.zip` (360 entradas; Java 17, Gradle 8.9 e Android SDK 35). O ZIP foi preparado na conversa ChatGPT do projeto e ainda necessita de transferência para este repositório.

## Compilação

O workflow `.github/workflows/android-debug-apk.yml` foi configurado para compilar a fonte V1.51 numa máquina GitHub Actions. Após o ZIP ser colocado na raiz do repositório, use **Actions → Build PokemonPvP APK (debug) → Run workflow**. Se a compilação e os testes passarem, descarregue o artefacto `PokemonPvP-v1.51-debug-apk` da execução.

## Limitações e segurança

- A APK ainda não foi compilada nem testada num dispositivo real.
- O Companion automático/reconhecimento visual não está concluído.
- Este repositório é **público** por decisão do proprietário. Não colocar chaves, tokens, keystores, dados de utilizadores ou ficheiros privados.
- Os nomes e imagens de Pokémon pertencem aos respetivos titulares dos direitos; não presumir autorização de distribuição comercial.

## Próximos gates

1. Importar o ZIP do projeto-fonte, sem acrescentar segredos ou binários licenciados.
2. Executar GitHub Actions e corrigir erros reais de compilação.
3. Instalar a APK debug e testar os fluxos essenciais.
4. Testar responsividade, permissões, cache e dados da Pokédex Nacional.
