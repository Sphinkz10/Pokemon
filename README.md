# Pokémon PvP — Android V1.51

Aplicação Android em Kotlin/Jetpack Compose, com funcionalidades de Coleção, Pokédex Nacional, Equipa, Batalhas e Companion (parcial). Design: [Figma · Pokémon Companion](https://www.figma.com/design/Y85bgqW7K2jEnrXdqDnwBE).

## Código importado

Em 1 de outubro de 2026, foram importados **227 ficheiros** de código-fonte, infraestrutura, testes e ferramentas de um arquivo local da V1.51, incluindo `app/`, `engine/`, `gradle/`, `tests/`, `tools/` e as configurações Gradle. A documentação histórica de evidências não foi importada.

Não é necessário enviar outro ZIP para iniciar a compilação: o workflow lê diretamente os ficheiros do repositório.

## Gerar APK debug

1. Abrir [GitHub Actions](https://github.com/Sphinkz10/Pokemon/actions/workflows/android-debug-apk.yml).
2. Selecionar **Run workflow** na branch `main`, caso não exista uma execução automática recente.
3. Esperar pelos passos JDK 17 → Android SDK 35 → Gradle 8.9 → `:engine:compileKotlin` → `:app:assembleDebug`.
4. Se tudo passar, abrir a execução e descarregar o artefacto `PokemonPvP-v1.51-debug-apk` (contém `app-debug.apk`).

Primeira execução com fonte importada: [run #36929906509](https://github.com/Sphinkz10/Pokemon/actions/runs/36929906509). **A existência de uma execução não prova que a APK tenha sido gerada.** Verificar a conclusão e os artefactos.

## Verificações ainda necessárias

- Confirmar compilação final do Android sem erros.
- Instalar APK em dispositivo físico e validar permissões, consumo, navegação e performance.
- Rever paridade entre app e Figma em 320/390/430 px.
- Verificar limites e tratamentos de erro de API, persistência, cache e dados offline.
- Completar e validar o Companion com reconhecimento automático, ainda incompleto.
- Confirmar condições de licença antes de publicar ilustrações, nomes ou outros ativos relacionados com Pokémon.

**Importante:** este repositório está configurado como **público** por decisão do proprietário. Nunca carregar tokens, credenciais, keystores de assinatura, identificadores de utilizadores ou dados privados.
