# Cobre Mobile — Roadmap e registro de trabalho

Documento vivo. Atualizar a cada entrega. Legenda: [x] feito · [~] escrito mas NÃO testado · [ ] a fazer.
Regra: nada vira [x] sem teste em aparelho real.

## 1. Situação atual (v0.1.0)

**Escrito, ainda não compilado nem testado em aparelho [~]**
- [~] Projeto Android (Kotlin, Compose, Media3, minSdk 29)
- [~] Biblioteca local via MediaStore, agrupada por artista|álbum (igual ao Desktop 3.1)
- [~] Grade de álbuns com capas, tela do álbum, mini player (anterior/tocar/próxima)
- [~] Reprodução em segundo plano, notificação e tela de bloqueio (MediaSession)
- [~] Pedido de permissão de áudio e de notificações
- [~] Tema com a paleta do Desktop
- [~] Build do APK de depuração pelo GitHub Actions

**Primeira tarefa de verificação:** compilar, instalar e conferir cada item acima. Registrar aqui o resultado.

## 2. Próximas fatias (ordem sugerida)

### Fatia 2 — Player de verdade (Etapa 4)
- [ ] Tela "Tocando agora": capa grande, barra de progresso com busca, aleatório, repetir
- [ ] Fila de reprodução: ver, reordenar, remover, "tocar a seguir"
- [ ] Busca por faixa, álbum e artista
- [ ] Abas: Álbuns, Artistas, Gêneros, Faixas
- [ ] Lembrar a fila e a posição ao reabrir o app
- [ ] Atualizar a biblioteca ao detectar músicas novas
- [ ] Ordenação e filtros (artista, ano, adicionadas recentemente)

### Fatia 3 — Dados do usuário no aparelho (Etapa 4)
- [ ] Banco local (Room): faixas, playlists, histórico, estrelas
- [ ] Playlists: criar, editar, apagar, ordenar
- [ ] Favoritos (novo, também entra no Desktop)
- [ ] Histórico e "mais ouvidos" (mesma regra do Desktop: conta após 30 s ou metade)
- [ ] Avaliação por estrelas e resenhas de álbum
- [ ] IDs estáveis (UUID por faixa/álbum) para sincronizar depois

### Fatia 4 — Letras e metadados
- [ ] Letras `.lrc` sincronizadas e `.txt` ao lado do arquivo, e embutidas na tag
- [ ] Artista do álbum, disco, total de faixas (ler tags direto do arquivo)
- [ ] Correção de metadados e capas personalizadas
- [ ] ReplayGain (normalização de volume)

### Fatia 5 — Áudio (Etapa 7, parte de DSP)
- [ ] Gapless e crossfade (validar o gapless de FLAC/MP3 com medição)
- [ ] Equalizador de 10 bandas em `AudioProcessor`, ponto flutuante, com pré-ganho e proteção contra clipping
- [ ] Balanço, mono, controle de graves e agudos
- [ ] Teste de nulo contra a saída do Desktop (mesmas fórmulas de biquad)
- [ ] Crossfeed e valvulado
- [ ] Informar o caminho de saída real. **Não anunciar bit-perfect/lossless sem verificar por aparelho**
- [ ] Controle de volume e ajuste de saída (fone, USB, Bluetooth)

### Fatia 6 — Cobre ID e dispositivos (Etapas 2b e 4)
- [ ] Login com Cobre ID; o app continua funcionando sem login para músicas locais
- [ ] Chave do dispositivo no Keystore; tokens com renovação
- [ ] Lista de dispositivos da conta; remover dispositivo
- [ ] Reconexão automática

### Fatia 7 — Biblioteca do PC por streaming (Etapa 5)
- [ ] Teste de rede (WebRTC Electron ⇄ Android, 4G/5G com CGNAT) antes de investir
- [ ] Biblioteca do PC no celular (álbuns, artistas, busca, capas, letras)
- [ ] `DataSource` do Media3 com Range, busca e cache
- [ ] Conexão direta com relay de fallback
- [ ] Mensagem clara quando o PC está desligado ou sem rede
- [ ] Reconexão em troca de rede
- [ ] Qualidade original por padrão; modo econômico opcional e sinalizado

### Fatia 8 — Sincronização (Etapa 6)
- [ ] Playlists, favoritos, estrelas, resenhas, histórico, preferências, configurações de áudio
- [ ] Alterações offline guardadas e enviadas depois
- [ ] Resolução de conflitos (HLC; histórico só acrescenta)

### Fatia 9 — Downloads e acabamento (Etapa 7)
- [ ] Baixar faixa, álbum ou várias faixas do PC
- [ ] Progresso, cancelar, retomar, espaço ocupado, excluir
- [ ] Marcar o que está disponível offline
- [ ] Android Auto
- [ ] Widget e atalhos
- [ ] Temporizador de sono

### Depois — Cobre TV (Etapa 8)
- [ ] Interface para controle remoto (Compose for TV) reaproveitando os módulos

## 3. Melhorias contínuas (sempre em andamento)
- [ ] Desempenho com biblioteca grande (paginação, grade eficiente, cache de capas)
- [ ] Consumo de bateria (medir em reprodução longa com tela apagada)
- [ ] Acessibilidade (leitor de tela, tamanhos de fonte, contraste)
- [ ] Layouts para telas pequenas, dobráveis e tablets
- [ ] Animações suaves sem queda de desempenho
- [ ] Testes automatizados da camada de dados e da sincronização
- [ ] Idiomas além do português
- [ ] Revisão de segurança antes de qualquer versão pública
- [ ] APK assinado para release (chave própria, fora do repositório)

## 4. Decisões registradas
| Decisão | Motivo | Revisável |
|---|---|---|
| Kotlin + Media3 + Compose | Segundo plano, codecs, bateria e Google TV | Não, salvo problema grave |
| minSdk 29 (Android 10) | Simplifica capas e armazenamento | Sim |
| Funciona sem login para músicas locais | Exigência do projeto | Não |
| Streaming: arquivo original por padrão | Qualidade; transcodificar só opcional | Não |
| Sincronização por log com relógio híbrido | Evita duplicação e conflito | Sim |
| Dados de sync no servidor sem cifra de ponta a ponta na v1 | Evita atrito no login; áudio é sempre ponta a ponta | Sim |

## 5. Limitações conhecidas hoje
- Sem artista do álbum nem disco: álbuns com participações podem se dividir (mesmo problema do Desktop)
- IDs ainda são os do MediaStore, locais ao aparelho
- Grade de álbuns sem paginação
- Nada foi testado em aparelho

## 6. Registro de mudanças
| Versão | O que mudou | Testado? |
|---|---|---|
| 0.1.0 | Primeira fatia do player local e build pelo GitHub Actions | Não |
