package com.oracle.svm.agentproxy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ProxyAgent {
    record AgentCommand(String premainClass, String opts) {

    }

    public static void premain(
            String agentArgs, Instrumentation inst) {
        List<AgentCommand> targetAgents = new ArrayList<>();
        if (agentArgs.startsWith("@")) {
            String optionFile = agentArgs.substring(1);
            try (BufferedReader br = new BufferedReader(new FileReader(optionFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    int spliterPos = line.indexOf(":");
                    String targetClass;
                    String opts = null;
                    if (spliterPos == -1) {
                        targetClass = line;
                    } else {
                        targetClass = line.substring(0, spliterPos);
                        opts = line.substring(spliterPos + 1);
                    }
                    targetAgents.add(new AgentCommand(targetClass, opts));
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot load ProxyAgent because failed to load the specified option file "
                        + optionFile, e);
            }

        } else if (agentArgs.startsWith("target")) {
            int spliterPos = agentArgs.indexOf(",");
            String targetSub;
            String optionSub = null;
            if (spliterPos == -1) {
                targetSub = agentArgs;
            } else {
                targetSub = agentArgs.substring(0, spliterPos);
                optionSub = agentArgs.substring(spliterPos + 1);
            }
            String[] targetOpt = targetSub.split("=");
            if (targetOpt.length != 2) {
                throw new RuntimeException("Cannot load ProxyAgent because target option was not correctly set."
                        + " It expects a value and no more than one =. But is " + agentArgs);
            }
            AgentCommand agentCommand = new AgentCommand(targetOpt[1], optionSub);
            targetAgents.add(agentCommand);
        } else {
            throw new RuntimeException("Cannot load ProxyAgent because the agent option must be started with" +
                    "@ or target, but is " + agentArgs);
        }

        targetAgents.forEach(entry -> {
            String className = entry.premainClass;
            String opts = entry.opts;
            Class<?> targetAgentClass;
            try {
                targetAgentClass = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't find the proxy target class " + className, e);
            }
            Method premain;
            try {
                premain = targetAgentClass.getDeclaredMethod("premain", String.class, Instrumentation.class);
            } catch (NoSuchMethodException e) {
                try {
                    premain = targetAgentClass.getDeclaredMethod("premain", String.class);
                } catch (NoSuchMethodException e1) {
                    throw new RuntimeException("Can't find premain method in " + targetAgentClass, e1);
                }
            }
            Constructor<?> agentConstructor;
            try {
                agentConstructor = targetAgentClass.getConstructor();
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Can't find the default constructor of target agent class " + className, e);
            }
            Object agentInstance;
            try {
                agentInstance = agentConstructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Can't initiate instance for target agent class " + className, e);
            }
            Object[] targetArgs;
            if (premain.getParameterCount() == 1) {
                targetArgs = new Object[1];
            } else {
                targetArgs = new Object[2];
                targetArgs[1] = InstrumentationProxy.createProxy(inst);
            }
            targetArgs[0] = opts;
            try {
                premain.invoke(agentInstance, targetArgs);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
