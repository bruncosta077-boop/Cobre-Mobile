# Cobre Mobile — status

## Versão 0.2.0 — validada pelo autor

No aparelho, o autor confirmou login Cobre ID, leitura das músicas, reprodução normal e ícone do app.

## Versão 0.3.0 — incorporada à 0.4.0

- Filtro da biblioteca por pasta escolhida no seletor de documentos do Android.
- Paletas de destaque correspondentes às opções do Desktop.
- Aba Configurações, volume de música do Android e informações detalhadas das saídas de áudio disponíveis.
- Ajuste da interface e da navegação para aproximar o app da identidade visual Desktop.

## Versão 0.4.0 — código em desenvolvimento

- Favoritos, histórico recente e playlists locais persistidos em SQLite.
- Criar playlist a partir da fila; reproduzir playlists e administrar favoritos na aba Coleção.
- O APK ainda precisa compilar e voltar ao aparelho antes de considerar essa etapa validada.

### Próxima validação
1. Compilar o APK 0.4.0 pelo GitHub Actions.
2. No aparelho, conferir pasta, paleta, saídas de áudio, favoritos, histórico, playlists, reprodução, fila e Cobre ID.
3. Registrar qualquer diferença e corrigir antes de avançar para streaming.

## Funções do Desktop que ainda faltam

Estatísticas, letras, correção de metadados, créditos de encarte, equalizador, DSP, crossfade, streaming do Desktop e sincronização com servidor.

## Limites da integração atual

Cobre ID autentica e envia presença. Isso não prova que o Desktop está alcançável. O servidor ainda não transporta áudio nem sincroniza dados. Para conexão pela internet, o servidor também precisa ser publicado com HTTPS.
