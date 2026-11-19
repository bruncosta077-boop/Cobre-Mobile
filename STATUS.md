# Cobre Mobile — status

## Versão 0.2.0 em desenvolvimento

O APK 0.1.0 foi instalado e usado no celular do autor. A versão 0.2.0 amplia o player local e adiciona a base de Cobre ID. Ela ainda precisa compilar no Actions e ser reinstalada para validar as mudanças novas.

### Incluído no código 0.2.0
- Biblioteca organizada em álbuns, artistas e faixas, com busca.
- Capa, detalhes do álbum, mini player e tela de reprodução.
- Fila com reprodução, remoção e reordenação.
- Reprodução em segundo plano pelo Media3.
- Ícone da marca incluído no APK, incluindo ícone adaptativo.
- Login/criação de conta Cobre ID, dispositivos e presença no servidor atual.
- Refresh token cifrado com chave protegida pelo Android Keystore.

### Próxima validação
1. Compilar `assembleDebug` pelo workflow do GitHub Actions.
2. Instalar o APK 0.2.0 no aparelho e conferir biblioteca, navegação, fila, controles, notificação e retomada.
3. Informar a URL do servidor na tela Cobre ID e conferir login e presença. A conexão depende de um Cobre Server acessível; streaming ainda não existe.

### Ainda em desenvolvimento
Room para estado persistente, playlists, favoritos, histórico, letras, metadados avançados, DSP, streaming da biblioteca do PC e sincronização.