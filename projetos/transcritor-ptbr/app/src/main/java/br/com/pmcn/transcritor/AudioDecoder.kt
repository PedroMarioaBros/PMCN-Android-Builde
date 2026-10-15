package br.com.pmcn.transcritor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import android.media.AudioFormat
import kotlin.math.max

object AudioDecoder {
    fun decode(context: Context, uri: Uri): File {
        val pcm = File.createTempFile("decoded_", ".pcm", context.cacheDir)
        var success = false
        val input = File.createTempFile("shared_audio_", ".bin", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                input.outputStream().use { source.copyTo(it) }
            } ?: error("Arquivo não encontrado")
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(input.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("Nenhuma faixa de áudio encontrada")
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Formato desconhecido")
                val codec = MediaCodec.createDecoderByType(mime)
                try {
                    codec.configure(format, null, null, 0)
                    codec.start()
                    var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val sink = pcm.outputStream().buffered()
                    var sourceIndex = 0L
                    var nextPosition = 0.0
                    var previous = 0f
                    var written = 0L
                    var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    var outputDone = false
                    var lastOutput = android.os.SystemClock.elapsedRealtime()
                    try { while (!outputDone) {
                        if (android.os.SystemClock.elapsedRealtime() - lastOutput > 30000) error("Tempo limite ao decodificar áudio")
                        if (!inputDone) {
                            val index = codec.dequeueInputBuffer(10000)
                            if (index >= 0) {
                                val buffer = codec.getInputBuffer(index)!!
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val index = codec.dequeueOutputBuffer(info, 10000)
                        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else android.media.AudioFormat.ENCODING_PCM_16BIT
                            if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT) error("PCM não suportado: $encoding")
                            pcmEncoding = encoding
                        } else if (index >= 0) {
                            lastOutput = android.os.SystemClock.elapsedRealtime()
                            val output = codec.getOutputBuffer(index)!!
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val chunk = output.slice().order(ByteOrder.nativeOrder())
                            val frame: FloatArray = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val floats = chunk.asFloatBuffer()
                                FloatArray(floats.remaining()).also { floats.get(it) }
                            } else {
                                val shorts = chunk.asShortBuffer()
                                FloatArray(shorts.remaining()) { shorts.get().toFloat() / 32768f }
                            }
                            for (i in frame.indices step max(1,channels)) {
                                var sum = 0f
                                for (c in 0 until channels) if (i+c < frame.size) sum += frame[i+c]
                                val current = sum / channels
                                if (sourceIndex == 0L) previous = current
                                while (nextPosition <= sourceIndex.toDouble()) {
                                    val fraction = (nextPosition - (sourceIndex - 1)).coerceIn(0.0, 1.0)
                                    val value = (previous * (1.0 - fraction) + current * fraction).toFloat()
                                    val bits = java.lang.Float.floatToIntBits(value)
                                    sink.write(bits and 255); sink.write((bits ushr 8) and 255)
                                    sink.write((bits ushr 16) and 255); sink.write((bits ushr 24) and 255)
                                    nextPosition += sampleRate.toDouble() / 16000.0
                                    written++
                                    if (written > 16000L * 60 * 180) error("O limite por arquivo é de 3 horas. Divida este áudio em partes menores.")
                                }
                                previous = current
                                sourceIndex++
                            }
                            codec.releaseOutputBuffer(index,false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        }
                    }
                    } finally { sink.close() }
                    if (written == 0L) error("Áudio vazio")
                    success = true
                    return pcm
                } finally { try { codec.stop() } catch (_: Exception) {} ; codec.release() }
            } finally { extractor.release() }
        } finally { input.delete(); if (!success) pcm.delete() }
    }
}
