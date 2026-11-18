# MPersonality

Tela inicial (launcher) leve e pessoal para Android, feita em Kotlin com Jetpack Compose.
Funciona em qualquer celular com **Android 5.0 ou mais novo**.

**Como usar no celular**
- **Deslize para cima** na tela inicial para abrir a gaveta de apps.
- Na gaveta: toque para abrir um app; **segure o dedo** em um app para ver as informações dele (desinstalar, permissões).
- Para fechar a gaveta: puxe para baixo ou use o botão "voltar".
- **Segure o dedo em um espaço vazio** da tela inicial (ou toque em "Ajustes" na gaveta) para abrir as configurações do relógio.

---

## Passo a passo (sem precisar saber programar)

### 1. Criar uma conta no GitHub
1. Acesse **https://github.com/signup** e crie sua conta (e-mail, senha e nome de usuário).
2. Confirme o e-mail que o GitHub enviar.

### 2. Instalar o GitHub Desktop no computador
1. Baixe em **https://desktop.github.com** e instale.
2. Abra o programa e clique em **Sign in to GitHub.com**. Entre com a conta que você criou.

### 3. Transformar esta pasta em um repositório
1. No GitHub Desktop, vá em **File → Add local repository...**
2. Clique em **Choose...** e selecione a pasta deste projeto (a que tem este arquivo `README.md`).
3. Vai aparecer o aviso *"This directory does not appear to be a Git repository"*. Clique no link **create a repository**.
4. No campo **Name**, escreva `MPersonality`. Deixe o resto como está e clique em **Create repository**.

### 4. Enviar o projeto para o GitHub
1. Clique no botão azul **Publish repository** (no topo).
2. **Desmarque** a opção *Keep this code private*. Assim fica mais fácil baixar o APK pelo celular, sem precisar fazer login. Pode deixar privado se preferir, mas aí você precisa estar logado no GitHub no navegador do celular.
3. Clique em **Publish repository**.

### 5. Esperar o APK ser gerado
1. No navegador, abra `https://github.com/SEU-USUARIO/MPersonality` (troque `SEU-USUARIO` pelo seu nome de usuário).
2. Clique na aba **Actions**. Vai aparecer uma tarefa chamada **Gerar APK** rodando (bolinha amarela).
3. Espere uns 5 minutos até aparecer um **✓ verde**. Se aparecer um **X vermelho**, deu erro: clique nele, copie a mensagem e me mande.

### 6. Baixar o APK no celular
No navegador do celular (Chrome), abra:

```
https://github.com/SEU-USUARIO/MPersonality/releases/latest/download/MPersonality-debug.apk
```

O download começa direto. Esse endereço **sempre** baixa a versão mais recente.

> Outra forma: na página do projeto, na coluna da direita, clique em **Releases → MPersonality - versão de teste** e toque em `MPersonality-debug.apk`.

### 7. Instalar no celular
1. Toque no arquivo baixado (na notificação de download ou no app **Arquivos**).
2. O Android vai pedir permissão para **instalar apps desconhecidos** a partir do Chrome. Toque em **Configurações**, ative **Permitir desta fonte** e volte.
3. Toque em **Instalar**.
4. Se o **Play Protect** avisar que o app não é conhecido, toque em **Mais detalhes → Instalar mesmo assim**. Isso acontece porque o app não veio da Play Store. É normal para um app feito por você.

### 8. Usar o MPersonality como tela inicial
1. Aperte o botão **Início** (ou faça o gesto de ir para o início). O Android deve perguntar qual tela inicial usar. Escolha **MPersonality** e depois **Sempre**.
2. Se ele não perguntar: abra o MPersonality, entre em **Ajustes → Definir como tela inicial padrão**, ou vá em **Configurações do Android → Apps → Apps padrão → App de tela inicial** e escolha **MPersonality**.

**Para voltar à tela inicial original da Motorola:** faça o mesmo caminho (*Apps → Apps padrão → App de tela inicial*) e escolha a tela inicial original.

---

## Como atualizar o app depois
Sempre que o projeto for alterado nesta pasta:
1. Abra o GitHub Desktop. As mudanças aparecem na lista à esquerda.
2. No campo **Summary** (canto inferior esquerdo), escreva um resumo curto, por exemplo `novo estilo de relógio`.
3. Clique em **Commit to main** e depois em **Push origin** (no topo).
4. Espere o ✓ verde na aba **Actions** e baixe o APK pelo mesmo link do passo 6.
5. Instale por cima da versão antiga. Não precisa desinstalar, e as suas configurações continuam salvas.

---

## Detalhes técnicos
- Kotlin + Jetpack Compose (foundation + material3). Nenhuma outra biblioteca.
- O papel de parede é o do próprio sistema (tema `Theme.Material.Wallpaper`), sem custo extra de memória.
- O relógio atualiza uma vez por minuto e só enquanto a tela está visível.
- As configurações ficam salvas no aparelho (`SharedPreferences`). Sem servidor, sem login, sem internet.
- A pasta `keystore/` tem uma chave de **debug** fixa. Assim todo APK gerado pelo GitHub Actions tem a mesma assinatura e instala por cima do anterior. Ela não é segredo e serve só para testes. Não use essa chave para publicar na Play Store.
- O GitHub Actions (`.github/workflows/gerar-apk.yml`) gera o APK a cada push. O APK fica salvo na aba Actions (por 30 dias) e, no branch `main`, também é publicado na release `ultima-versao`.
