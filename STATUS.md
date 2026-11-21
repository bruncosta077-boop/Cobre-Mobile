# Cobre Mobile — status

## Versão 0.13.0 — conexão simplificada e biblioteca Desktop

- Uma conexão única por Cobre ID + QR; endereço e concessão salvos automaticamente após a aprovação no Desktop.
- Tela Conexões reúne conta, estado do Desktop, QR, dispositivos e controles. Configurações apontam para esse mesmo fluxo.
- Erros HTTP 401/403/503 agora orientam para corrigir autorização, permissão ou disponibilidade do Server.
- Cabeçalho e organização das configurações ajustados à identidade cobre do Desktop; detalhes avançados de saída de áudio ficam recolhidos.
- Atualizador verifica versões ao abrir, permite busca manual, baixa APK de release, confere hash (quando a API do GitHub o informa), pacote, versão e certificado antes de entregar a instalação ao Android.
- O Android solicita permissão para instalar apps desta origem e confirmação final; as notas ficam guardadas e aparecem após a primeira abertura da nova versão.
- assembleRelease compilou localmente. A análise lintVital foi excluída porque excedeu o tempo razoável neste ambiente; o workflow do GitHub valida o APK assinado.
- Requer Cobre Desktop 3.12.0 para receber códigos de erro detalhados. Publicação depende do build assinado no GitHub Actions.

## Versão 0.12.0 — pré-release assinada publicada

- APK disponível em [Cobre Mobile 0.12.0](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.12.0): `Cobre-Mobile-0.12.0.apk`.
- GitHub Actions compilou o APK de release, confirmou a assinatura com `apksigner verify` e publicou o arquivo no [run 44796230905](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44796230905).
- Chave estável de distribuição configurada nos segredos do repositório. Cópia local protegida pelo Windows em `C:\Users\Bruno\Documents\Cobre-Mobile-Signing`; manter essa chave é necessário para assinar futuras atualizações.
- APKs antigos de debug podem exigir migração única: exportar o backup do app, desinstalar a instalação incompatível e instalar este APK. Depois da migração, versões com a mesma chave atualizam por cima.
- Continua pré-release: falta validação física desta build 0.12.0 no aparelho, incluindo QR, aprovação Desktop, stream prolongado, controles e restauração do backup.

- Solicitação e aprovação de acesso do Mobile no Desktop via Cobre ID; consentimento duradouro e tokens de mídia curtos renovados automaticamente.
- Catálogo, capas, áudio HTTP Range e comandos remotos usam rotas protegidas do Desktop. Server autoriza, mídia flui diretamente na rede local.
- Player reorganizado com capa em destaque, origem, progresso, controles e volume visível; configurações de música do computador, aparência e saída de áudio ficam em grupos identificados.
- Equalizador Android de cinco bandas implementado e preferências adicionadas ao backup seletivo.
- Revogação do acesso Cobre ID permanece efetiva no Mobile e não reabre automaticamente o modo legado com chave LAN.
- Conta, lista de dispositivos, acesso Desktop e pareamento TV estão separados em blocos; o Mobile busca e exibe faixa/estado real dos controles do Desktop.
- QR de pareamento do Desktop implementado: scanner sem permissão de câmera, validade de cinco minutos, nenhuma credencial no QR, solicitação de acesso Cobre ID e gravação do endereço após aprovação. Endereço manual continua disponível.
- O QR requer Cobre Desktop 3.10.0; o acesso protegido sem QR continua compatível com Desktop 3.9.0 ou posterior.

## Versão 0.9.0 — pré-release publicada

