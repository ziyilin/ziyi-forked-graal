package com.oracle.svm.agentproxy;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class InstrumentationProxy implements InvocationHandler {

    private Instrumentation originalInst;

    private InstrumentationProxy(Instrumentation originalInst) {
        this.originalInst = originalInst;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("addTransformer") && args.length == 2) {
            ClassFileTransformer classFileTransformerProxy = ClassFileTransformerProxy.createProxy((ClassFileTransformer) args[0]);
            args[0] = classFileTransformerProxy;
            return method.invoke(originalInst, args);
        } else {
            return method.invoke(originalInst, args);
        }
    }

    public static Instrumentation createProxy(Instrumentation target) {
        return (Instrumentation) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{Instrumentation.class},
                new InstrumentationProxy(target));
    }
}
