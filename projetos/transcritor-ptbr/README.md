# Transcritor PT-BR

Aplicativo Android pessoal para receber áudios compartilhados pelo WhatsApp e transcrever localmente em português, sem API paga.

## Escopo
- Android 13, Moto G52 (arm64)
- Receber `ACTION_SEND` com `audio/*` e `application/ogg`
- Copiar texto transcrito
- Processar exclusivamente no aparelho com Whisper multilíngue
- Nenhum upload de áudio ou envio automático de mensagens

## Estado
Projeto iniciado. **Ainda não há APK validado**. A integração nativa do Whisper e o suporte a arquivos OPUS do WhatsApp precisam de teste real. Não considerar este diretório uma versão pronta para instalação.

## Referência técnica
https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android
