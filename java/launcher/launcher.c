/*
 * Minimal native launcher: starts the JVM of the jlink image in .\runtime
 * inside this process (JNI invocation API) and calls simon.Simon.main.
 * Replaces java.exe / javaw.exe, which are stripped from the image.
 */
#include <windows.h>
#include <jni.h>

typedef jint (JNICALL *CreateJavaVM_t)(JavaVM**, void**, void*);

static void fail(const wchar_t* message) {
    MessageBoxW(NULL, message, L"Simon", MB_ICONERROR);
    ExitProcess(1);
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR command_line, int show) {
    (void)instance; (void)previous; (void)command_line; (void)show;
    wchar_t dir[MAX_PATH], path[MAX_PATH + 64];
    char dir_utf8[MAX_PATH * 3], class_path[MAX_PATH * 3 + 64], library[MAX_PATH * 3 + 64];

    GetModuleFileNameW(NULL, dir, MAX_PATH);
    wchar_t* slash = wcsrchr(dir, L'\\');
    if (slash) *slash = 0;

    wsprintfW(path, L"%s\\runtime\\bin", dir);
    SetDllDirectoryW(path);                     /* jvm.dll needs the DLLs of runtime\bin */
    wsprintfW(path, L"%s\\runtime\\bin\\server\\jvm.dll", dir);
    HMODULE jvm = LoadLibraryW(path);
    if (!jvm) fail(L"runtime\\bin\\server\\jvm.dll not found");
    CreateJavaVM_t create = (CreateJavaVM_t)(void*)GetProcAddress(jvm, "JNI_CreateJavaVM");
    if (!create) fail(L"JNI_CreateJavaVM missing");

    WideCharToMultiByte(CP_UTF8, 0, dir, -1, dir_utf8, sizeof dir_utf8, NULL, NULL);
    wsprintfA(class_path, "-Djava.class.path=%s\\simon.jar", dir_utf8);
    wsprintfA(library, "-Draven.library=%s\\raven.dll", dir_utf8);

    JavaVMOption options[] = {
        { class_path, NULL },
        { library, NULL },
        { "--enable-native-access=ALL-UNNAMED", NULL },
        { "-XX:+UseSerialGC", NULL },
    };
    JavaVMInitArgs args = { JNI_VERSION_21, sizeof options / sizeof options[0], options, JNI_FALSE };
    JavaVM* vm;
    JNIEnv* env;
    if (create(&vm, (void**)&env, &args) != JNI_OK) fail(L"cannot start the JVM");

    jclass main_class = (*env)->FindClass(env, "simon/Simon");
    jmethodID main = main_class ? (*env)->GetStaticMethodID(env, main_class, "main", "([Ljava/lang/String;)V") : NULL;
    if (!main) fail(L"simon.Simon.main not found");
    jobjectArray argv = (*env)->NewObjectArray(env, 0, (*env)->FindClass(env, "java/lang/String"), NULL);
    (*env)->CallStaticVoidMethod(env, main_class, main, argv);
    int code = (*env)->ExceptionCheck(env) ? 1 : 0;
    if (code) (*env)->ExceptionDescribe(env);
    (*vm)->DestroyJavaVM(vm);
    return code;
}
