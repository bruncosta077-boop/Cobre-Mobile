# Cobre Mobile — roadmap

Este documento acompanha o desenvolvimento do aplicativo Mobile. **Uma etapa só passa a validada depois de compilada e conferida em aparelho real.**

## Estado atual

### Base 0.1.0 — validada pelo autor
Player local instalado e usado no celular: leitura da biblioteca, grade de álbuns, reprodução Media3 e execução em segundo plano.

### Versão 0.2.0 — código em desenvolvimento
- Biblioteca com abas de álbuns, artistas e faixas; busca por título, álbum e artista.
- Detalhe do álbum, fila editável, mini player e tela completa com progresso, aleatório e repetição.
- Ícone do Cobre incluído no APK como ícone legado e adaptativo.
- Base Cobre ID para login/criação de conta, renovação de sessão, dispositivos e presença.
- Refresh token cifrado com chave do Android Keystore.
- Reprodução continua sendo local. O Cobre Server atual não fornece áudio nem sincroniza bibliotecas.

**Próximo passo:** compilar o 0.2.0 pelo workflow, instalar no aparelho e validar navegação, reprodução, fila, controles, notificação, reinício e integração Cobre ID com um servidor acessível.

## Próximas etapas

### 1 — Biblioteca e dados locais
- Room para biblioteca indexada, playlists, favoritos, avaliações e histórico.
- IDs estáveis para preparar sincronização entre aparelhos.
- Fila e faixa atual restauradas depois de fechar/reabrir o app.
- Paginação, atualização da biblioteca e melhor ordenação em coleções grandes.

### 2 — Letras e metadados
- Letras `.lrc` sincronizadas e letras `.txt` ao lado da música.
- Artista do álbum, disco e total de faixas a partir das tags.
- Correção de metadados, capas personalizadas e busca geral de encarte.

### 3 — Áudio
- Equalizador de 10 bandas com proteção contra clipping.
- Balanço, mono, graves/agudos e ReplayGain.
- Gapless e crossfade medidos com arquivos de referência.
- Identificar a saída real do aparelho; não prometer bit-perfect sem validação.

### 4 — Cobre ID e dispositivos
- Validar renovação, remoção e reconexão de dispositivos.
- Servidor publicado atrás de HTTPS para acesso pela internet.
- Indicadores de conexão que distingam presença, alcance e qualidade do áudio.

### 5 — Biblioteca do Desktop no celular
- Validar a arquitetura de transporte com rede local, 4G/5G e CGNAT antes de prometer conexão universal.
- Streaming protegido e autorizado pelo Cobre ID; leitura com Range e cache no Media3.
- Estado claro quando o PC estiver desligado, sem rede ou indisponível.
- Qualidade original por padrão e modo econômico opcional.

### 6 — Sincronização e acabamento
- Playlists, favoritos, avaliações, histórico e preferências.
- Escrita offline com envio posterior e resolução de conflitos.
- Downloads para uso offline, temporizador, widget e Android Auto.
- Acessibilidade, bateria, tablets/dobráveis e idiomas adicionais.

### Depois — Cobre TV
Interface para controle remoto e navegação por foco, reaproveitando componentes estáveis do Mobile.

## Decisões

| Decisão | Motivo |
|---|---|
| Kotlin, Compose e Media3 | Integração Android, reprodução em segundo plano e futura TV |
| Funcionar sem login para música local | Biblioteca do aparelho não depende de conta |
| Chave do refresh token no Android Keystore | Reduz exposição do token persistente |
| HTTPS para servidor fora da rede privada | Credenciais não devem atravessar a internet em HTTP |
| Arquivo original como qualidade padrão | Evitar transcodificação sem necessidade |

## Limites conhecidos

- 0.2.0 ainda não foi compilado nem validado em aparelho.
- A tela Cobre ID precisa de uma URL do Cobre Server acessível; o servidor atual é HTTP e ainda não está pronto para acesso público pela internet.
- Presença online não prova que o Desktop está alcançável e ainda não há streaming, sincronização ou métricas de velocidade.
- A biblioteca usa IDs do MediaStore, que são locais ao aparelho; ainda não há persistência de fila, playlists, histórico ou favoritos.
