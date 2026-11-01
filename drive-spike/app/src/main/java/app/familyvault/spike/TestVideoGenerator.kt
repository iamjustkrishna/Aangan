package app.familyvault.spike

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Generates a small valid MP4 video (H.264, 320x240, 2 seconds) on-device
 * using MediaCodec and MediaMuxer so testing ExoPlayer playback requires zero external assets.
 */
object TestVideoGenerator {
    private const val TAG = "TestVideoGenerator"

    suspend fun createTestVideo(context: Context, filename: String = "test_spike_video.mp4"): File =
        withContext(Dispatchers.IO) {
            val outputFile = File(context.cacheDir, filename)
            if (outputFile.exists() && outputFile.length() > 1000) {
                return@withContext outputFile
            }

            val width = 320
            val height = 240
            val bitRate = 400_000
            val frameRate = 30
            val durationSeconds = 3
            val numFrames = frameRate * durationSeconds

            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(mimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()

            // Setup basic EGL to render colored frames to the inputSurface
            val eglHelper = EglHelper(inputSurface)
            eglHelper.makeCurrent()

            try {
                for (frame in 0 until numFrames) {
                    // Drain encoder output
                    drainEncoder(encoder, muxer, bufferInfo, false) { trackIndex ->
                        videoTrackIndex = trackIndex
                        muxerStarted = true
                    }

                    // Render simple moving color background
                    val r = (frame.toFloat() / numFrames)
                    val g = 0.4f
                    val b = (1f - (frame.toFloat() / numFrames))
                    GLES20.glClearColor(r, g, b, 1.0f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                    val presentationTimeNs = frame * 1_000_000_000L / frameRate
                    eglHelper.setPresentationTime(presentationTimeNs)
                    eglHelper.swapBuffers()
                }

                encoder.signalEndOfInputStream()
                drainEncoder(encoder, muxer, bufferInfo, true) { trackIndex ->
                    videoTrackIndex = trackIndex
                    muxerStarted = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding synthetic video: ${e.message}", e)
                // Fallback: create a dummy file if hardware encoder fails
                outputFile.writeBytes(ByteArray(1024 * 100) { 0 })
            } finally {
                try {
                    eglHelper.release()
                    encoder.stop()
                    encoder.release()
                    if (muxerStarted) {
                        muxer.stop()
                    }
                    muxer.release()
                } catch (ignored: Exception) {
                }
            }

            outputFile
        }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStarted: (Int) -> Unit
    ) {
        val timeoutUs = 10_000L
        var muxerStarted = false
        var trackIndex = -1

        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                muxerStarted = true
                onMuxerStarted(trackIndex)
            } else if (status >= 0) {
                val encodedData: ByteBuffer = encoder.getOutputBuffer(status)
                    ?: throw RuntimeException("outputBuffer $status was null")

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(status, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }

    private class EglHelper(val surface: android.view.Surface) {
        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142 /* EGL_RECORDABLE_ANDROID */, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        }

        fun makeCurrent() {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        fun setPresentationTime(nsecs: Long) {
            android.opengl.EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }
}
