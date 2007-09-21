package com.sun.hk2.component;

import org.jvnet.hk2.annotations.*;
import org.jvnet.hk2.component.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Collection;

/**
 * Creates an object from its constructor.
 * @author Kohsuke Kawaguchi
 */
public class ConstructorWomb<T> extends AbstractWombImpl<T> {
    private final Habitat habitat;
    private final ScopeInstance singletonScope;


    public ConstructorWomb(Class<T> type, Habitat habitat, MultiMap<String,String> metadata) {
        super(type,metadata);
        this.habitat = habitat;
        singletonScope = habitat.singletonScope;
    }

    public T create() throws ComponentException {
        try {
            return type.newInstance();
        } catch (InstantiationException e) {
            throw new ComponentException("Failed to create "+type,e);
        } catch (IllegalAccessException e) {
            throw new ComponentException("Failed to create "+type,e);
        }
    }


    public void initialize(T t) throws ComponentException {

        Scoped scoped = t.getClass().getAnnotation(Scoped.class);
        ScopeInstance si = (scoped==null?singletonScope:getScope(scoped));


        (new InjectionManager<Inject>() {
            public boolean isOptional(Inject annotation) {
                return annotation.optional();
            }

            /**
             * Obtains the value to inject, based on the type and {@link Inject} annotation.
             */
            @SuppressWarnings("unchecked")
            protected Object getValue(AnnotatedElement target, Class type) throws ComponentException {
                if (type.isArray()) {
                    Class<?> ct = type.getComponentType();

                    Contract ctr = ct.getAnnotation(Contract.class);
                    if(ctr!=null) {
                        Collection instances = habitat.getAllByContract(ct);
                        return instances.toArray((Object[]) Array.newInstance(ct, instances.size()));
                    }
                } else {
                    Annotation ctr = type.getAnnotation(Contract.class);
                    if(ctr!=null)
                        // service lookup injection

                        return habitat.getComponent(type, target.getAnnotation(Inject.class).name());

                    // ideally we should check if type has @Service or @Configured

                    // component injection
                    return habitat.getByType(type);

                }
                throw new ComponentException("%s cannot be injected: it's neither a contract nor a service",type);
            }
        }).inject(t, Inject.class);

        // postContruct call if any
        if(t instanceof PostConstruct)
            ((PostConstruct)t).postConstruct();


        if(si!=null)
            // extraction amounts to no-op if this is prototype scope. so skip that.
            extract(t, si);

    }

    /**
     * Extracts resources identified with {@link org.jvnet.hk2.annotations.Extract} annotations
     * into the given scope.
     *
     * <p>
     * This method is for the use within HK2, and not really meant
     * to be used by application code.
     *
     * @param component the component we should extract resources from
     * @throws ComponentException if the resource extract fail
     */
    public void extract(Object component, ScopeInstance si) throws ComponentException {
        Class<?> currentClass = component.getClass();
        while (!currentClass.equals(Object.class)) {
            for (Field field : currentClass.getDeclaredFields()) {
                Extract extract = field.getAnnotation(Extract.class);
                if (extract == null)    continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(component);
                    Class<?> type = field.getType();

//                    if (LOGGER.isLoggable(Level.FINER)) {
//                        LOGGER.log(Level.FINER, "Extracting resource " + value + " returned from " + field);
//                    }
                    if (value!=null) {
                        extractValue(value, si, type);
                    } else {
//                        if (LOGGER.isLoggable(Level.FINE)) {
//                            LOGGER.log(Level.FINE, "Resource returned from " + field + " is null");
//                        }
                    }
                } catch (IllegalArgumentException ex) {
                    throw new ComponentException("Extraction failed on " + field, ex);

                } catch (IllegalAccessException ex) {
                    throw new ComponentException("Extraction failed on " + field, ex);
                }
            }
            for (Method method : currentClass.getDeclaredMethods()) {
                Extract extract = method.getAnnotation(Extract.class);
                if (extract == null)    continue;

                Class<?> type = method.getReturnType();
                if (type == null) {
                    throw new ComponentException("Extraction failed : %s has a void return type",method);
                }
                if (method.getParameterTypes().length > 0) {
                    throw new ComponentException("Extraction failed : %s takes parameters, it should not",method);
                }
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(component);
//                    if (LOGGER.isLoggable(Level.FINER)) {
//                        LOGGER.log(Level.FINER, "Extracting resource " + value + " returned from " + method);
//                    }
                    if (value!=null) {
                        extractValue(value, si, type);
                    } else {
//                        if (LOGGER.isLoggable(Level.FINE)) {
//                            LOGGER.log(Level.FINE, "Resource returned from " + method + " is null");
//                        }
                    }
                } catch (IllegalArgumentException ex) {
                    throw new ComponentException("Extraction failed on " + method.toGenericString(), ex);
                } catch (InvocationTargetException ex) {
                    throw new ComponentException("Extraction failed on " + method.toGenericString(), ex);
                } catch (IllegalAccessException ex) {
                    throw new ComponentException("Extraction failed on " + method.toGenericString(), ex);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    @SuppressWarnings("unchecked")
    private void extractValue(Object value, ScopeInstance si, Class type) {
        if(value instanceof Iterable) {
            for (Object o : (Iterable)value) {
                extractSingleValue(o,si);
            }
        } else
        if (type.isArray()) {
            Object[] values = (Object[]) value;
            for (Object o : values) {
                extractSingleValue(o,si);
            }
        } else {
            extractSingleValue(value,si);
        }
    }

    private <T> void extractSingleValue(T o, final ScopeInstance si) {
        // TODO: name support. Wouldn't it be nice if Map<String,Object> extracts to named objects?
        // or recognize the "Named" interface?
        Inhabitant<T> i = new AbstractWombImpl<T>((Class<T>)o.getClass(),MultiMap.<String,String>emptyMap()) {
            public T create() throws ComponentException {
                // only look at objects already available in the scope.
                // TODO: this obviously can return null. think about how to reconcile this semantics with the rest
                return si.get(this);
            }
        };
        habitat.add(i);
        si.put(i,o);
    }

    /**
     * Determines the {@link ScopeInstance} that stores the component.
     *
     * @return
     *      null for prototype scope. (Note that in {@link Scope#current()}
     *      null return value is an error.)
     */
    private ScopeInstance getScope(Scoped svc) throws ComponentException {
        Class<? extends Scope> s = svc.value();
        // for performance reason and to avoid infinite recursion,
        // recognize these two fundamental built-in scopes and process them differently.
        if(s==Singleton.class)
            return singletonScope;
        if(s==PerLookup.class)
            return null;

        // for all the other scopes, including user-defined ones.
        Scope scope = habitat.getByType(s);
        ScopeInstance si = scope.current();
        if(si==null) // scope is an extension point, so beware for broken implementations
            throw new ComponentException(scope+" returned null from the current() method");
        return si;
    }
    
}