- GitHub Actions compilou e publicou o APK `Cobre-Mobile-0.9.0.apk` em [Releases](https://github.com/bruncosta077-boop/Cobre-Mobile/releases/tag/mobile-v0.9.0).
- Backup seletivo inicial habilitado para banco local e cores.
- Certificados debug 0.8.0 e 0.9.0 foram comparados e são diferentes. Não é possível instalar 0.9.0 por cima da 0.8.0; não desinstalar se houver dados locais a preservar.
- A chave estável de distribuição continua sem configurar; APK debug não deve ser tratado como canal de atualização estável.

## Versão 0.11.0 — não publicada; alterações incorporadas à 0.12.0

- Backup do Android foi ampliado para endereço do Cobre Server, email, pasta e Desktop; o refresh token fica em arquivo separado protegido por Keystore e fora do backup.
- Migração de token antigo entre preferências é feita ao abrir o app sem limpar sessão.
- Build debug passou no GitHub Actions [44746930132](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/44746930132); restauração ainda não foi validada em aparelho. Pode exigir novo login e nova autorização SAF da pasta.
- A assinatura estável de distribuição e a migração segura de APKs debug continuam pendentes.

## Versão 0.2.0 — validada pelo autor

No aparelho, o autor confirmou login Cobre ID, leitura das músicas, reprodução normal e ícone do app.

## Versão 0.3.0 — incorporada à 0.4.0

- Filtro da biblioteca por pasta escolhida no seletor de documentos do Android.
- Paletas de destaque correspondentes às opções do Desktop.
- Aba Configurações, volume de música do Android e informações detalhadas das saídas de áudio disponíveis.
- Ajuste da interface e da navegação para aproximar o app da identidade visual Desktop.

## Versão 0.4.0 — incorporada à 0.5.0

- Favoritos, histórico recente e playlists locais persistidos em SQLite.
- Criar playlist a partir da fila; reproduzir playlists e administrar favoritos na aba Coleção.

## Versão 0.5.0 — incorporada à 0.6.0

- Biblioteca local combinada com o catálogo do Cobre Desktop.
- Reprodução das faixas do computador usando o endpoint LAN já existente no Desktop e suporte a HTTP Range do Media3.
- A conexão usa o endereço e a chave mostrados em **Acesso remoto** no Desktop; exige a mesma rede local e não passa pelo Cobre Server.
- Biblioteca LAN e áudio direto do Desktop.

## Versão 0.6.0 — conexão validada pelo autor

- O autor confirmou que a conexão com a biblioteca do Desktop funcionou bem.
- A tela informa quantidade de faixas e tempo de resposta do catálogo; isso não mede a taxa contínua do áudio.

## Versão 0.7.0 — em validação

- Alternância entre celular, computador e biblioteca conjunta; elimina faixas duplicadas na visão conjunta.
- Player reorganizado com volume visível e informações de álbum, ano, origem e faixa.
- Toque longo em álbum abre ações rápidas; paletas ajustadas para tons mais suaves.

## Versão 0.8.0 — em desenvolvimento

- Media3 mede latência até o primeiro bloco e taxa recebida durante streams HTTP do Desktop; arquivos locais não entram na medição.
- Medidas recentes são enviadas no heartbeat Cobre ID e exibidas no diagnóstico do Cobre Server.
- O pipeline de APK publica um arquivo instalável direto em Releases nas tags `mobile-v*`; a build de desenvolvimento ainda aguarda validação no telefone.
- A leitura expira após dois minutos sem atividade. Não representa a rede até o Server e não habilita transmissão pela internet.
- Próxima validação: tocar uma faixa do Desktop por mais de 5 segundos, conferir a taxa no painel Server e testar pausa, retomada, busca e próxima faixa.

### Próxima validação
1. Gerar e instalar o APK 0.8.0 pelo GitHub Actions.
2. Conferir no aparelho as fontes separadas e a visão conjunta, as ações por toque longo, volume, aparência do player e paletas.
3. Favoritos e playlists permanecem armazenados localmente; sincronizá-los com o servidor é etapa posterior.

## Funções do Desktop que ainda faltam

Estatísticas, letras, correção de metadados, créditos de encarte, equalizador, DSP, crossfade, sincronização de playlists/bibliotecas e acesso do Desktop pela internet.

## Limites da integração atual

Cobre ID autentica e envia presença, mas não anuncia o endpoint LAN do Desktop. O áudio atual usa o servidor HTTP local do Desktop; ambos os aparelhos precisam estar na mesma rede. O Cobre Server ainda não faz relay de áudio nem sincroniza dados.

## Versão 0.9.0 — backup seletivo

- Backup Android inclui o banco local e cores, sem salvar token Cobre ID, chave/endereço LAN do Desktop ou permissão de pasta.
- Migração das configurações antigas ao abrir a nova versão.
- Precisa validar instalação por cima da versão existente e restauração do backup num aparelho real.
