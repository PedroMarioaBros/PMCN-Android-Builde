package br.com.pmcn.transcritor

object WhisperBridge {
    init { System.loadLibrary("vozemtexto") }
    external fun transcribe(modelPath: String, pcm: FloatArray): String
}
