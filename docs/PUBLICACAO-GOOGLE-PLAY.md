# Publicação do Cobre Mobile na Google Play

## Preparação técnica em 0.19.0

- A variante `play` compila para API 36 e remove a permissão `REQUEST_INSTALL_PACKAGES`, que a política da loja proíbe usar para atualizar o próprio app. Essa variante não baixa nem instala APKs externos.
- A variante `direct` preserva o canal atual de APK e o atualizador por GitHub.
- A pré-release assinada `mobile-v0.19.0` já contém APK direto, AAB Play e checksums SHA-256; o workflow terminou com sucesso. O AAB está tecnicamente assinado, mas a vinculação da chave à configuração Play App Signing ainda precisa ser concluída na Play Console.
- O pacote `app.cobre.mobile` já foi distribuído fora da loja. A chave usada nos APKs diretos precisa ser preservada ao configurar Play App Signing para que os dois canais possam atualizar a mesma instalação.

## Estado do lançamento

- **GitHub:** `0.19.0` está publicada como pré-release. Para promovê-la a release estável no GitHub, não falta uma nova compilação; falta decidir se a transferência para o Desktop e os downloads/retomada offline podem ser declarados estáveis sem validação física. A compilação assinada foi verificada pelo CI, mas essas funções novas ainda não foram exercitadas num aparelho nesta versão.
- Corrigi o fluxo para que futuras tags numeradas sem sufixo (`mobile-v0.20.0`) saiam como releases estáveis no GitHub; tags com `-rc`, `-beta` ou `-alpha` continuam pré-releases. A 0.19.0 já publicada não muda retroativamente.
- **Google Play:** o aplicativo ainda não foi enviado à Play Console. “Release estável no GitHub” e “disponível na Play Store” são etapas diferentes.

## Itens obrigatórios antes do envio

1. **Teste de dispositivo:** validar a variante Play em Android 16, incluindo reprodução em segundo plano, notificações, QR, rede local, download interrompido/retomado, falta de espaço, fila restaurada e transferência Mobile → Desktop.
2. **Conta/assinatura:** registrar o app na Play Console verificada e concluir Play App Signing preservando a continuidade da chave de distribuição. A release de GitHub contém AAB assinado; não presumir que isso conclui a configuração de assinatura da loja.
3. **Privacidade:** publicar uma política pública, acessível dentro e fora do app, e preencher Segurança dos dados com base no que os clientes e o Cobre Server realmente coletam. O áudio segue do Desktop ao cliente e não passa pelo Server.
4. **Exclusão externa:** o fluxo no app e a exclusão no Server já existem; ainda falta uma página web pública que explique/inicie a solicitação externa e sua URL para a Play Console. A política deve explicar qualquer dado local que não seja removido pelo Server.
5. **Ficha da loja:** completar classificação etária, descrição, capturas, contato do desenvolvedor, declarações de permissões e instruções/credenciais de revisão do Cobre ID.
6. **Acesso à produção:** se a conta Play Console for pessoal e tiver sido criada após 13/11/2023, concluir teste fechado com pelo menos 12 participantes inscritos continuamente por 14 dias e solicitar acesso à produção. A exigência depende do tipo e da data da conta.

## Assinatura e atualização

Usar no Play App Signing a chave de assinatura existente do canal direto, em vez de aceitar uma chave diferente sem avaliar a consequência. Uma chave diferente impediria atualizações por cima entre APK direto e versão da Play. Manter a chave privada fora do repositório e dos artefatos públicos.

## Estado

API 36, flavor `play`, remoção de auto-update APK e geração do AAB passaram no workflow [45086261665](https://github.com/bruncosta077-boop/Cobre-Mobile/actions/runs/45086261665), que também verificou os artefatos assinados da tag `mobile-v0.19.0`. A Play Console não foi configurada e nenhum app foi enviado para revisão. A política do Google exige API 36 para novos apps e atualizações a partir de 31/08/2026; o projeto já usa target 36.
