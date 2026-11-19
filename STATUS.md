# Cobre Mobile — status

## Versão 0.2.0 — validada pelo autor

No aparelho, o autor confirmou login Cobre ID, leitura das músicas, reprodução normal e ícone do app.

## Versão 0.3.0 — código em desenvolvimento

- Filtro da biblioteca por pasta escolhida no seletor de documentos do Android.
- Paletas de destaque correspondentes às opções do Desktop.
- Aba Configurações, volume de música do Android e informações detalhadas das saídas de áudio disponíveis.
- Ajuste da interface e da navegação para aproximar o app da identidade visual Desktop.

### Próxima validação
1. Compilar o APK 0.3.0 pelo GitHub Actions.
2. No aparelho, conferir: filtro de pasta (incluindo Mostrar tudo), persistência da cor após reabrir, volume, saída conectada, biblioteca, reprodução, fila e Cobre ID.
3. Registrar qualquer diferença antes de avançar a paridade de recursos.

## Funções do Desktop que ainda faltam

Playlists persistentes, favoritos, histórico/estatísticas, letras, correção de metadados, créditos de encarte, equalizador, DSP, crossfade, streaming do Desktop e sincronização.

## Limites da integração atual

Cobre ID autentica e envia presença. Isso não prova que o Desktop está alcançável. O servidor ainda não transporta áudio nem sincroniza dados. Para conexão pela internet, o servidor também precisa ser publicado com HTTPS.