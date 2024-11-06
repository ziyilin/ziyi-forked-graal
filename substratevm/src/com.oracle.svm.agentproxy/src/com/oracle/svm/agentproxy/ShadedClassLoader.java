package com.oracle.svm.agentproxy;


public class ShadedClassLoader extends ClassLoader{

    public ShadedClassLoader(ClassLoader parent){
        super("shadedClassLoader", parent);
    }

    public Class<?> defineClass(String name, byte[] b)
            throws ClassFormatError {
        return super.defineClass(name, b, 0, b.length);
    }
}
