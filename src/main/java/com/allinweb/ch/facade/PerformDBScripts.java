package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.sql.*;
import lombok.Getter;
import lombok.Setter;

/**
 * PerformActions.
 *
 * @author Osvaldo Martini
 * @version 1.0
 */
public class PerformDBScripts {
    protected static volatile PerformDBScripts instance;

    @Getter
    @Setter
    public Connection conn = null;

    // Private constructor to prevent instantiation
    private PerformDBScripts() {
        // Initialize if necessary
    }

    public static PerformDBScripts getInstance() {
        if (instance == null) {
            synchronized (PerformDBScripts.class) {
                if (instance == null) {
                    instance = new PerformDBScripts();
                }
            }
        }
        return instance;
    }

    public final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    public final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    public final String CONNECTION_TYPE_SQLITE = "jdbc:sqlite:"; // no parameters needed

    private static final ARPropertyManager arPropertyManager;

    static {
        arPropertyManager = ARPropertyManager.getInstance();
    }

    public void initialize(Connection conn) {
        this.conn = conn;
    }

    public String deleteNullBlocksSQL(int botJobId) {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        String sql;

        if ("SQLite".equalsIgnoreCase(dataBaseType)) {
            // SQLite does not allow table alias in DELETE
            sql = "DELETE FROM block " + "WHERE bot_job_id = "
                    + botJobId + " " + "AND NOT EXISTS ("
                    + "  SELECT 1 FROM instruction bli "
                    + "  WHERE bli.block_id = block.id"
                    + ")";
        } else {
            // Postgres & Access support aliasing in DELETE
            sql = "DELETE FROM block b " + "WHERE b.bot_job_id = "
                    + botJobId + " " + "AND NOT EXISTS ("
                    + "  SELECT 1 FROM instruction bli "
                    + "  WHERE bli.block_id = b.id"
                    + ")";
        }
        return sql;
    }
}
