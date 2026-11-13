package com.videodl.engine

class NativeBridge {
    external fun ffmpegAvailable(): Boolean

    companion object {
        init {
            System.loadLibrary("videodl_native")
        }
    }
}
