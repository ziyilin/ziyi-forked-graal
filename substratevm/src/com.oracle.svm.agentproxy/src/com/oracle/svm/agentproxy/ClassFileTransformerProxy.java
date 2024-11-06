package com.oracle.svm.agentproxy;

import jdk.internal.module.ModuleLoaderMap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;

public class ClassFileTransformerProxy implements InvocationHandler {
    private static final Set<String> SYSTEM_MODULES = Set.of("org.graalvm.nativeimage.builder", "org.graalvm.nativeimage", "org.graalvm.nativeimage.base", "com.oracle.svm.svm_enterprise",
            "org.graalvm.word", "jdk.internal.vm.ci", "jdk.graal.compiler", "com.oracle.graal.graal_enterprise");
    private ClassFileTransformer target;
    private static ShadedClassLoader shadedClassLoader = new ShadedClassLoader(ClassLoader.getSystemClassLoader());
    private Map<String, Class<?>> shadedClasses = new HashMap<>();

    private ClassFileTransformerProxy(ClassFileTransformer target) {
        this.target = target;
    }

    /**
     * {@link ClassFileTransformer} interface has two {@code transform} methods. Here delegates both of them.
     *
     * @param proxy  the proxy instance that the method was invoked on
     * @param method the {@code Method} instance corresponding to
     *               the interface method invoked on the proxy instance.  The declaring
     *               class of the {@code Method} object will be the interface that
     *               the method was declared in, which may be a superinterface of the
     *               proxy interface that the proxy class inherits the method through.
     * @param args   an array of objects containing the values of the
     *               arguments passed in the method invocation on the proxy instance,
     *               or {@code null} if interface method takes no arguments.
     *               Arguments of primitive types are wrapped in instances of the
     *               appropriate primitive wrapper class, such as
     *               {@code java.lang.Integer} or {@code java.lang.Boolean}.
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Module module;
        byte[] originalClass;
        String className;
        if (args[0] instanceof Module) {
            module = (Module) args[0];
            originalClass = (byte[]) args[5];
            className = (String) args[2];
        } else {
            Class<?> classBeingRedefined = (Class<?>) args[3];
            module = classBeingRedefined == null ? null : classBeingRedefined.getModule();
            originalClass = (byte[]) args[4];
            className = (String) args[1];
        }
        String moduleName = module == null ? null : module.getName();
        try {
            byte[] ret = (byte[]) method.invoke(target, args);
            if (ret != null && !ret.equals(originalClass)) {
                if (moduleName != null &&
                        (ModuleLoaderMap.bootModules().contains(moduleName) || ModuleLoaderMap.platformModules().contains(moduleName)
                        || SYSTEM_MODULES.contains(moduleName))) {
                    shadeClass(className, ret);
                    return null;
                } else {
                    return ret;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
        return null;
    }

    private void shadeClass(String internalClassName, byte[] transformedClassBytes) {
        ClassReader classReader = new ClassReader(transformedClassBytes);
        ClassWriter classWriter = new ClassWriter(0);

        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM8, classWriter) {

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                String newName = "shaded/" + name.substring(0, name.lastIndexOf('/')) + name.substring(name.lastIndexOf('/'));
                super.visit(version, access, newName, signature, superName, interfaces);
            }
        };

        classReader.accept(classVisitor, 0);
        String className = "shaded." + internalClassName.replace("/", ".");
        Class<?> c = shadedClassLoader.defineClass(className, classWriter.toByteArray());
        shadedClasses.putIfAbsent(className, c);
    }

    public static ClassFileTransformer createProxy(ClassFileTransformer target) {
        return (ClassFileTransformer) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{ClassFileTransformer.class},
                new ClassFileTransformerProxy(target));
    }
}
