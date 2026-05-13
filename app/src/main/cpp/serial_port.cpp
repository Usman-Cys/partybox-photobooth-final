#include <jni.h>
#include <fcntl.h>
#include <termios.h>
#include <unistd.h>
#include <sys/select.h>
#include <errno.h>
#include <string>

static speed_t toSpeed(int baudRate) {
    switch (baudRate) {
        case 9600: return B9600;
        case 19200: return B19200;
        case 38400: return B38400;
        case 57600: return B57600;
        case 115200: return B115200;
        default: return B115200;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_photobooth_hardware_NativeSerialPort_openNative(JNIEnv *env, jobject /*thiz*/, jstring path, jint baudRate) {
    const char *pathChars = env->GetStringUTFChars(path, nullptr);
    int fd = open(pathChars, O_RDWR | O_NOCTTY | O_SYNC);
    env->ReleaseStringUTFChars(path, pathChars);
    if (fd < 0) {
        return -1;
    }

    struct termios tty{};
    if (tcgetattr(fd, &tty) != 0) {
        close(fd);
        return -2;
    }

    cfmakeraw(&tty);
    speed_t speed = toSpeed(baudRate);
    cfsetospeed(&tty, speed);
    cfsetispeed(&tty, speed);
    tty.c_cflag = (tty.c_cflag & ~CSIZE) | CS8;
    tty.c_cflag |= CLOCAL | CREAD;
    tty.c_cflag &= ~(PARENB | PARODD);
    tty.c_cflag &= ~CSTOPB;
    tty.c_cflag &= ~CRTSCTS;

    if (tcsetattr(fd, TCSANOW, &tty) != 0) {
        close(fd);
        return -3;
    }

    tcflush(fd, TCIOFLUSH);
    return fd;
}

extern "C" JNIEXPORT void JNICALL
Java_com_photobooth_hardware_NativeSerialPort_closeNative(JNIEnv * /*env*/, jobject /*thiz*/, jint fd) {
    if (fd >= 0) {
        close(fd);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_photobooth_hardware_NativeSerialPort_writeNative(JNIEnv *env, jobject /*thiz*/, jint fd, jbyteArray data, jint timeoutMs) {
    if (fd < 0) {
        return -1;
    }

    jsize len = env->GetArrayLength(data);
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);

    fd_set writeSet;
    FD_ZERO(&writeSet);
    FD_SET(fd, &writeSet);
    struct timeval tv{};
    tv.tv_sec = timeoutMs / 1000;
    tv.tv_usec = (timeoutMs % 1000) * 1000;

    int ready = select(fd + 1, nullptr, &writeSet, nullptr, &tv);
    if (ready <= 0) {
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
        return -2;
    }

    int written = write(fd, bytes, len);
    tcdrain(fd);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return written;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_photobooth_hardware_NativeSerialPort_readNative(JNIEnv *env, jobject /*thiz*/, jint fd, jint count, jint timeoutMs) {
    if (fd < 0 || count <= 0) {
        return env->NewByteArray(0);
    }

    std::string buffer(static_cast<size_t>(count), '\0');
    int total = 0;

    while (total < count) {
        fd_set readSet;
        FD_ZERO(&readSet);
        FD_SET(fd, &readSet);

        struct timeval tv{};
        tv.tv_sec = timeoutMs / 1000;
        tv.tv_usec = (timeoutMs % 1000) * 1000;

        int ready = select(fd + 1, &readSet, nullptr, nullptr, &tv);
        if (ready <= 0) {
            break;
        }

        int chunk = static_cast<int>(::read(fd, &buffer[static_cast<size_t>(total)], static_cast<size_t>(count - total)));
        if (chunk <= 0) {
            break;
        }
        total += chunk;
    }

    jbyteArray arr = env->NewByteArray(total);
    if (total > 0) {
        env->SetByteArrayRegion(arr, 0, total, reinterpret_cast<const jbyte *>(buffer.data()));
    }
    return arr;
}
