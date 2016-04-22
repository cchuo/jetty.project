//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.common.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Provide argument utilities for working with methods that
 * have a dynamic assortment of arguments.
 * <ol>
 * <li>Can identify a set of parameters as matching the Builder</li>
 * <li>Can create a DynamicArgs for the matched signature</li>
 * <li>Can create an argument array for the provided potential arguments,
 * suitable to be used with {@link Method#invoke(Object, Object...)}</li>
 * </ol>
 */
public class DynamicArgs
{
    public static interface Signature
    {
        /**
         * Predicate to test if signature matches
         *
         * @return the predicate to test if signature matches
         */
        public BiPredicate<Method, Class<?>[]> getPredicate();

        /**
         * BiFunction to use to invoke method
         * against give object, with provided (potential) arguments,
         * returning appropriate result from invocation.
         *
         * @param method
         *            the method to base BiFunction off of.
         * @param callArgs
         *            the description of arguments passed into each {@link DynamicArgs#invoke(Object, Object...)}
         *            call in the future. Used to map the incoming arguments to the method arguments.
         * @return the return result of the invoked method
         */
        public BiFunction<Object, Object[], Object> getInvoker(Method method, DynamicArgs.Arg... callArgs);

        public void appendDescription(StringBuilder str);
    }

    public static class Arg
    {
        public final Class<?> type;
        public Method method;
        public int index;
        public Object tag;

        public Arg(Class<?> type)
        {
            this.type = type;
        }

        public Arg(int idx, Class<?> type)
        {
            this.index = idx;
            this.type = type;
        }

        public Arg(Method method, int idx, Class<?> type)
        {
            this.method = method;
            this.index = idx;
            this.type = type;
        }

        public Arg setTag(String tag)
        {
            this.tag = tag;
            return this;
        }

        @Override
        public String toString()
        {
            return String.format("%s[%d%s]",type.getName(),index,tag == null ? "" : "/" + tag);
        }

        public <T extends Annotation> T getAnnotation(Class<T> annoClass)
        {
            if(method == null)
                return null;

            Annotation annos[] = method.getParameterAnnotations()[index];
            if(annos != null || (annos.length > 0))
            {
                for(Annotation anno: annos)
                {
                    if(anno.annotationType().equals(annoClass))
                    {
                        return (T) anno;
                    }
                }
            }
            return null;
        }
    }

    public static class Builder
    {
        private List<Signature> signatures = new ArrayList<>();

        public DynamicArgs build(Method method, Arg... callArgs)
        {
            // FIXME: add DynamicArgs build cache (key = method+callargs)

            Class<?> paramTypes[] = method.getParameterTypes();
            for (Signature sig : signatures)
            {
                if (sig.getPredicate().test(method, paramTypes))
                {
                    return new DynamicArgs(sig.getInvoker(method,callArgs));
                }
            }

            return null;
        }

        /**
         * Used to identify a possible method signature match.
         *
         * @param method the method to test
         * @return true if it is a match
         */
        public boolean hasMatchingSignature(Method method)
        {
            // FIXME: add match cache (key = method)

            Class<?> paramTypes[] = method.getParameterTypes();
            for (Signature sig : signatures)
            {
                if (sig.getPredicate().test(method, paramTypes))
                {
                    return true;
                }
            }

            return false;
        }

        public Builder addSignature(Signature sig)
        {
            signatures.add(sig);
            return this;
        }

        public void appendDescription(StringBuilder err)
        {
            for (Signature sig : signatures)
            {
                err.append(System.lineSeparator());
                sig.appendDescription(err);
            }
        }
    }

    private static List<ArgIdentifier> argIdentifiers;

    public static List<ArgIdentifier> lookupArgIdentifiers()
    {
        if (argIdentifiers == null)
        {
            ServiceLoader<ArgIdentifier> loader = ServiceLoader.load(ArgIdentifier.class);
            argIdentifiers = new ArrayList<>();
            for (ArgIdentifier argId : loader)
            {
                argIdentifiers.add(argId);
            }
        }

        return argIdentifiers;
    }

    private final BiFunction<Object, Object[], Object> invoker;

    private DynamicArgs(BiFunction<Object, Object[], Object> invoker)
    {
        this.invoker = invoker;
    }

    /**
     * Invoke the signature / method with the provided potential args.
     *
     * @param o
     *            the object to call method on
     * @param potentialArgs
     *            the potential args in the same order as the FIXME
     * @return the response object from the invoke
     * @throws IllegalAccessException
     *             if unable to access the method or object
     * @throws IllegalArgumentException
     *             if call to method has invalid/illegal arguments
     * @throws InvocationTargetException
     *             if unable to invoke the method on the object
     */
    public Object invoke(Object o, Object... potentialArgs) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        return invoker.apply(o,potentialArgs);
    }
}
