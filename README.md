# MPersonality

Tela inicial (launcher) leve e pessoal para Android, feita em Kotlin com Jetpack Compose.
Funciona em qualquer celular com **Android 5.0 ou mais novo**.

## Qual APK baixar

| Arquivo | Para quê |
|---|---|
| **MPersonality.apk** | Versão **rápida** (release, otimizada com R8). **Use esta no dia a dia.** |
| MPersonality-debug.apk | Versão de testes, mais lenta. Aparece como "MPersonality Debug" e pode ficar instalada junto com a outra. |

Links diretos (sempre a versão mais nova):
- https://github.com/Dhcitizen/MPersonality/releases/latest/download/MPersonality.apk
- https://github.com/Dhcitizen/MPersonality/releases/latest/download/MPersonality-debug.apk

> O `MPersonality.apk` só aparece depois que a chave de assinatura estiver guardada nos Secrets do GitHub (veja abaixo).

## Como usar no celular

**Tela inicial**
- **Deslize para cima**: abre a gaveta de apps.
- **Deslize para os lados**: troca de página.
- **Segure um espaço vazio**: menu com "Criar card" e "Configurações".
- **Segure um ícone**: menu com "Remover" e "Informações do app". Se arrastar sem soltar, você move o ícone.
- **Arrastando**: solte numa célula, no dock (embaixo, até 5 apps) ou dentro de um card. Para remover, solte na faixa **"✕ Remover"** no topo. Segure o ícone na borda da tela para ir para outra página.

**Gaveta de apps**
- Toque para abrir o app.
- Segure e **arraste** para colocar o app na tela inicial, no dock ou num card. Se só segurar, aparece o menu "Adicionar à tela inicial".

**Cards de apps**
- Segure um espaço vazio e escolha **"Criar card aqui"**. O editor abre na hora.
- No editor você escolhe:
  - título;
  - borda: liga ou desliga, grossura e cor;
  - cantos arredondados;
  - fundo: transparente, desfoque ou cor sólida, com transparência ajustável;
  - espaço entre os ícones;
  - quais apps entram no card.
- Segure o card para **editar, remover ou arrastar** para outra linha ou página.
- Se o card tiver mais apps do que cabem, ele **rola para o lado**.

**Relógio** (Configurações)
- 7 estilos:
  - hora gigante;
  - hora empilhada;
  - fina e elegante com data;
  - contorno sem preenchimento;
  - brilho suave;
  - analógico minimalista;
  - analógico clássico.
- Fonte, tamanho, cor, sombra e posição (alinhamento e distância do topo).

**Papel de parede** (Configurações)
- **"Escolher imagem da galeria"**: a imagem é reduzida para a resolução da tela, o que deixa o app leve, e ativa o efeito de desfoque nos cards e na gaveta.

---

## Guardar a chave de assinatura nos Secrets do GitHub (uma vez só)

A chave foi criada na pasta **Documentos → MPersonality-chave** do computador. Lá também tem um `LEIA-ME.txt` com este mesmo passo a passo.

1. Abra https://github.com/Dhcitizen/MPersonality e clique na aba **Settings**.
2. No menu da esquerda: **Secrets and variables → Actions**.
3. Clique em **New repository secret** e crie o primeiro secret:
   - **Name:** `MPERSONALITY_KEYSTORE_BASE64`
   - **Secret:** abra `chave-em-base64.txt` no Bloco de Notas, aperte **Ctrl+A**, depois **Ctrl+C**, e cole aqui.
   - Clique em **Add secret**.
4. Clique de novo em **New repository secret** e crie o segundo:
   - **Name:** `MPERSONALITY_KEYSTORE_PASSWORD`
   - **Secret:** copie e cole o conteúdo de `senha.txt`.
   - Clique em **Add secret**.
5. Vá na aba **Actions**, clique em **Gerar APK** (à esquerda), depois em **Run workflow** e confirme em **Run workflow**.
6. Quando aparecer o ✓ verde (uns 5 minutos), o `MPersonality.apk` estará no link acima.

**Importante:** faça uma cópia da pasta `MPersonality-chave` num lugar seguro, como um pendrive ou Google Drive. Sem essa chave, não dá para atualizar o app sem desinstalar.

---

## Passo a passo do começo (para referência)

1. **Instalar no celular:**
   - Abra o link do APK no Chrome do celular e toque no arquivo baixado.
   - Permita "instalar apps desconhecidos" para o Chrome.
   - Se o Play Protect avisar, toque em **Mais detalhes → Instalar mesmo assim**.
2. **Definir como tela inicial:**
   - Aperte o botão Início e escolha **MPersonality → Sempre**.
   - Ou vá em **Configurações do Android → Apps → Apps padrão → App de tela inicial**.
3. **Voltar para a tela da Motorola:** use o mesmo caminho do passo 2 e escolha a tela inicial original.
4. **Atualizar:** quando o projeto for alterado, o GitHub gera os APKs de novo nos mesmos links. Basta instalar por cima.

---

## Detalhes técnicos
- **Bibliotecas:** só Kotlin + Jetpack Compose (foundation e material3), sem nenhuma outra.
- **Release:** R8 com minify e shrinkResources. A assinatura vem dos Secrets; a chave nunca fica no repositório.
- **Debug:** usa `applicationIdSuffix = ".debug"` e a chave de testes fixa da pasta `keystore/`.
- **Ícones:** desenhados em segundo plano e guardados em cache (LRU). Os da tela inicial são pré-carregados primeiro.
- **Lista de apps:** só é recarregada quando o Android avisa que algum app mudou (`LauncherApps.Callback`).
- **Relógio:** fica numa camada gráfica própria (`graphicsLayer`) e atualiza uma vez por minuto, só com a tela visível. Os relógios analógicos leem a hora só na fase de desenho.
- **Papel de parede:** é decodificado com `inSampleSize`, recortado e salvo na resolução da tela. O desfoque é calculado uma única vez, numa cópia pequena. Com papel de parede próprio, o sistema deixa de desenhar o dele por trás (`FLAG_SHOW_WALLPAPER` desligada).
- **Arrastar:** a posição do dedo é lida só na fase de layout e desenho, então a tela não é recomposta a cada movimento.
- **Armazenamento:** layout, cards e configurações ficam no próprio aparelho (`SharedPreferences`). Sem servidor, sem login.
