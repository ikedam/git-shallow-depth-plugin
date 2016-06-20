/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc., CloudBees, Inc.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.InternetAddress;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.User;
import hudson.model.AbstractBuild.DependencyChange;
import hudson.plugins.emailext.EmailRecipientUtils;
import hudson.plugins.emailext.ExtendedEmailPublisherContext;
import hudson.plugins.emailext.plugins.RecipientProvider;
import hudson.plugins.emailext.plugins.RecipientProviderDescriptor;
import hudson.scm.ChangeLogSet.Entry;

/**
 *
 */
public class RootCulpritsRecipientProvider extends RecipientProvider {
    @DataBoundConstructor
    public RootCulpritsRecipientProvider() {
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addRecipients(ExtendedEmailPublisherContext context, EnvVars env, Set<InternetAddress> to, Set<InternetAddress> cc, Set<InternetAddress> bcc) {
        Set<User> users = getCulprits(context.getBuild());
        for (User user: users) {
            if (!EmailRecipientUtils.isExcludedRecipient(user, context.getListener())) {
                String userAddress = EmailRecipientUtils.getUserConfiguredEmail(user);
                if (userAddress != null) {
                    EmailRecipientUtils.addAddressesFromRecipientList(to, cc, bcc, userAddress, env, context.getListener());
                }
            }
        }
    }
    
    /**
     * @param build
     * @return
     * @see AbstractBuild#getCulprits()
     */
    private Set<User> getCulprits(AbstractBuild<?, ?> build) {
        Set<User> r = new HashSet<User>();
        AbstractBuild<?, ?> p = build.getPreviousCompletedBuild();
        if (p != null && p.isBuilding()) {
            Result pr = p.getResult();
            if (pr != null && pr.isWorseThan(Result.SUCCESS)) {
                r.addAll(getCulprits(p));
            }
        }
        for (Entry e: p.getChangeSet()) {
            r.add(e.getAuthor());
        }
        
        // upstream culprits
        if (build.getPreviousNotFailedBuild() != null) {
            @SuppressWarnings("rawtypes")
            Map <AbstractProject, DependencyChange> depmap = build.getDependencyChanges(build.getPreviousSuccessfulBuild());
            for (DependencyChange dep : depmap.values()) {
                for (AbstractBuild<?,?> b : dep.getBuilds()) {
                    for (Entry entry : b.getChangeSet()) {
                        r.add(entry.getAuthor());
                    }
                    
                    // When you use ShallowDepthCloneOption#setDisableForMatrixParent,
                    // You cannot retrieve full changelogs from a child build.
                    AbstractBuild<?, ?> root = b.getRootBuild();
                    if (root != null && root != b) {
                        for (Entry entry : root.getChangeSet()) {
                            r.add(entry.getAuthor());
                        }
                    }
                }
            }
        }
        return r;
    }
    
    @Extension(optional=true)
    public static class DescriptorImpl extends RecipientProviderDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.RootCulpritsRecipientProvider_DisplayName();
        }
    }
}
