package hudson.scm.subversion;

import hudson.Extension;
import hudson.scm.SubversionSCM;
import org.kohsuke.stapler.DataBoundConstructor;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link WorkspaceUpdater} that removes all untracked files (separately in externals)  before "svn update"
 * Created by aleksey.svistunov on 22.10.2015.
 */
public class UpdateWithFullCleanUpdater extends WorkspaceUpdater {
    private static final long serialVersionUID = 7427138737745329413L;

    private static boolean isModified;

    @DataBoundConstructor
    public UpdateWithFullCleanUpdater() {}

    @Override
    public UpdateTask createTask() {
        return new TaskImpl();
    }

    // mostly "svn update" plus extra
    public static class TaskImpl extends UpdateUpdater.TaskImpl {
        /**
         *
         */
        private static final long serialVersionUID = -4120852266435704852L;

        List<File> fileToDelete = new ArrayList<File>();

        @Override
        protected void preUpdate(SubversionSCM.ModuleLocation module, File local) throws SVNException, IOException {
            listener.getLogger().println("Cleaning up " + local);
            clientManager.getWCClient().doCleanup(local, false);
            listener.getLogger().println("Root dir " + local + " cleaned up");



            clientManager.getStatusClient().setEventHandler(new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    if (event.getAction() == SVNEventAction.STATUS_EXTERNAL && event.getNodeKind() == SVNNodeKind.DIR) {
                        if (fileToDelete.contains(event.getFile())) {
                            listener.getLogger().println("Exclude an external " + event.getFile() + " from the deleting list");
                            fileToDelete.remove(event.getFile());
                        }
                        clientManager.getWCClient().doCleanup(event.getFile(), false);
                        listener.getLogger().println("An external " + event.getFile() + " cleaned up");
                    }
                }

                public void checkCancelled() throws SVNCancelException {
                }
            });


            clientManager.getStatusClient().doStatus(local, null, SVNDepth.INFINITY, false, false, true, false, new ISVNStatusHandler() {
                public void handleStatus(SVNStatus status) throws SVNException {
                    SVNStatusType s = status.getContentsStatus();
                    SVNStatusType combinedStatus = status.getCombinedNodeAndContentsStatus();
                    if (combinedStatus == SVNStatusType.STATUS_CONFLICTED || combinedStatus == SVNStatusType.STATUS_UNVERSIONED ||
                            combinedStatus == SVNStatusType.STATUS_IGNORED || combinedStatus == SVNStatusType.STATUS_MODIFIED) {
                        fileToDelete.add(status.getFile());
                    }
                }
            }, null);

            if (!fileToDelete.isEmpty()) {
                for (File file : fileToDelete) {
                    listener.getLogger().println("Deleting " + file.getName());
                    try {
                        if (file.exists()) {
                            if (file.isDirectory()) {
                                hudson.Util.deleteRecursive(file);
                            } else {
                                file.delete();
                            }
                        }
                    } catch (IOException e) {
                        throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, e));
                    }
                }
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends WorkspaceUpdaterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.UpdateWithFullCleanUpdater_DisplayName();
        }
    }
}
