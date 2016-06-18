/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Erik Ramfelt,
 * Yahoo! Inc., Tom Huybrechts, Olivier Lamy
 * Copyright (c) 2016 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jp.ikedam.jenkins.plugins.gitshallowdepth;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;

import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Util;
import hudson.util.ReflectionUtils;

/**
 *
 */
public class GitShallowDepthJenkinsRule extends JenkinsRule {
    @CheckForNull
    private AbstractMap.SimpleEntry<String, Class<?>> extractDataBoundSetter(Method m) {
        if (!Modifier.isPublic(m.getModifiers())) {
            return null;
        }
        if (!m.getName().startsWith("set")) {
            return null;
        }
        if (m.getParameterTypes().length != 1) {
            return null;
        }
        if (!m.isAnnotationPresent(DataBoundSetter.class)) {
            return null;
        }
        
        // setXyz -> xyz
        return new AbstractMap.SimpleEntry<String, Class<?>>(
                Introspector.decapitalize(m.getName().substring(3)),
                m.getParameterTypes()[0]
        );
    }
    
    private Map<String, Class<?>> extractDataBoundSetterProperties(Class<?> c) {
        Map<String, Class<?>> ret = new HashMap<String, Class<?>>();
        for ( ;c != null; c = c.getSuperclass()) {
            for (Method m: c.getDeclaredMethods()) {
                AbstractMap.SimpleEntry<String, Class<?>> nameAndType = extractDataBoundSetter(m);
                if (nameAndType == null) {
                    continue;
                }
                if (ret.containsKey(nameAndType.getKey())) {
                    continue;
                }
                ret.put(nameAndType.getKey(),  nameAndType.getValue());
            }
        }
        return ret;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void assertEqualDataBoundBeans(Object lhs, Object rhs) throws Exception {
        super.assertEqualDataBoundBeans(lhs, rhs);
        Map<String, Class<?>> lprops = extractDataBoundSetterProperties(lhs.getClass());
        Map<String, Class<?>> rprops = extractDataBoundSetterProperties(rhs.getClass());
        assertThat("Data bound setters mismatch. Different type?", lprops, is(rprops));
        
        List<String> primitiveProperties = new ArrayList<String>();
        
        for (Map.Entry<String, Class<?>> e: lprops.entrySet()) {
            Object lv = ReflectionUtils.getPublicProperty(lhs, e.getKey());
            Object rv = ReflectionUtils.getPublicProperty(rhs, e.getKey());
            
            if (Iterable.class.isAssignableFrom(e.getValue())) {
                Iterable<?> lcol = (Iterable<?>) lv;
                Iterable<?> rcol = (Iterable<?>) rv;
                Iterator<?> ltr, rtr;
                for (ltr = lcol.iterator(), rtr = rcol.iterator(); ltr.hasNext() && rtr.hasNext();) {
                    Object litem = ltr.next();
                    Object ritem = rtr.next();
                    
                    if (findDataBoundConstructor(litem.getClass())!=null) {
                        assertEqualDataBoundBeans(litem,ritem);
                    } else {
                        assertThat(ritem, is(litem));
                    }
                }
                assertThat("collection size mismatch between " + lhs + " and " + rhs, ltr.hasNext() ^ rtr.hasNext(),
                        is(false));
            } else {
                if (
                        findDataBoundConstructor(e.getValue()) != null
                        || (lv != null && findDataBoundConstructor(lv.getClass()) != null)
                        || (rv != null && findDataBoundConstructor(rv.getClass()) != null)
                ) {
                    // recurse into nested databound objects
                    assertEqualDataBoundBeans(lv,rv);
                } else {
                    primitiveProperties.add(e.getKey());
                }
            }
            
            // compare shallow primitive properties
            if (!primitiveProperties.isEmpty()) {
                assertEqualBeans(lhs,rhs,Util.join(primitiveProperties,","));
            }
        }
    }
    
    @Override
    public String createUniqueProjectName() {
        return super.createUniqueProjectName();
    }
}
