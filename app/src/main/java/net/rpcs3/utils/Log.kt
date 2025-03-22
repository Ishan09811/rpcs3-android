
package net.rpcs3.utils

object Log {
    external fun log(level: Int, message: String)

    fun d(message: String) = log(0, message)
    fun i(message: String) = log(1, message)
    fun w(message: String) = log(2, message)
    fun e(message: String) = log(3, message)
}

  
