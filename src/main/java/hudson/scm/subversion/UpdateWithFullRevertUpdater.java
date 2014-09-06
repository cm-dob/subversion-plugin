/*
Revert for externals
*/

package hudson.scm.subversion;

import hudson.Extension;
import hudson.scm.SubversionSCM.ModuleLocation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.IOException;

/**
 * {@link WorkspaceUpdater} that performs "svn revert" + "svn revert" of externals + "svn update"
 *
 * @author Andrey Stegantsov
 */
public class UpdateWithFullRevertUpdater extends WorkspaceUpdater {
    private static final long serialVersionUID = 585917115133281105L;

    @DataBoundConstructor
    public UpdateWithFullRevertUpdater() {
    }

    @Override
    public UpdateTask createTask() {
        return new TaskImpl();
    }

    // mostly "svn update" plus extra
    public static class TaskImpl extends UpdateUpdater.TaskImpl {
        /**
         *
         */
        private static final long serialVersionUID = -2562813147341259328L;

        private static boolean isExternal;

        @Override
        protected void preUpdate(ModuleLocation module, File local) throws SVNException, IOException {
            listener.getLogger().println("Reverting " + local + " to depth " + module.getDepthOption() + " with ignoreExternals: " + module.isIgnoreExternalsOption());
            final SVNWCClient svnwc = manager.getWCClient();
            svnwc.setIgnoreExternals(module.isIgnoreExternalsOption());
            svnwc.doRevert(new File[]{local.getCanonicalFile()}, getSvnDepth(module.getDepthOption()), null);

            // http://svnkit.com/javadoc/org/tmatesoft/svn/core/wc/SVNStatusClient.html#doStatus%28java.io.File,%20org.tmatesoft.svn.core.wc.SVNRevision,%20org.tmatesoft.svn.core.SVNDepth,%20boolean,%20boolean,%20boolean,%20boolean,%20org.tmatesoft.svn.core.wc.ISVNStatusHandler,%20java.util.Collection%29
            // If SVNBasicClient.isIgnoreExternals() returns false, then recurses into externals definitions (if any exist and depth is either SVNDepth.INFINITY or SVNDepth.UNKNOWN) after handling the main target. This calls the client notification handler (ISVNEventHandler) with the SVNEventAction.STATUS_EXTERNAL action before handling each externals definition, and with SVNEventAction.STATUS_COMPLETED after each.
            // collectParentExternals - obsolete (not used)
            manager.getStatusClient().setEventHandler(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) {
                    if (event.getAction() == SVNEventAction.STATUS_EXTERNAL) {
                        isExternal = true;
                    } else if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
                        // seems not work
                        isExternal = false;
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                }
            });
            manager.getStatusClient().doStatus(local.getCanonicalFile(), null, SVNDepth.INFINITY, false, false, true, true,
                    new ISVNStatusHandler() {
                        public void handleStatus(SVNStatus status) throws SVNException {
                            SVNStatusType s = status.getContentsStatus();
                            if (isExternal && (s == SVNStatusType.STATUS_MODIFIED || s == SVNStatusType.STATUS_CONFLICTED)) {
                                listener.getLogger().println("Reverting external " + status.getFile());
                                manager.getWCClient().doRevert(new File[]{status.getFile()}, SVNDepth.INFINITY, null);
                            }
                        }
                    }, null);
        }
    }

    @Extension
    public static class DescriptorImpl extends WorkspaceUpdaterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.UpdateWithFullRevertUpdater_DisplayName();
        }
    }
}
