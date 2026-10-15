# Voz em Texto 0.3.0

Aplicativo gratuito para Android, com reconhecimento local em português usando Whisper.cpp v1.7.6 e Whisper Base multilíngue. O APK inclui ARM64 (Moto G52) e x86_64 (emulador). O modelo é baixado na primeira transcrição; os áudios não são enviados a servidores.

## Instalação e uso

1. Baixe o APK e abra-o no Android. Autorize a instalação por essa fonte quando solicitado.
2. Compartilhe um áudio do WhatsApp com **Voz em Texto**, ou toque em **Escolher arquivo de áudio**.
3. A transcrição começa automaticamente. Na primeira utilização, mantenha a internet disponível para baixar cerca de 148 MB do modelo.
4. Mantenha o aplicativo aberto durante o processamento. Depois, use **Copiar texto**.
5. Após o download, o modelo permanece no armazenamento privado e permite transcrever offline.

## Implementação e correções

- Java e Kotlin alinhados em JVM 17; Gradle 8.9, AGP 8.7.3, NDK 27.2.12479018 e CMake 3.22.1.
- Corrigida a propriedade Kotlin `isFillViewport`, que impedia a compilação da interface.
- Whisper fixado no commit `a8d002cfd879315632a579e73f0148d06959de36`, com otimização nativa `-O3`, sem GPU e sem instruções ARM específicas da máquina de compilação.
- MediaExtractor/MediaCodec decodificam o áudio; o PCM é convertido para mono a 16 kHz e gravado em arquivo temporário, sem uma lista contendo todo o áudio em memória.
- O reconhecimento lê blocos de até cinco minutos. O limite implementado por arquivo é de três horas; arquivos inválidos, erros de leitura/download e memória insuficiente recebem mensagens de erro. A duração máxima não foi validada em aparelho físico.
- O modelo é verificado pelo SHA-1 oficial `465707469ff3a37a2b9b8d8f89f2f99de7299dac`. Downloads incompletos são descartados.
- Seleção pelo seletor de documentos do Android e recebimento de `ACTION_SEND`, incluindo `audio/*` e `application/ogg`, sem exigir acesso amplo aos arquivos do aparelho.
- Reconhecimento forçado para português, sem tradução ou resumo; texto selecionável e botão para copiar.

## Validação

O workflow `.github/workflows/transcritor-ptbr.yml` compila o aplicativo e os testes, verifica a assinatura com `apksigner` e a presença da biblioteca ARM64, e executa testes instrumentados em Android 13 x86_64.

Os testes usam uma fala sintética em português convertida para OGG/Opus, com Wi-Fi e dados móveis desativados após instalar o modelo. Cobrem decodificação e reconhecimento, rejeição de arquivo inválido, recebimento por URI compartilhada e cópia para a área de transferência. O compartilhamento é simulado pelo mecanismo do Android; não é um teste com o aplicativo WhatsApp instalado.

### Resultado em 15/10/2026

APK do commit `ec0d509c326f706b70b72f0ff524a7a1c1c887cf`, execução https://github.com/PedroMarioaBros/PMCN-Android-Builde/actions/runs/38819978397.

- Compilação, assinatura, instalação e abertura no Android 13: concluídas.
- Rejeição de arquivo inválido: passou.
- Compartilhamento por URI, transcrição offline e botão de copiar: passou.
- Teste de reconhecimento OGG/Opus: decodificou e transcreveu, mas falhou na exigência de reconhecer literalmente a palavra “português”. Portanto, a suíte terminou com **2 testes aprovados e 1 falha de precisão**, sem erro de execução. A execução não está verde e a falha não foi ocultada.
- A frase inicial foi reconhecida: “Olá! Este é um teste de transcrição de voz em texto.” As frases seguintes da voz sintética tiveram erros. Consulte `VALIDACAO.txt` para a saída integral.
- Cada transcrição do exemplo levou aproximadamente 173 segundos nesse emulador. Esse tempo não é uma medição do Moto G52.
- SHA-256 do APK: `7cc8584bf996b9674d5e28d4ccb129e8599b5ab08ac9873d0dde441dff2a9ec7`.

A publicação é uma versão de testes; a precisão precisa ser avaliada com mensagens reais no aparelho.

## Limitações

- Ainda não testado em Moto G52 físico. Desempenho, consumo de bateria, seleção manual pela interface e compartilhamento a partir do WhatsApp real precisam dessa validação.
- Mantenha o aplicativo aberto durante a transcrição; não há serviço de processamento em segundo plano.
- O reconhecimento pode errar palavras, nomes e pontuação. A divisão em blocos pode afetar palavras próximas às bordas em áudios longos.
- APK assinado para testes, sem publicação na Play Store. Não contém anúncios, assinatura nem API paga.
