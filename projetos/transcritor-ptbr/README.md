# Voz em Texto — Transcritor PT-BR

Aplicativo pessoal Android (Moto G52 / Android 13 / arm64) para receber áudios compartilhados do WhatsApp e transcrever em português **localmente**, usando Whisper.cpp.

## Funcionamento previsto
1. Instalar o APK de debug.
2. Compartilhar um áudio do WhatsApp com **Voz em Texto**, ou escolher um arquivo de áudio dentro do aplicativo.
3. Tocar em **Transcrever áudio**.
4. Na primeira execução, baixar gratuitamente o modelo Whisper Base multilíngue (cerca de 148 MB; requer internet).
5. Aguardar o processamento local e copiar a transcrição.

O aplicativo não envia mensagens no WhatsApp nem faz upload dos áudios. Após baixar o modelo, a transcrição foi projetada para funcionar offline.

## Compilação automatizada
Workflow: `.github/workflows/transcritor-ptbr.yml`.
O workflow baixa whisper.cpp v1.7.6 e compila o APK debug usando Gradle, Android SDK e NDK.
Quando a execução terminar com sucesso, baixar o artefato **transcritor-ptbr-debug** na aba Actions do repositório.

## Limitações e testes pendentes
**Ainda não foi confirmada uma compilação bem-sucedida nem testado o APK em um Moto G52.**
O compartilhamento de mensagens de voz depende das opções oferecidas pela versão do WhatsApp.
Os codecs OPUS/OGG e o desempenho de Whisper Base no dispositivo exigem validação prática.
O primeiro download do modelo necessita de internet e espaço disponível.
O reconhecimento pode conter erros; conferir nomes próprios e informações importantes.

## Tecnologias
Kotlin, Android MediaExtractor/MediaCodec, CMake/NDK, whisper.cpp e modelo Whisper Base multilíngue.
Sem APIs comerciais ou mensalidades.
