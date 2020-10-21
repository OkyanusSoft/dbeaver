package org.jkiss.dbeaver.ext.mysql.tasks;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.task.DBTTask;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.tasks.nativetool.AbstractNativeToolHandler;
import org.jkiss.dbeaver.tasks.nativetool.AbstractNativeToolSettings;
import org.jkiss.utils.CommonUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public abstract class MySQLNativeToolHandler<SETTINGS extends AbstractNativeToolSettings<BASE_OBJECT>, BASE_OBJECT extends DBSObject, PROCESS_ARG>
        extends AbstractNativeToolHandler<SETTINGS, BASE_OBJECT, PROCESS_ARG> {

    private File config;

    @Override
    protected boolean doExecute(DBRProgressMonitor monitor, DBTTask task, SETTINGS settings, Log log) throws DBException, InterruptedException {
        try {
            return super.doExecute(monitor, task, settings, log);
        } finally {
            if (config != null && !config.delete()) {
                log.debug("Failed to delete configuration file");
            }
        }
    }

    @Override
    protected void setupProcessParameters(SETTINGS settings, PROCESS_ARG process_arg, ProcessBuilder process) {
        if (!settings.isToolOverrideCredentials()) {
            String toolUserPassword = settings.getToolUserPassword();

            if (CommonUtils.isEmpty(settings.getToolUserName())) {
                toolUserPassword = settings.getDataSourceContainer().getActualConnectionConfiguration().getUserPassword();
            }

            process.environment().put("MYSQL_PWD", toolUserPassword);
        }
    }

    protected List<String> getMySQLToolCommandLine(AbstractNativeToolHandler<SETTINGS, BASE_OBJECT, PROCESS_ARG> handler, SETTINGS settings, PROCESS_ARG arg) throws IOException {
        List<String> cmd = new ArrayList<>();
        handler.fillProcessParameters(settings, arg, cmd);

        String toolUserName = settings.getToolUserName();
        String toolUserPassword = settings.getToolUserPassword();

        /*
         * Use credentials derived from connection configuration
         * if no username was specified by export configuration itself.
         * This is needed to avoid overriding empty password.
         */
        if (CommonUtils.isEmpty(toolUserName)) {
            toolUserName = settings.getDataSourceContainer().getActualConnectionConfiguration().getUserName();
            toolUserPassword = settings.getDataSourceContainer().getActualConnectionConfiguration().getUserPassword();
        }

        if (settings.isToolOverrideCredentials()) {
            config = createCredentialsFile(toolUserName, toolUserPassword);
            cmd.add(1, "--defaults-file=" + config.getAbsolutePath());
        } else {
            cmd.add("-u");
            cmd.add(toolUserName);
        }

        DBPConnectionConfiguration connectionInfo = settings.getDataSourceContainer().getActualConnectionConfiguration();
        cmd.add("--host=" + connectionInfo.getHostName());
        if (!CommonUtils.isEmpty(connectionInfo.getHostPort())) {
            cmd.add("--port=" + connectionInfo.getHostPort());
        }

        return cmd;
    }

    private static File createCredentialsFile(String username, String password) throws IOException {
        File dir = DBWorkbench.getPlatform().getTempFolder(new VoidProgressMonitor(), "mysql-native-handler"); //$NON-NLS-1$
        File cnf = new File(dir, ".my.cnf"); //$NON-NLS-1$
        cnf.deleteOnExit();

        try (Writer writer = new FileWriter(cnf)) {
            writer.write("[client]"); //$NON-NLS-1$
            writer.write("\nuser=" + CommonUtils.notEmpty(username)); //$NON-NLS-1$
            writer.write("\npassword=" + CommonUtils.notEmpty(password)); //$NON-NLS-1$
        }

        return cnf;
    }
}
