/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.hk2.xml.internal;

import java.net.URI;

import javax.inject.Singleton;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.xml.api.XmlRootHandle;
import org.glassfish.hk2.xml.api.XmlService;

/**
 * @author jwells
 *
 */
@Singleton
public class XmlServiceImpl implements XmlService {
    private final JAUtilities jaUtilities = new JAUtilities();
    
    /* (non-Javadoc)
     * @see org.glassfish.hk2.xml.api.XmlService#unmarshall(java.net.URI, java.lang.Class, boolean, boolean)
     */
    @Override
    public <T> XmlRootHandle<T> unmarshall(URI uri,
            Class<T> jaxbAnnotatedClassOrInterface) {
        return unmarshall(uri, jaxbAnnotatedClassOrInterface, true, true);
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.xml.api.XmlService#unmarshall(java.net.URI, java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> XmlRootHandle<T> unmarshall(URI uri,
            Class<T> jaxbAnnotatedClassOrInterface,
            boolean advertiseInRegistry, boolean advertiseInHub) {
        Class<T> originalClass = jaxbAnnotatedClassOrInterface;
        
        if (uri == null || jaxbAnnotatedClassOrInterface == null) throw new IllegalArgumentException();
        
        try {
            if (jaxbAnnotatedClassOrInterface.isInterface()) {
                jaxbAnnotatedClassOrInterface = (Class<T>) jaUtilities.convertRootAndLeaves(jaxbAnnotatedClassOrInterface);
            }
        
            return unmarshallClass(uri, jaxbAnnotatedClassOrInterface, originalClass);
        }
        catch (RuntimeException re) {
            throw re;
        }
        catch (Exception e) {
            throw new MultiException(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private <T> XmlRootHandle<T> unmarshallClass(URI uri, Class<T> jaxbAnnotatedClass, Class<T> originalClass) throws Exception {
        JAXBContext context = JAXBContext.newInstance(jaxbAnnotatedClass);
        
        Unmarshaller unmarshaller = context.createUnmarshaller();
        T root = (T) unmarshaller.unmarshal(uri.toURL());
        
        return new XmlRootHandleImpl<T>(root, originalClass, uri);
    }

    /* (non-Javadoc)
     * @see org.glassfish.hk2.xml.api.XmlService#createEmptyHandle(java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> XmlRootHandle<T> createEmptyHandle(
            Class<T> jaxbAnnotationInterface) {
        try {
            if (jaxbAnnotationInterface.isInterface()) {
                jaUtilities.convertRootAndLeaves(jaxbAnnotationInterface);
            }
        
            return new XmlRootHandleImpl<T>(null, jaxbAnnotationInterface, null);
        }
        catch (RuntimeException re) {
            throw re;
        }
        catch (Exception e) {
            throw new MultiException(e);
        }
    }

    

}
