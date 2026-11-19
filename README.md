# Cobre Mobile

Player Android para a biblioteca de músicas local do aparelho. Este repositório contém somente o aplicativo Mobile.

## Estado atual

- Versão do projeto: **0.1.0**.
- Kotlin, Jetpack Compose e AndroidX Media3.
- Leitura da biblioteca local, tela de álbuns, mini player e reprodução em segundo plano estão implementados no código.
- A versão ainda não foi compilada nem validada em um aparelho real. Veja [STATUS.md](STATUS.md) e [ROADMAP.md](ROADMAP.md).
- Cobre ID, playlists, fila completa, letras, DSP e conexão com Desktop/TV ainda não estão implementados no Mobile.

## Gerar APK de depuração

1. Abra a aba **Actions** deste repositório.
2. Escolha **Build APK** e clique em **Run workflow**.
3. Quando terminar, baixe o artefato `cobre-mobile-debug-apk` e extraia o APK.

O workflow também é executado em cada atualização enviada para `main`. O APK gerado é de depuração; ele não é uma versão assinada para publicação na Play Store.

## Compilar localmente

Requer JDK 17 e Gradle 8.9:

```sh
gradle assembleDebug
```

## Estrutura

- `app/` — aplicativo Android.
- `ROADMAP.md` — próximas etapas planejadas.
- `STATUS.md` — o que foi escrito e o que ainda precisa ser verificado.

Este código não declara uma licença de redistribuição. Consulte o autor antes de reutilizar ou redistribuir.
